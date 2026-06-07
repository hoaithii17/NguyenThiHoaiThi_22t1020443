// ---------------------------------------------------------------------------
// Formatters – price, time, volume
// ---------------------------------------------------------------------------

/** Format a price dynamically based on its value */
export function formatPrice(value: number): string {
  if (value === 0) return "0.00";
  const absVal = Math.abs(value);
  let decimals = 2;
  if (absVal < 0.00001) decimals = 8;
  else if (absVal < 0.001) decimals = 6;
  else if (absVal < 1) decimals = 4;
  else if (absVal < 100) decimals = 3;

  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/** Format large numbers compactly: 1.2M, 340K */
export function formatCompact(value: number): string {
  return new Intl.NumberFormat("en-US", {
    notation: "compact",
    maximumFractionDigits: 2,
  }).format(value);
}

/** Format volume dynamically to show small amounts without truncating to 0 */
export function formatVolume(value: number): string {
  if (value === 0) return "0.0000";
  const absVal = Math.abs(value);
  let decimals = 4;
  if (absVal < 0.00001) decimals = 8;
  else if (absVal < 0.001) decimals = 6;
  else if (absVal < 0.1) decimals = 5;

  // To avoid long trailing zeros on integers, we don't force minimumFractionDigits
  // to be the same as maximum unless it's small, but actually we can just use maximum.
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/** Format spread in basis points */
export function formatBps(spread: number, mid: number): string {
  if (mid === 0) return "0.00 bps";
  return `${((spread / mid) * 10_000).toFixed(2)} bps`;
}

/** Format ISO timestamp to HH:MM:SS.mmm */
export function formatTime(iso: string): string {
  const d = new Date(iso);
  const h = d.getHours().toString().padStart(2, "0");
  const m = d.getMinutes().toString().padStart(2, "0");
  const s = d.getSeconds().toString().padStart(2, "0");
  const ms = d.getMilliseconds().toString().padStart(3, "0");
  return `${h}:${m}:${s}.${ms}`;
}

/** Format ISO timestamp to HH:MM:SS */
export function formatTimeShort(iso: string): string {
  const d = new Date(iso);
  const h = d.getHours().toString().padStart(2, "0");
  const m = d.getMinutes().toString().padStart(2, "0");
  const s = d.getSeconds().toString().padStart(2, "0");
  return `${h}:${m}:${s}`;
}

/** ISO → Unix seconds (for lightweight-charts) */
export function toUnixSeconds(iso: string): number {
  return Math.floor(new Date(iso).getTime() / 1000);
}
