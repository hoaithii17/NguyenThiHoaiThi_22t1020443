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
import { useCallback, useEffect, useRef } from "react";
import { Card } from "@/components/ui/card";
import { useSpreadStream } from "@/hooks/use-spread-stream";
import { COLORS } from "@/lib/constants";
import { toUnixSeconds } from "@/lib/format";
import type { Spread } from "@/lib/types";

interface SpreadChartProps {
  initialSpread: Spread[];
}

export function SpreadChart({ initialSpread }: SpreadChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const spreadSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const midSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const spreadMapRef = useRef<Map<number, Spread>>(new Map());
  const { data: streamSpread } = useSpreadStream();

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
      rightPriceScale: {
        borderColor: "rgba(255,255,255,0.08)",
      },
      timeScale: {
        borderColor: "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: true,
      },
    });

    const spreadSeries = chart.addSeries(LineSeries, {
      color: COLORS.spread,
      lineWidth: 2,
      priceScaleId: "right",
      title: "Spread",
    });

    const midSeries = chart.addSeries(LineSeries, {
      color: COLORS.accent,
      lineWidth: 1,
      lineStyle: LineStyle.Dotted,
      priceScaleId: "mid",
      title: "Mid",
    });

    chart.priceScale("mid").applyOptions({
      scaleMargins: { top: 0.1, bottom: 0.1 },
    });

    chartRef.current = chart;
    spreadSeriesRef.current = spreadSeries;
    midSeriesRef.current = midSeries;

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
    };
  }, []);

  const applySpreadData = useCallback((map: Map<number, Spread>) => {
    if (!spreadSeriesRef.current || !midSeriesRef.current) return;
    const sorted = [...map.values()].sort(
      (a, b) => toUnixSeconds(a.ts) - toUnixSeconds(b.ts),
    );
    spreadSeriesRef.current.setData(
      sorted.map(
        (s): LineData<Time> => ({
          time: toUnixSeconds(s.ts) as Time,
          value: s.spread,
        }),
      ),
    );
    midSeriesRef.current.setData(
      sorted.map(
        (s): LineData<Time> => ({
          time: toUnixSeconds(s.ts) as Time,
          value: s.midPrice,
        }),
      ),
    );
  }, []);

  // Initial data – bulk load
  useEffect(() => {
    const map = new Map<number, Spread>();
    for (const s of initialSpread) {
      map.set(toUnixSeconds(s.ts), s);
    }
    spreadMapRef.current = map;
    applySpreadData(map);
    chartRef.current?.timeScale().fitContent();
  }, [initialSpread, applySpreadData]);

  // Single SSE point – use update() instead of rebuilding the whole series
  useEffect(() => {
    if (!streamSpread) return;
    const t = toUnixSeconds(streamSpread.ts) as Time;
    spreadSeriesRef.current?.update({ time: t, value: streamSpread.spread });
    midSeriesRef.current?.update({ time: t, value: streamSpread.midPrice });
    // Keep map in sync for symbol-reset reloads
    spreadMapRef.current.set(toUnixSeconds(streamSpread.ts), streamSpread);
  }, [streamSpread]);

  return (
    <Card title="Bid-Ask Spread" className="col-span-6 min-h-0">
      <div ref={containerRef} className="h-50 w-full" />
    </Card>
  );
}
