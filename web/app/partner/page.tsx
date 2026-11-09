import type { Metadata } from "next";
import { PartnerDashboard } from "@/components/partner/PartnerDashboard";

export const metadata: Metadata = { title: "fIPTV Partner" };
export default function PartnerPage() { return <PartnerDashboard />; }
