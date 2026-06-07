"use client";

import { useEffect, useState } from "react";
import { Card } from "@/components/ui/card";
import { STATS_REFRESH_INTERVAL } from "@/lib/constants";
import { formatCompact, formatTimeShort } from "@/lib/format";
import type { StatsRow } from "@/lib/types";

// Direct URL to the API — same approach as the README recommends for
// client-side fetches. The browser already trusts the mkcert CA via
// `mkcert -install`, so no proxy hop or NODE_EXTRA_CA_CERTS needed here.
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "https://localhost:8443";

interface StatItemProps {
  label: string;
  value: string;
  sub?: string;
}

const StatItem = ({ label, value, sub }: StatItemProps) => (
  <div className="flex flex-col gap-0.5">
    <span className="text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">
      {label}
    </span>
    <span className="text-lg font-bold text-zinc-100 tabular-nums leading-tight">
      {value}
    </span>
    {sub && (
      <span className="text-[10px] text-zinc-500 tabular-nums">{sub}</span>
    )}
  </div>
);

interface StatsPanelProps {
  initialStats: StatsRow | null;
}

export function StatsPanel({ initialStats }: StatsPanelProps) {
  const [stats, setStats] = useState<StatsRow | null>(initialStats);

  useEffect(() => {
    const refresh = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/indicators/stats`);
        if (!res.ok) return;
        const rows: StatsRow[] = await res.json();
        if (rows?.length > 0) setStats(rows[0]);
      } catch {
        // Retry on next tick
      }
    };
    // Fire immediately so stats are fresh on mount, then poll on the interval.
    void refresh();
    const id = globalThis.setInterval(refresh, STATS_REFRESH_INTERVAL);
    return () => globalThis.clearInterval(id);
  }, []);

  if (!stats) {
    return (
      <Card className="col-span-12">
        <div className="text-xs text-zinc-500">Loading stats…</div>
      </Card>
    );
  }

  return (
    <div className="col-span-12 grid grid-cols-5 gap-3">
      <Card compact>
        <StatItem
          label="Total Trades (24h)"
          value={formatCompact(stats.total_trades)}
        />
      </Card>
      <Card compact>
        <StatItem
          label="Volume (USD)"
          value={`$${formatCompact(stats.total_volume_usd)}`}
        />
      </Card>
      <Card compact>
        <StatItem label="Active Symbols" value={String(stats.symbols)} />
      </Card>
      <Card compact>
        <StatItem
          label="First Trade"
          value={stats.first_trade ? formatTimeShort(stats.first_trade) : "—"}
        />
      </Card>
      <Card compact>
        <StatItem
          label="Last Trade"
          value={stats.last_trade ? formatTimeShort(stats.last_trade) : "—"}
        />
      </Card>
    </div>
  );
}
