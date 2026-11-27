import type { FieldErrors, FieldValues, Path, Resolver, UseFormSetError } from 'react-hook-form';
import type { ApiError } from '../api/errors';
import type { ValidationErrors } from '../validation/common';

/**
 * Adapts a pure validator (`values → { field: message }`) to a react-hook-form resolver, so the
 * ported validation rules stay framework-free and unit-testable.
 */
export function validatorResolver<T extends FieldValues>(
  validate: (values: T) => ValidationErrors<T>,
): Resolver<T> {
  return (values) => {
    const errors = validate(values);
    const keys = Object.keys(errors) as (keyof T & string)[];
    if (keys.length === 0) {
      return { values, errors: {} };
    }
    const fieldErrors: Record<string, { type: string; message: string }> = {};
    for (const key of keys) {
      const message = errors[key];
      if (message !== undefined) {
        fieldErrors[key] = { type: 'validate', message };
      }
    }
    return { values: {}, errors: fieldErrors as FieldErrors<T> };
  };
}

/**
 * Attaches server-side field errors (ProblemDetail `errors[]`) to the matching form fields
 * (indexed/nested paths map to their top-level field).
 * Returns the errors whose field is not part of the form, so the caller can show them in the
 * page-level alert.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: ApiError,
  formFields: readonly (keyof T & string)[],
  setError: UseFormSetError<T>,
): Record<string, string> {
  const unmapped: Record<string, string> = {};
  let focused = false;
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    // "capabilities[1]" or "primaryEsf.esfNumber" → "capabilities" / "primaryEsf"
    const base = field.split(/[.[]/)[0] ?? field;
    if ((formFields as readonly string[]).includes(base)) {
      setError(base as Path<T>, { type: 'server', message }, { shouldFocus: !focused });
      focused = true;
    } else {
      unmapped[field] = message;
    }
  }
  return unmapped;
}
