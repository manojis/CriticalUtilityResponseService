import {
  dedupeCapabilities,
  toCreateResourceRequest,
  validateAdditionalEsfs,
  validateCost,
  validateMaxDistance,
  validateNewCapability,
  validateResource,
  type ResourceFormValues,
} from './resource';

const valid: ResourceFormValues = {
  resourceName: 'Hydraulic Crane',
  model: 'XL-200',
  primaryEsf: '3',
  additionalEsfs: [1, 7],
  capabilities: ['Lift 20t', 'Night ops'],
  cost: '150.00',
  costUnit: 'Hour',
  latitude: '33.77',
  longitude: '-84.39',
  maxDistanceKm: '120',
};

describe('validateResource', () => {
  it('accepts a valid resource', () => {
    expect(validateResource(valid)).toEqual({});
  });

  it('accepts optional fields left blank', () => {
    expect(
      validateResource({
        ...valid,
        model: '',
        additionalEsfs: [],
        capabilities: [],
        maxDistanceKm: '',
      }),
    ).toEqual({});
  });

  it('reports required fields', () => {
    const errors = validateResource({
      ...valid,
      resourceName: 'ab',
      primaryEsf: '',
      cost: '',
      costUnit: '',
      latitude: '',
      longitude: '200',
    });
    expect(errors).toMatchObject({
      resourceName: 'Resource name must be between 3 and 100 characters',
      primaryEsf: 'Primary ESF is required',
      cost: 'Cost is required',
      costUnit: 'Cost unit is required',
      latitude: 'Latitude is required',
      longitude: 'Longitude must be a number between -180 and 180',
    });
  });

  it('limits model length and capability length', () => {
    expect(validateResource({ ...valid, model: 'm'.repeat(101) }).model).toBe(
      'Model must be at most 100 characters',
    );
    expect(validateResource({ ...valid, capabilities: ['ok', 'c'.repeat(101)] }).capabilities).toBe(
      'Each capability must be between 1 and 100 characters',
    );
  });
});

describe('field rules', () => {
  it.each([
    ['0', undefined],
    ['150', undefined],
    ['150.25', undefined],
    ['-1', 'Cost must be a number greater than or equal to 0'],
    ['abc', 'Cost must be a number greater than or equal to 0'],
    ['1.234', 'Cost can have at most 2 decimal places'],
  ])('cost %s', (value, expected) => {
    expect(validateCost(value)).toBe(expected);
  });

  it.each([
    ['', undefined],
    ['0.5', undefined],
    ['0', 'Max distance must be a number greater than 0'],
    ['-3', 'Max distance must be a number greater than 0'],
  ])('max distance %j', (value, expected) => {
    expect(validateMaxDistance(value)).toBe(expected);
  });

  it('rejects additional ESFs that contain the primary or duplicates', () => {
    expect(validateAdditionalEsfs([3, 1], '3')).toBe(
      'Additional ESFs cannot include the primary ESF',
    );
    expect(validateAdditionalEsfs([1, 1], '3')).toBe('Additional ESFs must be unique');
    expect(validateAdditionalEsfs([1, 2], '3')).toBeUndefined();
    expect(validateAdditionalEsfs([1], '')).toBeUndefined();
  });

  it('validates new capabilities (trim, length, case-insensitive duplicates)', () => {
    expect(validateNewCapability('   ', [])).toBe('Enter a capability');
    expect(validateNewCapability('x'.repeat(101), [])).toBe(
      'Capability must be at most 100 characters',
    );
    expect(validateNewCapability(' night OPS ', ['Night ops'])).toBe('Capability already added');
    expect(validateNewCapability('Lift 20t', ['Night ops'])).toBeUndefined();
  });

  it('dedupes capabilities case-insensitively, keeping the first spelling', () => {
    expect(dedupeCapabilities([' Lift ', 'lift', 'Night', '', 'NIGHT'])).toEqual(['Lift', 'Night']);
  });
});

describe('toCreateResourceRequest', () => {
  it('builds the API payload', () => {
    expect(toCreateResourceRequest(valid)).toEqual({
      resourceName: 'Hydraulic Crane',
      model: 'XL-200',
      primaryEsf: 3,
      additionalEsfs: [1, 7],
      capabilities: ['Lift 20t', 'Night ops'],
      cost: 150,
      costUnit: 'Hour',
      latitude: 33.77,
      longitude: -84.39,
      maxDistanceKm: 120,
    });
  });

  it('omits a blank model, sends null max distance and strips the primary from additional ESFs', () => {
    const payload = toCreateResourceRequest({
      ...valid,
      model: '  ',
      maxDistanceKm: '',
      additionalEsfs: [3, 1],
    });
    expect(payload).not.toHaveProperty('model');
    expect(payload.maxDistanceKm).toBeNull();
    expect(payload.additionalEsfs).toEqual([1]);
  });
});
