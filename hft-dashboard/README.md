# OKX HFT Dashboard

Real-time high-frequency trading dashboard powered by [OKX](https://www.okx.com/) market data, [QuestDB](https://questdb.io/) time-series storage, and [Next.js 16](https://nextjs.org/).

![Next.js 16](https://img.shields.io/badge/Next.js-16.2.1-black)
![React 19](https://img.shields.io/badge/React-19.2.4-61dafb)
![TradingView Charts](https://img.shields.io/badge/Charts-lightweight--charts%20v5-2962FF)
![Tailwind CSS 4](https://img.shields.io/badge/Tailwind-v4-38bdf8)

---

## Features

| Panel | Description | Data source |
|---|---|---|
| **Candlestick Chart** | OHLCV + volume histogram via TradingView lightweight-charts | SSE `/api/stream/candles` |
| **Order Book** | 5-level bid/ask depth with visual bars, mid-price & spread | REST `/api/orderbook` (500 ms poll) |
| **Trade Tape** | Scrolling real-time trade feed with buy/sell color coding | SSE `/api/stream/trades` |
| **Spread Chart** | Bid-ask spread + mid-price line chart | SSE `/api/stream/spread` |
| **VWAP & Bollinger** | Close price with VWAP overlay and 20-period Bollinger Bands | REST `/api/indicators/vwap`, `/api/indicators/bollinger` |
| **RSI Gauge** | 14-period RSI value with overbought/oversold visual zones | REST `/api/indicators/rsi` (5 s poll) |
| **Stats Overview** | Total trades, USD volume, active symbols, first/last trade | REST `/api/indicators/stats` (5 s poll) |
| **Symbol Selector** | Switch between trading pairs (BTC-USDT, ETH-USDT, SOL-USDT …) | REST `/api/indicators/symbols` |
| **Interval Selector** | Candle intervals: 5 s, 15 s, 30 s, 1 m, 15 m, 30 m, 1 h | — |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Browser                              │
│  ┌────────────┐  ┌──────────┐  ┌───────────────────────┐ │
│  │  Sidebar   │  │  Header  │  │   Dashboard Grid      │ │
│  │  (Server)  │  │ (Client) │  │  Charts · Panels · …  │ │
│  └────────────┘  └──────────┘  └───────────────────────┘ │
│        ▲              ▲                ▲                 │
│        │         SymbolProvider        │                 │
│        │         (React Context)       │                 │
│        │              │            useSSE hooks          │
└────────┼──────────────┼───────────────┼──────────────────┘
         │              │               │
    next.config.ts rewrites  ──────►  /api/*
                                        │
                              ┌─────────▼──────────┐
                              │  OKX HFT Backend   │
                              │  localhost:8080    │
                              │  (QuestDB + OKX)   │
                              └────────────────────┘
```

- **Server Components** (`page.tsx`) fetch initial snapshots at request time via `Promise.all()`.
- **Client Components** hydrate with that data, then subscribe to SSE streams for live updates.
- **API proxy** — `next.config.ts` rewrites `/api/*` → backend, eliminating CORS.
- **No external state library** — React Context (`SymbolProvider`) is enough for symbol + interval.

## Project Structure

```
src/
├── app/                         # Next.js App Router
│   ├── layout.tsx               # Root layout (dark theme, Geist Mono)
│   ├── loading.tsx              # Skeleton loading state
│   ├── page.tsx                 # Server Component entry point
│   └── globals.css              # Tailwind v4 dark trading theme
├── lib/                         # Shared utilities (server-safe)
│   ├── types.ts                 # TypeScript types from OpenAPI spec
│   ├── api.ts                   # Server-side fetch + QuestDB parsers
│   ├── constants.ts             # Colors, intervals, polling rates
│   └── format.ts                # Price / time / volume formatters
├── hooks/                       # Client-side React hooks
│   ├── use-sse.ts               # Generic EventSource with auto-reconnect
│   ├── use-trades-stream.ts     # SSE → Trade[]
│   ├── use-candles-stream.ts    # SSE → Candle[]
│   └── use-spread-stream.ts     # SSE → Spread[]
└── components/
    ├── providers/symbol-provider.tsx   # Symbol + interval context
    ├── layout/                  # Shell, sidebar, header
    ├── charts/                  # TradingView lightweight-charts panels
    ├── panels/                  # Order book, trade tape, stats, RSI
    └── ui/                      # Card, Badge, Skeleton primitives
```

## Prerequisites

- **Bun** ≥ 1.1 ([install](https://bun.sh/docs/installation))
- **OKX HFT Backend** running at `http://localhost:8080` (or set `API_URL` env var)

## Getting Started

```bash
# Install dependencies
bun install

# Start the dev server (http://localhost:3000)
bun dev
```

To point at a different backend:

```bash
API_URL=http://your-backend:8080 bun dev
```

## Scripts

| Command | Description |
|---|---|
| `bun dev` | Start development server (Turbopack) |
| `bun run build` | Production build |
| `bun start` | Serve production build |
| `bun run lint` | Run Biome linter |
| `bun run format` | Auto-format with Biome |

## Coding Conventions

- **Traditional `function` declarations** for default exports (pages, layouts, route handlers).
- **Arrow functions** for internal components, hooks, and helpers.
- **Biome** for linting and formatting (no ESLint / Prettier).
- **Tailwind CSS v4** utility-first styling, dark theme only.
- Components follow the Server / Client split — `"use client"` only where interactivity or browser APIs are required.

## Tech Stack

| Layer | Technology                                             |
|---|--------------------------------------------------------|
| Runtime | Bun 1.x                                                |
| Framework | Next.js 16.2.1 (App Router, Turbopack, React Compiler) |
| UI | React 19.2.4, Tailwind CSS v4                          |
| Charts | TradingView lightweight-charts v5                      |
| Linting | Biome 2.2                                              |
| Backend | OKX HFT Trading API Service (Java + QuestDB)           |
| Data | Real-time OKX WebSocket → QuestDB → REST/SSE           |

## License

Private — not for redistribution.
