import { makeJwt, loginResponse, ALICE } from '../test/fixtures';
import {
  clearSession,
  currentToken,
  decodeJwtExpiry,
  isSessionExpired,
  readSession,
  SESSION_STORAGE_KEY,
  sessionFromLogin,
  writeSession,
} from './session';

describe('decodeJwtExpiry', () => {
  it('returns exp in milliseconds', () => {
    expect(decodeJwtExpiry(makeJwt(1_800_000_000))).toBe(1_800_000_000_000);
  });
  it.each(['', 'abc', 'a.b', 'a.!!!.c', `${btoa('{}')}.${btoa('{"exp":"soon"}')}.s`])(
    'returns null for malformed token %j',
    (token) => {
      expect(decodeJwtExpiry(token)).toBeNull();
    },
  );
  it('returns null when exp is missing', () => {
    expect(decodeJwtExpiry(`${btoa('{}')}.${btoa('{"sub":"alice"}')}.sig`)).toBeNull();
  });
});

describe('session storage', () => {
  const now = 1_800_000_000_000;

  it('builds a session from the login response, preferring the token exp', () => {
    const response = { ...loginResponse(), accessToken: makeJwt(now / 1000 + 600) };
    expect(sessionFromLogin(response, now).expiresAt).toBe(now + 600_000);
  });

  it('falls back to expiresIn when the token has no exp', () => {
    const response = { ...loginResponse(ALICE, 3600), accessToken: 'opaque-token' };
    expect(sessionFromLogin(response, now).expiresAt).toBe(now + 3_600_000);
  });

  it('persists and reads back a valid session under "erms.auth"', () => {
    const session = { token: 't', user: ALICE, expiresAt: now + 60_000 };
    writeSession(session);
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '')).toEqual(session);
    expect(readSession(now)).toEqual(session);
    expect(currentToken(now)).toBe('t');
  });

  it('drops an expired session on read (exp checked on load)', () => {
    writeSession({ token: 't', user: ALICE, expiresAt: now + 1_000 });
    expect(readSession(now)).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('drops corrupted or malformed entries', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{not json');
    expect(readSession(now)).toBeNull();
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        token: 't',
        user: { ...ALICE, userType: 'ADMIN' },
        expiresAt: now + 60_000,
      }),
    );
    expect(readSession(now)).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('clears the session', () => {
    writeSession({ token: 't', user: ALICE, expiresAt: now + 60_000 });
    clearSession();
    expect(readSession(now)).toBeNull();
    expect(currentToken(now)).toBeNull();
  });

  it('treats tokens as expired slightly before exp', () => {
    expect(isSessionExpired({ token: 't', user: ALICE, expiresAt: now + 4_000 }, now)).toBe(true);
    expect(isSessionExpired({ token: 't', user: ALICE, expiresAt: now + 60_000 }, now)).toBe(false);
  });
});
