import { validateExpectedReturnDate, validateSearchDistance } from './request';
import { validateLogin } from './login';

describe('validateExpectedReturnDate', () => {
  it('requires a date on or after today', () => {
    expect(validateExpectedReturnDate('2026-11-25', '2026-11-25')).toBeUndefined();
    expect(validateExpectedReturnDate('2026-11-24', '2026-11-25')).toBe(
      'Expected return date cannot be in the past',
    );
    expect(validateExpectedReturnDate('', '2026-11-25')).toBe('Expected return date is required');
  });
});

describe('validateSearchDistance', () => {
  const base = { keyword: '', esf: '', incidentId: 'MD-2', distanceKm: '' };
  it('allows no distance', () => {
    expect(validateSearchDistance(base)).toBeUndefined();
  });
  it('requires an incident (F-5 AC5)', () => {
    expect(validateSearchDistance({ ...base, incidentId: '', distanceKm: '10' })).toBe(
      'Select an incident to search by distance',
    );
  });
  it('requires a positive number', () => {
    expect(validateSearchDistance({ ...base, distanceKm: '0' })).toBe(
      'Distance must be a number greater than 0',
    );
    expect(validateSearchDistance({ ...base, distanceKm: '12.5' })).toBeUndefined();
  });
});

describe('validateLogin', () => {
  it('requires both fields and enforces API max lengths', () => {
    expect(validateLogin({ username: '', password: '' })).toEqual({
      username: 'Username is required',
      password: 'Password is required',
    });
    expect(validateLogin({ username: 'u'.repeat(51), password: 'p'.repeat(101) })).toEqual({
      username: 'Username must be at most 50 characters',
      password: 'Password must be at most 100 characters',
    });
    expect(validateLogin({ username: 'alice', password: 'password' })).toEqual({});
  });
});
