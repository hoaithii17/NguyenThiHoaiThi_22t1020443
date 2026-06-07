import { clsx } from "clsx";

interface BadgeProps {
  variant?: "buy" | "sell" | "neutral" | "accent";
  children: React.ReactNode;
  className?: string;
}

const variants: Record<string, string> = {
  buy: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  sell: "bg-red-500/15 text-red-400 border-red-500/20",
  neutral: "bg-zinc-500/15 text-zinc-400 border-zinc-500/20",
  accent: "bg-blue-500/15 text-blue-400 border-blue-500/20",
};

export const Badge = ({
  variant = "neutral",
  children,
  className,
}: BadgeProps) => (
  <span
    className={clsx(
      "inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider",
      variants[variant],
      className,
    )}
  >
    {children}
  </span>
);
