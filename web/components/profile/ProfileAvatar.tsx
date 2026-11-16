"use client";

import { avatarGradient, avatarSrc, colorToCss, customAvatarSrc } from "@/lib/profiles";
import type { Profile } from "@/lib/types";

export function ProfileAvatarVisual({ profile, avatarImages = {}, animated = false }: {
  profile: Profile;
  avatarImages?: Record<string, string>;
  /** fIPTV: animierte Version zeigen (Profilauswahl) */
  animated?: boolean;
}) {
  const custom = customAvatarSrc(profile, avatarImages);
  if (custom) {
    return <img className="avatar-visual" src={custom} alt="" decoding="async" />;
  }
  // Custom avatar expected but its image hasn't hydrated yet — show a neutral
  // gradient placeholder rather than flashing the name initial.
  if (profile.avatarImageVersion && profile.avatarImageVersion > 0) {
    return <div className="avatar-visual avatar-pending" style={{ background: colorToCss(profile.avatarColor) }} />;
  }
  if (profile.avatarId && profile.avatarId > 0) {
    const [c1, c2] = avatarGradient(profile.avatarId);
    return (
      <div className="avatar-visual" style={{ background: `linear-gradient(180deg, ${c1}, ${c2})` }}>
        <img className="avatar-emoji" src={avatarSrc(profile.avatarId, animated)} alt="" />
      </div>
    );
  }
  return (
    <div className="avatar-visual avatar-letter" style={{ background: colorToCss(profile.avatarColor) }}>
      <span>{profile.name.charAt(0)?.toUpperCase() || "?"}</span>
    </div>
  );
}
