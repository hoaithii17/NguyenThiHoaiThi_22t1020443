"use client";

import { clsx } from "clsx";
import { memo, useEffect, useRef, useState } from "react";
import { useSymbol } from "@/components/providers/symbol-provider";
import { Card } from "@/components/ui/card";
import { useTradesStream } from "@/hooks/use-trades-stream";
import { formatPrice, formatTime, formatVolume } from "@/lib/format";
import type { Trade } from "@/lib/types";

const MAX_TRADES = 50;

// Direct API access for REST snapshot — browser already trusts the mkcert CA.
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "https://localhost:8443";

/**
 * Stable identity key for a trade.
 * Used for both React list keys and deduplication.
 * The SSE delta-poller starts from `now − 5s`, so it overlaps with
 * `initialTrades`; scanning the full 50-item window is O(50) and cheap.
 */
const makeTradeKey = (t: Trade) => `${t.ts}-${t.price}-${t.side}-${t.amount}`;

// Memoised row – only re-renders when the trade object reference changes.
// Since each trade is immutable once created this means zero unnecessary repaints
// for the 49 trades that didn't change when a new one is prepended.
const TradeRow = memo(function TradeRow({
  trade,
  flash,
}: {
  trade: Trade;
  flash: boolean;
}) {
  return (
    <div
      className={clsx(
        "grid grid-cols-[70px_1fr_1fr] gap-1 py-0.75 text-xs font-mono border-b border-zinc-900",
        flash && "animate-flash",
      )}
    >
      <span className="text-zinc-500 tabular-nums">
        {formatTime(trade.ts).slice(0, 8)}
      </span>
      <span
        className={clsx(
          "text-right tabular-nums",
          trade.side === "buy" ? "text-emerald-500" : "text-red-500",
        )}
      >
        {formatPrice(trade.price)}
      </span>
      <span className="text-right text-zinc-300 tabular-nums">
        {formatVolume(trade.amount)}
      </span>
    </div>
  );
});

interface TradeTapeProps {
  initialTrades?: Trade[];
}

export function TradeTape({ initialTrades = [] }: TradeTapeProps) {
  const { symbol } = useSymbol();
  const { data: streamTrade, status } = useTradesStream();
  const [trades, setTrades] = useState<Trade[]>(initialTrades);
  const listRef = useRef<HTMLDivElement>(null);

  // Re-seed with REST snapshot whenever the symbol changes (client-side).
  // Do NOT eagerly clear – old symbol's trades stay visible during the fetch so
  // the user never sees the "Waiting for trades…" flash.
  useEffect(() => {
    const controller = new AbortController();

    fetch(
      `${API_BASE}/api/trades?symbol=${encodeURIComponent(symbol)}&limit=${MAX_TRADES}`,
      {
        signal: controller.signal,
      },
    )
      .then((r) =>
        r.ok ? (r.json() as Promise<Trade[]>) : Promise.resolve([]),
      )
      .then((data) => {
        if (data.length > 0) setTrades(data);
      })
      .catch(() => {
        // ignore abort / network errors – SSE will fill in as trades arrive
      });

    return () => controller.abort();
  }, [symbol]);

  // Prepend each incoming SSE trade — skip if it already exists anywhere in
  // the window (the delta-poller starts at now−5s, so it overlaps with
  // initialTrades on first load and would cause duplicate React keys otherwise).
  useEffect(() => {
    if (!streamTrade) return;
    setTrades((prev) => {
      const key = makeTradeKey(streamTrade);
      if (prev.some((t) => makeTradeKey(t) === key)) return prev;
      return [streamTrade, ...prev].slice(0, MAX_TRADES);
    });
  }, [streamTrade]);

  return (
    <Card title="Trade Tape" compact className="col-span-4 row-span-2">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[10px] text-zinc-500 tabular-nums">
          {trades.length} trades
        </span>
        <div className="flex items-center gap-1.5">
          <div
            className={clsx(
              "h-1.5 w-1.5 rounded-full",
              status === "open" ? "bg-emerald-500" : "bg-yellow-500",
            )}
          />
          <span className="text-[10px] text-zinc-500 uppercase">
            {status === "open" ? "live" : status}
          </span>
        </div>
      </div>

      {/* Column headers */}
      <div className="mb-1 grid grid-cols-[70px_1fr_1fr] gap-1 text-[10px] font-semibold text-zinc-500 tracking-wider">
        <span>Time</span>
        <span className="text-right">Price</span>
        <span className="text-right">Amount</span>
      </div>

      {/* Scrollable trade list */}
      <div ref={listRef} className="max-h-95 overflow-y-auto scrollbar-thin">
        {trades.map((trade, i) => (
          // Stable key based on content – only the new top item mounts/unmounts,
          // the remaining 49 rows keep their identity and skip re-render.
          <TradeRow key={makeTradeKey(trade)} trade={trade} flash={i === 0} />
        ))}
        {trades.length === 0 && (
          <div className="py-8 text-center text-xs text-zinc-500">
            Waiting for trades…
          </div>
        )}
      </div>
    </Card>
  );
}
