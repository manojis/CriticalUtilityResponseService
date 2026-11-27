import { Navigate, Outlet, useLocation } from 'react-router';
import type { RedirectState } from './AuthContext';
import { useAuth } from './useAuth';

/** Renders child routes only for authenticated users; otherwise redirects to /login. */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    const state: RedirectState = { from: location };
    return <Navigate to="/login" replace state={state} />;
  }
  return <Outlet />;
}
