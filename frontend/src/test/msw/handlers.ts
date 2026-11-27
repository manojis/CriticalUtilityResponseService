import { http, HttpResponse } from 'msw';
import {
  ALICE_PROFILE,
  COST_UNITS,
  DECLARATIONS,
  ESFS,
  INCIDENTS,
  loginResponse,
  page,
  problem,
  REPORT,
  SEARCH_RESULTS,
} from '../fixtures';

/** Relative to the jsdom origin, exactly like the SPA's same-origin `/api/v1` base URL. */
export const api = (path: string) => `/api/v1${path}`;

const PROBLEM_JSON = { headers: { 'Content-Type': 'application/problem+json' } };

export function problemResponse(
  status: number,
  code: string,
  detail: string,
  errors?: { field: string; message: string }[],
) {
  return HttpResponse.json(problem(status, code, detail, errors), { status, ...PROBLEM_JSON });
}

/** Happy-path defaults. Tests override individual endpoints with `server.use(...)`. */
export const handlers = [
  http.post(api('/auth/login'), async ({ request }) => {
    const body = (await request.json()) as { username: string; password: string };
    if (body.username === 'alice' && body.password === 'password') {
      return HttpResponse.json(loginResponse());
    }
    return problemResponse(401, 'auth.invalid_credentials', 'Invalid username or password');
  }),
  http.get(api('/me'), () => HttpResponse.json(ALICE_PROFILE)),
  http.get(api('/esfs'), () => HttpResponse.json(ESFS)),
  http.get(api('/cost-units'), () => HttpResponse.json(COST_UNITS)),
  http.get(api('/declarations'), () => HttpResponse.json(DECLARATIONS)),
  http.get(api('/incidents'), () => HttpResponse.json(INCIDENTS)),
  http.get(api('/resources'), () => HttpResponse.json(page(SEARCH_RESULTS))),
  http.get(api('/resource-requests'), () => HttpResponse.json([])),
  http.get(api('/reports/resources-by-esf'), () => HttpResponse.json(REPORT)),
];
