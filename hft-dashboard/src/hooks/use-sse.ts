"use client";

import { useEffect, useRef, useState } from "react";

export type SseStatus = "connecting" | "open" | "closed" | "error";

// ---------------------------------------------------------------------------
// Lightweight SSE text-protocol parser for ReadableStream<Uint8Array>.
//
// Using fetch() + ReadableStream instead of EventSource because:
//   1. fetch() negotiates HTTP/2 natively — all streams multiplex over one
//      TCP connection. EventSource falls back to HTTP/1.1 (6-conn limit).
//   2. AbortController gives us silent, instant cleanup on page refresh —
//      no "connection interrupted" errors in the console.
//   3. We can pass Last-Event-ID as a header for gap-free reconnection.
// ---------------------------------------------------------------------------

interface ParsedSseEvent {
  event: string;
  data: string;
  id: string;
}

const createSseParser = (onEvent: (e: ParsedSseEvent) => void) => {
  let buf = "";
  let eventType = "message";
  let dataLines: string[] = [];
  let lastId = "";

  const dispatch = () => {
    if (dataLines.length > 0) {
      onEvent({ event: eventType, data: dataLines.join("\n"), id: lastId });
    }
    eventType = "message";
    dataLines = [];
  };

  const feed = (chunk: string) => {
    buf += chunk;
    const parts = buf.split("\n");
    buf = parts.pop() ?? "";

    for (const raw of parts) {
      const line = raw.endsWith("\r") ? raw.slice(0, -1) : raw;

      if (line === "") {
        dispatch();
        continue;
      }
      if (line.startsWith(":")) continue;

      const colon = line.indexOf(":");
      const field = colon === -1 ? line : line.slice(0, colon);
      const value =
        colon === -1
          ? ""
          : line[colon + 1] === " "
            ? line.slice(colon + 2)
            : line.slice(colon + 1);

      switch (field) {
        case "event":
          eventType = value;
          break;
        case "data":
          dataLines.push(value);
          break;
        case "id":
          if (!value.includes("\0")) lastId = value;
          break;
      }
    }
  };

  return { feed };
};

// ---------------------------------------------------------------------------
// Exponential backoff with jitter (1 s → 30 s cap)
// ---------------------------------------------------------------------------

const calcBackoff = (attempt: number, base = 1000, max = 30_000): number => {
  const exp = Math.min(base * 2 ** attempt, max);
  return exp + Math.random() * Math.min(exp * 0.3, 1000);
};

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Generic SSE hook backed by `fetch()` + `ReadableStream`.
 *
 * Connects to `url`, parses each SSE frame, filters by `event` name,
 * and returns the latest parsed value.
 *
 * IMPORTANT: `event` is a flat string parameter (NOT wrapped in an options
 * object). Passing `{ event }` creates a new object reference on every render
 * which — when the React Compiler is enabled — is tracked as a changing
 * dependency, causing the effect to re-run every render and producing
 * "Maximum update depth exceeded". Both deps (`url`, `event`) are stable
 * string primitives that never change between renders.
 *
 * Reconnects automatically with exponential back-off and forwards
 * `Last-Event-ID` so the server can resume from where it left off.
 */
export const useSSE = <T>(
  url: string | null,
  parse: (raw: string) => T,
  /** SSE event name to listen for (default: "message") */
  event = "message",
): { data: T | null; status: SseStatus } => {
  const [data, setData] = useState<T | null>(null);
  const [status, setStatus] = useState<SseStatus>(
    url ? "connecting" : "closed",
  );

  // Stable ref so parse fn identity never causes reconnects
  const parseRef = useRef(parse);
  parseRef.current = parse;

  // Persist last event ID across reconnections (reset on URL change)
  const lastIdRef = useRef("");

  useEffect(() => {
    if (!url) {
      setData(null);
      setStatus("closed");
      return;
    }

    let active = true;
    let abortController: AbortController | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;

    setData(null);
    setStatus("connecting");
    lastIdRef.current = "";

    const run = async () => {
      while (active) {
        abortController = new AbortController();

        try {
          const headers: Record<string, string> = {
            Accept: "text/event-stream",
          };
          if (lastIdRef.current) {
            headers["Last-Event-ID"] = lastIdRef.current;
          }

          const res = await fetch(url, {
            signal: abortController.signal,
            headers,
            cache: "no-store",
          });

          if (!res.ok || !res.body) {
            throw new Error(`SSE upstream ${res.status}`);
          }

          if (active) {
            setStatus("open");
            attempt = 0;
          }

          const reader = res.body.getReader();
          const decoder = new TextDecoder();

          const { feed } = createSseParser((e) => {
            if (e.id) lastIdRef.current = e.id;
            if (e.event === event && active) {
              try {
                setData(parseRef.current(e.data));
              } catch {
                /* malformed payload — skip */
              }
            }
          });

          while (active) {
            const { done, value } = await reader.read();
            if (done) break;
            feed(decoder.decode(value, { stream: true }));
          }
        } catch (err: unknown) {
          if (err instanceof DOMException && err.name === "AbortError") return;
          if (!active) return;
        }

        if (!active) return;
        setStatus("error");
        const delay = calcBackoff(attempt);
        attempt = Math.min(attempt + 1, 10);

        await new Promise<void>((resolve) => {
          reconnectTimer = setTimeout(resolve, delay);
        });
      }
    };

    run();

    return () => {
      active = false;
      abortController?.abort();
      if (reconnectTimer) clearTimeout(reconnectTimer);
    };
  }, [url, event]); // both stable string primitives — never change on re-render

  return { data, status };
};
