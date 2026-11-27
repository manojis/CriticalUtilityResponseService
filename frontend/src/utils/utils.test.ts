import { compareIsoDates, isIsoDate, toIsoDate, todayIso } from './date';
import { availabilityLabel, formatCost, formatDistance, profileSubtitle } from './format';

describe('date utils', () => {
  it('formats local dates as YYYY-MM-DD', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(todayIso(new Date(2026, 10, 25, 23, 59))).toBe('2026-11-25');
  });
  it('validates real calendar dates', () => {
    expect(isIsoDate('2024-02-29')).toBe(true);
    expect(isIsoDate('2026-02-29')).toBe(false);
    expect(isIsoDate('2026-13-01')).toBe(false);
    expect(isIsoDate('2026-1-01')).toBe(false);
  });
  it('compares ISO dates', () => {
    expect(compareIsoDates('2026-01-01', '2026-01-02')).toBe(-1);
    expect(compareIsoDates('2026-01-02', '2026-01-01')).toBe(1);
    expect(compareIsoDates('2026-01-01', '2026-01-01')).toBe(0);
  });
});

describe('format utils', () => {
  it('picks the profile subtitle by user type (F-2)', () => {
    const base = { username: 'u', name: 'N' };
    expect(profileSubtitle({ ...base, userType: 'INDIVIDUAL', jobTitle: 'Engineer' })).toBe(
      'Engineer',
    );
    expect(profileSubtitle({ ...base, userType: 'COMPANY', headquarters: 'Atlanta' })).toBe(
      'Atlanta',
    );
    expect(
      profileSubtitle({ ...base, userType: 'MUNICIPALITY', municipalityCategory: 'City' }),
    ).toBe('City');
    expect(
      profileSubtitle({ ...base, userType: 'GOVERNMENT_AGENCY', agencyNameLocalOffice: 'FEMA R4' }),
    ).toBe('FEMA R4');
    expect(profileSubtitle({ ...base, userType: 'COMPANY' })).toBeNull();
  });
  it('formats cost, distance and availability', () => {
    expect(formatCost(150, 'Hour')).toBe('$150.00 / Hour');
    expect(formatDistance(3.24)).toBe('3.2 km');
    expect(formatDistance(0)).toBe('0.0 km');
    expect(formatDistance(null)).toBe('—');
    expect(availabilityLabel({ available: true })).toBe('Available');
    expect(availabilityLabel({ available: false, nextAvailableDate: '2026-12-01' })).toBe(
      'In use until 2026-12-01',
    );
    expect(availabilityLabel({ available: false })).toBe('In use');
  });
});
