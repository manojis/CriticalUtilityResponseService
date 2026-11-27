import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { render, screen, within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { AppRoutes } from '../App';
import { AuthProvider } from '../auth/AuthContext';
import { writeSession } from '../auth/session';
import { NotificationProvider } from '../components/NotificationProvider';
import { theme } from '../theme';
import type { AuthUser } from '../types/api';
import { ALICE, makeJwt } from './fixtures';

/** Stores a valid session (as if the user had logged in) before rendering. */
export function signIn(user: AuthUser = ALICE, ttlSeconds = 3600): void {
  const exp = Math.floor(Date.now() / 1000) + ttlSeconds;
  writeSession({ token: makeJwt(exp, user.username), user, expiresAt: exp * 1000 });
}

/** Renders the current location so tests can assert redirects inside a MemoryRouter. */
export function LocationProbe() {
  const location = useLocation();
  return (
    <div data-testid="location" hidden>
      {`${location.pathname}${location.search}`}
    </div>
  );
}

export function currentLocation(): string {
  return screen.getByTestId('location').textContent;
}

interface Options {
  route?: string;
  /** Sign in as this user before rendering (default ALICE). Pass null to stay signed out. */
  user?: AuthUser | null;
}

/** Wraps `ui` in the same providers as the real app, inside a MemoryRouter. */
export function renderWithProviders(ui: ReactElement, { route = '/', user = ALICE }: Options = {}) {
  if (user) signIn(user);
  const result: RenderResult = render(
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <MemoryRouter initialEntries={[route]}>
        <NotificationProvider>
          <AuthProvider>
            {ui}
            <LocationProbe />
          </AuthProvider>
        </NotificationProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
  return { user: userEvent.setup(), ...result };
}

/** Renders the full route table at `route`. */
export function renderApp(route: string, options: Omit<Options, 'route'> = {}) {
  return renderWithProviders(<AppRoutes />, { ...options, route });
}

/** Opens an MUI select (by its accessible name) and picks an option. */
export async function selectOption(
  user: ReturnType<typeof userEvent.setup>,
  label: RegExp | string,
  option: RegExp | string,
): Promise<void> {
  await user.click(screen.getByRole('combobox', { name: label }));
  const listbox = await screen.findByRole('listbox');
  await user.click(within(listbox).getByRole('option', { name: option }));
}
