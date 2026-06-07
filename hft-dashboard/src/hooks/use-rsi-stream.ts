"use client";

import type { RsiRow } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/**
 * SSE `event: rsi` (API v2) — each payload is a **single** RsiPoint object.
 * Delta-polled every 3s; `limit=100` seeds ~8min of history on connect.
 * The gauge only needs the latest value — no array accumulation required.
 */
export const useRsiStream = () =>
  useStreamEvent<RsiRow>("rsi", (raw) => JSON.parse(raw) as RsiRow);
