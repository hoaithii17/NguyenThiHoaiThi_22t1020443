"use client";

import { clsx } from "clsx";
import { useEffect, useState } from "react";
import { useSymbol } from "@/components/providers/symbol-provider";
import { CANDLE_INTERVALS } from "@/lib/constants";
import type { CandleInterval } from "@/lib/types";

interface HeaderProps {
  symbols: string[];
}

export function Header({ symbols }: HeaderProps) {
  const { symbol, setSymbol, interval, setInterval } = useSymbol();
  const [clock, setClock] = useState("");

  useEffect(() => {
    const tick = () => {
      const now = new Date();
      setClock(
        now.toLocaleTimeString("en-US", {
          hour12: false,
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }),
      );
    };
    tick();
    const id = globalThis.setInterval(tick, 1000);
    return () => globalThis.clearInterval(id);
  }, []);

  return (
    <header className="flex h-12 items-center justify-between border-b border-zinc-800 bg-zinc-950/80 backdrop-blur-sm px-4">
      <div className="flex items-center gap-4">
        {/* Symbol Selector */}
        <select
          value={symbol}
          onChange={(e) => setSymbol(e.target.value)}
          className="rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-sm font-semibold text-zinc-100 outline-none focus:border-blue-500 transition-colors cursor-pointer"
        >
          {symbols.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>

        {/* Interval Selector */}
        <div className="flex rounded-md border border-zinc-800 overflow-hidden">
          {CANDLE_INTERVALS.map((ci) => (
            <button
              key={ci.value}
              type="button"
              onClick={() => setInterval(ci.value as CandleInterval)}
              className={clsx(
                "px-2.5 py-1 text-xs font-medium transition-colors",
                interval === ci.value
                  ? "bg-blue-600 text-white"
                  : "bg-zinc-900 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200",
              )}
            >
              {ci.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex items-center gap-4">
        <span className="text-xs text-zinc-500">OKX HFT Terminal</span>
        <span className="font-mono text-sm text-zinc-300 tabular-nums">
          {clock}
        </span>
      </div>
    </header>
  );
}
