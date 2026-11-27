import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { currentLocation, renderWithProviders } from '../test/render';
import { ProtectedRoute } from './ProtectedRoute';

function routes() {
  return (
    <Routes>
      <Route path="/login" element={<p>login page</p>} />
      <Route element={<ProtectedRoute />}>
        <Route path="/resource-status" element={<p>secret page</p>} />
      </Route>
    </Routes>
  );
}

describe('ProtectedRoute', () => {
  it('redirects anonymous users to /login', () => {
    renderWithProviders(routes(), { route: '/resource-status', user: null });
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('secret page')).not.toBeInTheDocument();
    expect(currentLocation()).toBe('/login');
  });

  it('renders the child route for authenticated users', () => {
    renderWithProviders(routes(), { route: '/resource-status' });
    expect(screen.getByText('secret page')).toBeInTheDocument();
  });
});
