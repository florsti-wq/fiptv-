// fIPTV: Webseite für den eigenen Sync-Server bauen.
// Kopiert die Startseite (Anmeldung/TV-Kopplung) und die Konto-Löschseite nach public-build
// und setzt dort den App-Schlüssel (APP_ANON_KEY aus den Netlify-Variablen) statt des ARVIO-Schlüssels ein.
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";

const out = "public-build";
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
for (const f of ["index.html", "delete-account.html", "assets", "_headers", "_redirects"]) {
  cpSync(f, `${out}/${f}`, { recursive: true });
}

// Gleiche tolerante Bereinigung wie im Server (_backend.js → appAnonKey)
const key = String(process.env.APP_ANON_KEY || "")
  .trim()
  .replace(/^APP_ANON_KEY\s*[=:]\s*/i, "")
  .trim()
  .replace(/^["']+|["']+$/g, "");
if (!key) {
  console.warn("fIPTV: APP_ANON_KEY fehlt – die Webseite kann sich nicht beim Server anmelden.");
}

for (const page of ["index.html", "delete-account.html"]) {
  const path = `${out}/${page}`;
  const html = readFileSync(path, "utf8");
  const replaced = html.replace(/const APP_ANON_KEY = "[^"]*";/, `const APP_ANON_KEY = ${JSON.stringify(key)};`);
  if (replaced === html) throw new Error(`fIPTV: APP_ANON_KEY-Zeile in ${page} nicht gefunden`);
  writeFileSync(path, replaced);
}
console.log(`fIPTV: Webseite gebaut (Schlüssellänge ${key.length}).`);
