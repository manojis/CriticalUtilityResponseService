import { screen } from '@testing-library/react';
import { currentLocation, renderApp } from './test/render';

describe('routing', () => {
  it.each([
    '/',
    '/resources/new',
    '/incidents/new',
    '/resources/search',
    '/resource-status',
    '/reports/resources-by-esf',
  ])('redirects %s to /login when signed out', (route) => {
    renderApp(route, { user: null });
    expect(currentLocation()).toBe('/login');
  });

  it('shows the 404 page for unknown routes', () => {
    renderApp('/does-not-exist', { user: null });
    expect(screen.getByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to main menu' })).toHaveAttribute('href', '/');
  });

  it('shows the user name and type in the app bar', async () => {
    renderApp('/resource-status');
    expect(await screen.findByRole('heading', { name: 'Resource Status' })).toBeInTheDocument();
    const banner = screen.getByRole('banner');
    expect(banner).toHaveTextContent('Alice Smith');
    expect(banner).toHaveTextContent('Individual');
  });
});
