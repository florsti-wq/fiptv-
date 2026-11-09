import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const env = { ...process.env };
// Netlify CLI can replace browser variables with masked secret values. Carry
// the CI-supplied public key under a separate name until the actual Next build.
if (env.ARVIO_BUILD_APP_ANON_KEY) env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY = env.ARVIO_BUILD_APP_ANON_KEY;
delete env.ARVIO_BUILD_APP_ANON_KEY;
// fIPTV: derselbe App-Schlüssel wie beim eigenen Sync-Server (Netlify-Variable APP_ANON_KEY,
// darf auch als ganze Zeile „APP_ANON_KEY = …“ eingefügt sein)
// Fehlt die Variable, den öffentlichen App-Schlüssel von der Kopplungsseite des eigenen Servers lesen
// (er steht dort ohnehin im Seitenquelltext und ist auch in der Android-App enthalten).
if (!env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY && !env.APP_ANON_KEY) {
  try {
    const site = String(env.NEXT_PUBLIC_NETLIFY_BACKEND_URL || "https://fipsite.netlify.app/.netlify/functions").replace(/\/\.netlify\/functions\/?$/, "/");
    const html = await (await fetch(site, { signal: AbortSignal.timeout(15000) })).text();
    const found = html.match(/const APP_ANON_KEY = "([^"]+)"/)?.[1];
    if (found) { env.APP_ANON_KEY = found; console.log(`fIPTV: App-Schlüssel von ${site} übernommen (${found.length} Zeichen).`); }
  } catch (error) {
    console.warn("fIPTV: App-Schlüssel konnte nicht vom Sync-Server gelesen werden:", error?.message ?? error);
  }
}
if (!env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY && env.APP_ANON_KEY) {
  env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY = String(env.APP_ANON_KEY).trim()
    .replace(/^APP_ANON_KEY\s*[=:]\s*/i, "").trim().replace(/^["']+|["']+$/g, "");
  env.APP_ANON_KEY = env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY;
}
if (env.ARVIO_VERIFY_BUILD_CONFIG === "true") {
  const key = env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY ?? "";
  if (key.length < 40 || key.includes("*") || key.startsWith("$")) {
    throw new Error("Missing or masked public Cloud client key; refusing production build.");
  }
}
// Validate configuration before generating files or starting the build.
// One stamp is inherited by every Next worker. Remove only the obsolete public
// generated file, whose route is now built atomically with the client bundle.
env.ARVIO_BUILD_STAMP = String(Date.now());
rmSync(fileURLToPath(new URL('../public/version.json', import.meta.url)), { force: true });
const translationMode = env.ARVIO_TRANSLATION_MODE || "generate";
if (translationMode === "generate") {
  await import('./generate-translations.mjs');
} else if (translationMode === "prebuilt") {
  const { validatePrebuiltTranslations } = await import('./validate-prebuilt-translations.mjs');
  const checked = validatePrebuiltTranslations();
  console.log(`Validated ${checked.locales} packaged interface languages (${checked.phrases} phrases each).`);
} else {
  throw new Error("ARVIO_TRANSLATION_MODE must be generate or prebuilt.");
}
const result = spawnSync(process.execPath, [require.resolve("next/dist/bin/next"), "build"], { env, stdio: "inherit" });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
