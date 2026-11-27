import type { AuthUser, LoginResponse, UserType } from '../types/api';

/** sessionStorage key holding the current session (ARCHITECTURE.md §4.2). */
export const SESSION_STORAGE_KEY = 'erms.auth';

/** Treat a token as expired slightly early so requests don't race the server-side expiry. */
const EXPIRY_SKEW_MS = 5_000;

export interface Session {
  token: string;
  user: AuthUser;
  /** Epoch milliseconds after which the token must not be used. */
  expiresAt: number;
}

const USER_TYPES: readonly UserType[] = [
  'INDIVIDUAL',
  'MUNICIPALITY',
  'GOVERNMENT_AGENCY',
  'COMPANY',
];

function base64UrlDecode(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/**
 * Reads the `exp` claim (seconds) of a JWT and returns it in epoch milliseconds.
 * The signature is NOT verified; the server does that. Returns null for malformed tokens.
 */
export function decodeJwtExpiry(token: string): number | null {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) {
    return null;
  }
  try {
    const payload: unknown = JSON.parse(base64UrlDecode(parts[1]));
    if (typeof payload === 'object' && payload !== null && 'exp' in payload) {
      const exp = payload.exp;
      return typeof exp === 'number' && Number.isFinite(exp) ? exp * 1000 : null;
    }
    return null;
  } catch {
    return null;
  }
}

/** Builds a session from the login response. Prefers the token's `exp`, else `expiresIn`. */
export function sessionFromLogin(response: LoginResponse, now: number = Date.now()): Session {
  const fromToken = decodeJwtExpiry(response.accessToken);
  return {
    token: response.accessToken,
    user: response.user,
    expiresAt: fromToken ?? now + response.expiresIn * 1000,
  };
}

export function isSessionExpired(session: Session, now: number = Date.now()): boolean {
  return now >= session.expiresAt - EXPIRY_SKEW_MS;
}

function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Partial<Session>;
  const user = candidate.user as Partial<AuthUser> | undefined;
  return (
    typeof candidate.token === 'string' &&
    candidate.token.length > 0 &&
    typeof candidate.expiresAt === 'number' &&
    typeof user === 'object' &&
    typeof user.username === 'string' &&
    typeof user.name === 'string' &&
    typeof user.userType === 'string' &&
    USER_TYPES.includes(user.userType)
  );
}

function storage(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/**
 * Returns the stored session if present, well-formed and not expired. Invalid or expired
 * entries are removed so they can't be reused.
 */
export function readSession(now: number = Date.now()): Session | null {
  const store = storage();
  const raw = store?.getItem(SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (isSession(parsed) && !isSessionExpired(parsed, now)) {
      return parsed;
    }
  } catch {
    // fall through: corrupted entry
  }
  store?.removeItem(SESSION_STORAGE_KEY);
  return null;
}

export function writeSession(session: Session): void {
  storage()?.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  storage()?.removeItem(SESSION_STORAGE_KEY);
}

/** The current bearer token, or null when there is no valid session. */
export function currentToken(now: number = Date.now()): string | null {
  return readSession(now)?.token ?? null;
}
