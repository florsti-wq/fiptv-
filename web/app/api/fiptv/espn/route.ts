// fIPTV: Sportdaten (Bundesliga, Champions League) über die öffentliche ESPN-Schnittstelle.
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const ALLOWED = /^\/apis\/(site\/v2|v2)\/sports\/soccer\/(ger\.1|uefa\.champions)\/(teams|scoreboard|standings|summary|teams\/\d+\/schedule)$/;

export async function GET(request: NextRequest) {
  const path = request.nextUrl.searchParams.get("path") ?? "";
  if (!ALLOWED.test(path)) return NextResponse.json({ error: "Nicht erlaubt" }, { status: 400 });
  const target = new URL(`https://site.api.espn.com${path}`);
  request.nextUrl.searchParams.forEach((value, key) => { if (key !== "path") target.searchParams.set(key, value); });
  try {
    const response = await fetch(target, { headers: { "user-agent": "fIPTV" }, signal: AbortSignal.timeout(15_000), cache: "no-store" });
    const text = await response.text();
    return new NextResponse(text, {
      status: response.ok ? 200 : 502,
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
    });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "ESPN nicht erreichbar" }, { status: 502 });
  }
}
