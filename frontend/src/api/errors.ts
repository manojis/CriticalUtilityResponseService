import { isAxiosError, isCancel } from 'axios';
import type { ProblemDetail, ProblemFieldError } from '../types/api';

/**
 * Normalised error thrown by every API function. Built from an RFC 9457 ProblemDetail when the
 * server sent one, or synthesised for network failures and non-JSON responses.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly code: string | undefined;
  readonly requestId: string | undefined;
  /** Field name → first message, from ProblemDetail `errors[]`. */
  readonly fieldErrors: Readonly<Record<string, string>>;

  constructor(init: {
    status: number;
    title: string;
    detail: string;
    code?: string | undefined;
    requestId?: string | undefined;
    fieldErrors?: Record<string, string>;
  }) {
    super(init.detail || init.title);
    this.name = 'ApiError';
    this.status = init.status;
    this.title = init.title;
    this.detail = init.detail;
    this.code = init.code;
    this.requestId = init.requestId;
    this.fieldErrors = init.fieldErrors ?? {};
  }

  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0;
  }
}

const DEFAULT_TITLES: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  500: 'Internal Server Error',
  502: 'Bad Gateway',
  503: 'Service Unavailable',
  504: 'Gateway Timeout',
};

const DEFAULT_DETAILS: Record<number, string> = {
  401: 'Your session has expired. Please sign in again.',
  403: 'You are not allowed to perform this action.',
  404: 'The requested item was not found.',
  409: 'The action conflicts with the current state. Refresh and try again.',
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined;
}

function isFieldError(value: unknown): value is ProblemFieldError {
  return isRecord(value) && typeof value.field === 'string' && typeof value.message === 'string';
}

/** Type guard for an RFC 9457 body. Tolerates missing optional members. */
export function isProblemDetail(value: unknown): value is ProblemDetail {
  return isRecord(value) && typeof value.status === 'number';
}

/** Maps `errors[]` to `{ field: firstMessage }`. Later duplicates for the same field are ignored. */
export function toFieldErrorMap(errors: readonly unknown[] | undefined): Record<string, string> {
  const map: Record<string, string> = {};
  for (const entry of errors ?? []) {
    if (isFieldError(entry) && !(entry.field in map)) {
      map[entry.field] = entry.message;
    }
  }
  return map;
}

/** Builds an {@link ApiError} from a ProblemDetail-like body and the HTTP status. */
export function fromProblemDetail(
  status: number,
  body: unknown,
  requestIdHeader?: string,
): ApiError {
  const problem = isRecord(body) ? body : {};
  const effectiveStatus = typeof problem.status === 'number' ? problem.status : status;
  const title =
    asString(problem.title) ?? DEFAULT_TITLES[effectiveStatus] ?? `HTTP ${effectiveStatus}`;
  const detail =
    asString(problem.detail) ??
    DEFAULT_DETAILS[effectiveStatus] ??
    (effectiveStatus >= 500
      ? 'The server could not complete the request. Please try again later.'
      : 'The request could not be completed.');
  return new ApiError({
    status: effectiveStatus,
    title,
    detail,
    code: asString(problem.code),
    requestId: asString(problem.requestId) ?? requestIdHeader,
    fieldErrors: toFieldErrorMap(Array.isArray(problem.errors) ? problem.errors : undefined),
  });
}

/** True for errors caused by aborting a request (component unmounted, superseded search, …). */
export function isCancellation(error: unknown): boolean {
  return isCancel(error) || (error instanceof DOMException && error.name === 'AbortError');
}

/** Converts anything thrown by axios (or our own code) into an {@link ApiError}. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (isAxiosError(error)) {
    const response = error.response;
    if (response) {
      const header: unknown = response.headers['x-request-id'];
      return fromProblemDetail(response.status, response.data, asString(header));
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError({
        status: 0,
        title: 'Request timed out',
        detail: 'The server took too long to respond. Please try again.',
      });
    }
    return new ApiError({
      status: 0,
      title: 'Network error',
      detail: 'Could not reach the ERMS server. Check your connection and try again.',
    });
  }
  return new ApiError({
    status: 0,
    title: 'Unexpected error',
    detail: error instanceof Error && error.message ? error.message : 'Something went wrong.',
  });
}
