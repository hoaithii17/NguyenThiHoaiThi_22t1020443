import { clsx } from "clsx";
import type { ReactNode } from "react";

interface CardProps {
  title?: string;
  children: ReactNode;
  className?: string;
  /** Compact padding for dense panels */
  compact?: boolean;
  /** Optional element rendered to the right of the title (e.g. live indicator) */
  badge?: ReactNode;
}

export const Card = ({
  title,
  children,
  className,
  compact,
  badge,
}: CardProps) => (
  <div
    className={clsx(
      "rounded-lg border border-zinc-800 bg-zinc-900/80 backdrop-blur-sm",
      compact ? "p-3" : "p-4",
      className,
    )}
  >
    {(title || badge) && (
      <div className="mb-3 flex items-center justify-between">
        {title && (
          <h3 className="text-xs font-semibold tracking-wider text-zinc-400 uppercase">
            {title}
          </h3>
        )}
        {badge}
      </div>
    )}
    {children}
  </div>
);
