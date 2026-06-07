"use client";

import {
  ColorType,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  LineSeries,
  LineStyle,
  type Time,
} from "lightweight-charts";
import { useEffect, useRef, useState } from "react";
import { useSymbol } from "@/components/providers/symbol-provider";
import { Card } from "@/components/ui/card";
import { useBollingerStream } from "@/hooks/use-bollinger-stream";
import { useVwapStream } from "@/hooks/use-vwap-stream";
import { COLORS } from "@/lib/constants";
import { toUnixSeconds } from "@/lib/format";
import type { BollingerRow, VwapRow } from "@/lib/types";

// ── types ──────────────────────────────────────────────────────────────────

interface IndicatorOverlaysProps {
  /** Server-side pre-fetched data to seed the chart before the first SSE tick */
  initialVwapData?: VwapRow[];
  initialBollingerData?: BollingerRow[];
}

// ── helpers ────────────────────────────────────────────────────────────────

/** ISO-8601 strings sort lexicographically — no Date allocation needed */
const sortByTs = <T extends { ts: string }>(rows: T[]): T[] =>
  [...rows].sort((a, b) => (a.ts < b.ts ? -1 : a.ts > b.ts ? 1 : 0));

// ── component ──────────────────────────────────────────────────────────────

export function IndicatorOverlays({
  initialVwapData,
  initialBollingerData,
}: IndicatorOverlaysProps) {
  // API v2: each SSE event is a single VwapRow / BollingerRow object
  const { data: latestVwap, status: vwapStatus } = useVwapStream();
  const { data: latestBollinger } = useBollingerStream();

  const { symbol } = useSymbol();

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  useEffect(() => {
    if (latestVwap) setLastUpdated(new Date());
  }, [latestVwap]);

  // ── chart refs ─────────────────────────────────────────────────────────
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const closeSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const vwapSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const smaSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const upperSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const lowerSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);

  // Track the latest rendered timestamp for each series group.
  // series.update() throws "Cannot update oldest data" when called with a time
  // older than the last bar — we skip those points during the initial burst.
  const vwapLatestTsRef = useRef<number>(0);
  const bollingerLatestTsRef = useRef<number>(0);

  // ── chart setup (once) ──────────────────────────────────────────────────
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
      rightPriceScale: { borderColor: "rgba(255,255,255,0.08)" },
      timeScale: {
        borderColor: "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: true,
      },
    });

    closeSeriesRef.current = chart.addSeries(LineSeries, {
      color: COLORS.textBright,
      lineWidth: 1,
      title: "Close",
    });
    vwapSeriesRef.current = chart.addSeries(LineSeries, {
      color: COLORS.vwap,
      lineWidth: 2,
      title: "VWAP",
    });
    smaSeriesRef.current = chart.addSeries(LineSeries, {
      color: COLORS.bollinger,
      lineWidth: 1,
      title: "SMA20",
    });
    upperSeriesRef.current = chart.addSeries(LineSeries, {
      color: COLORS.bollinger,
      lineWidth: 1,
      lineStyle: LineStyle.Dashed,
      title: "Upper",
    });
    lowerSeriesRef.current = chart.addSeries(LineSeries, {
      color: COLORS.bollinger,
      lineWidth: 1,
      lineStyle: LineStyle.Dashed,
      title: "Lower",
    });

    chartRef.current = chart;

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

  // ── seed chart with server-side VWAP data (bulk setData, runs once) ────
  // biome-ignore lint/correctness/useExhaustiveDependencies: intentionally runs once on mount — initialVwapData is stable server-side data
  useEffect(() => {
    if (
      !closeSeriesRef.current ||
      !vwapSeriesRef.current ||
      !initialVwapData?.length
    )
      return;

    const sorted = sortByTs(initialVwapData);
    const closeData: LineData<Time>[] = [];
    const vwapPts: LineData<Time>[] = [];
    for (const p of sorted) {
      const t = toUnixSeconds(p.ts) as Time;
      closeData.push({ time: t, value: p.close });
      vwapPts.push({ time: t, value: p.vwap });
    }
    closeSeriesRef.current.setData(closeData);
    vwapSeriesRef.current.setData(vwapPts);
    chartRef.current?.timeScale().fitContent();
    // Record the latest seeded ts so SSE updates know what's already rendered
    vwapLatestTsRef.current = toUnixSeconds(sorted[sorted.length - 1].ts);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps — intentionally once on mount

  // ── seed chart with server-side Bollinger data (bulk setData, runs once) ─
  // biome-ignore lint/correctness/useExhaustiveDependencies: intentionally runs once on mount — initialBollingerData is stable server-side data
  useEffect(() => {
    if (
      !smaSeriesRef.current ||
      !upperSeriesRef.current ||
      !lowerSeriesRef.current ||
      !initialBollingerData?.length
    )
      return;

    const sorted = sortByTs(initialBollingerData);
    const smaData: LineData<Time>[] = [];
    const upperData: LineData<Time>[] = [];
    const lowerData: LineData<Time>[] = [];
    for (const p of sorted) {
      const t = toUnixSeconds(p.ts) as Time;
      smaData.push({ time: t, value: p.sma20 });
      upperData.push({ time: t, value: p.upperBand });
      lowerData.push({ time: t, value: p.lowerBand });
    }
    smaSeriesRef.current.setData(smaData);
    upperSeriesRef.current.setData(upperData);
    lowerSeriesRef.current.setData(lowerData);
    bollingerLatestTsRef.current = toUnixSeconds(sorted[sorted.length - 1].ts);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps — intentionally once on mount

  // ── clear chart on symbol change (SSE hooks reconnect automatically) ───
  // biome-ignore lint/correctness/useExhaustiveDependencies: `symbol` is the intentional trigger — the body only mutates refs which biome can't track
  useEffect(() => {
    closeSeriesRef.current?.setData([]);
    vwapSeriesRef.current?.setData([]);
    smaSeriesRef.current?.setData([]);
    upperSeriesRef.current?.setData([]);
    lowerSeriesRef.current?.setData([]);
    vwapLatestTsRef.current = 0;
    bollingerLatestTsRef.current = 0;
  }, [symbol]);

  // ── incremental VWAP update — O(1) series.update() ─────────────────────
  useEffect(() => {
    if (!latestVwap || !closeSeriesRef.current || !vwapSeriesRef.current)
      return;

    const tNum = toUnixSeconds(latestVwap.ts);
    // Guard: skip points older than the last rendered bar to avoid
    // "Cannot update oldest data" error from lightweight-charts.
    if (tNum < vwapLatestTsRef.current) return;

    const t = tNum as Time;
    closeSeriesRef.current.update({ time: t, value: latestVwap.close });
    vwapSeriesRef.current.update({ time: t, value: latestVwap.vwap });
    vwapLatestTsRef.current = tNum;
  }, [latestVwap]);

  // ── incremental Bollinger update — O(1) series.update() ────────────────
  useEffect(() => {
    if (
      !latestBollinger ||
      !smaSeriesRef.current ||
      !upperSeriesRef.current ||
      !lowerSeriesRef.current
    )
      return;

    const tNum = toUnixSeconds(latestBollinger.ts);
    if (tNum < bollingerLatestTsRef.current) return;

    const t = tNum as Time;
    smaSeriesRef.current.update({ time: t, value: latestBollinger.sma20 });
    upperSeriesRef.current.update({
      time: t,
      value: latestBollinger.upperBand,
    });
    lowerSeriesRef.current.update({
      time: t,
      value: latestBollinger.lowerBand,
    });
    bollingerLatestTsRef.current = tNum;
  }, [latestBollinger]);

  // ── render ──────────────────────────────────────────────────────────────
  const lastUpdatedLabel = lastUpdated
    ? lastUpdated.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : null;

  const liveBadge =
    vwapStatus === "open" ? (
      <span className="flex items-center gap-1.5 text-[10px] text-zinc-500">
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-amber-400 opacity-75" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-amber-500" />
        </span>
        {lastUpdatedLabel ?? "LIVE"}
      </span>
    ) : vwapStatus === "connecting" ? (
      <span className="text-[10px] text-zinc-600 animate-pulse">
        connecting…
      </span>
    ) : null;

  return (
    <Card
      title="VWAP & Bollinger Bands"
      className="col-span-6 min-h-0"
      badge={liveBadge}
    >
      <div ref={containerRef} className="h-50 w-full" />
    </Card>
  );
}
