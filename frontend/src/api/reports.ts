import type { EsfReport } from '../types/api';
import { apiClient } from './client';

/** #18 `GET /reports/resources-by-esf` — the caller's resources grouped by primary ESF. */
export async function getResourcesByEsfReport(signal?: AbortSignal): Promise<EsfReport> {
  const { data } = await apiClient.get<EsfReport>('/reports/resources-by-esf', { signal });
  return data;
}
