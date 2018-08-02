const resource_validators = {
  resource_name: {
    rules: [
      {
        test: (value) => {
          return value.length > 2;
        },
        message: 'Resource name must be longer than two characters',
      },
    ],
    errors: [],
    valid: false,
    state: '',
  },
  primary_esf: {
    rules: [
      {
        test: (value) => {
          return (value !== null);
        },
        message: 'Primary ESF must be selected.',
      },
    ],
    errors: [],
    valid: false,
    state: ''
  },
  longitude: {
    rules: [
      {
        test: (value) => {
          return (value >= -180 && value <= 180) && (value !== '');
        },
        message: 'Longitude must be numeric value between -180 to 180',
      },
    ],
    errors: [],
    valid: false,
    state: ''
  },
  latitude: {
    rules: [
      {
        test: (value) => {
          return (value >= -90 && value <= 90) && (value !== '');
        },
        message: 'Latitude must be numeric value between -90 to 90',
      },
    ],
    errors: [],
    valid: false,
    state: ''
  },
  cost: {
    rules: [
      {
        test: (value) => {
          return (value >= 0) && (value !== '');
        },
        message: 'Cost must be numeric value > = 0',
      },
    ],
    errors: [],
    valid: false,
    state: ''
  },
  unit: {
    rules: [
      {
        test: (value) => {
          return (value !== null);
        },
        message: 'Unit must be selected.',
      },
    ],
    errors: [],
    valid: false,
    state: ''
  }
};

export default resource_validators;
