"use client";

import type { Trade } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/** SSE `event: trade` — each payload is a single Trade object */
export const useTradesStream = () =>
  useStreamEvent<Trade>("trade", (raw) => JSON.parse(raw) as Trade);
