import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { CreateResourceRequest } from '../../types/api';
import { resourceDetail } from '../../test/fixtures';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp, selectOption } from '../../test/render';

type User = ReturnType<typeof renderApp>['user'];

async function addCapability(user: User, text: string) {
  await user.type(screen.getByLabelText('Capability'), `${text}{Enter}`);
}

async function chooseAdditionalEsf(user: User, name: string) {
  await user.click(screen.getByRole('combobox', { name: 'Additional ESFs' }));
  await user.click(await screen.findByRole('option', { name }));
}

async function fillRequired(user: User) {
  await user.type(screen.getByLabelText(/resource name/i), 'Hydraulic Crane');
  await selectOption(user, /primary esf/i, '#3 Public Works and Engineering');
  await user.type(screen.getByLabelText(/^cost/i), '150.00');
  await selectOption(user, /^per/i, 'Hour');
  await user.type(screen.getByLabelText(/latitude/i), '33.77');
  await user.type(screen.getByLabelText(/longitude/i), '-84.39');
}

describe('AddResourcePage', () => {
  it('shows the owner from /me (read-only)', async () => {
    renderApp('/resources/new');
    const owner = await screen.findByLabelText('Owner');
    expect(owner).toHaveValue('Alice Smith (alice)');
    expect(owner).toHaveAttribute('readonly');
  });

  it('creates a resource with capabilities and additional ESFs and shows the new id', async () => {
    let body: CreateResourceRequest | undefined;
    server.use(
      http.post(api('/resources'), async ({ request }) => {
        body = (await request.json()) as CreateResourceRequest;
        return HttpResponse.json(resourceDetail({ resourceId: 1010 }), { status: 201 });
      }),
    );
    const { user } = renderApp('/resources/new');
    await screen.findByLabelText('Owner');
    await fillRequired(user);
    await user.type(screen.getByLabelText(/^model/i), 'XL-200');
    await chooseAdditionalEsf(user, '#1 Transportation');
    await addCapability(user, 'Lift 20t');
    await addCapability(user, 'Night ops');
    await user.type(screen.getByLabelText(/max distance/i), '120');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Resource #1010 created')).toBeInTheDocument();
    expect(body).toEqual({
      resourceName: 'Hydraulic Crane',
      model: 'XL-200',
      primaryEsf: 3,
      additionalEsfs: [1],
      capabilities: ['Lift 20t', 'Night ops'],
      cost: 150,
      costUnit: 'Hour',
      latitude: 33.77,
      longitude: -84.39,
      maxDistanceKm: 120,
    });
  });

  it('removes the primary ESF from the additional ESFs when it changes', async () => {
    const { user } = renderApp('/resources/new');
    await screen.findByLabelText('Owner');
    await chooseAdditionalEsf(user, '#2 Communications');
    expect(screen.getByRole('button', { name: '#2 Communications' })).toBeInTheDocument();

    await selectOption(user, /primary esf/i, '#2 Communications');
    expect(screen.queryByRole('button', { name: '#2 Communications' })).not.toBeInTheDocument();

    // The primary is not offered as an additional ESF.
    await user.click(screen.getByRole('combobox', { name: 'Additional ESFs' }));
    const listbox = await screen.findByRole('listbox');
    expect(
      within(listbox).queryByRole('option', { name: '#2 Communications' }),
    ).not.toBeInTheDocument();
  });

  it('adds and removes capability chips and rejects duplicates', async () => {
    const { user } = renderApp('/resources/new');
    await screen.findByLabelText('Owner');
    await addCapability(user, 'Night ops');
    await addCapability(user, 'night OPS');
    expect(screen.getByText('Capability already added')).toBeInTheDocument();

    const list = screen.getByRole('list', { name: 'Capabilities' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(1);
    await user.click(screen.getByRole('img', { name: 'Remove Night ops' }));
    expect(within(list).queryAllByRole('listitem')).toHaveLength(0);

    await user.clear(screen.getByLabelText('Capability'));
    await user.click(screen.getByRole('button', { name: 'Add' }));
    expect(screen.getByText('Enter a capability')).toBeInTheDocument();
  });

  it('validates required fields', async () => {
    const { user } = renderApp('/resources/new');
    await screen.findByLabelText('Owner');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(
      await screen.findByText('Resource name must be between 3 and 100 characters'),
    ).toBeInTheDocument();
    expect(screen.getByText('Primary ESF is required')).toBeInTheDocument();
    expect(screen.getByText('Cost is required')).toBeInTheDocument();
    expect(screen.getByText('Cost unit is required')).toBeInTheDocument();
  });

  it('maps server field errors (including indexed paths) onto fields', async () => {
    server.use(
      http.post(api('/resources'), () =>
        problemResponse(400, 'validation.failed', 'Request validation failed', [
          { field: 'costUnit', message: 'unknown cost unit' },
          { field: 'capabilities[0]', message: 'size must be between 1 and 100' },
        ]),
      ),
    );
    const { user } = renderApp('/resources/new');
    await screen.findByLabelText('Owner');
    await fillRequired(user);
    await addCapability(user, 'Lift');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('unknown cost unit')).toBeInTheDocument();
    expect(screen.getByText('size must be between 1 and 100')).toBeInTheDocument();
  });

  it('shows an error with retry when reference data fails', async () => {
    server.use(
      http.get(api('/cost-units'), () =>
        problemResponse(500, 'internal.error', 'Unexpected error'),
      ),
    );
    renderApp('/resources/new');
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
