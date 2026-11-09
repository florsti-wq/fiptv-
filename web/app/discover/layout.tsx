import type { Metadata } from "next";

export const metadata: Metadata = { title: "Catalog packs · fIPTV", description: "Community-curated sets of Home rows for the fIPTV app." };

export default function DiscoverLayout({ children }: { children: React.ReactNode }) {
  return children;
}
