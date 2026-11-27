import type { CreateResourceRequest } from '../types/api';
import {
  compact,
  decimalPlaces,
  isBlank,
  parseDecimal,
  validateLatitude,
  validateLength,
  validateLongitude,
  validateRequired,
  type ValidationErrors,
} from './common';

export const NAME_MIN = 3;
export const NAME_MAX = 100;
export const MODEL_MAX = 100;
export const CAPABILITY_MAX = 100;

export interface ResourceFormValues {
  resourceName: string;
  model: string;
  /** ESF number as a string ('' = not selected) so it can back a select input. */
  primaryEsf: string;
  additionalEsfs: number[];
  capabilities: string[];
  cost: string;
  costUnit: string;
  latitude: string;
  longitude: string;
  /** Optional; blank = no distance limit. */
  maxDistanceKm: string;
}

export type ResourceFormErrors = ValidationErrors<ResourceFormValues>;

export function validateCost(value: string): string | undefined {
  if (isBlank(value)) return 'Cost is required';
  const parsed = parseDecimal(value);
  if (parsed === null || parsed < 0) return 'Cost must be a number greater than or equal to 0';
  if (decimalPlaces(value) > 2) return 'Cost can have at most 2 decimal places';
  return undefined;
}

export function validateMaxDistance(value: string): string | undefined {
  if (isBlank(value)) return undefined;
  const parsed = parseDecimal(value);
  if (parsed === null || parsed <= 0) return 'Max distance must be a number greater than 0';
  return undefined;
}

export function validateAdditionalEsfs(
  additional: readonly number[],
  primary: string,
): string | undefined {
  if (new Set(additional).size !== additional.length) return 'Additional ESFs must be unique';
  if (primary !== '' && additional.includes(Number(primary))) {
    return 'Additional ESFs cannot include the primary ESF';
  }
  return undefined;
}

/**
 * Validates a capability before it is added to the list: trimmed, 1–100 chars, and not a
 * case-insensitive duplicate of an existing entry.
 */
export function validateNewCapability(
  value: string,
  existing: readonly string[],
): string | undefined {
  const trimmed = value.trim();
  if (trimmed.length === 0) return 'Enter a capability';
  if (trimmed.length > CAPABILITY_MAX) {
    return `Capability must be at most ${CAPABILITY_MAX} characters`;
  }
  const lower = trimmed.toLowerCase();
  if (existing.some((item) => item.toLowerCase() === lower)) {
    return 'Capability already added';
  }
  return undefined;
}

function validateCapabilities(capabilities: readonly string[]): string | undefined {
  const invalid = capabilities.find(
    (c) => c.trim().length === 0 || c.trim().length > CAPABILITY_MAX,
  );
  return invalid === undefined
    ? undefined
    : `Each capability must be between 1 and ${CAPABILITY_MAX} characters`;
}

/**
 * Client-side rules for "Add resource" (FEATURES.md F-4 BR), ported from the legacy
 * Resource_validators.js and aligned with the API contract.
 */
export function validateResource(values: ResourceFormValues): ResourceFormErrors {
  return compact<ResourceFormValues>({
    resourceName: validateLength(values.resourceName, 'Resource name', NAME_MIN, NAME_MAX),
    model:
      values.model.trim().length > MODEL_MAX
        ? `Model must be at most ${MODEL_MAX} characters`
        : undefined,
    primaryEsf: validateRequired(values.primaryEsf, 'Primary ESF'),
    additionalEsfs: validateAdditionalEsfs(values.additionalEsfs, values.primaryEsf),
    capabilities: validateCapabilities(values.capabilities),
    cost: validateCost(values.cost),
    costUnit: validateRequired(values.costUnit, 'Cost unit'),
    latitude: validateLatitude(values.latitude),
    longitude: validateLongitude(values.longitude),
    maxDistanceKm: validateMaxDistance(values.maxDistanceKm),
  });
}

/** Removes duplicates case-insensitively, keeping the first spelling; trims entries. */
export function dedupeCapabilities(capabilities: readonly string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const raw of capabilities) {
    const trimmed = raw.trim();
    const key = trimmed.toLowerCase();
    if (trimmed && !seen.has(key)) {
      seen.add(key);
      result.push(trimmed);
    }
  }
  return result;
}

/** Converts validated form values to the API payload. */
export function toCreateResourceRequest(values: ResourceFormValues): CreateResourceRequest {
  const model = values.model.trim();
  const primaryEsf = Number(values.primaryEsf);
  return {
    resourceName: values.resourceName.trim(),
    ...(model ? { model } : {}),
    primaryEsf,
    additionalEsfs: values.additionalEsfs.filter((esf) => esf !== primaryEsf),
    capabilities: dedupeCapabilities(values.capabilities),
    cost: parseDecimal(values.cost) ?? Number.NaN,
    costUnit: values.costUnit,
    latitude: parseDecimal(values.latitude) ?? Number.NaN,
    longitude: parseDecimal(values.longitude) ?? Number.NaN,
    maxDistanceKm: isBlank(values.maxDistanceKm) ? null : parseDecimal(values.maxDistanceKm),
  };
}
