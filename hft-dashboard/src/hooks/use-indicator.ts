"use client";

import { useCallback, useEffect, useRef, useState } from "react";

interface UseIndicatorResult<T> {
  data: T | null;
  lastUpdated: Date | null;
  loading: boolean;
}

/**
 * Polls a REST indicator endpoint at a fixed interval.
 * Automatically resets state when `url` changes (e.g. on symbol switch),
 * so the previous symbol's data is never shown for the new symbol.
 */
export const useIndicator = <T>(
  url: string | null,
  intervalMs: number,
): UseIndicatorResult<T> => {
  const [data, setData] = useState<T | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [loading, setLoading] = useState(true);
  const activeRef = useRef(true);

  const fetchData = useCallback(async () => {
    if (!url || !activeRef.current) return;
    try {
      const res = await fetch(url);
      if (!res.ok || !activeRef.current) return;
      const json = (await res.json()) as T;
      if (activeRef.current) {
        setData(json);
        setLastUpdated(new Date());
        setLoading(false);
      }
    } catch {
      // Retry on next interval tick
    }
  }, [url]);

  useEffect(() => {
    activeRef.current = true;
    // Reset stale data immediately so the consumer shows a loading state
    setLoading(true);
    setData(null);
    setLastUpdated(null);
    fetchData();
    const id = globalThis.setInterval(fetchData, intervalMs);
    return () => {
      activeRef.current = false;
      globalThis.clearInterval(id);
    };
  }, [fetchData, intervalMs]);

  return { data, lastUpdated, loading };
};
