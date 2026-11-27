import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { currentLocation, renderApp } from '../../test/render';

describe('MainMenuPage', () => {
  it('shows the profile with the type-specific subtitle and links to every function', async () => {
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Alice Smith' })).toBeInTheDocument();
    expect(screen.getByTestId('profile-subtitle')).toHaveTextContent('Field Coordinator');
    expect(screen.getByText('2020-03-01')).toBeInTheDocument();

    const nav = screen.getByRole('navigation', { name: 'Main menu' });
    const links = within(nav).getAllByRole('link');
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/resources/new',
      '/incidents/new',
      '/resources/search',
      '/resource-status',
      '/reports/resources-by-esf',
    ]);
  });

  it.each([
    [{ userType: 'COMPANY', headquarters: 'Atlanta, GA', numberOfEmployees: 250 }, 'Atlanta, GA'],
    [{ userType: 'MUNICIPALITY', municipalityCategory: 'City' }, 'City'],
    [{ userType: 'GOVERNMENT_AGENCY', agencyNameLocalOffice: 'FEMA Region 4' }, 'FEMA Region 4'],
  ])('uses the subtitle for %o', async (fields, subtitle) => {
    server.use(
      http.get(api('/me'), () =>
        HttpResponse.json({ username: 'u1', name: 'Some Org', ...fields }),
      ),
    );
    renderApp('/');
    expect(await screen.findByTestId('profile-subtitle')).toHaveTextContent(subtitle);
  });

  it('shows an error with retry when the profile cannot be loaded', async () => {
    server.use(
      http.get(api('/me'), () => problemResponse(500, 'internal.error', 'Unexpected error')),
    );
    const { user } = renderApp('/');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error');
    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { name: 'Alice Smith' })).toBeInTheDocument();
  });

  it('logs out from the app bar', async () => {
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Alice Smith' });
    await user.click(screen.getByRole('button', { name: 'Logout' }));
    expect(currentLocation()).toBe('/login');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });
});
