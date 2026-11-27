/**
 * TypeScript mirror of the ERMS REST API v1 contract (docs/API.md).
 *
 * Conventions taken from the contract:
 * - JSON is camelCase.
 * - Dates are ISO-8601 `YYYY-MM-DD` strings (`IsoDate`).
 * - Nulls are omitted from responses, so every nullable response field is optional and may
 *   also arrive as an explicit `null` (the contract's own examples show both).
 */

/** ISO-8601 calendar date, `YYYY-MM-DD`. */
export type IsoDate = string;

export type UserType = 'INDIVIDUAL' | 'MUNICIPALITY' | 'GOVERNMENT_AGENCY' | 'COMPANY';

export type RequestStatus = 'PENDING' | 'DEPLOYED' | 'REJECTED' | 'CANCELLED' | 'RETURNED';

// ---------------------------------------------------------------------------------------------
// Errors (RFC 9457 ProblemDetail, API.md §3)
// ---------------------------------------------------------------------------------------------

export type ProblemCode =
  | 'validation.failed'
  | 'request.malformed'
  | 'auth.unauthorized'
  | 'auth.invalid_credentials'
  | 'auth.forbidden'
  | 'resource.not_found'
  | 'state.conflict'
  | 'internal.error';

export interface ProblemFieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  /** One of {@link ProblemCode}; typed as string so unknown future codes don't break parsing. */
  code?: string;
  requestId?: string;
  errors?: ProblemFieldError[];
}

// ---------------------------------------------------------------------------------------------
// #1 POST /auth/login, #2 GET /me
// ---------------------------------------------------------------------------------------------

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthUser {
  username: string;
  name: string;
  userType: UserType;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Token lifetime in seconds. */
  expiresIn: number;
  user: AuthUser;
}

/** Only the fields for the caller's user type are present (nulls omitted). */
export interface UserProfile extends AuthUser {
  jobTitle?: string | null;
  hireDate?: IsoDate | null;
  municipalityCategory?: string | null;
  agencyNameLocalOffice?: string | null;
  headquarters?: string | null;
  numberOfEmployees?: number | null;
}

// ---------------------------------------------------------------------------------------------
// #3–#5 Reference data
// ---------------------------------------------------------------------------------------------

export interface Esf {
  esfNumber: number;
  description: string;
}

/** e.g. "Day", "Each", "Hour", "Week" (alphabetical). */
export type CostUnit = string;

export interface Declaration {
  abbreviation: string;
  name: string;
}

// ---------------------------------------------------------------------------------------------
// #6–#8 Incidents
// ---------------------------------------------------------------------------------------------

export interface CreateIncidentRequest {
  declarationAbbreviation: string;
  incidentDate: IsoDate;
  description: string;
  latitude: number;
  longitude: number;
}

export interface Incident {
  /** Server-generated, `<ABBR>-<n>` e.g. `MD-3`. */
  incidentId: string;
  declarationAbbreviation: string;
  incidentDate: IsoDate;
  description: string;
  latitude: number;
  longitude: number;
  ownerUsername: string;
}

// ---------------------------------------------------------------------------------------------
// #9–#11 Resources
// ---------------------------------------------------------------------------------------------

export interface CreateResourceRequest {
  resourceName: string;
  model?: string;
  primaryEsf: number;
  additionalEsfs?: number[];
  capabilities?: string[];
  cost: number;
  costUnit: CostUnit;
  latitude: number;
  longitude: number;
  /** Omitted/null = no distance limit. */
  maxDistanceKm?: number | null;
}

export interface ResourceDetail {
  resourceId: number;
  resourceName: string;
  model?: string | null;
  ownerUsername: string;
  ownerName: string;
  primaryEsf: Esf;
  additionalEsfs: Esf[];
  capabilities: string[];
  cost: number;
  costUnit: CostUnit;
  latitude: number;
  longitude: number;
  maxDistanceKm?: number | null;
  available: boolean;
  nextAvailableDate?: IsoDate | null;
}

export interface ResourceSearchParams {
  keyword?: string;
  esf?: number;
  incidentId?: string;
  /** Requires `incidentId`. */
  distanceKm?: number;
  /** Zero-based page index. */
  page?: number;
  /** 1..100, default 20. */
  size?: number;
}

export interface ResourceSearchResult {
  resourceId: number;
  resourceName: string;
  ownerUsername: string;
  ownerName: string;
  cost: number;
  costUnit: CostUnit;
  available: boolean;
  nextAvailableDate?: IsoDate | null;
  /** Null/absent when no incidentId was given; otherwise rounded to 0.1 km. */
  distanceKm?: number | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------------------------------------------------------------------------------------------
// #12–#17 Resource requests
// ---------------------------------------------------------------------------------------------

export interface CreateResourceRequestRequest {
  resourceId: number;
  incidentId: string;
  expectedReturnDate: IsoDate;
  /** Only allowed when the caller owns the resource; creates the request already DEPLOYED. */
  deployImmediately?: boolean;
}

export type RequestRole = 'requester' | 'owner';

export type RequestAction = 'deploy' | 'reject' | 'cancel' | 'return';

export interface ResourceRequest {
  requestId: number;
  resourceId: number;
  resourceName: string;
  ownerUsername: string;
  incidentId: string;
  incidentDescription: string;
  requestedBy: string;
  status: RequestStatus;
  requestDate: IsoDate;
  expectedReturnDate: IsoDate;
  deployDate?: IsoDate | null;
  actualReturnDate?: IsoDate | null;
  /** True when no DEPLOYED request currently exists for the resource. Enables "Deploy". */
  resourceAvailable: boolean;
}

// ---------------------------------------------------------------------------------------------
// #18 Report
// ---------------------------------------------------------------------------------------------

export interface EsfReportRow {
  esfNumber: number;
  esfDescription: string;
  totalResources: number;
  resourcesInUse: number;
}

export interface EsfReport {
  rows: EsfReportRow[];
  totals: {
    totalResources: number;
    resourcesInUse: number;
  };
}
