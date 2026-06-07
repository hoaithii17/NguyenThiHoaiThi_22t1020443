"use client";

import type { Spread } from "@/lib/types";
import { useStreamEvent } from "./use-stream-event";

/** SSE `event: spread` — each payload is a single Spread object */
export const useSpreadStream = () =>
  useStreamEvent<Spread>("spread", (raw) => JSON.parse(raw) as Spread);
