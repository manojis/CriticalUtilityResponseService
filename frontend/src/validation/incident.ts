import type { CreateIncidentRequest } from '../types/api';
import {
  compact,
  parseDecimal,
  validateDateNotAfter,
  validateLatitude,
  validateLength,
  validateLongitude,
  validateRequired,
  type ValidationErrors,
} from './common';

export const DESCRIPTION_MIN = 6;
export const DESCRIPTION_MAX = 500;

/** Raw form state: every input is a string until submitted. */
export interface IncidentFormValues {
  declarationAbbreviation: string;
  incidentDate: string;
  description: string;
  latitude: string;
  longitude: string;
}

export type IncidentFormErrors = ValidationErrors<IncidentFormValues>;

/**
 * Client-side rules for "Add incident" (FEATURES.md F-3 BR), ported from the legacy
 * Incident_validators.js and tightened to match the API: date ≤ today, description 6–500,
 * latitude −90..90, longitude −180..180, declaration required.
 */
export function validateIncident(values: IncidentFormValues, today: string): IncidentFormErrors {
  return compact<IncidentFormValues>({
    declarationAbbreviation: validateRequired(values.declarationAbbreviation, 'Declaration'),
    incidentDate: validateDateNotAfter(values.incidentDate, 'Incident date', today),
    description: validateLength(
      values.description,
      'Description',
      DESCRIPTION_MIN,
      DESCRIPTION_MAX,
    ),
    latitude: validateLatitude(values.latitude),
    longitude: validateLongitude(values.longitude),
  });
}

/** Converts validated form values to the API payload. */
export function toCreateIncidentRequest(values: IncidentFormValues): CreateIncidentRequest {
  return {
    declarationAbbreviation: values.declarationAbbreviation,
    incidentDate: values.incidentDate,
    description: values.description.trim(),
    latitude: parseDecimal(values.latitude) ?? Number.NaN,
    longitude: parseDecimal(values.longitude) ?? Number.NaN,
  };
}
