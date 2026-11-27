import type { CreateIncidentRequest, Incident } from '../types/api';
import { apiClient } from './client';

/** #6 `POST /incidents` → 201 with the server-generated incidentId. */
export async function createIncident(body: CreateIncidentRequest): Promise<Incident> {
  const { data } = await apiClient.post<Incident>('/incidents', body);
  return data;
}

/** #7 `GET /incidents` — the caller's incidents, newest first. */
export async function listMyIncidents(signal?: AbortSignal): Promise<Incident[]> {
  const { data } = await apiClient.get<Incident[]>('/incidents', { signal });
  return data;
}

/** #8 `GET /incidents/{incidentId}` (owner only). */
export async function getIncident(incidentId: string, signal?: AbortSignal): Promise<Incident> {
  const { data } = await apiClient.get<Incident>(`/incidents/${encodeURIComponent(incidentId)}`, {
    signal,
  });
  return data;
}
