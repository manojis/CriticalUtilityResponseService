import type { LoginRequest, LoginResponse } from '../types/api';
import { apiClient } from './client';

/** #1 `POST /auth/login` */
export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', credentials);
  return data;
}
