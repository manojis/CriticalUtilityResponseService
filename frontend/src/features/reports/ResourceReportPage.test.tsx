import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { REPORT } from '../../test/fixtures';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

describe('ResourceReportPage', () => {
  it('lists all 15 ESFs plus a totals row (F10/F11)', async () => {
    renderApp('/reports/resources-by-esf');
    const table = await screen.findByRole('table', { name: 'Resources by primary ESF' });
    // header + 15 rows + totals
    expect(within(table).getAllByRole('row')).toHaveLength(17);
    expect(within(table).getByText('ESF 15 description')).toBeInTheDocument();
    expect(screen.getByTestId('total-resources')).toHaveTextContent('5');
    expect(screen.getByTestId('total-in-use')).toHaveTextContent('2');
  });

  it('shows a hint when the user has no resources', async () => {
    server.use(
      http.get(api('/reports/resources-by-esf'), () =>
        HttpResponse.json({
          rows: REPORT.rows.map((row) => ({ ...row, totalResources: 0, resourcesInUse: 0 })),
          totals: { totalResources: 0, resourcesInUse: 0 },
        }),
      ),
    );
    renderApp('/reports/resources-by-esf');
    expect(
      await screen.findByText('You have not registered any resources yet.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Add a resource' })).toHaveAttribute(
      'href',
      '/resources/new',
    );
  });

  it('shows an error state', async () => {
    server.use(
      http.get(api('/reports/resources-by-esf'), () =>
        problemResponse(500, 'internal.error', 'Unexpected error'),
      ),
    );
    renderApp('/reports/resources-by-esf');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error');
  });
});
