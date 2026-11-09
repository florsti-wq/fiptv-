# fIPTV Web – Heim-PC-Version
# Laedt fIPTV Web von GitHub, baut es einmal und startet es auf diesem PC.
# Sender und Streams laufen dann ueber deinen eigenen Internetanschluss (wie beim Fire TV).
# Aufruf: fIPTV-Web.bat            -> starten (beim ersten Mal: herunterladen + bauen)
#         fIPTV-Web.bat update     -> neueste Version holen und neu bauen
param([string]$Mode = "start")

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Port = 3000
$Root = Join-Path $env:LOCALAPPDATA "fIPTV-Web"
$Zip = "https://codeload.github.com/florsti-wq/fiptv-/zip/refs/heads/main"

function Info($text) { Write-Host "`n>> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host "`nFEHLER: $text" -ForegroundColor Red; Read-Host "Enter druecken zum Schliessen"; exit 1 }

# 1) Node.js pruefen
$nodeOk = $false
try {
  $v = (& node -v) -replace "^v", ""
  $p = $v.Split(".") | ForEach-Object { [int]$_ }
  $nodeOk = ($p[0] -gt 22) -or ($p[0] -eq 22 -and $p[1] -ge 19)
} catch {}
if (-not $nodeOk) {
  Write-Host "fIPTV Web braucht Node.js 22.19 oder neuer." -ForegroundColor Yellow
  $a = Read-Host "Jetzt Node.js LTS mit winget installieren? (j/n)"
  if ($a -match "^[jJyY]") {
    winget install --id OpenJS.NodeJS.LTS -e --accept-package-agreements --accept-source-agreements
    Write-Host "`nNode.js ist installiert. Bitte dieses Fenster schliessen und fIPTV-Web.bat noch einmal starten." -ForegroundColor Green
    Read-Host "Enter druecken zum Schliessen"; exit 0
  }
  Fail "Bitte Node.js von https://nodejs.org (LTS) installieren und danach neu starten."
}

New-Item -ItemType Directory -Force -Path $Root | Out-Null

# 2) TMDB-Schluessel (bleibt nur auf diesem PC, in %LOCALAPPDATA%\fIPTV-Web\.env.local)
$EnvFile = Join-Path $Root ".env.local"
if (-not (Test-Path $EnvFile) -or -not (Select-String -Path $EnvFile -Pattern "^TMDB_API_KEY=.+" -Quiet)) {
  Write-Host "`nEinmalig: dein TMDB-Schluessel (derselbe wie bei Netlify/GitHub)." -ForegroundColor Yellow
  $key = (Read-Host "TMDB-Schluessel einfuegen").Trim()
  if (-not $key) { Fail "Kein TMDB-Schluessel eingegeben." }
  Set-Content -Path $EnvFile -Value "TMDB_API_KEY=$key" -Encoding ASCII
}

# 3) Herunterladen (erstes Mal oder "update")
$App = Get-ChildItem -Path $Root -Directory -Filter "fiptv-*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Mode -eq "update" -or -not $App) {
  Info "Lade die neueste fIPTV-Version von GitHub ..."
  $tmp = Join-Path $Root "download.zip"
  Invoke-WebRequest -Uri $Zip -OutFile $tmp -UseBasicParsing
  Get-ChildItem -Path $Root -Directory -Filter "fiptv-*" | Remove-Item -Recurse -Force
  Expand-Archive -Path $tmp -DestinationPath $Root -Force
  Remove-Item $tmp -Force
  $App = Get-ChildItem -Path $Root -Directory -Filter "fiptv-*" | Select-Object -First 1
  if (-not $App) { Fail "Download fehlgeschlagen." }
}
$Web = Join-Path $App.FullName "web"
Copy-Item $EnvFile (Join-Path $Web ".env.local") -Force
Set-Location $Web

# Einstellungen der Heim-Version: eigener Sync-Server, Streams ueber diesen PC, kein Cloudflare-Weiterleiter
$env:NEXT_PUBLIC_NETLIFY_BACKEND_URL = "https://fipsite.netlify.app/.netlify/functions"
$env:NETLIFY_BACKEND_URL = "https://fipsite.netlify.app/.netlify/functions"
$env:NEXT_PUBLIC_PAYWALL_ENABLED = "false"
$env:NEXT_PUBLIC_TELEGRAM_ENABLED = "false"
$env:NEXT_PUBLIC_ALLOW_NETLIFY_MEDIA_PROXY = "true"
$env:ALLOW_NETLIFY_MEDIA_PROXY = "true"
$env:FIPTV_HOME_PROXY = "true"
$env:NEXT_PUBLIC_ARVIO_RESOLVER_URL = ""
$env:NEXT_TELEMETRY_DISABLED = "1"

# 4) Bauen (nur wenn noch nicht gebaut)
if (-not (Test-Path (Join-Path $Web ".next\BUILD_ID"))) {
  Info "Installiere Bausteine (einmalig, ein paar Minuten) ..."
  & npm ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { Fail "npm ci ist fehlgeschlagen." }
  Info "Baue fIPTV Web (einmalig, ca. 3-5 Minuten) ..."
  & node scripts/build.mjs
  if ($LASTEXITCODE -ne 0) { Fail "Der Bau ist fehlgeschlagen." }
}

# 5) Starten
Info "fIPTV Web laeuft auf http://localhost:$Port  (dieses Fenster offen lassen; schliessen = beenden)"
Start-Job -ScriptBlock { param($u) Start-Sleep 4; Start-Process $u } -ArgumentList "http://localhost:$Port" | Out-Null
& node node_modules/next/dist/bin/next start -p $Port -H 127.0.0.1
