import { isBlank, parseDecimal, validateDateNotBefore } from './common';

/** Expected return date for Request/Deploy: required, valid, and ≥ today (FEATURES.md F-5). */
export function validateExpectedReturnDate(value: string, today: string): string | undefined {
  return validateDateNotBefore(value, 'Expected return date', today);
}

export interface SearchFilterValues {
  keyword: string;
  /** ESF number as string, '' = any. */
  esf: string;
  /** '' = no incident. */
  incidentId: string;
  /** Blank = no distance limit. */
  distanceKm: string;
}

/** "Within N km" must be a positive number and needs an incident (FEATURES.md F-5 AC5). */
export function validateSearchDistance(values: SearchFilterValues): string | undefined {
  if (isBlank(values.distanceKm)) return undefined;
  if (values.incidentId === '') return 'Select an incident to search by distance';
  const parsed = parseDecimal(values.distanceKm);
  if (parsed === null || parsed <= 0) return 'Distance must be a number greater than 0';
  return undefined;
}
