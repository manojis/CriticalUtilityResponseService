import type { IsoDate } from '../types/api';

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Formats a local calendar date as `YYYY-MM-DD`. */
export function toIsoDate(date: Date): IsoDate {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Today's date in the browser's time zone. The server is authoritative ("today" there is
 * computed in ERMS_TIME_ZONE); this is only used for client-side hints and validation.
 */
export function todayIso(now: Date = new Date()): IsoDate {
  return toIsoDate(now);
}

/** True when `value` is a real calendar date in strict `YYYY-MM-DD` form (rejects 2026-02-30). */
export function isIsoDate(value: string): boolean {
  const match = ISO_DATE.exec(value);
  if (!match) {
    return false;
  }
  const [, y, m, d] = match.map(Number) as [number, number, number, number];
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Lexicographic comparison is correct for valid ISO dates. */
export function compareIsoDates(a: IsoDate, b: IsoDate): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}
