"use client";

import type { Candle } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/**
 * SSE `event: candle` (API v2 — singular event name, plural URL path).
 * Each payload is a **single** Candle object (delta-polled every 2s).
 * Pre-populates ~5min of history on connect.
 */
export const useCandlesStream = () =>
  useStreamEvent<Candle>("candle", (raw) => JSON.parse(raw) as Candle);
