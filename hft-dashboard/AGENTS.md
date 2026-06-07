<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

## Project-specific notes

- `src/app/page.tsx` is the App Router entry point and stays server-rendered. It preloads the initial dashboard snapshots with `Promise.all()` via `src/lib/api.ts`, then passes those snapshots into client charts/panels inside `SymbolProvider`.
- Shared trading state lives in `src/components/providers/symbol-provider.tsx`. `Header` updates the active symbol / candle interval, and hooks/components read from `useSymbol()`. Follow that context instead of introducing another client state store.
- Server-side fetches use `API_URL` (`src/lib/api.ts`, `next.config.ts`). Client-side live data currently connects directly to `NEXT_PUBLIC_API_URL` (`src/hooks/use-stream-event.ts`, `src/components/panels/stats-panel.tsx`, `src/components/panels/trade-tape.tsx`). Do not assume browser code should call relative `/api/*`.
- Keep `next.config.ts` and `src/app/api/stream/[...path]/route.ts` in sync: the `/api/:path*` rewrite intentionally lives under `fallback` so `/api/stream/*` hits the route handler first and avoids SSE buffering in Next.js/Turbopack dev mode.
- SSE conventions are specific here: `src/hooks/use-sse.ts` uses `fetch()` + `ReadableStream`, not `EventSource`, so streams can reuse HTTP/2 and forward `Last-Event-ID`. Pass the event name as a flat string (`useSSE(url, parse, "trade")`), not an options object. Stream payloads are single objects, not arrays; `useStreamEvent()` maps `trade -> /api/stream/trades` and `candle -> /api/stream/candles`.
- The chart components all follow the same `lightweight-charts` pattern: create the chart once, keep series refs, bulk seed with `setData()`, then append live points with `series.update()` while guarding against out-of-order timestamps. See `src/components/charts/candlestick-chart.tsx`, `src/components/charts/indicator-overlays.tsx`, and `src/components/charts/spread-chart.tsx`.
- Local backend defaults are HTTPS at `https://localhost:8443`, not the older `http://localhost:8080` value still mentioned elsewhere. `package.json` also runs `bun --bun next dev --turbopack --experimental-https` and injects `NODE_EXTRA_CA_CERTS` for server-side TLS, so verify the current script/env setup before changing local dev instructions.
- **Scripts overview:** Any package manager (`bun`, `yarn`, `npm`, `pnpm`) can run the scripts. Requires `mkcert` on `PATH`.
  - **Linux/macOS:** `bun dev` / `yarn dev` — uses bun shell's `$(mkcert -CAROOT)/rootCA.pem` inline substitution.
  - **Windows 11:** `bun run dev:win` / `yarn dev:win` — uses `scripts/with-mkcert-ca.mjs`, a portable Node helper that calls `mkcert -CAROOT` via `child_process` and injects `NODE_EXTRA_CA_CERTS` before spawning the Next.js process. This avoids the PowerShell/cmd.exe shell-substitution problem — do **not** revert to `$(mkcert -CAROOT)` in `:win` scripts; bun shell syntax is not available when yarn/npm runs the script on Windows. The helper is machine-agnostic — no hardcoded paths.
- Match the current code style when adding files: default exports in App Router files use `function` declarations (`src/app/page.tsx`, `src/app/layout.tsx`, `src/app/loading.tsx`), while hooks and internal helpers use arrow functions. Tooling is Bun + Biome (`bun install`, `bun dev`, `bun run build`, `bun run lint`, `bun run format`) with the `@/*` import alias from `tsconfig.json`.
