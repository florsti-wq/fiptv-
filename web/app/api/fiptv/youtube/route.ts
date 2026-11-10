// fIPTV: YouTube ohne Anmeldung – neueste Videos eines Kanals über den öffentlichen RSS-Feed,
// Kanal aus Link/@Name ermitteln. Abgespielt wird im offiziellen YouTube-Player (Einbettung).
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const decode = (s: string) => s
  .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
  .replace(/&#(\d+);/g, (_, n: string) => String.fromCharCode(Number(n)));

async function feed(channelId: string) {
  const response = await fetch(`https://www.youtube.com/feeds/videos.xml?channel_id=${encodeURIComponent(channelId)}`, {
    headers: { "user-agent": "Mozilla/5.0 fIPTV", "accept-language": "de-DE,de;q=0.9" },
    signal: AbortSignal.timeout(15_000),
    cache: "no-store"
  });
  if (!response.ok) throw new Error(`Kanal nicht gefunden (HTTP ${response.status})`);
  const xml = await response.text();
  const title = decode(xml.match(/<title>([^<]*)<\/title>/)?.[1] ?? "");
  const videos = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].map(([, e]) => ({
    id: e.match(/<yt:videoId>([^<]+)<\/yt:videoId>/)?.[1] ?? "",
    title: decode(e.match(/<title>([^<]*)<\/title>/)?.[1] ?? ""),
    published: e.match(/<published>([^<]+)<\/published>/)?.[1] ?? "",
    thumb: e.match(/<media:thumbnail url="([^"]+)"/)?.[1] ?? "",
    views: Number(e.match(/<media:statistics views="(\d+)"/)?.[1] ?? 0) || undefined,
    description: decode(e.match(/<media:description>([\s\S]*?)<\/media:description>/)?.[1] ?? "").slice(0, 400)
  })).filter((v) => v.id);
  return { channelId, title, videos };
}

async function resolve(input: string) {
  const text = input.trim();
  const direct = text.match(/(UC[0-9A-Za-z_-]{22})/)?.[1];
  if (direct) return direct;
  let url: string;
  if (/^https?:\/\//i.test(text)) url = text;
  else if (text.startsWith("@")) url = `https://www.youtube.com/${text}`;
  else url = `https://www.youtube.com/@${text.replace(/\s+/g, "")}`;
  const host = new URL(url).hostname.replace(/^www\.|^m\./, "");
  if (host !== "youtube.com" && host !== "youtu.be") throw new Error("Bitte einen YouTube-Link oder @Kanalnamen angeben");
  const response = await fetch(url, { headers: { "user-agent": "Mozilla/5.0 fIPTV", "accept-language": "de-DE,de;q=0.9" }, signal: AbortSignal.timeout(15_000), cache: "no-store" });
  const html = await response.text();
  const id = html.match(/"externalId":"(UC[0-9A-Za-z_-]{22})"/)?.[1]
    ?? html.match(/<link rel="canonical" href="https:\/\/www\.youtube\.com\/channel\/(UC[0-9A-Za-z_-]{22})"/)?.[1]
    ?? html.match(/"channelId":"(UC[0-9A-Za-z_-]{22})"/)?.[1];
  if (!id) throw new Error("Kanal nicht gefunden. Tipp: den Link der Kanalseite (…/channel/UC…) einfügen.");
  return id;
}

export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  try {
    const channel = params.get("channel");
    if (channel) {
      if (!/^UC[0-9A-Za-z_-]{22}$/.test(channel)) return NextResponse.json({ error: "Ungültige Kanal-ID" }, { status: 400 });
      return NextResponse.json(await feed(channel));
    }
    const input = params.get("resolve");
    if (input) {
      const id = await resolve(input);
      const data = await feed(id);
      return NextResponse.json({ id, title: data.title, thumb: data.videos[0]?.thumb ?? "" });
    }
    return NextResponse.json({ error: "channel oder resolve fehlt" }, { status: 400 });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "YouTube nicht erreichbar" }, { status: 502 });
  }
}
