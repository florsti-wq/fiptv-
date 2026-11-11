// fIPTV: Google-Konto für YouTube in der Windows-App (Zugang je Profil auf diesem Gerät).
import type { YtChannel, YtVideo } from "@/lib/fiptvYouTube";

const KEY = (profile: string) => `fiptv.ytAccount.${profile}`;
type Stored = { refreshToken: string; name: string; thumb: string };
let access: { profile: string; token: string; until: number } | null = null;

async function call<T = Record<string, unknown>>(body: Record<string, unknown>): Promise<T> {
  const r = await fetch("/api/fiptv/youtube-account", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body), cache: "no-store" });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error((j as { error?: string }).error || "Google nicht erreichbar");
  return j as T;
}

export async function accountAvailable() {
  try { return Boolean((await call<{ available: boolean }>({ action: "status" })).available); } catch { return false; }
}

export function storedAccount(profile: string): Stored | null {
  try { const s = JSON.parse(localStorage.getItem(KEY(profile)) || "null"); return s?.refreshToken ? s : null; } catch { return null; }
}

export const startSignIn = () => call<{ deviceCode: string; userCode: string; url: string; interval: number; expiresIn: number }>({ action: "start" });

export async function pollSignIn(profile: string, deviceCode: string) {
  const r = await call<{ status: string; message?: string; refreshToken?: string; accessToken?: string; expiresIn?: number; name?: string; thumb?: string }>({ action: "poll", deviceCode });
  if (r.status === "done" && r.refreshToken) {
    localStorage.setItem(KEY(profile), JSON.stringify({ refreshToken: r.refreshToken, name: r.name ?? "Google-Konto", thumb: r.thumb ?? "" }));
    access = { profile, token: r.accessToken ?? "", until: Date.now() + (Number(r.expiresIn) || 3600) * 1000 - 60_000 };
  }
  return r;
}

async function token(profile: string): Promise<string> {
  if (access && access.profile === profile && Date.now() < access.until && access.token) return access.token;
  const s = storedAccount(profile);
  if (!s) throw new Error("Bitte zuerst mit Google anmelden.");
  try {
    const r = await call<{ accessToken: string; expiresIn: number }>({ action: "refresh", refreshToken: s.refreshToken });
    access = { profile, token: r.accessToken, until: Date.now() + (Number(r.expiresIn) || 3600) * 1000 - 60_000 };
    return r.accessToken;
  } catch (e) {
    if (e instanceof Error && e.message === "invalid_grant") { localStorage.removeItem(KEY(profile)); throw new Error("Die Google-Anmeldung ist nicht mehr gültig – bitte neu anmelden."); }
    throw e;
  }
}

export async function signOut(profile: string) {
  const s = storedAccount(profile);
  localStorage.removeItem(KEY(profile));
  access = null;
  if (s) await call({ action: "revoke", token: s.refreshToken }).catch(() => null);
}

export const subscriptions = async (profile: string) =>
  (await call<{ items: YtChannel[] }>({ action: "subscriptions", token: await token(profile) })).items;
export const playlists = async (profile: string) =>
  (await call<{ items: Array<{ id: string; title: string; thumb: string; count: number }> }>({ action: "playlists", token: await token(profile) })).items;
export const playlistVideos = async (profile: string, id: string) =>
  (await call<{ items: Array<Omit<YtVideo, "views">> }>({ action: "playlist", id, token: await token(profile) })).items as YtVideo[];
export const likedVideos = async (profile: string) =>
  (await call<{ items: Array<Omit<YtVideo, "views">> }>({ action: "liked", token: await token(profile) })).items as YtVideo[];
export const subscribe = async (profile: string, channelId: string) =>
  call({ action: "subscribe", channelId, token: await token(profile) });
