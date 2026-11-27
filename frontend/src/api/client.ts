import axios, { AxiosHeaders, isAxiosError } from 'axios';
import { currentToken } from '../auth/session';
import { isCancellation, toApiError } from './errors';

/** Same-origin base path. nginx (or the Vite dev proxy / the Ingress) routes `/api` to the backend. */
export const API_BASE_URL = '/api/v1';

/** Endpoints whose 401 means "bad credentials", not "session expired". */
const AUTH_ENDPOINTS = ['/auth/login'];

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * Registers the callback invoked when any authenticated call returns 401 (expired or invalid
 * token). The AuthProvider uses it to log out and redirect to `/login?expired=1`.
 * Returns an unregister function.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

function isAuthEndpoint(url: string | undefined): boolean {
  return url !== undefined && AUTH_ENDPOINTS.some((path) => url.endsWith(path));
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  headers: { Accept: 'application/json, application/problem+json' },
});

apiClient.interceptors.request.use((config) => {
  const token = currentToken();
  if (token && !isAuthEndpoint(config.url)) {
    const headers = AxiosHeaders.from(config.headers);
    headers.set('Authorization', `Bearer ${token}`);
    config.headers = headers;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (isCancellation(error)) {
      return Promise.reject(error instanceof Error ? error : new Error('Request cancelled'));
    }
    if (
      isAxiosError(error) &&
      error.response?.status === 401 &&
      !isAuthEndpoint(error.config?.url)
    ) {
      unauthorizedHandler?.();
    }
    return Promise.reject(toApiError(error));
  },
);
