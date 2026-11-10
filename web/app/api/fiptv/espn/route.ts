// fIPTV: Sportdaten über öffentliche Schnittstellen – ESPN (Fußball, Formel 1, Tennis)
// und TheSportsDB (WWE/AEW, freier Test-Schlüssel). Nur fest erlaubte Pfade.
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const SOCCER = /^\/apis\/(site\/v2|v2)\/sports\/soccer\/(ger\.1|ger\.2|ger\.dfb_pokal|eng\.1|esp\.1|ita\.1|fra\.1|uefa\.champions|uefa\.europa|uefa\.europa\.conf|uefa\.nations|uefa\.euro|uefa\.euroq|fifa\.world|fifa\.worldq\.uefa|fifa\.friendly|club\.friendly)\/(teams|scoreboard|standings|summary|statistics|teams\/\d+\/schedule)$/;
const OTHER = /^\/apis\/(site\/v2|v2)\/sports\/(racing\/f1\/(scoreboard|standings)|tennis\/(atp|wta)\/(scoreboard|rankings))$/;
const TSDB = /^\/tsdb\/(eventsnextleague|eventspastleague)\.php$/;

export async function GET(request: NextRequest) {
  const path = request.nextUrl.searchParams.get("path") ?? "";
  let target: URL;
  if (TSDB.test(path)) {
    const id = request.nextUrl.searchParams.get("id") ?? "";
    if (!/^(4444|4563)$/.test(id)) return NextResponse.json({ error: "Nicht erlaubt" }, { status: 400 });
    target = new URL(`https://www.thesportsdb.com/api/v1/json/123${path.slice(5)}`);
    target.searchParams.set("id", id);
  } else if (SOCCER.test(path) || OTHER.test(path)) {
    target = new URL(`https://site.api.espn.com${path}`);
    request.nextUrl.searchParams.forEach((value, key) => { if (key !== "path") target.searchParams.set(key, value); });
  } else {
    return NextResponse.json({ error: "Nicht erlaubt" }, { status: 400 });
  }
  try {
    const response = await fetch(target, { headers: { "user-agent": "Mozilla/5.0 fIPTV" }, signal: AbortSignal.timeout(15_000), cache: "no-store" });
    const text = await response.text();
    return new NextResponse(text, {
      status: response.ok ? 200 : 502,
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
    });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "Sportdaten nicht erreichbar" }, { status: 502 });
  }
}
