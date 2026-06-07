"use client";

import type { BollingerRow } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/**
 * SSE `event: bollinger` (API v2) — each payload is a **single**
 * BollingerBandPoint object. Delta-polled every 3s; `limit=100` seeds
 * ~8min of history on connect.
 */
export const useBollingerStream = () =>
  useStreamEvent<BollingerRow>(
    "bollinger",
    (raw) => JSON.parse(raw) as BollingerRow,
  );
