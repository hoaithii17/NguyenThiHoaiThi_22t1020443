"use client";

import type { OrderBook } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/** SSE `event: orderbook` — each payload is a single OrderBook snapshot */
export const useOrderBookStream = () =>
  useStreamEvent<OrderBook>("orderbook", (raw) => JSON.parse(raw) as OrderBook);
