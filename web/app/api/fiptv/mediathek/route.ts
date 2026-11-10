// fIPTV: Weiterleitung zur freien Mediathek-Suche von MediathekViewWeb
// (der Browser darf sie wegen CORS nicht direkt fragen).
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  const body = await request.text();
  if (body.length > 8_000) return NextResponse.json({ err: ["Anfrage zu groß"] }, { status: 413 });
  try {
    JSON.parse(body);
  } catch {
    return NextResponse.json({ err: ["Ungültige Anfrage"] }, { status: 400 });
  }
  try {
    const response = await fetch("https://mediathekviewweb.de/api/query", {
      method: "POST",
      // MediathekViewWeb erwartet JSON als text/plain
      headers: { "content-type": "text/plain", "user-agent": "fIPTV" },
      body,
      cache: "no-store",
      signal: AbortSignal.timeout(20_000)
    });
    const text = await response.text();
    return new NextResponse(text, {
      status: response.ok ? 200 : 502,
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
    });
  } catch (error) {
    return NextResponse.json({ err: [error instanceof Error ? error.message : "Mediathek nicht erreichbar"] }, { status: 502 });
  }
}
