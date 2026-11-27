import {
  compact,
  decimalPlaces,
  isBlank,
  parseDecimal,
  validateDateNotAfter,
  validateDateNotBefore,
  validateLatitude,
  validateLength,
  validateLongitude,
} from './common';

describe('parseDecimal', () => {
  it.each([
    ['12', 12],
    [' -84.388 ', -84.388],
    ['+3.5', 3.5],
    ['.5', 0.5],
    ['10.', 10],
  ])('parses %s', (input, expected) => {
    expect(parseDecimal(input)).toBe(expected);
  });

  it.each(['', '  ', 'abc', '1e3', '0x10', '1.2.3', '12abc', 'Infinity'])('rejects %j', (input) => {
    expect(parseDecimal(input)).toBeNull();
  });
});

describe('helpers', () => {
  it('counts decimal places', () => {
    expect(decimalPlaces('12')).toBe(0);
    expect(decimalPlaces('12.5')).toBe(1);
    expect(decimalPlaces('12.505')).toBe(3);
  });

  it('detects blank values', () => {
    expect(isBlank('')).toBe(true);
    expect(isBlank('   ')).toBe(true);
    expect(isBlank(undefined)).toBe(true);
    expect(isBlank(null)).toBe(true);
    expect(isBlank('x')).toBe(false);
  });

  it('validates trimmed length', () => {
    expect(validateLength('  ab  ', 'Name', 3, 5)).toBe('Name must be between 3 and 5 characters');
    expect(validateLength('abc', 'Name', 3, 5)).toBeUndefined();
    expect(validateLength('abcdef', 'Name', 3, 5)).toBeDefined();
  });

  it('compacts undefined entries', () => {
    expect(compact<{ a: string; b: string }>({ a: undefined, b: 'bad' })).toEqual({ b: 'bad' });
  });
});

describe('coordinates (legacy rules, inclusive bounds)', () => {
  it.each(['-90', '90', '0', '33.749'])('accepts latitude %s', (value) => {
    expect(validateLatitude(value)).toBeUndefined();
  });
  it.each(['-90.0001', '91', 'north'])('rejects latitude %s', (value) => {
    expect(validateLatitude(value)).toBe('Latitude must be a number between -90 and 90');
  });
  it('requires latitude', () => {
    expect(validateLatitude('')).toBe('Latitude is required');
  });
  it.each(['-180', '180', '-84.388'])('accepts longitude %s', (value) => {
    expect(validateLongitude(value)).toBeUndefined();
  });
  it.each(['180.5', '-181', 'x'])('rejects longitude %s', (value) => {
    expect(validateLongitude(value)).toBe('Longitude must be a number between -180 and 180');
  });
});

describe('date rules', () => {
  const today = '2026-11-25';
  it('not-after: allows today and past, rejects future and invalid', () => {
    expect(validateDateNotAfter('2026-11-25', 'Date', today)).toBeUndefined();
    expect(validateDateNotAfter('1999-01-01', 'Date', today)).toBeUndefined();
    expect(validateDateNotAfter('2026-11-26', 'Date', today)).toBe('Date cannot be in the future');
    expect(validateDateNotAfter('2026-02-30', 'Date', today)).toBe(
      'Date must be a valid date (YYYY-MM-DD)',
    );
    expect(validateDateNotAfter('', 'Date', today)).toBe('Date is required');
  });
  it('not-before: allows today and future, rejects past', () => {
    expect(validateDateNotBefore('2026-11-25', 'Date', today)).toBeUndefined();
    expect(validateDateNotBefore('2027-01-01', 'Date', today)).toBeUndefined();
    expect(validateDateNotBefore('2026-11-24', 'Date', today)).toBe('Date cannot be in the past');
    expect(validateDateNotBefore('11/30/2026', 'Date', today)).toBe(
      'Date must be a valid date (YYYY-MM-DD)',
    );
    expect(validateDateNotBefore(' ', 'Date', today)).toBe('Date is required');
  });
});
