import { compareIsoDates, isIsoDate } from '../utils/date';

/** Field name → error message. Absent key = field is valid. */
export type ValidationErrors<T> = Partial<Record<keyof T, string>>;

const DECIMAL = /^[+-]?(\d+(\.\d*)?|\.\d+)$/;

/** Strictly parses a decimal string (no exponents, hex, or trailing junk). Null when invalid/blank. */
export function parseDecimal(value: string): number | null {
  const trimmed = value.trim();
  if (!DECIMAL.test(trimmed)) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Number of digits after the decimal point in a decimal string. */
export function decimalPlaces(value: string): number {
  const fraction = value.trim().split('.')[1];
  return fraction?.length ?? 0;
}

export function isBlank(value: string | null | undefined): boolean {
  return value === null || value === undefined || value.trim() === '';
}

export function validateRequired(value: string, label: string): string | undefined {
  return isBlank(value) ? `${label} is required` : undefined;
}

export function validateLength(
  value: string,
  label: string,
  min: number,
  max: number,
): string | undefined {
  const length = value.trim().length;
  if (length < min || length > max) {
    return `${label} must be between ${min} and ${max} characters`;
  }
  return undefined;
}

export function validateNumberInRange(
  value: string,
  label: string,
  min: number,
  max: number,
): string | undefined {
  if (isBlank(value)) {
    return `${label} is required`;
  }
  const parsed = parseDecimal(value);
  if (parsed === null || parsed < min || parsed > max) {
    return `${label} must be a number between ${min} and ${max}`;
  }
  return undefined;
}

export const validateLatitude = (value: string): string | undefined =>
  validateNumberInRange(value, 'Latitude', -90, 90);

export const validateLongitude = (value: string): string | undefined =>
  validateNumberInRange(value, 'Longitude', -180, 180);

/** Required ISO date that must not be after `today`. */
export function validateDateNotAfter(
  value: string,
  label: string,
  today: string,
): string | undefined {
  if (isBlank(value)) return `${label} is required`;
  if (!isIsoDate(value)) return `${label} must be a valid date (YYYY-MM-DD)`;
  if (compareIsoDates(value, today) > 0) return `${label} cannot be in the future`;
  return undefined;
}

/** Required ISO date that must be on or after `today`. */
export function validateDateNotBefore(
  value: string,
  label: string,
  today: string,
): string | undefined {
  if (isBlank(value)) return `${label} is required`;
  if (!isIsoDate(value)) return `${label} must be a valid date (YYYY-MM-DD)`;
  if (compareIsoDates(value, today) < 0) return `${label} cannot be in the past`;
  return undefined;
}

/** Removes undefined entries so `Object.keys(errors).length === 0` means "valid". */
export function compact<T>(errors: Record<keyof T, string | undefined>): ValidationErrors<T> {
  const result: ValidationErrors<T> = {};
  for (const key of Object.keys(errors) as (keyof T)[]) {
    const message = errors[key];
    if (message !== undefined) {
      result[key] = message;
    }
  }
  return result;
}
