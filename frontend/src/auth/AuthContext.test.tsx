import { act, render, screen, waitFor } from '@testing-library/react';
import { http } from 'msw';
import { Route, Routes } from 'react-router';
import { getMe } from '../api/me';
import { ALICE, makeJwt } from '../test/fixtures';
import { api, problemResponse } from '../test/msw/handlers';
import { server } from '../test/msw/server';
import { currentLocation, renderWithProviders } from '../test/render';
import { readSession, SESSION_STORAGE_KEY, writeSession } from './session';
import { useAuth } from './useAuth';

function Probe() {
  const { user, isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <p>{isAuthenticated ? `signed in as ${user?.name ?? ''}` : 'signed out'}</p>
      <button
        type="button"
        onClick={() => {
          void login({ username: 'alice', password: 'password' });
        }}
      >
        login
      </button>
      <button type="button" onClick={logout}>
        logout
      </button>
    </div>
  );
}

function renderProbe(options: Parameters<typeof renderWithProviders>[1] = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="*" element={<Probe />} />
    </Routes>,
    { route: '/resource-status', ...options },
  );
}

describe('AuthProvider', () => {
  it('logs in and persists the session in sessionStorage', async () => {
    const { user } = renderProbe({ user: null });
    expect(screen.getByText('signed out')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'login' }));
    expect(await screen.findByText('signed in as Alice Smith')).toBeInTheDocument();
    expect(readSession()?.user).toEqual(ALICE);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toContain('"username":"alice"');
  });

  it('restores a persisted session on load', () => {
    renderProbe();
    expect(screen.getByText('signed in as Alice Smith')).toBeInTheDocument();
  });

  it('ignores an expired persisted session on load', () => {
    const exp = Math.floor(Date.now() / 1000) - 60;
    writeSession({ token: makeJwt(exp), user: ALICE, expiresAt: exp * 1000 });
    renderProbe({ user: null });
    expect(screen.getByText('signed out')).toBeInTheDocument();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('logout clears the session and goes to /login', async () => {
    const { user } = renderProbe();
    await user.click(screen.getByRole('button', { name: 'logout' }));
    expect(screen.getByText('signed out')).toBeInTheDocument();
    expect(readSession()).toBeNull();
    expect(currentLocation()).toBe('/login');
  });

  it('expires the session and redirects to /login?expired=1 on any 401', async () => {
    server.use(
      http.get(api('/me'), () => problemResponse(401, 'auth.unauthorized', 'Token expired')),
    );
    renderProbe();
    await act(async () => {
      await getMe().catch(() => undefined);
    });
    await waitFor(() => {
      expect(currentLocation()).toBe('/login?expired=1');
    });
    expect(screen.getByText('signed out')).toBeInTheDocument();
    expect(readSession()).toBeNull();
  });

  it('expires the session when the token exp is reached', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const exp = Math.floor(Date.now() / 1000) + 30;
      writeSession({ token: makeJwt(exp), user: ALICE, expiresAt: exp * 1000 });
      renderProbe({ user: null });
      expect(screen.getByText('signed in as Alice Smith')).toBeInTheDocument();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(31_000);
      });
      expect(screen.getByText('signed out')).toBeInTheDocument();
      expect(currentLocation()).toBe('/login?expired=1');
    } finally {
      vi.useRealTimers();
    }
  });

  it('useAuth throws outside the provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    function Orphan() {
      useAuth();
      return null;
    }
    expect(() => {
      // Rendering without providers must fail loudly.
      render(<Orphan />);
    }).toThrow('useAuth must be used inside <AuthProvider>');
    spy.mockRestore();
  });
});
