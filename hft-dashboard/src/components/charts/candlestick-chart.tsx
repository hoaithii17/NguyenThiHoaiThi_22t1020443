"use client";

import {
  type CandlestickData,
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  createChart,
  type HistogramData,
  HistogramSeries,
  type IChartApi,
  type ISeriesApi,
  type Time,
} from "lightweight-charts";
import { useCallback, useEffect, useRef } from "react";
import { useSymbol } from "@/components/providers/symbol-provider";
import { Card } from "@/components/ui/card";
import { useCandlesStream } from "@/hooks/use-candles-stream";
import { useTradesStream } from "@/hooks/use-trades-stream";
import { COLORS } from "@/lib/constants";
import { toUnixSeconds } from "@/lib/format";
import type { Candle } from "@/lib/types";

// ---------------------------------------------------------------------------
// Two-layer update strategy
//
//  • Candle SSE (500ms):  authoritative OHLCV for COMPLETED candle periods.
//    The delta-poll cursor advances once per completed candle, so the forming
//    candle is NOT re-sent between period boundaries — chart appears frozen.
//
//  • Trade SSE (500ms):  live-tick the FORMING candle's close/high/low with
//    each execution price.  No bucket-timestamp math needed — we just update
//    the latest candle ts tracked in latestCandleTsRef (O(1) lookup).
//    When the candle SSE eventually delivers the authoritative OHLCV for the
//    new period it overwrites the live-ticked values.
// ---------------------------------------------------------------------------

interface CandlestickChartProps {
  initialCandles: Candle[];
}

const candleToChart = (c: Candle): CandlestickData<Time> => ({
  time: toUnixSeconds(c.ts) as Time,
  open: c.open,
  high: c.high,
  low: c.low,
  close: c.close,
});

const candleToVolume = (c: Candle): HistogramData<Time> => ({
  time: toUnixSeconds(c.ts) as Time,
  value: c.volume,
  color: c.close >= c.open ? "rgba(34,197,94,0.3)" : "rgba(239,68,68,0.3)",
});

export function CandlestickChart({ initialCandles }: CandlestickChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);
  const candleMapRef = useRef<Map<number, Candle>>(new Map());
  // O(1) tracking of the latest candle bucket ts (seconds) — avoids
  // Math.max(...map.keys()) spread on every trade tick.
  const latestCandleTsRef = useRef<number>(0);
  // Track the last time sent to chart.update to prevent out-of-order updates
  const lastChartUpdateTsRef = useRef<number>(0);

  const { data: streamCandle } = useCandlesStream();
  const { data: streamTrade } = useTradesStream();
  const { symbol } = useSymbol();

  // ── chart setup (once) ────────────────────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: COLORS.text,
        fontFamily: "var(--font-geist-mono), monospace",
        fontSize: 11,
      },
      grid: {
        vertLines: { color: COLORS.grid },
        horzLines: { color: COLORS.grid },
      },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: {
        borderColor: "rgba(255,255,255,0.08)",
        scaleMargins: { top: 0.1, bottom: 0.25 },
      },
      timeScale: {
        borderColor: "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: true,
      },
      handleScroll: { vertTouchDrag: false },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: COLORS.buy,
      downColor: COLORS.sell,
      borderUpColor: COLORS.buy,
      borderDownColor: COLORS.sell,
      wickUpColor: COLORS.buy,
      wickDownColor: COLORS.sell,
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "volume",
    });

    chart.priceScale("volume").applyOptions({
      scaleMargins: { top: 0.8, bottom: 0 },
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;

    const observer = new ResizeObserver(() => {
      if (containerRef.current) {
        chart.applyOptions({
          width: containerRef.current.clientWidth,
          height: containerRef.current.clientHeight,
        });
      }
    });
    observer.observe(containerRef.current);

    return () => {
      observer.disconnect();
      chart.remove();
      chartRef.current = null;
    };
  }, []);

  const applyCandleData = useCallback((map: Map<number, Candle>) => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return;
    const sorted = [...map.values()].sort(
      (a, b) => toUnixSeconds(a.ts) - toUnixSeconds(b.ts),
    );
    const candleData: CandlestickData<Time>[] = [];
    const volumeData: HistogramData<Time>[] = [];
    for (const c of sorted) {
      candleData.push(candleToChart(c));
      volumeData.push(candleToVolume(c));
    }
    candleSeriesRef.current.setData(candleData);
    volumeSeriesRef.current.setData(volumeData);
  }, []);

  // ── reset chart on symbol change ──────────────────────────────────────
  // biome-ignore lint/correctness/useExhaustiveDependencies: symbol is the intentional trigger
  useEffect(() => {
    candleMapRef.current = new Map();
    latestCandleTsRef.current = 0;
    lastChartUpdateTsRef.current = 0;
    candleSeriesRef.current?.setData([]);
    volumeSeriesRef.current?.setData([]);
  }, [symbol]);

  // ── bulk load server-side candles (initial seed) ─────────────────────
  useEffect(() => {
    const map = new Map<number, Candle>();
    let maxTs = 0;
    for (const c of initialCandles) {
      const t = toUnixSeconds(c.ts);
      map.set(t, c);
      if (t > maxTs) maxTs = t;
    }
    candleMapRef.current = map;
    latestCandleTsRef.current = maxTs;
    lastChartUpdateTsRef.current = maxTs;
    applyCandleData(map);
    chartRef.current?.timeScale().fitContent();
  }, [initialCandles, applyCandleData]);

  // ── candle SSE: authoritative OHLCV for completed periods ────────────
  useEffect(() => {
    if (!streamCandle || !candleSeriesRef.current || !volumeSeriesRef.current)
      return;
    const t = toUnixSeconds(streamCandle.ts);
    candleMapRef.current.set(t, streamCandle);
    if (t > latestCandleTsRef.current) latestCandleTsRef.current = t;
    // Only update if t >= lastChartUpdateTsRef
    if (t >= lastChartUpdateTsRef.current) {
      const chartCandle = candleToChart(streamCandle);
      const volumeCandle = candleToVolume(streamCandle);
      candleSeriesRef.current.update(chartCandle);
      volumeSeriesRef.current.update(volumeCandle);
      lastChartUpdateTsRef.current = t;
    }
  }, [streamCandle]);

  // ── trade SSE: live-tick the forming candle's close/high/low ─────────
  // Runs every ~500ms so the chart always reflects the latest execution
  // price — no candle period boundary wait needed.
  useEffect(() => {
    if (
      !streamTrade ||
      !candleSeriesRef.current ||
      !volumeSeriesRef.current ||
      latestCandleTsRef.current === 0
    )
      return;

    const latestTs = latestCandleTsRef.current;
    const existing = candleMapRef.current.get(latestTs);
    if (!existing) return;

    // Only forward-tick: ignore trades older than the candle's open time.
    if (Date.parse(streamTrade.ts) / 1000 < latestTs) return;

    const updated: Candle = {
      ...existing,
      close: streamTrade.price,
      high: Math.max(existing.high, streamTrade.price),
      low: Math.min(existing.low, streamTrade.price),
    };
    candleMapRef.current.set(latestTs, updated);
    // Only update if latestTs >= lastChartUpdateTsRef
    if (latestTs >= lastChartUpdateTsRef.current) {
      const chartCandle = candleToChart(updated);
      const volumeCandle = candleToVolume(updated);
      candleSeriesRef.current.update(chartCandle);
      volumeSeriesRef.current.update(volumeCandle);
      lastChartUpdateTsRef.current = latestTs;
    }
  }, [streamTrade]);

  return (
    <Card title="Price Chart" className="col-span-8 row-span-1 min-h-0">
      <div ref={containerRef} className="h-100 w-full" />
    </Card>
  );
}
