import type {
  CreateResourceRequestRequest,
  RequestAction,
  RequestRole,
  RequestStatus,
  ResourceRequest,
} from '../types/api';
import { apiClient } from './client';

/** #12 `POST /resource-requests` → 201 ResourceRequest (PENDING, or DEPLOYED with deployImmediately). */
export async function createResourceRequest(
  body: CreateResourceRequestRequest,
): Promise<ResourceRequest> {
  const { data } = await apiClient.post<ResourceRequest>('/resource-requests', body);
  return data;
}

/** #13 `GET /resource-requests?role=requester|owner&status=A,B` */
export async function listResourceRequests(
  role: RequestRole,
  statuses: readonly RequestStatus[] = [],
  signal?: AbortSignal,
): Promise<ResourceRequest[]> {
  const params: Record<string, string> = { role };
  if (statuses.length > 0) {
    params.status = statuses.join(',');
  }
  const { data } = await apiClient.get<ResourceRequest[]>('/resource-requests', { params, signal });
  return data;
}

/** #14–#17 `POST /resource-requests/{id}/{deploy|reject|cancel|return}` */
export async function performRequestAction(
  requestId: number,
  action: RequestAction,
): Promise<ResourceRequest> {
  const { data } = await apiClient.post<ResourceRequest>(
    `/resource-requests/${String(requestId)}/${action}`,
  );
  return data;
}
