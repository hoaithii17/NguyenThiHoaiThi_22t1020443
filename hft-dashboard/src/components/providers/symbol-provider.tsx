"use client";

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";
import { DEFAULT_CANDLE_INTERVAL, DEFAULT_SYMBOL } from "@/lib/constants";
import type { CandleInterval } from "@/lib/types";

interface SymbolContextValue {
  symbol: string;
  setSymbol: (s: string) => void;
  interval: CandleInterval;
  setInterval: (i: CandleInterval) => void;
}

const SymbolContext = createContext<SymbolContextValue | null>(null);

interface SymbolProviderProps {
  symbols: string[];
  children: ReactNode;
}

export function SymbolProvider({ children }: SymbolProviderProps) {
  const [symbol, setSymbolState] = useState(DEFAULT_SYMBOL);
  const [interval, setIntervalState] = useState<CandleInterval>(
    DEFAULT_CANDLE_INTERVAL as CandleInterval,
  );

  const setSymbol = useCallback((s: string) => setSymbolState(s), []);
  const setInterval = useCallback(
    (i: CandleInterval) => setIntervalState(i),
    [],
  );

  const value = useMemo(
    () => ({ symbol, setSymbol, interval, setInterval }),
    [symbol, setSymbol, interval, setInterval],
  );

  return (
    <SymbolContext.Provider value={value}>{children}</SymbolContext.Provider>
  );
}

export const useSymbol = (): SymbolContextValue => {
  const ctx = useContext(SymbolContext);
  if (!ctx) throw new Error("useSymbol must be used within SymbolProvider");
  return ctx;
};
