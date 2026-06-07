import { clsx } from "clsx";

const NAV_ITEMS = [
  { icon: "◉", label: "Dashboard", href: "/", active: true },
] as const;

export function Sidebar() {
  return (
    <aside className="flex w-16 flex-col items-center border-r border-zinc-800 bg-zinc-950 py-4 gap-6">
      {/* Logo */}
      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-violet-600 text-sm font-black text-white">
        HF
      </div>

      {/* Navigation */}
      <nav className="flex flex-1 flex-col items-center gap-2">
        {NAV_ITEMS.map((item) => (
          <a
            key={item.label}
            href={item.href}
            title={item.label}
            className={clsx(
              "flex h-10 w-10 items-center justify-center rounded-lg text-sm transition-colors",
              item.active
                ? "bg-zinc-800 text-white"
                : "text-zinc-500 hover:bg-zinc-800/50 hover:text-zinc-300",
            )}
          >
            {item.icon}
          </a>
        ))}
      </nav>

      {/* Status indicator */}
      <div className="flex flex-col items-center gap-1">
        <div className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
        <span className="text-[9px] text-zinc-500">LIVE</span>
      </div>
    </aside>
  );
}
