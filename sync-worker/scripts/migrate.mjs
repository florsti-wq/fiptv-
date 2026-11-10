// fIPTV: Daten des Sync-Servers von Netlify Blobs nach Cloudflare D1 umziehen.
// Aufruf (in GitHub Actions): node scripts/migrate.mjs
// Braucht: NETLIFY_AUTH_TOKEN, NETLIFY_SITE_ID, TARGET_URL, MIGRATE_TOKEN
import { createRequire } from "node:module";
const require = createRequire(new URL("../../netlify-auth-site/package.json", import.meta.url));
const { getStore, listStores } = require("@netlify/blobs");

const { NETLIFY_AUTH_TOKEN: token, NETLIFY_SITE_ID: siteID, TARGET_URL: target, MIGRATE_TOKEN: migrateToken } = process.env;
if (!token || !siteID || !target || !migrateToken) throw new Error("NETLIFY_AUTH_TOKEN, NETLIFY_SITE_ID, TARGET_URL und MIGRATE_TOKEN werden gebraucht");

// Stores, die der Sync-Server benutzt (falls listStores nichts liefert)
const KNOWN = ["account-sync", "account-sync-events", "legacy-supabase-sync", "app-usage", "arvio-auth", "tv-auth-sessions",
  "discord-auth-sessions", "simkl-proxy-rate-limits", "account-deletion-jobs", "entitlements", "premium-funnel",
  "premium-trial-emails", "premium-member-emails", "premium-referrals", "premium-exit-survey", "stream-keys", "sports-metadata"];

let names = [];
try {
  for await (const page of listStores({ siteID, token, paginate: true })) names.push(...(page.stores || []));
} catch (error) {
  console.warn("listStores nicht möglich:", error.message);
}
names = [...new Set([...names, ...KNOWN])];
console.log("Stores:", names.join(", "));

let batch = [];
let batchBytes = 0;
let total = 0;
const flush = async () => {
  if (!batch.length) return;
  const res = await fetch(`${target}/fiptv-admin/import`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${migrateToken}` },
    body: JSON.stringify({ items: batch }),
  });
  if (!res.ok) throw new Error(`Import fehlgeschlagen: HTTP ${res.status} ${await res.text()}`);
  const { written } = await res.json();
  total += written;
  batch = [];
  batchBytes = 0;
};

const summary = {};
for (const name of names) {
  const store = getStore({ name, siteID, token });
  let count = 0;
  let bytes = 0;
  for await (const page of store.list({ paginate: true })) {
    for (const blob of page.blobs || []) {
      const entry = await store.getWithMetadata(blob.key, { type: "text" });
      if (!entry || entry.data == null) continue;
      batch.push({ store: name, key: blob.key, value: entry.data, metadata: entry.metadata || {} });
      batchBytes += entry.data.length;
      count += 1;
      bytes += entry.data.length;
      if (batch.length >= 50 || batchBytes > 4_000_000) await flush();
    }
  }
  summary[name] = { count, kb: Math.round(bytes / 1024) };
}
await flush();
console.table(summary);
console.log(`Übertragen: ${total} Einträge`);
