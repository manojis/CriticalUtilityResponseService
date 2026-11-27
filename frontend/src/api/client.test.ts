import { http, HttpResponse } from 'msw';
import { signIn } from '../test/render';
import { api, problemResponse } from '../test/msw/handlers';
import { server } from '../test/msw/server';
import { login } from './auth';
import { apiClient, setUnauthorizedHandler } from './client';
import { ApiError } from './errors';
import { getIncident } from './incidents';
import { getMe } from './me';
import { listResourceRequests, performRequestAction } from './requests';
import { getResource, searchResources, toSearchQuery } from './resources';

describe('apiClient', () => {
  it('uses the relative /api/v1 base URL (no hardcoded host)', () => {
    expect(apiClient.defaults.baseURL).toBe('/api/v1');
  });

  it('adds the Bearer token from the stored session', async () => {
    signIn();
    let authorization: string | null = null;
    server.use(
      http.get(api('/me'), ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json({ username: 'alice', name: 'Alice', userType: 'INDIVIDUAL' });
      }),
    );
    await getMe();
    expect(authorization).toMatch(/^Bearer [\w-]+\.[\w-]+\.signature$/);
  });

  it('sends no Authorization header without a session', async () => {
    let authorization: string | null = 'unset';
    server.use(
      http.get(api('/me'), ({ request }) => {
        authorization = request.headers.get('Authorization');
        return problemResponse(401, 'auth.unauthorized', 'Full authentication is required');
      }),
    );
    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
    expect(authorization).toBeNull();
  });

  it('calls the unauthorized handler on 401 from an authenticated endpoint', async () => {
    signIn();
    const handler = vi.fn();
    const unregister = setUnauthorizedHandler(handler);
    server.use(
      http.get(api('/me'), () => problemResponse(401, 'auth.unauthorized', 'Token expired')),
    );
    await expect(getMe()).rejects.toMatchObject({ status: 401, code: 'auth.unauthorized' });
    expect(handler).toHaveBeenCalledTimes(1);
    unregister();
  });

  it('does not treat a failed login (401) as an expired session', async () => {
    const handler = vi.fn();
    const unregister = setUnauthorizedHandler(handler);
    await expect(login({ username: 'alice', password: 'wrong' })).rejects.toMatchObject({
      status: 401,
      code: 'auth.invalid_credentials',
      detail: 'Invalid username or password',
    });
    expect(handler).not.toHaveBeenCalled();
    unregister();
  });

  it('normalises ProblemDetail responses into ApiError with field errors', async () => {
    signIn();
    server.use(
      http.post(api('/resource-requests/7/cancel'), () =>
        problemResponse(409, 'state.conflict', 'Request 7 is DEPLOYED; cannot cancel'),
      ),
    );
    const error = await performRequestAction(7, 'cancel').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 409,
      detail: 'Request 7 is DEPLOYED; cannot cancel',
      requestId: 'req-123',
    });
  });

  it('unregistering only removes the matching handler', () => {
    const first = vi.fn();
    const second = vi.fn();
    const unregisterFirst = setUnauthorizedHandler(first);
    const unregisterSecond = setUnauthorizedHandler(second);
    unregisterFirst();
    unregisterSecond();
    expect(first).not.toHaveBeenCalled();
  });
});

describe('endpoint functions', () => {
  it('fetches single incidents and resources with encoded ids', async () => {
    signIn();
    const paths: string[] = [];
    server.use(
      http.get(api('/incidents/:id'), ({ request, params }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json({ incidentId: params.id });
      }),
      http.get(api('/resources/:id'), ({ params }) =>
        HttpResponse.json({ resourceId: Number(params.id) }),
      ),
    );
    await expect(getIncident('MD 2')).resolves.toMatchObject({ incidentId: 'MD 2' });
    expect(paths).toEqual(['/api/v1/incidents/MD%202']);
    await expect(getResource(1001)).resolves.toMatchObject({ resourceId: 1001 });
  });

  it('serialises search filters and omits blanks', async () => {
    signIn();
    let url: URL | null = null;
    server.use(
      http.get(api('/resources'), ({ request }) => {
        url = new URL(request.url);
        return HttpResponse.json({
          content: [],
          page: 1,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        });
      }),
    );
    await searchResources({
      keyword: '  crane ',
      esf: 3,
      incidentId: 'MD-2',
      distanceKm: 50,
      page: 1,
      size: 20,
    });
    expect(Object.fromEntries(url!.searchParams)).toEqual({
      keyword: 'crane',
      esf: '3',
      incidentId: 'MD-2',
      distanceKm: '50',
      page: '1',
      size: '20',
    });
    expect(toSearchQuery({ keyword: '   ' })).toEqual({});
  });

  it('lists requests with role and comma-separated statuses', async () => {
    signIn();
    let url: URL | null = null;
    server.use(
      http.get(api('/resource-requests'), ({ request }) => {
        url = new URL(request.url);
        return HttpResponse.json([]);
      }),
    );
    await listResourceRequests('requester', ['PENDING', 'DEPLOYED']);
    expect(url!.searchParams.get('role')).toBe('requester');
    expect(url!.searchParams.get('status')).toBe('PENDING,DEPLOYED');
    await listResourceRequests('owner');
    expect(url!.searchParams.has('status')).toBe(false);
  });
});
