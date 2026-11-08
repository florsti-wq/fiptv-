# fIPTV – private Familien-Version auf Basis von ARVIO

Dieses Repository ist ein Fork von [ARVIO](https://github.com/ProdigyV21/ARVIO) (Apache License 2.0, siehe `LICENSE`).
Alle Rechte am Original liegen bei den ARVIO-Autoren. fIPTV ist kein offizielles ARVIO-Produkt.

## Änderungen gegenüber ARVIO
- App-Name „fIPTV“, App-ID `com.fiptv.tv` (läuft neben dem Original-ARVIO).
- Versionsnummer: ARVIO-Version + Build-Nummer (z. B. 2.3.1.5), Selbst-Update aus den Releases dieses Forks.
- Keine Absturz-/Nutzungsberichte an das Firebase-Projekt von ARVIO.
- Workflows von ARVIO für Website/Resolver/Unraid entfernt; neuer Workflow `fiptv-apk.yml` baut die APK.
- Geplant: Bereich „Mediatheken“ (ARD, ZDF, arte, 3sat, … über MediathekViewWeb), Bereich „YouTube“ (eigene Abos je Profil, offizieller Player), eigener Sync-Server (Supabase).

## APK bauen
GitHub → Actions → „fIPTV APK bauen“ → „Run workflow“ (läuft auch automatisch bei jeder Änderung).
Download (immer neueste Version): https://github.com/florsti-wq/fiptv-/releases/latest/download/fiptv.apk

Benötigte Repository-Secrets: `FIPTV_KEYSTORE_B64`, `FIPTV_KEYSTORE_PASSWORD` (Pflicht), `TMDB_API_KEY` (empfohlen),
später `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `APP_ANON_KEY`, `NETLIFY_BACKEND_URL`, `GOOGLE_WEB_CLIENT_ID`, `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `TRAKT_*`.
