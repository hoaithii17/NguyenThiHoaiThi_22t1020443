import { CardSkeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <div className="flex h-full flex-1">
      {/* Sidebar skeleton */}
      <div className="w-16 border-r border-zinc-800 bg-zinc-950" />

      <div className="flex flex-1 flex-col">
        {/* Header skeleton */}
        <div className="h-12 border-b border-zinc-800 bg-zinc-950/80" />

        {/* Content skeleton */}
        <div className="flex-1 p-3">
          <div className="grid grid-cols-12 gap-3">
            {/* Stats row */}
            <CardSkeleton className="col-span-2" />
            <CardSkeleton className="col-span-2" />
            <CardSkeleton className="col-span-3" />
            <CardSkeleton className="col-span-2" />
            <CardSkeleton className="col-span-3" />

            {/* Chart area */}
            <CardSkeleton className="col-span-8 h-110" />
            <CardSkeleton className="col-span-4 h-110" />

            {/* Bottom row */}
            <CardSkeleton className="col-span-6 h-60" />
            <CardSkeleton className="col-span-6 h-60" />
          </div>
        </div>
      </div>
    </div>
  );
}
