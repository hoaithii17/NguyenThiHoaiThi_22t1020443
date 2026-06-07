// ---------------------------------------------------------------------------
// SSE stream proxy – pipes the backend EventSource through without buffering.
//
// Routing order (Next.js 16):
//   7. Dynamic route handlers  ← we land here
//   8. fallback rewrites  ← our /api/:path* catch-all lives here
//
// By moving the general rewrite to `fallback` in next.config.ts we guarantee
// this route handler wins for /api/stream/* before the rewrite can intercept.
// ---------------------------------------------------------------------------

const API_URL = process.env.API_URL ?? "https://localhost:8443";

export const dynamic = "force-dynamic";

export const GET = async (
  request: Request,
  { params }: { params: Promise<{ path: string[] }> },
): Promise<Response> => {
  const { path } = await params;
  const { searchParams } = new URL(request.url);

  const backendUrl = new URL(`/api/stream/${path.join("/")}`, API_URL);
  for (const [key, value] of searchParams) {
    backendUrl.searchParams.set(key, value);
  }

  // Mirror the client abort signal so we close the upstream fetch when the
  // browser disconnects (tab close, navigation, refresh, etc.)
  const abort = new AbortController();
  request.signal.addEventListener("abort", () => abort.abort(), { once: true });

  try {
    // Forward Last-Event-ID for gap-free reconnection
    const upstreamHeaders: Record<string, string> = {
      Accept: "text/event-stream",
    };
    const lastEventId = request.headers.get("Last-Event-ID");
    if (lastEventId) {
      upstreamHeaders["Last-Event-ID"] = lastEventId;
    }

    const upstream = await fetch(backendUrl.toString(), {
      headers: upstreamHeaders,
      signal: abort.signal,
    });

    if (!upstream.ok || !upstream.body) {
      abort.abort();
      return new Response(`Upstream SSE ${upstream.status}`, { status: 502 });
    }

    // Use an explicit pull-loop rather than piping upstream.body directly.
    // Passing a ReadableStream body through Next.js/Turbopack in dev mode can
    // stall because the runtime tries to buffer before flushing.  The pull
    // loop forces each SSE chunk to be enqueued immediately.
    const reader = upstream.body.getReader();

    const stream = new ReadableStream({
      async pull(controller) {
        try {
          const { done, value } = await reader.read();
          if (done) {
            controller.close();
          } else {
            controller.enqueue(value);
          }
        } catch {
          controller.close();
        }
      },
      cancel() {
        reader.cancel();
        abort.abort();
      },
    });

    // HTTP/2 compatible headers — do NOT send `Connection` or
    // `Transfer-Encoding` as they are prohibited in H2 (RFC 9113 §8.2.2).
    return new Response(stream, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache, no-transform",
        "X-Accel-Buffering": "no",
      },
    });
  } catch (err: unknown) {
    // AbortError = client disconnected, not a real error
    if (err instanceof DOMException && err.name === "AbortError") {
      return new Response(null, { status: 499 });
    }
    console.error("[SSE proxy] upstream error:", err);
    return new Response("Upstream connection failed", { status: 504 });
  }
};
