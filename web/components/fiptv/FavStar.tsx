"use client";

import { useEffect, useRef, useState } from "react";
import { Star } from "lucide-react";
import { playEffect } from "@/lib/fiptvEffects";

/** fIPTV: Favoriten-Stern, der beim Merken kurz aufploppt (mit Klang). */
export function FavStar({ favorite, size }: { favorite: boolean; size: number }) {
  const prev = useRef(favorite);
  const [pop, setPop] = useState(0);
  useEffect(() => {
    if (favorite && !prev.current) {
      setPop((n) => n + 1);
      playEffect("star");
    }
    prev.current = favorite;
  }, [favorite]);
  return <Star key={pop} size={size} fill={favorite ? "currentColor" : "none"} className={pop > 0 && favorite ? "fiptv-star-pop" : undefined} />;
}
