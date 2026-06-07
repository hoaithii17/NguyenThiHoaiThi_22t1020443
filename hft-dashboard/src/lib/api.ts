// ---------------------------------------------------------------------------
// Server-side API helpers – called from Server Components
// ---------------------------------------------------------------------------

import type {
  BollingerRow,
  Candle,
  CandleInterval,
  OrderBook,
  RsiRow,
  Spread,
  SpreadInterval,
  StatsRow,
  Trade,
  VwapRow,
} from "./types";

const BASE = process.env.API_URL ?? "https://localhost:8443";

async function fetchJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`API ${res.status}: ${path}`);
  return res.json() as Promise<T>;
}

// ── REST endpoints ─────────────────────────────────────────────────────────

export function fetchSymbols(): Promise<string[]> {
  return fetchJson<string[]>("/api/indicators/symbols");
}

export function fetchTrades(symbol: string, limit = 100): Promise<Trade[]> {
  return fetchJson<Trade[]>(
    `/api/trades?symbol=${encodeURIComponent(symbol)}&limit=${limit}`,
  );
}

export function fetchCandles(
  symbol: string,
  interval: CandleInterval = "1m",
): Promise<Candle[]> {
  return fetchJson<Candle[]>(
    `/api/candles?symbol=${encodeURIComponent(symbol)}&interval=${interval}`,
  );
}

export function fetchOrderBook(symbol: string): Promise<OrderBook[]> {
  return fetchJson<OrderBook[]>(
    `/api/orderbook?symbol=${encodeURIComponent(symbol)}`,
  );
}

export function fetchSpreadHistory(
  symbol: string,
  interval: SpreadInterval = "5s",
): Promise<Spread[]> {
  return fetchJson<Spread[]>(
    `/api/orderbook/spread?symbol=${encodeURIComponent(symbol)}&interval=${interval}`,
  );
}

export function fetchVwap(symbol: string): Promise<VwapRow[]> {
  return fetchJson<VwapRow[]>(
    `/api/indicators/vwap?symbol=${encodeURIComponent(symbol)}`,
  );
}

export function fetchBollinger(symbol: string): Promise<BollingerRow[]> {
  return fetchJson<BollingerRow[]>(
    `/api/indicators/bollinger?symbol=${encodeURIComponent(symbol)}`,
  );
}

export function fetchRsi(symbol: string): Promise<RsiRow[]> {
  return fetchJson<RsiRow[]>(
    `/api/indicators/rsi?symbol=${encodeURIComponent(symbol)}`,
  );
}

export function fetchStats(): Promise<StatsRow[]> {
  return fetchJson<StatsRow[]>("/api/indicators/stats");
}
