import type { ResourceSearchResult } from '../../types/api';

export type RowAction = 'deploy' | 'request';

/**
 * Search-result action when an incident is selected (ARCHITECTURE.md §4.3):
 * **Deploy** for the caller's own available resource (sent with `deployImmediately: true`),
 * otherwise **Request**.
 */
export function resolveRowAction(
  row: Pick<ResourceSearchResult, 'ownerUsername' | 'available'>,
  currentUsername: string,
): RowAction {
  return row.ownerUsername === currentUsername && row.available ? 'deploy' : 'request';
}
