"use client";

import "./fiptvEffects.css";
import { useEffect, useRef, useState } from "react";
import { animationsEnabled, spaceMode, spacePaletteIndex, subscribeEffects } from "@/lib/fiptvEffects";

/**
 * fIPTV: animierter Weltraum-Hintergrund (viele Sterne, dezente Nebel, ferne Galaxien).
 * Nebel und Sternenstaub werden einmal vorbereitet; pro Bild bewegen sich nur Ebenen und
 * funkelnde Sterne (max. 30 Bilder/s, pausiert im Hintergrund-Tab).
 */
const PALS: Array<Array<[number, number, number]>> = [
  [[295, 40, 45], [190, 45, 42], [255, 35, 40]],
  [[20, 45, 45], [205, 45, 45], [35, 40, 45]],
  [[155, 35, 38], [275, 40, 45], [195, 40, 42]],
];

type Star = { x: number; y: number; p: number; d: number; col: string };

export function SpaceBackground() {
  const ref = useRef<HTMLCanvasElement | null>(null);
  const [mode, setMode] = useState(0);
  const [pal, setPal] = useState(0);

  useEffect(() => {
    const read = () => { setMode(spaceMode()); setPal(spacePaletteIndex()); };
    read();
    return subscribeEffects(read);
  }, []);

  useEffect(() => {
    if (mode === 0) return undefined;
    const c = ref.current;
    if (!c) return undefined;
    const x = c.getContext("2d");
    if (!x) return undefined;
    const R = Math.random;
    let W = 0, H = 0, neb: HTMLCanvasElement | null = null, dust: HTMLCanvasElement | null = null;
    let raf = 0, last = 0, t0 = performance.now();
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const layers: Star[][] = [0, 1, 2].map((d) => Array.from({ length: [420, 260, 140][d] }, () => ({
      x: R(), y: R(), p: R() * 6.28, d,
      col: R() < 0.18 ? `hsl(${R() < 0.55 ? 210 : 38},80%,82%)` : "#fff",
    })));
    const bright = Array.from({ length: 12 }, () => ({ x: R(), y: R(), p: R() * 6.28, s: 1.2 + R() * 2.2, h: R() < 0.5 ? 205 : 40 }));

    const blob = (g: CanvasRenderingContext2D, cx: number, cy: number, r: number, h: number, s: number, l: number, a: number) => {
      const gr = g.createRadialGradient(cx, cy, 0, cx, cy, r);
      gr.addColorStop(0, `hsla(${h},${s}%,${l}%,${a})`);
      gr.addColorStop(0.5, `hsla(${h},${s}%,${l - 12}%,${a * 0.4})`);
      gr.addColorStop(1, `hsla(${h},${s}%,${l - 20}%,0)`);
      g.fillStyle = gr; g.beginPath(); g.arc(cx, cy, r, 0, 6.29); g.fill();
    };

    const build = () => {
      W = c.width = Math.round(window.innerWidth * dpr);
      H = c.height = Math.round(window.innerHeight * dpr);
      const N = document.createElement("canvas");
      N.width = Math.round(W * 0.8); N.height = Math.round(H * 0.8);
      const g = N.getContext("2d")!;
      g.globalCompositeOperation = "lighter";
      for (const [h, s, l] of PALS[pal]) {
        let px = N.width * (0.2 + R() * 0.6), py = N.height * (0.2 + R() * 0.6);
        for (let i = 0; i < 22; i++) {
          px += (R() - 0.5) * N.width * 0.07; py += (R() - 0.5) * N.height * 0.07;
          blob(g, px, py, (0.05 + R() * 0.13) * N.width, h + (R() - 0.5) * 20, s, l, 0.02 + R() * 0.03);
        }
      }
      g.globalCompositeOperation = "source-over";
      for (let i = 0; i < 40; i++) blob(g, R() * N.width, R() * N.height, (0.02 + R() * 0.06) * N.width, 240, 20, 3, 0.3);
      g.globalCompositeOperation = "lighter";
      for (let i = 0; i < 4; i++) {
        const cx = R() * N.width, cy = R() * N.height, rr = 6 + R() * 10;
        g.save(); g.translate(cx, cy); g.rotate(R() * 3); g.scale(1, 0.35);
        const gr = g.createRadialGradient(0, 0, 0, 0, 0, rr * 2);
        gr.addColorStop(0, "rgba(255,240,225,.55)"); gr.addColorStop(0.35, "rgba(190,190,230,.15)"); gr.addColorStop(1, "rgba(0,0,0,0)");
        g.fillStyle = gr; g.beginPath(); g.arc(0, 0, rr * 2, 0, 6.29); g.fill(); g.restore();
      }
      neb = N;
      const D = document.createElement("canvas");
      D.width = W; D.height = H;
      const d = D.getContext("2d")!;
      const area = Math.min(2, Math.max(0.4, (W * H) / (1920 * 1080)));
      for (let i = 0; i < 3600 * area; i++) {
        const a = R() * R() * 0.75;
        d.fillStyle = `rgba(${220 + R() * 35 | 0},${225 + R() * 30 | 0},255,${a})`;
        d.fillRect(R() * W, R() * H, 1.2, 1.2);
      }
      for (let b = 0; b < 2; b++) {
        const y0 = H * (0.3 + R() * 0.4), ang = (R() - 0.5) * 0.6;
        for (let i = 0; i < 2400 * area; i++) {
          const u = R() * W, off = (R() + R() + R() - 1.5) * H * 0.12;
          d.fillStyle = `rgba(255,255,255,${R() * 0.45})`;
          d.fillRect(u, y0 + u * Math.tan(ang) * 0.3 + off, 1.1, 1.1);
        }
      }
      dust = D;
    };

    const spike = (px: number, py: number, r: number, a: number, h: number) => {
      x.strokeStyle = `hsla(${h},80%,88%,${a * 0.45})`; x.lineWidth = 1;
      x.beginPath(); x.moveTo(px - r * 4, py); x.lineTo(px + r * 4, py); x.moveTo(px, py - r * 4); x.lineTo(px, py + r * 4); x.stroke();
      const gr = x.createRadialGradient(px, py, 0, px, py, r * 3);
      gr.addColorStop(0, `rgba(255,255,255,${a})`); gr.addColorStop(0.3, `hsla(${h},80%,80%,${a * 0.45})`); gr.addColorStop(1, "rgba(0,0,0,0)");
      x.fillStyle = gr; x.fillRect(px - r * 3, py - r * 3, r * 6, r * 6);
    };

    const draw = (t: number) => {
      x.fillStyle = "#010207"; x.fillRect(0, 0, W, H);
      const ox = Math.sin(t * 0.02) * W * 0.15 + W * 0.3, oy = Math.cos(t * 0.015) * H * 0.12 + H * 0.3, sc = 1 + Math.sin(t * 0.01) * 0.04;
      if (neb) { x.save(); x.translate(W / 2, H / 2); x.rotate(t * 0.004); x.scale(sc * 2, sc * 2); x.drawImage(neb, (-W / 2 - ox) / 2, (-H / 2 - oy) / 2); x.restore(); }
      if (dust) x.drawImage(dust, 0, 0);
      for (const L of layers) for (const s of L) {
        const px = ((s.x + t * (s.d + 1) * 0.0012) % 1) * W, py = s.y * H;
        const a = 0.45 + 0.55 * Math.abs(Math.sin(t * (0.5 + s.d * 0.35) + s.p));
        x.fillStyle = s.col; x.globalAlpha = a * (0.3 + s.d * 0.32);
        const z = (0.8 + s.d * 0.7) * dpr * 0.7;
        x.fillRect(px, py, z, z);
      }
      x.globalAlpha = 1;
      for (const b of bright) spike(((b.x + t * 0.0025) % 1) * W, b.y * H, b.s * dpr, 0.55 + 0.45 * Math.sin(t * 1.1 + b.p), b.h);
    };

    const loop = (now: number) => {
      raf = requestAnimationFrame(loop);
      if (document.hidden || now - last < 33) return;
      last = now;
      draw((now - t0) / 1000);
    };
    build();
    if (animationsEnabled()) raf = requestAnimationFrame(loop); else draw(0);
    const onResize = () => { build(); if (!animationsEnabled()) draw(0); };
    window.addEventListener("resize", onResize);
    return () => { cancelAnimationFrame(raf); window.removeEventListener("resize", onResize); t0 = 0; };
  }, [mode, pal]);

  if (mode === 0) return null;
  return <canvas ref={ref} className="fiptv-space-bg" aria-hidden="true" />;
}
