import type { CostUnit, Declaration, Esf } from '../types/api';
import { apiClient } from './client';

/** #3 `GET /esfs`, ordered by esfNumber. */
export async function getEsfs(signal?: AbortSignal): Promise<Esf[]> {
  const { data } = await apiClient.get<Esf[]>('/esfs', { signal });
  return data;
}

/** #4 `GET /cost-units`, alphabetical. */
export async function getCostUnits(signal?: AbortSignal): Promise<CostUnit[]> {
  const { data } = await apiClient.get<CostUnit[]>('/cost-units', { signal });
  return data;
}

/** #5 `GET /declarations`, ordered by name. */
export async function getDeclarations(signal?: AbortSignal): Promise<Declaration[]> {
  const { data } = await apiClient.get<Declaration[]>('/declarations', { signal });
  return data;
}
