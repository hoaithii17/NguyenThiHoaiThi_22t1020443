import type { ReactNode } from "react";

interface DashboardShellProps {
  children: ReactNode;
}

export const DashboardShell = ({ children }: DashboardShellProps) => (
  <main className="flex-1 overflow-auto p-3 bg-zinc-950">
    <div className="grid h-full grid-cols-12 grid-rows-[auto_1fr_auto] gap-3">
      {children}
    </div>
  </main>
);
