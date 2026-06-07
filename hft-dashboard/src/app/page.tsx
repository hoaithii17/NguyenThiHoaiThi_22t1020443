import { Suspense } from "react";
import { CandlestickChart } from "@/components/charts/candlestick-chart";
import { IndicatorOverlays } from "@/components/charts/indicator-overlays";
import { SpreadChart } from "@/components/charts/spread-chart";
import { DashboardShell } from "@/components/layout/dashboard-shell";
import { Header } from "@/components/layout/header";
import { Sidebar } from "@/components/layout/sidebar";
import { OrderBookPanel } from "@/components/panels/order-book";
import { RsiGauge } from "@/components/panels/rsi-gauge";
import { StatsPanel } from "@/components/panels/stats-panel";
import { TradeTape } from "@/components/panels/trade-tape";
import { SymbolProvider } from "@/components/providers/symbol-provider";
import { CardSkeleton } from "@/components/ui/skeleton";
import {
  fetchBollinger,
  fetchCandles,
  fetchSpreadHistory,
  fetchStats,
  fetchSymbols,
  fetchTrades,
  fetchVwap,
} from "@/lib/api";
import { DEFAULT_CANDLE_INTERVAL, DEFAULT_SYMBOL } from "@/lib/constants";
import type { CandleInterval } from "@/lib/types";

export default async function Page() {
  // Fetch all initial data in parallel on the server
  const [
    symbols,
    candles,
    spread,
    vwapData,
    bollingerData,
    statsRows,
    initialTrades,
  ] = await Promise.all([
    fetchSymbols().catch(() => [DEFAULT_SYMBOL]),
    fetchCandles(
      DEFAULT_SYMBOL,
      DEFAULT_CANDLE_INTERVAL as CandleInterval,
    ).catch(() => []),
    fetchSpreadHistory(DEFAULT_SYMBOL).catch(() => []),
    fetchVwap(DEFAULT_SYMBOL).catch(() => []),
    fetchBollinger(DEFAULT_SYMBOL).catch(() => []),
    fetchStats().catch(() => []),
    fetchTrades(DEFAULT_SYMBOL, 50).catch(() => []),
  ]);

  const stats = statsRows.length > 0 ? statsRows[0] : null;

  return (
    <SymbolProvider symbols={symbols}>
      <div className="flex h-screen flex-1 overflow-hidden">
        <Sidebar />

        <div className="flex flex-1 flex-col min-w-0">
          <Header symbols={symbols} />

          <DashboardShell>
            {/* ── Row 1: Stats Overview ─────────────────────────────── */}
            <Suspense
              fallback={
                <div className="col-span-12 grid grid-cols-5 gap-3">
                  <CardSkeleton />
                  <CardSkeleton />
                  <CardSkeleton />
                  <CardSkeleton />
                  <CardSkeleton />
                </div>
              }
            >
              <StatsPanel initialStats={stats} />
            </Suspense>

            {/* ── Row 2: Main Chart + Order Book ───────────────────── */}
            <CandlestickChart initialCandles={candles} />
            <OrderBookPanel />

            {/* ── Row 3: Indicators + Spread + RSI ─────────────────── */}
            <IndicatorOverlays
              initialVwapData={vwapData}
              initialBollingerData={bollingerData}
            />
            <SpreadChart initialSpread={spread} />
            <RsiGauge />
          </DashboardShell>
        </div>

        {/* Trade Tape — right sidebar */}
        <div className="w-80 border-l border-zinc-800 bg-zinc-950 overflow-hidden flex flex-col">
          <TradeTape initialTrades={initialTrades} />
        </div>
      </div>
    </SymbolProvider>
  );
}
