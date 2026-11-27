import { fireEvent, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { CreateResourceRequestRequest } from '../../types/api';
import { todayIso } from '../../utils/date';
import { page, resourceRequest, SEARCH_RESULTS } from '../../test/fixtures';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp, selectOption } from '../../test/render';
import { resolveRowAction } from './rowAction';
import { toSearchParams } from './searchParams';

type User = ReturnType<typeof renderApp>['user'];

/** Records every search request's query string. */
function captureSearches(results = SEARCH_RESULTS.map((r, i) => ({ ...r, distanceKm: i * 1.25 }))) {
  const queries: Record<string, string>[] = [];
  server.use(
    http.get(api('/resources'), ({ request }) => {
      const params = Object.fromEntries(new URL(request.url).searchParams);
      queries.push(params);
      return HttpResponse.json(params.incidentId ? page(results) : page(SEARCH_RESULTS));
    }),
  );
  return queries;
}

async function searchWithIncident(user: User) {
  await screen.findByText('Hydraulic Crane');
  await selectOption(user, /incident/i, '(MD-2) Flooding on I-75');
  await user.click(screen.getByRole('button', { name: 'Search' }));
  await screen.findByRole('columnheader', { name: 'Action' });
}

describe('resolveRowAction (Request vs Deploy)', () => {
  it('offers Deploy only for my own available resource', () => {
    expect(resolveRowAction({ ownerUsername: 'alice', available: true }, 'alice')).toBe('deploy');
    expect(resolveRowAction({ ownerUsername: 'alice', available: false }, 'alice')).toBe('request');
    expect(resolveRowAction({ ownerUsername: 'bob', available: true }, 'alice')).toBe('request');
  });
});

describe('toSearchParams', () => {
  it('drops blank filters and ignores distance without an incident', () => {
    expect(toSearchParams({ keyword: ' ', esf: '', incidentId: '', distanceKm: '5' }, 0)).toEqual({
      page: 0,
      size: 20,
    });
    expect(
      toSearchParams({ keyword: 'crane', esf: '3', incidentId: 'MD-2', distanceKm: '50' }, 2),
    ).toEqual({
      keyword: 'crane',
      esf: 3,
      incidentId: 'MD-2',
      distanceKm: 50,
      page: 2,
      size: 20,
    });
  });
});

describe('SearchResourcesPage', () => {
  it('lists every resource on load (no filters) without distance or actions', async () => {
    const queries = captureSearches();
    renderApp('/resources/search');
    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(SEARCH_RESULTS.length + 1);
    expect(screen.getByText('$150.00 / Hour')).toBeInTheDocument();
    expect(screen.getByText('In use until 2026-12-01')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Distance' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Action' })).not.toBeInTheDocument();
    expect(queries[0]).toEqual({ page: '0', size: '20' });
  });

  it('sends keyword, ESF, incident and distance filters', async () => {
    const queries = captureSearches();
    const { user } = renderApp('/resources/search');
    await screen.findByText('Hydraulic Crane');
    await user.type(screen.getByLabelText(/keyword/i), 'crane');
    await selectOption(user, /^esf/i, '#3 Public Works and Engineering');
    expect(screen.getByLabelText(/within/i)).toBeDisabled();
    await selectOption(user, /incident/i, '(MD-2) Flooding on I-75');
    await user.type(screen.getByLabelText(/within/i), '50');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    await screen.findByRole('columnheader', { name: 'Distance' });
    expect(queries.at(-1)).toEqual({
      keyword: 'crane',
      esf: '3',
      incidentId: 'MD-2',
      distanceKm: '50',
      page: '0',
      size: '20',
    });
    expect(screen.getByText('0.0 km')).toBeInTheDocument();
    expect(screen.getByText('1.3 km')).toBeInTheDocument();
  });

  it('shows Deploy for my available resource and Request otherwise', async () => {
    captureSearches();
    const { user } = renderApp('/resources/search');
    await searchWithIncident(user);
    expect(screen.getByRole('button', { name: 'Request Hydraulic Crane' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deploy Water Pump' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Request Generator' })).toBeInTheDocument();
  });

  it('requests another owner’s resource with deployImmediately=false', async () => {
    captureSearches();
    let body: CreateResourceRequestRequest | undefined;
    server.use(
      http.post(api('/resource-requests'), async ({ request }) => {
        body = (await request.json()) as CreateResourceRequestRequest;
        return HttpResponse.json(resourceRequest({ requestId: 7 }), { status: 201 });
      }),
    );
    const { user } = renderApp('/resources/search');
    await searchWithIncident(user);
    await user.click(screen.getByRole('button', { name: 'Request Hydraulic Crane' }));

    const dialog = await screen.findByRole('dialog', { name: 'Request Hydraulic Crane' });
    // Past dates are rejected client-side.
    fireEvent.change(within(dialog).getByLabelText(/expected return date/i), {
      target: { value: '2000-01-01' },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Request' }));
    expect(
      within(dialog).getByText('Expected return date cannot be in the past'),
    ).toBeInTheDocument();
    expect(body).toBeUndefined();

    fireEvent.change(within(dialog).getByLabelText(/expected return date/i), {
      target: { value: todayIso() },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Request' }));

    expect(await screen.findByText('Request #7 sent for Hydraulic Crane')).toBeInTheDocument();
    expect(body).toEqual({
      resourceId: 1001,
      incidentId: 'MD-2',
      expectedReturnDate: todayIso(),
      deployImmediately: false,
    });
  });

  it('deploys my own resource with deployImmediately=true', async () => {
    captureSearches();
    let body: CreateResourceRequestRequest | undefined;
    server.use(
      http.post(api('/resource-requests'), async ({ request }) => {
        body = (await request.json()) as CreateResourceRequestRequest;
        return HttpResponse.json(
          resourceRequest({ resourceId: 1002, resourceName: 'Water Pump', status: 'DEPLOYED' }),
          { status: 201 },
        );
      }),
    );
    const { user } = renderApp('/resources/search');
    await searchWithIncident(user);
    await user.click(screen.getByRole('button', { name: 'Deploy Water Pump' }));
    const dialog = await screen.findByRole('dialog', { name: 'Deploy Water Pump' });
    fireEvent.change(within(dialog).getByLabelText(/expected return date/i), {
      target: { value: todayIso() },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Deploy' }));

    expect(await screen.findByText('Water Pump deployed to MD-2')).toBeInTheDocument();
    expect(body?.deployImmediately).toBe(true);
    expect(body?.resourceId).toBe(1002);
  });

  it('keeps the dialog open and shows the 409 detail on conflict', async () => {
    captureSearches();
    server.use(
      http.post(api('/resource-requests'), () =>
        problemResponse(
          409,
          'state.conflict',
          'An active request already exists for this resource and incident',
        ),
      ),
    );
    const { user } = renderApp('/resources/search');
    await searchWithIncident(user);
    await user.click(screen.getByRole('button', { name: 'Request Hydraulic Crane' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/expected return date/i), {
      target: { value: todayIso() },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Request' }));
    expect(await within(dialog).findByText(/active request already exists/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
  });

  it('requires an incident before searching by distance and clears distance with the incident', async () => {
    captureSearches();
    const { user } = renderApp('/resources/search?incidentId=MD-2');
    await screen.findByRole('columnheader', { name: 'Action' });
    await user.type(screen.getByLabelText(/within/i), '0');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(screen.getByText('Distance must be a number greater than 0')).toBeInTheDocument();

    await selectOption(user, /incident/i, 'No incident');
    expect(screen.getByLabelText(/within/i)).toHaveValue('');
    expect(screen.getByLabelText(/within/i)).toBeDisabled();
  });

  it('paginates through the server pages', async () => {
    const queries: Record<string, string>[] = [];
    server.use(
      http.get(api('/resources'), ({ request }) => {
        const params = Object.fromEntries(new URL(request.url).searchParams);
        queries.push(params);
        const current = Number(params.page);
        return HttpResponse.json(
          page(
            [
              {
                ...SEARCH_RESULTS[0]!,
                resourceId: 2000 + current,
                resourceName: `Item page ${String(current)}`,
              },
            ],
            {
              page: current,
              totalElements: 45,
              totalPages: 3,
            },
          ),
        );
      }),
    );
    const { user } = renderApp('/resources/search');
    expect(await screen.findByText('Item page 0')).toBeInTheDocument();
    expect(screen.getByText('45 found')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(await screen.findByText('Item page 1')).toBeInTheDocument();
    expect(queries.at(-1)?.page).toBe('1');
  });

  it('shows the empty state and resets filters', async () => {
    server.use(http.get(api('/resources'), () => HttpResponse.json(page([]))));
    const { user } = renderApp('/resources/search');
    expect(await screen.findByText('No resources match your filters.')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/keyword/i), 'zzz');
    await user.click(screen.getByRole('button', { name: 'Reset' }));
    expect(screen.getByLabelText(/keyword/i)).toHaveValue('');
  });

  it('shows the API error (e.g. 404 for a foreign incident)', async () => {
    server.use(
      http.get(api('/resources'), () =>
        problemResponse(404, 'resource.not_found', 'Incident MD-9 not found'),
      ),
    );
    renderApp('/resources/search?incidentId=MD-9');
    expect(await screen.findByText('Incident MD-9 not found')).toBeInTheDocument();
  });
});
