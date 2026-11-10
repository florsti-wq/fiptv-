// fIPTV Sync-Server auf Cloudflare Workers.
// Führt dieselben Funktionen aus wie die Netlify-Seite „fipsite“ (netlify-auth-site/),
// unter denselben Pfaden (/.netlify/functions/<name>) – Apps müssen nur die Adresse wechseln.
// Daten (Netlify Blobs) liegen in D1, die Postgres-Datenbank (DATABASE_URL) bleibt dieselbe.
import { functions } from "./functions.generated.mjs";
import * as sportsWarm from "../../netlify-auth-site/netlify/functions/sports-metadata-warm.mjs";

function pick(mod) {
  if (typeof mod.handler === "function") return { kind: "lambda", fn: mod.handler };
  if (mod.default && typeof mod.default.handler === "function") return { kind: "lambda", fn: mod.default.handler };
  if (typeof mod.default === "function") return { kind: "web", fn: mod.default };
  return null;
}

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "*",
  "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
};

async function toLambdaEvent(request, url, name) {
  const headers = {};
  const multiValueHeaders = {};
  request.headers.forEach((value, key) => {
    headers[key.toLowerCase()] = value;
    multiValueHeaders[key.toLowerCase()] = [value];
  });
  // Netlify kennt die Client-IP in diesen Kopfzeilen
  const ip = request.headers.get("cf-connecting-ip");
  if (ip) {
    headers["x-nf-client-connection-ip"] = ip;
    if (!headers["x-forwarded-for"]) headers["x-forwarded-for"] = ip;
  }
  const query = {};
  const multiQuery = {};
  url.searchParams.forEach((value, key) => {
    query[key] = value;
    (multiQuery[key] ||= []).push(value);
  });
  let body = null;
  let isBase64Encoded = false;
  if (request.method !== "GET" && request.method !== "HEAD") {
    const type = (request.headers.get("content-type") || "").toLowerCase();
    const buf = new Uint8Array(await request.arrayBuffer());
    if (!buf.length) body = null;
    else if (/^(text\/|application\/(json|x-www-form-urlencoded|xml|javascript))/.test(type) || !type) body = new TextDecoder().decode(buf);
    else {
      let bin = "";
      for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000));
      body = btoa(bin);
      isBase64Encoded = true;
    }
  }
  return {
    path: url.pathname,
    rawUrl: url.toString(),
    rawQuery: url.search.replace(/^\?/, ""),
    httpMethod: request.method,
    headers,
    multiValueHeaders,
    queryStringParameters: query,
    multiValueQueryStringParameters: multiQuery,
    body,
    isBase64Encoded,
    functionName: name,
  };
}

function fromLambdaResult(result) {
  if (!result) return new Response(null, { status: 204, headers: CORS });
  const headers = new Headers();
  for (const [key, value] of Object.entries(result.headers || {})) if (value != null) headers.set(key, String(value));
  for (const [key, values] of Object.entries(result.multiValueHeaders || {})) {
    headers.delete(key);
    for (const value of values || []) headers.append(key, String(value));
  }
  for (const [key, value] of Object.entries(CORS)) if (!headers.has(key)) headers.set(key, value);
  const status = result.statusCode || 200;
  let body = result.body ?? null;
  if (body != null && result.isBase64Encoded) body = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  if (status === 204 || status === 304) body = null;
  return new Response(body, { status, headers });
}

function bindEnv(env) {
  globalThis.__FIPTV_ENV = env;
  // Variablen/Secrets auch als process.env (nodejs_compat füllt das meist schon)
  if (globalThis.process && globalThis.process.env) {
    for (const [key, value] of Object.entries(env)) {
      if (typeof value === "string" && globalThis.process.env[key] === undefined) globalThis.process.env[key] = value;
    }
  }
}

async function runFunction(name, request, url, ctx) {
  const mod = functions[name];
  const target = mod && pick(mod);
  if (!target) return new Response(JSON.stringify({ error: "Unbekannte Funktion" }), { status: 404, headers: { ...CORS, "content-type": "application/json" } });
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (target.kind === "web") {
    const response = await target.fn(request, { waitUntil: (p) => ctx.waitUntil(p) });
    const headers = new Headers(response.headers);
    for (const [key, value] of Object.entries(CORS)) if (!headers.has(key)) headers.set(key, value);
    return new Response(response.body, { status: response.status, headers });
  }
  const event = await toLambdaEvent(request, url, name);
  // Hintergrund-Funktionen (…-background) antworten bei Netlify sofort mit 202
  if (name.endsWith("-background")) {
    ctx.waitUntil(Promise.resolve(target.fn(event, {})).catch((error) => console.error(`[${name}]`, error)));
    return new Response(null, { status: 202, headers: CORS });
  }
  try {
    return fromLambdaResult(await target.fn(event, { callbackWaitsForEmptyEventLoop: false, functionName: name }));
  } catch (error) {
    console.error(`[${name}]`, error && error.stack || error);
    return new Response(JSON.stringify({ error: "Serverfehler" }), { status: 500, headers: { ...CORS, "content-type": "application/json" } });
  }
}

export default {
  async fetch(request, env, ctx) {
    bindEnv(env);
    const url = new URL(request.url);
    if (!env.URL && globalThis.process?.env) globalThis.process.env.URL = url.origin;
    const match = url.pathname.match(/^\/(?:\.netlify\/functions|api)\/([a-z0-9-]+)\/?/i);
    if (match) return runFunction(match[1], request, url, ctx);
    if (url.pathname === "/fiptv-health") {
      const ok = await env.DB.prepare("SELECT 1 AS ok").first().then(() => true).catch(() => false);
      return Response.json({ ok, db: ok, time: new Date().toISOString() }, { headers: CORS });
    }
    // Startseite / Anmeldeseite / Konto löschen (statische Dateien)
    if (/^\/(delete|account-deletion|delete-account)(\/.*)?$/.test(url.pathname)) {
      return env.ASSETS.fetch(new Request(new URL("/delete-account.html", url), request));
    }
    return env.ASSETS.fetch(request);
  },

  async scheduled(event, env, ctx) {
    bindEnv(env);
    if (event.cron === "*/5 * * * *") {
      ctx.waitUntil(Promise.resolve(sportsWarm.default()).catch((error) => console.error("[sports-metadata-warm]", error)));
      return;
    }
    const cleanup = functions["retention-cleanup"] && pick(functions["retention-cleanup"]);
    if (cleanup) {
      const event2 = { path: "/.netlify/functions/retention-cleanup", httpMethod: "POST", headers: {}, queryStringParameters: {}, body: null, isBase64Encoded: false };
      ctx.waitUntil(Promise.resolve(cleanup.fn(event2, {})).catch((error) => console.error("[retention-cleanup]", error)));
    }
  },
};
