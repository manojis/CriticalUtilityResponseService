import { createContext } from 'react';
import type { AuthUser, LoginRequest } from '../types/api';

export interface AuthContextValue {
  /** The signed-in user (from the login response), or null. */
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** Calls the login endpoint and stores the session. Rejects with ApiError on failure. */
  login: (credentials: LoginRequest) => Promise<AuthUser>;
  /** Clears the session and returns to /login. */
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
