import type { ResourceSearchResult, UserProfile, UserType } from '../types/api';

export const USER_TYPE_LABELS: Record<UserType, string> = {
  INDIVIDUAL: 'Individual',
  MUNICIPALITY: 'Municipality',
  GOVERNMENT_AGENCY: 'Government agency',
  COMPANY: 'Company',
};

/** Main-menu subtitle per user type (FEATURES.md "Actors and user types"). */
export function profileSubtitle(profile: UserProfile): string | null {
  switch (profile.userType) {
    case 'INDIVIDUAL':
      return profile.jobTitle ?? null;
    case 'MUNICIPALITY':
      return profile.municipalityCategory ?? null;
    case 'GOVERNMENT_AGENCY':
      return profile.agencyNameLocalOffice ?? null;
    case 'COMPANY':
      return profile.headquarters ?? null;
  }
}

const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** `150` + `Hour` → `$150.00 / Hour` */
export function formatCost(cost: number, unit: string): string {
  return `${currency.format(cost)} / ${unit}`;
}

/** Distance in km with one decimal, or an em dash when unknown. */
export function formatDistance(distanceKm: number | null | undefined): string {
  return distanceKm === null || distanceKm === undefined ? '—' : `${distanceKm.toFixed(1)} km`;
}

/** "Available" or "In use until YYYY-MM-DD" (FEATURES.md F-5 AC6). */
export function availabilityLabel(
  result: Pick<ResourceSearchResult, 'available' | 'nextAvailableDate'>,
): string {
  if (result.available) {
    return 'Available';
  }
  return result.nextAvailableDate ? `In use until ${result.nextAvailableDate}` : 'In use';
}
