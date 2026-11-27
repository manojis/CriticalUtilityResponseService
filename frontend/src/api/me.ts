import type { UserProfile } from '../types/api';
import { apiClient } from './client';

/** #2 `GET /me` */
export async function getMe(signal?: AbortSignal): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/me', { signal });
  return data;
}
