import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { readSession } from '../../auth/session';
import { loginResponse } from '../../test/fixtures';
import { api } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { currentLocation, renderApp } from '../../test/render';

describe('LoginPage', () => {
  it('signs in and redirects to the main menu', async () => {
    let body: unknown;
    server.use(
      http.post(api('/auth/login'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(loginResponse());
      }),
    );
    const { user } = renderApp('/login', { user: null });
    await user.type(screen.getByLabelText(/username/i), ' alice ');
    await user.type(screen.getByLabelText(/password/i), 'password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/');
    expect(body).toEqual({ username: 'alice', password: 'password' });
    expect(readSession()?.user.username).toBe('alice');
  });

  it('masks the password field', () => {
    renderApp('/login', { user: null });
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
  });

  it('shows the generic API error on bad credentials (401) and stays on /login', async () => {
    const { user } = renderApp('/login', { user: null });
    await user.type(screen.getByLabelText(/username/i), 'alice');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Invalid username or password');
    expect(alert).toHaveTextContent('Reference: req-123');
    expect(currentLocation()).toBe('/login');
    expect(readSession()).toBeNull();
  });

  it('validates required fields before calling the API', async () => {
    const { user } = renderApp('/login', { user: null });
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('returns to the originally requested page after login', async () => {
    const { user } = renderApp('/reports/resources-by-esf', { user: null });
    expect(currentLocation()).toBe('/login');
    await user.type(screen.getByLabelText(/username/i), 'alice');
    await user.type(screen.getByLabelText(/password/i), 'password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { name: 'Resource Report' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/reports/resources-by-esf');
  });

  it('shows "Session expired" after a forced logout', () => {
    renderApp('/login?expired=1', { user: null });
    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
  });

  it('redirects an already signed-in user away from /login', async () => {
    renderApp('/login');
    expect(await screen.findByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
  });
});
