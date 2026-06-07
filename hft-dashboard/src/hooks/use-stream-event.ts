"use client";

import { useSymbol } from "@/components/providers/symbol-provider";
import { useSSE } from "./use-sse";

// ---------------------------------------------------------------------------
// API v2.0.0 — all SSE streams emit individual objects (not arrays).
//
// URL strategy: connect directly to the API server (NEXT_PUBLIC_API_URL).
// The browser already trusts the mkcert CA via `mkcert -install`, and the
// Spring Boot WebClientConfig has CORS configured for browser access.
// Direct connection eliminates the unreliable Next.js proxy hop and gives
// true HTTP/2 multiplexing from the browser to the API.
// ---------------------------------------------------------------------------

// Map SSE event names → URL path segments when they differ.
//   "trade"  → /api/stream/trades
//   "candle" → /api/stream/candles  (API v2: singular event, plural URL path)
const PATH_MAP: Record<string, string> = {
  trade: "trades",
  candle: "candles",
};

/**
 * Subscribe to a specific SSE event type by connecting directly to the API
 * server over HTTPS/H2.
 *
 * `event` is passed as a flat string — NOT as `{ event }` — so the React
 * Compiler never treats a new object reference as a changed dependency
 * (which would cause "Maximum update depth exceeded").
 *
 * Reconnects automatically with back-off when the symbol / interval changes.
 */
export const useStreamEvent = <T>(
  eventName: string,
  parse: (raw: string) => T,
) => {
  const { symbol, interval } = useSymbol();
  const s = encodeURIComponent(symbol);

  // NEXT_PUBLIC_API_URL is inlined at build time from .env.local
  const apiBase = process.env.NEXT_PUBLIC_API_URL ?? "https://localhost:8443";
  const path = PATH_MAP[eventName] ?? eventName;

  let url = `${apiBase}/api/stream/${path}?symbol=${s}`;
  if (path === "candles") {
    url += `&interval=${encodeURIComponent(interval)}`;
  } else if (["vwap", "bollinger", "rsi"].includes(path)) {
    url += `&limit=100`;
  }

  // Flat string — see useSSE JSDoc for why an object literal breaks here.
  return useSSE<T>(url, parse, eventName);
};
