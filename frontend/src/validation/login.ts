import { compact, isBlank, type ValidationErrors } from './common';

export interface LoginFormValues {
  username: string;
  password: string;
}

/** Mirrors the API constraints: both required, username ≤ 50, password ≤ 100 characters. */
export function validateLogin(values: LoginFormValues): ValidationErrors<LoginFormValues> {
  return compact<LoginFormValues>({
    username: isBlank(values.username)
      ? 'Username is required'
      : values.username.trim().length > 50
        ? 'Username must be at most 50 characters'
        : undefined,
    password: isBlank(values.password)
      ? 'Password is required'
      : values.password.length > 100
        ? 'Password must be at most 100 characters'
        : undefined,
  });
}
