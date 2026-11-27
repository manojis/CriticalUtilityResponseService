import { AxiosError, AxiosHeaders, CanceledError } from 'axios';
import {
  ApiError,
  fromProblemDetail,
  isCancellation,
  isProblemDetail,
  toApiError,
  toFieldErrorMap,
} from './errors';

function axiosErrorWith(status: number, data: unknown, headers: Record<string, string> = {}) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('failed', 'ERR_BAD_RESPONSE', config, null, {
    status,
    statusText: '',
    data,
    headers,
    config,
  });
}

describe('ProblemDetail normalisation', () => {
  it('maps title, detail, code, requestId and field errors', () => {
    const error = fromProblemDetail(400, {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      code: 'validation.failed',
      requestId: 'abc',
      errors: [
        { field: 'latitude', message: 'must be less than or equal to 90' },
        { field: 'latitude', message: 'second message ignored' },
        { field: 'description', message: 'size must be between 6 and 500' },
        { nonsense: true },
      ],
    });
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      title: 'Bad Request',
      detail: 'Request validation failed',
      code: 'validation.failed',
      requestId: 'abc',
    });
    expect(error.fieldErrors).toEqual({
      latitude: 'must be less than or equal to 90',
      description: 'size must be between 6 and 500',
    });
    expect(error.hasFieldErrors).toBe(true);
    expect(error.message).toBe('Request validation failed');
  });

  it('falls back to defaults for non-ProblemDetail bodies', () => {
    const error = fromProblemDetail(502, '<html>Bad gateway</html>', 'hdr-id');
    expect(error.title).toBe('Bad Gateway');
    expect(error.detail).toMatch(/could not complete/);
    expect(error.requestId).toBe('hdr-id');
    expect(error.hasFieldErrors).toBe(false);
    expect(fromProblemDetail(409, {}).detail).toMatch(/conflicts/);
    expect(fromProblemDetail(418, null).title).toBe('HTTP 418');
    expect(fromProblemDetail(418, null).detail).toBe('The request could not be completed.');
  });

  it('recognises ProblemDetail bodies', () => {
    expect(isProblemDetail({ status: 404 })).toBe(true);
    expect(isProblemDetail({ title: 'x' })).toBe(false);
    expect(isProblemDetail([])).toBe(false);
  });

  it('builds a field map from an undefined list', () => {
    expect(toFieldErrorMap(undefined)).toEqual({});
  });
});

describe('toApiError', () => {
  it('passes ApiError through', () => {
    const original = new ApiError({ status: 409, title: 'Conflict', detail: 'x' });
    expect(toApiError(original)).toBe(original);
  });

  it('uses the response body and X-Request-Id header', () => {
    const error = toApiError(
      axiosErrorWith(
        409,
        { status: 409, detail: 'Request 7 is DEPLOYED; cannot cancel', code: 'state.conflict' },
        {
          'x-request-id': 'from-header',
        },
      ),
    );
    expect(error).toMatchObject({ status: 409, code: 'state.conflict', requestId: 'from-header' });
    expect(error.detail).toBe('Request 7 is DEPLOYED; cannot cancel');
  });

  it('maps network errors and timeouts', () => {
    expect(toApiError(new AxiosError('Network Error', 'ERR_NETWORK')).title).toBe('Network error');
    expect(toApiError(new AxiosError('timeout', 'ECONNABORTED')).title).toBe('Request timed out');
  });

  it('maps unknown errors', () => {
    expect(toApiError(new Error('boom')).detail).toBe('boom');
    expect(toApiError('weird').detail).toBe('Something went wrong.');
  });

  it('detects cancellations', () => {
    expect(isCancellation(new CanceledError())).toBe(true);
    expect(isCancellation(new DOMException('aborted', 'AbortError'))).toBe(true);
    expect(isCancellation(new Error('x'))).toBe(false);
  });
});
