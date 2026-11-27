import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ResourceRequest } from '../../types/api';
import { resourceRequest } from '../../test/fixtures';
import { api, problemResponse } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

const IN_USE = resourceRequest({
  requestId: 11,
  resourceName: 'Hydraulic Crane',
  status: 'DEPLOYED',
  deployDate: '2026-11-21',
  resourceAvailable: false,
});
const REQUESTED = resourceRequest({ requestId: 12, resourceId: 1004, resourceName: 'Boat' });
const RECEIVED_FREE = resourceRequest({
  requestId: 13,
  resourceId: 1002,
  resourceName: 'Water Pump',
  ownerUsername: 'alice',
  requestedBy: 'bob',
});
const RECEIVED_BUSY = resourceRequest({
  requestId: 14,
  resourceId: 1003,
  resourceName: 'Generator',
  ownerUsername: 'alice',
  requestedBy: 'carol',
  resourceAvailable: false,
});

/** Serves the three tables from mutable lists and records action calls. */
function mockRequests() {
  const state = {
    inUse: [IN_USE],
    requested: [REQUESTED],
    received: [RECEIVED_FREE, RECEIVED_BUSY],
    calls: [] as string[],
    listCalls: 0,
  };
  server.use(
    http.get(api('/resource-requests'), ({ request }) => {
      state.listCalls += 1;
      const url = new URL(request.url);
      const key = `${url.searchParams.get('role') ?? ''}:${url.searchParams.get('status') ?? ''}`;
      const map: Record<string, ResourceRequest[]> = {
        'requester:DEPLOYED': state.inUse,
        'requester:PENDING': state.requested,
        'owner:PENDING': state.received,
      };
      return map[key]
        ? HttpResponse.json(map[key])
        : problemResponse(400, 'request.malformed', key);
    }),
    http.post(api('/resource-requests/:id/:action'), ({ params }) => {
      const id = Number(params.id);
      const action = String(params.action);
      state.calls.push(`${action}:${String(id)}`);
      const all = [...state.inUse, ...state.requested, ...state.received];
      const target = all.find((r) => r.requestId === id)!;
      state.inUse = state.inUse.filter((r) => r.requestId !== id);
      state.requested = state.requested.filter((r) => r.requestId !== id);
      state.received = state.received.filter((r) => r.requestId !== id);
      const status = {
        deploy: 'DEPLOYED',
        reject: 'REJECTED',
        cancel: 'CANCELLED',
        return: 'RETURNED',
      }[action] as ResourceRequest['status'];
      return HttpResponse.json({ ...target, status });
    }),
  );
  return state;
}

function table(name: string) {
  return screen.getByRole('region', { name });
}

async function confirmAction(
  user: ReturnType<typeof renderApp>['user'],
  buttonName: string,
  confirm: string,
) {
  await user.click(screen.getByRole('button', { name: buttonName }));
  const dialog = await screen.findByRole('dialog');
  await user.click(within(dialog).getByRole('button', { name: confirm }));
}

describe('ResourceStatusPage', () => {
  it('renders the three tables from the right queries', async () => {
    mockRequests();
    renderApp('/resource-status');
    await screen.findByText('Boat');

    const inUse = table('Resources in use');
    expect(within(inUse).getByText('Hydraulic Crane')).toBeInTheDocument();
    expect(within(inUse).getByText('2026-11-21')).toBeInTheDocument();
    expect(within(inUse).getByRole('button', { name: 'Return request 11' })).toBeEnabled();

    const requested = table('Resources requested by me');
    expect(within(requested).getByRole('button', { name: 'Cancel request 12' })).toBeEnabled();

    const received = table('Requests received');
    expect(within(received).getByText('bob')).toBeInTheDocument();
    expect(within(received).getByRole('button', { name: 'Deploy request 13' })).toBeEnabled();
    // Deploy is disabled while the resource is deployed elsewhere; Reject stays available.
    expect(within(received).getByRole('button', { name: 'Deploy request 14' })).toBeDisabled();
    expect(within(received).getByRole('button', { name: 'Reject request 14' })).toBeEnabled();
  });

  it.each([
    ['Return request 11', 'Return', 'return:11', 'Hydraulic Crane returned'],
    ['Cancel request 12', 'Cancel request', 'cancel:12', 'Request #12 cancelled'],
    ['Deploy request 13', 'Deploy', 'deploy:13', 'Water Pump deployed to MD-2'],
    ['Reject request 14', 'Reject', 'reject:14', 'Request #14 rejected'],
  ])(
    '%s calls the right endpoint and refreshes all tables',
    async (button, confirm, call, message) => {
      const state = mockRequests();
      const { user } = renderApp('/resource-status');
      await screen.findByText('Boat');
      const listCallsBefore = state.listCalls;

      await confirmAction(user, button, confirm);

      expect(await screen.findByText(message)).toBeInTheDocument();
      expect(state.calls).toEqual([call]);
      await waitFor(() => {
        expect(state.listCalls).toBe(listCallsBefore + 3);
      });
      const id = call.split(':')[1]!;
      await waitFor(() => {
        expect(
          screen.queryByRole('button', { name: new RegExp(`request ${id}$`) }),
        ).not.toBeInTheDocument();
      });
    },
  );

  it('does nothing when the confirmation is dismissed', async () => {
    const state = mockRequests();
    const { user } = renderApp('/resource-status');
    await screen.findByText('Boat');
    await user.click(screen.getByRole('button', { name: 'Reject request 13' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Go back' }),
    );
    expect(state.calls).toEqual([]);
  });

  it('shows the server message when an action conflicts (409)', async () => {
    mockRequests();
    server.use(
      http.post(api('/resource-requests/:id/deploy'), () =>
        problemResponse(409, 'state.conflict', 'Resource 1002 is already deployed'),
      ),
    );
    const { user } = renderApp('/resource-status');
    await screen.findByText('Boat');
    await confirmAction(user, 'Deploy request 13', 'Deploy');
    expect(await screen.findByText('Resource 1002 is already deployed')).toBeInTheDocument();
  });

  it('shows empty states', async () => {
    renderApp('/resource-status');
    expect(await screen.findByText('You are not using any resources.')).toBeInTheDocument();
    expect(screen.getByText('You have no pending requests.')).toBeInTheDocument();
    expect(screen.getByText('Nobody has requested your resources.')).toBeInTheDocument();
  });

  it('shows a per-table error with retry', async () => {
    server.use(
      http.get(api('/resource-requests'), ({ request }) =>
        new URL(request.url).searchParams.get('role') === 'owner'
          ? problemResponse(500, 'internal.error', 'Unexpected error')
          : HttpResponse.json([]),
      ),
    );
    renderApp('/resource-status');
    const received = await screen.findByRole('region', { name: 'Requests received' });
    expect(await within(received).findByText('Unexpected error')).toBeInTheDocument();
    expect(within(received).getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
