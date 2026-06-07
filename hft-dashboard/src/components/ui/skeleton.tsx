import { clsx } from "clsx";

interface SkeletonProps {
  className?: string;
}

export const Skeleton = ({ className }: SkeletonProps) => (
  <div className={clsx("animate-pulse rounded bg-zinc-800/60", className)} />
);

/** Pre-built panel skeleton matching Card dimensions */
export const CardSkeleton = ({ className }: SkeletonProps) => (
  <div
    className={clsx(
      "rounded-lg border border-zinc-800 bg-zinc-900/80 p-4 space-y-3",
      className,
    )}
  >
    <Skeleton className="h-3 w-24" />
    <Skeleton className="h-32 w-full" />
    <div className="flex gap-2">
      <Skeleton className="h-3 w-16" />
      <Skeleton className="h-3 w-20" />
    </div>
  </div>
);
