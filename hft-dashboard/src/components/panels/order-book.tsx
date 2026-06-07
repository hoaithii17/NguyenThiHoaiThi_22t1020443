"use client";

import { useMemo } from "react";
import { Card } from "@/components/ui/card";
import { useOrderBookStream } from "@/hooks/use-orderbook-stream";
import { formatPrice, formatVolume } from "@/lib/format";
import type { BookLevel, OrderBook as OrderBookType } from "@/lib/types";

const normalizeBook = (
  ob: OrderBookType,
): {
  bids: BookLevel[];
  asks: BookLevel[];
} => {
  const bids: BookLevel[] = [];
  const asks: BookLevel[] = [];
  let bidTotal = 0;
  let askTotal = 0;

  for (let i = 1; i <= 5; i++) {
    const bp = ob[`bid${i}Price` as keyof OrderBookType] as number;
    const bs = ob[`bid${i}Size` as keyof OrderBookType] as number;
    bidTotal += bs;
    if (bp) bids.push({ price: bp, size: bs, total: bidTotal });

    const ap = ob[`ask${i}Price` as keyof OrderBookType] as number;
    const as_ = ob[`ask${i}Size` as keyof OrderBookType] as number;
    askTotal += as_;
    if (ap) asks.push({ price: ap, size: as_, total: askTotal });
  }

  return { bids, asks };
};

export function OrderBookPanel() {
  const { data: book } = useOrderBookStream();

  // Memoize so normalizeBook only recomputes when the book reference changes,
  // not on every parent re-render (OB updates at WS tick frequency).
  const normalized = useMemo(() => (book ? normalizeBook(book) : null), [book]);

  const maxTotal = useMemo(
    () =>
      normalized
        ? Math.max(
            normalized.bids[normalized.bids.length - 1]?.total ?? 0,
            normalized.asks[normalized.asks.length - 1]?.total ?? 0,
          )
        : 0,
    [normalized],
  );

  if (!book || !normalized) {
    return (
      <Card title="Order Book" className="col-span-4 row-span-1">
        <div className="flex h-full items-center justify-center text-xs text-zinc-500">
          Waiting for data…
        </div>
      </Card>
    );
  }

  const { bids, asks } = normalized;

  return (
    <Card title="Order Book" compact className="col-span-4 row-span-1">
      {/* Header */}
      <div className="mb-1 grid grid-cols-3 text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">
        <span>Price</span>
        <span className="text-right">Size</span>
        <span className="text-right">Total</span>
      </div>

      {/* Asks (reversed so best ask is at bottom) */}
      <div className="flex flex-col-reverse">
        {asks.map((level) => (
          <div
            key={level.price}
            className="relative grid grid-cols-3 py-0.5 text-xs font-mono"
          >
            <div
              className="absolute inset-0 bg-red-500/10"
              style={{
                width: `${(level.total / maxTotal) * 100}%`,
                right: 0,
                left: "auto",
              }}
            />
            <span className="relative text-red-400">
              {formatPrice(level.price)}
            </span>
            <span className="relative text-right text-zinc-300">
              {formatVolume(level.size)}
            </span>
            <span className="relative text-right text-zinc-500">
              {formatVolume(level.total)}
            </span>
          </div>
        ))}
      </div>

      {/* Mid-price */}
      <div className="my-2 flex items-center justify-between border-y border-zinc-800 py-1.5">
        <span className="text-sm font-bold text-zinc-100 tabular-nums">
          {formatPrice(book.midPrice)}
        </span>
        <span className="text-[10px] text-zinc-500">
          Spread: {formatPrice(book.spread)}
        </span>
      </div>

      {/* Bids */}
      <div className="flex flex-col">
        {bids.map((level) => (
          <div
            key={level.price}
            className="relative grid grid-cols-3 py-0.5 text-xs font-mono"
          >
            <div
              className="absolute inset-0 bg-emerald-500/10"
              style={{
                width: `${(level.total / maxTotal) * 100}%`,
                right: 0,
                left: "auto",
              }}
            />
            <span className="relative text-emerald-400">
              {formatPrice(level.price)}
            </span>
            <span className="relative text-right text-zinc-300">
              {formatVolume(level.size)}
            </span>
            <span className="relative text-right text-zinc-500">
              {formatVolume(level.total)}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
