import { toCreateIncidentRequest, validateIncident, type IncidentFormValues } from './incident';

const TODAY = '2026-11-25';

const valid: IncidentFormValues = {
  declarationAbbreviation: 'MD',
  incidentDate: '2026-11-20',
  description: 'Flooding on I-75',
  latitude: '33.749',
  longitude: '-84.388',
};

describe('validateIncident', () => {
  it('accepts a valid incident', () => {
    expect(validateIncident(valid, TODAY)).toEqual({});
  });

  it('reports every invalid field', () => {
    const errors = validateIncident(
      {
        declarationAbbreviation: '',
        incidentDate: '2026-11-26',
        description: 'short',
        latitude: '95',
        longitude: '',
      },
      TODAY,
    );
    expect(errors).toEqual({
      declarationAbbreviation: 'Declaration is required',
      incidentDate: 'Incident date cannot be in the future',
      description: 'Description must be between 6 and 500 characters',
      latitude: 'Latitude must be a number between -90 and 90',
      longitude: 'Longitude is required',
    });
  });

  it('enforces description bounds 6..500 after trimming', () => {
    expect(
      validateIncident({ ...valid, description: '  12345 ' }, TODAY).description,
    ).toBeDefined();
    expect(
      validateIncident({ ...valid, description: '123456' }, TODAY).description,
    ).toBeUndefined();
    expect(
      validateIncident({ ...valid, description: 'x'.repeat(500) }, TODAY).description,
    ).toBeUndefined();
    expect(
      validateIncident({ ...valid, description: 'x'.repeat(501) }, TODAY).description,
    ).toBeDefined();
  });

  it('allows an incident dated today', () => {
    expect(validateIncident({ ...valid, incidentDate: TODAY }, TODAY)).toEqual({});
  });
});

describe('toCreateIncidentRequest', () => {
  it('converts strings to the API payload without swapping lat/lon (F7)', () => {
    expect(toCreateIncidentRequest({ ...valid, description: '  Flooding on I-75 ' })).toEqual({
      declarationAbbreviation: 'MD',
      incidentDate: '2026-11-20',
      description: 'Flooding on I-75',
      latitude: 33.749,
      longitude: -84.388,
    });
  });
});
