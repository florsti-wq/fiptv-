import type { Metadata } from "next";

export const metadata: Metadata = { title: "Report a problem · fIPTV", description: "Send an fIPTV crash report or problem to the fIPTV Discord." };

export default function ReportLayout({ children }: { children: React.ReactNode }) {
  return children;
}
