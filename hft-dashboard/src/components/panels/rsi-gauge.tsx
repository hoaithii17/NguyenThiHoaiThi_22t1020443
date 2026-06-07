"use client";

import { clsx } from "clsx";
import { useEffect, useMemo, useState } from "react";
import { Card } from "@/components/ui/card";
import { useRsiStream } from "@/hooks/use-rsi-stream";

// ── helpers ────────────────────────────────────────────────────────────────

const getRsiZone = (
  rsi: number,
): { label: string; color: string; bg: string } => {
  if (rsi >= 70)
    return { label: "Overbought", color: "text-red-400", bg: "bg-red-500" };
  if (rsi >= 60)
    return {
      label: "Bullish",
      color: "text-emerald-400",
      bg: "bg-emerald-500",
    };
  if (rsi >= 40)
    return { label: "Neutral", color: "text-zinc-400", bg: "bg-zinc-500" };
  if (rsi >= 30)
    return { label: "Bearish", color: "text-orange-400", bg: "bg-orange-500" };
  return { label: "Oversold", color: "text-emerald-400", bg: "bg-emerald-500" };
};

// ── component ──────────────────────────────────────────────────────────────

export function RsiGauge() {
  // API v2: each SSE event is a single RsiRow (not RsiRow[])
  const { data: latestRsi, status } = useRsiStream();

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  useEffect(() => {
    if (latestRsi) setLastUpdated(new Date());
  }, [latestRsi]);

  const rsi = useMemo(
    () => (latestRsi && !Number.isNaN(latestRsi.rsi) ? latestRsi.rsi : null),
    [latestRsi],
  );
  const zone = useMemo(() => (rsi !== null ? getRsiZone(rsi) : null), [rsi]);

  const lastUpdatedLabel = useMemo(
    () =>
      lastUpdated
        ? lastUpdated.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
          })
        : null,
    [lastUpdated],
  );

  const liveBadge =
    status === "open" ? (
      <span className="flex items-center gap-1.5 text-[10px] text-zinc-500">
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-cyan-400 opacity-75" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-cyan-500" />
        </span>
        {lastUpdatedLabel ?? "LIVE"}
      </span>
    ) : status === "connecting" ? (
      <span className="text-[10px] text-zinc-600 animate-pulse">
        connecting…
      </span>
    ) : null;

  return (
    <Card title="RSI (14)" compact className="col-span-4" badge={liveBadge}>
      {rsi === null ? (
        <div className="flex h-24 items-center justify-center text-xs text-zinc-500">
          {status === "connecting" ? "Connecting…" : "Loading…"}
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 py-2">
          {/* Value */}
          <span className="text-4xl font-black tabular-nums text-zinc-100">
            {rsi.toFixed(1)}
          </span>

          {/* Bar gauge */}
          <div className="relative w-full h-2 rounded-full bg-zinc-800 overflow-hidden">
            <div className="absolute left-0 h-full w-[30%] bg-emerald-500/20 rounded-l-full" />
            <div className="absolute right-0 h-full w-[30%] bg-red-500/20 rounded-r-full" />
            <div
              className={clsx(
                "absolute top-0 h-full w-1 rounded-full transition-all duration-500",
                zone?.bg,
              )}
              style={{ left: `${Math.min(Math.max(rsi, 0), 100)}%` }}
            />
          </div>

          {/* Zone label */}
          <span className={clsx("text-xs font-semibold", zone?.color)}>
            {zone?.label}
          </span>
        </div>
      )}
    </Card>
  );
}
