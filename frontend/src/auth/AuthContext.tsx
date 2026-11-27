import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, type Location } from 'react-router';
import { login as loginRequest } from '../api/auth';
import { setUnauthorizedHandler } from '../api/client';
import type { LoginRequest } from '../types/api';
import { AuthContext, type AuthContextValue } from './context';
import { clearSession, readSession, sessionFromLogin, writeSession, type Session } from './session';

/** setTimeout delays above 2^31-1 ms overflow and fire immediately. */
const MAX_TIMER_MS = 2_147_483_647;
const EXPIRY_SKEW_MS = 5_000;

export interface RedirectState {
  from?: Location;
}

/**
 * Holds the JWT session (persisted in sessionStorage under "erms.auth"), exposes login/logout,
 * and logs the user out — redirecting to /login?expired=1 — when the token expires locally or
 * any API call returns 401. Must be rendered inside a router.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [session, setSession] = useState<Session | null>(() => readSession());

  const locationRef = useRef(location);
  useEffect(() => {
    locationRef.current = location;
  }, [location]);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
    void navigate('/login', { replace: true });
  }, [navigate]);

  const expire = useCallback(() => {
    clearSession();
    setSession(null);
    const from = locationRef.current;
    const state: RedirectState | undefined = from.pathname === '/login' ? undefined : { from };
    void navigate('/login?expired=1', { replace: true, state });
  }, [navigate]);

  // Any 401 from an authenticated endpoint ends the session.
  useEffect(() => setUnauthorizedHandler(expire), [expire]);

  // Proactively expire the session when the token's exp is reached.
  useEffect(() => {
    if (!session) {
      return undefined;
    }
    const delay = Math.min(
      Math.max(session.expiresAt - EXPIRY_SKEW_MS - Date.now(), 0),
      MAX_TIMER_MS,
    );
    const timer = window.setTimeout(expire, delay);
    return () => {
      window.clearTimeout(timer);
    };
  }, [session, expire]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await loginRequest(credentials);
    const next = sessionFromLogin(response);
    writeSession(next);
    setSession(next);
    return next.user;
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      login,
      logout,
    }),
    [session, login, logout],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
