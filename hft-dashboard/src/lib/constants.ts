// ---------------------------------------------------------------------------
// Constants & configuration
// ---------------------------------------------------------------------------

export const DEFAULT_SYMBOL = "BTC-USDT";
export const DEFAULT_CANDLE_INTERVAL = "1m";
export const DEFAULT_SPREAD_INTERVAL = "5s";

export const CANDLE_INTERVALS = [
  { value: "5s", label: "5s" },
  { value: "15s", label: "15s" },
  { value: "30s", label: "30s" },
  { value: "1m", label: "1m" },
  { value: "15m", label: "15m" },
  { value: "30m", label: "30m" },
  { value: "1h", label: "1h" },
] as const;

export const SPREAD_INTERVALS = [
  { value: "1s", label: "1s" },
  { value: "5s", label: "5s" },
  { value: "15s", label: "15s" },
  { value: "1m", label: "1m" },
] as const;

/** Trading-terminal color palette */
export const COLORS = {
  buy: "#22c55e",
  sell: "#ef4444",
  buyDim: "rgba(34,197,94,0.15)",
  sellDim: "rgba(239,68,68,0.15)",
  accent: "#3b82f6",
  vwap: "#f59e0b",
  bollinger: "#8b5cf6",
  bollingerBand: "rgba(139,92,246,0.15)",
  rsiLine: "#06b6d4",
  spread: "#14b8a6",
  grid: "rgba(255,255,255,0.04)",
  text: "#a1a1aa",
  textBright: "#f4f4f5",
} as const;

/** Stats refresh interval in ms */
export const STATS_REFRESH_INTERVAL = 5000;

/** Indicator (RSI / VWAP / Bollinger) refresh interval in ms */
export const INDICATOR_REFRESH_INTERVAL = 3000;

/** @deprecated Use INDICATOR_REFRESH_INTERVAL */
export const RSI_REFRESH_INTERVAL = INDICATOR_REFRESH_INTERVAL;
