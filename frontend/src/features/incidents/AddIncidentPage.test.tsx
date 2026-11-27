import { fireEvent, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { CreateIncidentRequest } from '../../types/api';
import { todayIso } from '../../utils/date';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp, selectOption } from '../../test/render';

async function fillValidForm(user: ReturnType<typeof renderApp>['user']) {
  await selectOption(user, /declaration/i, '(MD) Major Disaster');
  fireEvent.change(screen.getByLabelText(/incident date/i), { target: { value: '2026-11-20' } });
  await user.type(screen.getByLabelText(/description/i), 'Flooding on I-75');
  await user.type(screen.getByLabelText(/latitude/i), '33.749');
  await user.type(screen.getByLabelText(/longitude/i), '-84.388');
}

describe('AddIncidentPage', () => {
  it('creates an incident and shows the server-generated id with a search link', async () => {
    let body: CreateIncidentRequest | undefined;
    server.use(
      http.post(api('/incidents'), async ({ request }) => {
        body = (await request.json()) as CreateIncidentRequest;
        return HttpResponse.json(
          { incidentId: 'MD-3', ownerUsername: 'alice', ...body },
          { status: 201, headers: { Location: '/api/v1/incidents/MD-3' } },
        );
      }),
    );
    const { user } = renderApp('/incidents/new');
    await screen.findByRole('heading', { name: 'Add Emergency Incident' });
    await screen.findByRole('combobox', { name: /declaration/i });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const success = await screen.findByText('Incident MD-3 created');
    expect(body).toEqual({
      declarationAbbreviation: 'MD',
      incidentDate: '2026-11-20',
      description: 'Flooding on I-75',
      latitude: 33.749,
      longitude: -84.388,
    });
    const status = success.closest<HTMLElement>('[role="status"]')!;
    expect(within(status).getByRole('link', { name: /search resources/i })).toHaveAttribute(
      'href',
      '/resources/search?incidentId=MD-3',
    );
    // The form is reset for the next incident.
    expect(screen.getByLabelText(/description/i)).toHaveValue('');
  });

  it('never asks the user for an incident id', async () => {
    renderApp('/incidents/new');
    expect(await screen.findByLabelText(/incident id/i)).toBeDisabled();
  });

  it('defaults the date to today and blocks future dates', async () => {
    renderApp('/incidents/new');
    const date = await screen.findByLabelText(/incident date/i);
    expect(date).toHaveValue(todayIso());
    expect(date).toHaveAttribute('max', todayIso());
  });

  it('shows client-side validation messages per field', async () => {
    const { user } = renderApp('/incidents/new');
    await screen.findByRole('combobox', { name: /declaration/i });
    await user.type(screen.getByLabelText(/description/i), 'short');
    await user.type(screen.getByLabelText(/latitude/i), '95');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Declaration is required')).toBeInTheDocument();
    expect(
      screen.getByText('Description must be between 6 and 500 characters'),
    ).toBeInTheDocument();
    expect(screen.getByText('Latitude must be a number between -90 and 90')).toBeInTheDocument();
    expect(screen.getByText('Longitude is required')).toBeInTheDocument();
  });

  it('maps server field errors onto the matching inputs', async () => {
    server.use(
      http.post(api('/incidents'), () =>
        problemResponse(400, 'validation.failed', 'Request validation failed', [
          { field: 'description', message: 'size must be between 6 and 500' },
          { field: 'somethingElse', message: 'is odd' },
        ]),
      ),
    );
    const { user } = renderApp('/incidents/new');
    await screen.findByRole('combobox', { name: /declaration/i });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('size must be between 6 and 500')).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toHaveAttribute('aria-invalid', 'true');
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Request validation failed');
    expect(alert).toHaveTextContent('somethingElse');
  });

  it('shows an error when declarations fail to load', async () => {
    server.use(http.get(api('/declarations'), () => HttpResponse.error()));
    renderApp('/incidents/new');
    expect(await screen.findByText('Network error')).toBeInTheDocument();
  });
});
