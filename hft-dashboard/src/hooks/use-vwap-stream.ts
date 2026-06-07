"use client";

import type { VwapRow } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/**
 * SSE `event: vwap` (API v2) — each payload is a **single** VwapPoint object.
 * Delta-polled every 3s; `limit=100` seeds ~8min of history on connect.
 * The chart component accumulates points incrementally via `series.update()`.
 */
export const useVwapStream = () =>
  useStreamEvent<VwapRow>("vwap", (raw) => JSON.parse(raw) as VwapRow);
