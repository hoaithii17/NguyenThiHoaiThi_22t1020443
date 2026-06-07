// ---------------------------------------------------------------------------
// TypeScript types derived from the OKX HFT Trading API OpenAPI spec v1.0.0
// ---------------------------------------------------------------------------

/** Trade execution record */
export interface Trade {
  ts: string;
  symbol: string;
  side: "buy" | "sell";
  price: number;
  amount: number;
}

/** OHLCV candlestick */
export interface Candle {
  ts: string;
  symbol: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

/** Top-5 bid/ask order book snapshot */
export interface OrderBook {
  ts: string;
  symbol: string;
  bid1Price: number;
  bid1Size: number;
  bid2Price: number;
  bid2Size: number;
  bid3Price: number;
  bid3Size: number;
  bid4Price: number;
  bid4Size: number;
  bid5Price: number;
  bid5Size: number;
  ask1Price: number;
  ask1Size: number;
  ask2Price: number;
  ask2Size: number;
  ask3Price: number;
  ask3Size: number;
  ask4Price: number;
  ask4Size: number;
  ask5Price: number;
  ask5Size: number;
  midPrice: number;
  spread: number;
}

/** Aggregated spread snapshot */
export interface Spread {
  ts: string;
  symbol: string;
  bestBid: number;
  bestAsk: number;
  spread: number;
  midPrice: number;
}

/** VWAP row from /api/indicators/vwap */
export interface VwapRow {
  ts: string;
  close: number;
  vwap: number;
}

/** Bollinger Bands row from /api/indicators/bollinger */
export interface BollingerRow {
  ts: string;
  close: number;
  sma20: number;
  upperBand: number;
  lowerBand: number;
}

/** RSI row from /api/indicators/rsi */
export interface RsiRow {
  ts: string;
  close: number;
  rsi: number;
}

/** Stats row from /api/indicators/stats */
export interface StatsRow {
  total_trades: number;
  symbols: number;
  total_volume_usd: number;
  first_trade: string;
  last_trade: string;
}

/** Order book level for normalized rendering */
export interface BookLevel {
  price: number;
  size: number;
  total: number;
}

/** Candle interval enum */
export type CandleInterval = "5s" | "15s" | "30s" | "1m" | "15m" | "30m" | "1h";

/** Spread interval enum */
export type SpreadInterval = "1s" | "5s" | "15s" | "1m";
