import type {
  CreateResourceRequest,
  Page,
  ResourceDetail,
  ResourceSearchParams,
  ResourceSearchResult,
} from '../types/api';
import { apiClient } from './client';

/** #9 `POST /resources` → 201 ResourceDetail. */
export async function createResource(body: CreateResourceRequest): Promise<ResourceDetail> {
  const { data } = await apiClient.post<ResourceDetail>('/resources', body);
  return data;
}

/** #10 `GET /resources/{resourceId}` */
export async function getResource(
  resourceId: number,
  signal?: AbortSignal,
): Promise<ResourceDetail> {
  const { data } = await apiClient.get<ResourceDetail>(`/resources/${String(resourceId)}`, {
    signal,
  });
  return data;
}

/** Drops empty/undefined filters so the query string only carries what the user set. */
export function toSearchQuery(params: ResourceSearchParams): Record<string, string> {
  const query: Record<string, string> = {};
  const keyword = params.keyword?.trim();
  if (keyword) query.keyword = keyword;
  if (params.esf !== undefined) query.esf = String(params.esf);
  if (params.incidentId) query.incidentId = params.incidentId;
  if (params.distanceKm !== undefined) query.distanceKm = String(params.distanceKm);
  if (params.page !== undefined) query.page = String(params.page);
  if (params.size !== undefined) query.size = String(params.size);
  return query;
}

/** #11 `GET /resources?keyword&esf&incidentId&distanceKm&page&size` */
export async function searchResources(
  params: ResourceSearchParams,
  signal?: AbortSignal,
): Promise<Page<ResourceSearchResult>> {
  const { data } = await apiClient.get<Page<ResourceSearchResult>>('/resources', {
    params: toSearchQuery(params),
    signal,
  });
  return data;
}
