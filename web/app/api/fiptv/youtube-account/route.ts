// fIPTV: Google-Anmeldung für YouTube (Verfahren für Fernseher/Geräte: Code + QR-Code am Handy bestätigen)
// und Abfragen mit diesem Zugang (Abos, Playlists, „Mag ich“, Abonnieren). Nur in der Windows-App eingerichtet.
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const ID = () => (process.env.FIPTV_YT_CLIENT_ID ?? "").trim();
const SECRET = () => (process.env.FIPTV_YT_CLIENT_SECRET ?? "").trim();
const API = "https://www.googleapis.com/youtube/v3";

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

async function form(url: string, body: Record<string, string>): Promise<Json> {
  const r = await fetch(url, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(body), signal: AbortSignal.timeout(15_000) });
  const text = await r.text();
  try { return JSON.parse(text); } catch { return { error: `http_${r.status}` }; }
}

const thumb = (sn: Json) => sn?.thumbnails?.medium?.url ?? sn?.thumbnails?.high?.url ?? sn?.thumbnails?.default?.url ?? "";

async function yt(path: string, token: string, params: Record<string, string>, body?: Json): Promise<Json> {
  const url = new URL(`${API}/${path}`);
  for (const [k, v] of Object.entries(params)) if (v) url.searchParams.set(k, v);
  const r = await fetch(url, {
    method: body ? "POST" : "GET",
    headers: { authorization: `Bearer ${token}`, ...(body ? { "content-type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15_000)
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) {
    const reason = j?.error?.errors?.[0]?.reason ?? j?.error?.status ?? r.status;
    throw new Error(reason === "subscriptionDuplicate" ? "Diesen Kanal hast du schon abonniert." : r.status === 401 ? "Google-Anmeldung abgelaufen – bitte neu anmelden." : `YouTube-Fehler (${reason})`);
  }
  return j;
}

async function videos(token: string, ids: string[]) {
  if (!ids.length) return [];
  const j = await yt("videos", token, { part: "snippet", id: ids.slice(0, 50).join(","), hl: "de" });
  const byId = new Map<string, Json>((j.items ?? []).map((v: Json) => [v.id, v]));
  return ids.map((id) => byId.get(id)).filter(Boolean).map((v) => ({
    id: v!.id, title: v!.snippet?.localized?.title ?? v!.snippet?.title ?? "", published: v!.snippet?.publishedAt ?? "",
    thumb: thumb(v!.snippet), channelId: v!.snippet?.channelId ?? "", channelTitle: v!.snippet?.channelTitle ?? "", description: ""
  }));
}

export async function POST(request: NextRequest) {
  const body = await request.json().catch(() => ({})) as Json;
  const action = String(body.action ?? "");
  if (action === "status") return NextResponse.json({ available: Boolean(ID() && SECRET()) });
  if (!ID() || !SECRET()) return NextResponse.json({ error: "Die Google-Anmeldung ist in dieser App-Version nicht eingerichtet." }, { status: 400 });
  try {
    switch (action) {
      case "start": {
        const j = await form("https://oauth2.googleapis.com/device/code", { client_id: ID(), scope: "https://www.googleapis.com/auth/youtube" });
        if (j.error) throw new Error(j.error === "unauthorized_client" ? "Der Google-Zugang muss vom Typ „TVs and Limited Input devices“ sein." : String(j.error_description ?? j.error));
        return NextResponse.json({ deviceCode: j.device_code, userCode: j.user_code, url: j.verification_url ?? "https://www.google.com/device", interval: Math.max(5, Number(j.interval) || 5), expiresIn: Number(j.expires_in) || 1800 });
      }
      case "poll": {
        const j = await form("https://oauth2.googleapis.com/token", { client_id: ID(), client_secret: SECRET(), device_code: String(body.deviceCode ?? ""), grant_type: "urn:ietf:params:oauth:grant-type:device_code" });
        if (j.error) return NextResponse.json({ status: j.error === "authorization_pending" ? "pending" : j.error === "slow_down" ? "slow" : "failed",
          message: j.error === "access_denied" ? "Die Anmeldung wurde am Handy abgelehnt." : j.error === "expired_token" ? "Der Code ist abgelaufen – bitte neu starten." : String(j.error_description ?? j.error) });
        const me = await yt("channels", j.access_token, { part: "snippet", mine: "true" }).catch(() => ({} as Json));
        const ch = me.items?.[0];
        return NextResponse.json({ status: "done", refreshToken: j.refresh_token, accessToken: j.access_token, expiresIn: j.expires_in, name: ch?.snippet?.title ?? "Google-Konto", thumb: thumb(ch?.snippet) });
      }
      case "refresh": {
        const j = await form("https://oauth2.googleapis.com/token", { client_id: ID(), client_secret: SECRET(), refresh_token: String(body.refreshToken ?? ""), grant_type: "refresh_token" });
        if (j.error) return NextResponse.json({ error: j.error === "invalid_grant" ? "invalid_grant" : String(j.error_description ?? j.error) }, { status: 401 });
        return NextResponse.json({ accessToken: j.access_token, expiresIn: j.expires_in });
      }
      case "revoke":
        await form("https://oauth2.googleapis.com/revoke", { token: String(body.token ?? "") }).catch(() => null);
        return NextResponse.json({ ok: true });
      case "subscriptions": {
        const out: Array<{ id: string; title: string; thumb: string }> = [];
        let page = "";
        do {
          const j = await yt("subscriptions", String(body.token), { part: "snippet", mine: "true", maxResults: "50", order: "alphabetical", pageToken: page });
          for (const it of j.items ?? []) out.push({ id: it.snippet?.resourceId?.channelId ?? "", title: it.snippet?.title ?? "", thumb: thumb(it.snippet) });
          page = j.nextPageToken ?? "";
        } while (page && out.length < 500);
        return NextResponse.json({ items: out.filter((c) => c.id) });
      }
      case "playlists": {
        const j = await yt("playlists", String(body.token), { part: "snippet,contentDetails", mine: "true", maxResults: "50", hl: "de" });
        return NextResponse.json({ items: (j.items ?? []).map((p: Json) => ({ id: p.id, title: p.snippet?.localized?.title ?? p.snippet?.title ?? "", thumb: thumb(p.snippet), count: p.contentDetails?.itemCount ?? 0 })) });
      }
      case "playlist": {
        const j = await yt("playlistItems", String(body.token), { part: "contentDetails", playlistId: String(body.id ?? ""), maxResults: "50" });
        return NextResponse.json({ items: await videos(String(body.token), (j.items ?? []).map((i: Json) => i.contentDetails?.videoId).filter(Boolean)) });
      }
      case "liked": {
        const j = await yt("videos", String(body.token), { part: "snippet", myRating: "like", maxResults: "50", hl: "de" });
        return NextResponse.json({ items: (j.items ?? []).map((v: Json) => ({ id: v.id, title: v.snippet?.localized?.title ?? v.snippet?.title ?? "", published: v.snippet?.publishedAt ?? "", thumb: thumb(v.snippet), channelId: v.snippet?.channelId ?? "", channelTitle: v.snippet?.channelTitle ?? "", description: "" })) });
      }
      case "subscribe":
        await yt("subscriptions", String(body.token), { part: "snippet" }, { snippet: { resourceId: { kind: "youtube#channel", channelId: String(body.channelId ?? "") } } });
        return NextResponse.json({ ok: true });
      default:
        return NextResponse.json({ error: "Unbekannte Aktion" }, { status: 400 });
    }
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "Google nicht erreichbar" }, { status: 502 });
  }
}
