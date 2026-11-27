import type {
  AuthUser,
  Declaration,
  EsfReport,
  Esf,
  Incident,
  LoginResponse,
  Page,
  ResourceDetail,
  ResourceRequest,
  ResourceSearchResult,
  UserProfile,
} from '../types/api';

export const ALICE: AuthUser = { username: 'alice', name: 'Alice Smith', userType: 'INDIVIDUAL' };

export const ALICE_PROFILE: UserProfile = {
  ...ALICE,
  jobTitle: 'Field Coordinator',
  hireDate: '2020-03-01',
};

export const ESFS: Esf[] = [
  { esfNumber: 1, description: 'Transportation' },
  { esfNumber: 2, description: 'Communications' },
  { esfNumber: 3, description: 'Public Works and Engineering' },
];

export const COST_UNITS = ['Day', 'Each', 'Hour', 'Week'];

export const DECLARATIONS: Declaration[] = [
  { abbreviation: 'FM', name: 'Fire Management Assistance' },
  { abbreviation: 'MD', name: 'Major Disaster' },
];

export const INCIDENTS: Incident[] = [
  {
    incidentId: 'MD-2',
    declarationAbbreviation: 'MD',
    incidentDate: '2026-11-20',
    description: 'Flooding on I-75',
    latitude: 33.749,
    longitude: -84.388,
    ownerUsername: 'alice',
  },
];

export const SEARCH_RESULTS: ResourceSearchResult[] = [
  {
    resourceId: 1001,
    resourceName: 'Hydraulic Crane',
    ownerUsername: 'acme_rescue',
    ownerName: 'Acme Rescue Co',
    cost: 150,
    costUnit: 'Hour',
    available: true,
    distanceKm: null,
  },
  {
    resourceId: 1002,
    resourceName: 'Water Pump',
    ownerUsername: 'alice',
    ownerName: 'Alice Smith',
    cost: 40.5,
    costUnit: 'Day',
    available: true,
    distanceKm: null,
  },
  {
    resourceId: 1003,
    resourceName: 'Generator',
    ownerUsername: 'alice',
    ownerName: 'Alice Smith',
    cost: 75,
    costUnit: 'Day',
    available: false,
    nextAvailableDate: '2026-12-01',
    distanceKm: null,
  },
];

export function page<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  };
}

export function resourceRequest(overrides: Partial<ResourceRequest> = {}): ResourceRequest {
  return {
    requestId: 7,
    resourceId: 1001,
    resourceName: 'Hydraulic Crane',
    ownerUsername: 'acme_rescue',
    incidentId: 'MD-2',
    incidentDescription: 'Flooding on I-75',
    requestedBy: 'alice',
    status: 'PENDING',
    requestDate: '2026-11-25',
    expectedReturnDate: '2026-12-10',
    resourceAvailable: true,
    ...overrides,
  };
}

export function resourceDetail(overrides: Partial<ResourceDetail> = {}): ResourceDetail {
  return {
    resourceId: 1010,
    resourceName: 'Hydraulic Crane',
    model: 'XL-200',
    ownerUsername: 'alice',
    ownerName: 'Alice Smith',
    primaryEsf: { esfNumber: 3, description: 'Public Works and Engineering' },
    additionalEsfs: [],
    capabilities: [],
    cost: 150,
    costUnit: 'Hour',
    latitude: 33.77,
    longitude: -84.39,
    maxDistanceKm: null,
    available: true,
    nextAvailableDate: null,
    ...overrides,
  };
}

export const REPORT: EsfReport = {
  rows: Array.from({ length: 15 }, (_, index) => ({
    esfNumber: index + 1,
    esfDescription: `ESF ${String(index + 1)} description`,
    totalResources: index === 0 ? 2 : index === 2 ? 3 : 0,
    resourcesInUse: index === 0 ? 1 : index === 2 ? 1 : 0,
  })),
  totals: { totalResources: 5, resourcesInUse: 2 },
};

/** Builds an unsigned JWT-shaped token whose payload carries `exp` (seconds). */
export function makeJwt(expSeconds: number, sub = 'alice'): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ iss: 'erms', sub, exp: expSeconds })}.signature`;
}

export function loginResponse(user: AuthUser = ALICE, ttlSeconds = 3600): LoginResponse {
  return {
    accessToken: makeJwt(Math.floor(Date.now() / 1000) + ttlSeconds, user.username),
    tokenType: 'Bearer',
    expiresIn: ttlSeconds,
    user,
  };
}

export function problem(
  status: number,
  code: string,
  detail: string,
  errors?: { field: string; message: string }[],
) {
  return {
    type: 'about:blank',
    title: status === 400 ? 'Bad Request' : status === 409 ? 'Conflict' : 'Error',
    status,
    detail,
    instance: '/api/v1/test',
    code,
    requestId: 'req-123',
    ...(errors ? { errors } : {}),
  };
}
