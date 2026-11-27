import type { ResourceSearchParams } from '../../types/api';
import { parseDecimal } from '../../validation/common';
import type { SearchFilterValues } from '../../validation/request';

/** Results per page (FEATURES.md F-5 UI notes). */
export const PAGE_SIZE = 20;

/** Converts the filter form to API params (blank filters omitted). */
export function toSearchParams(filters: SearchFilterValues, page: number): ResourceSearchParams {
  const params: ResourceSearchParams = { page, size: PAGE_SIZE };
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
  if (filters.esf) params.esf = Number(filters.esf);
  if (filters.incidentId) {
    params.incidentId = filters.incidentId;
    const distance = parseDecimal(filters.distanceKm);
    if (distance !== null) params.distanceKm = distance;
  }
  return params;
}
