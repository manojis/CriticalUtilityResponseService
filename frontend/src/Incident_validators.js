const resource_validators = {
  description: {
    rules: [
      {
        test: (value) => {
          return value.length > 5;
        },
        message: 'Description must be longer than five characters',
      },
    ],
    errors: [],
    valid: false,
    state: '',
  },
  date: {
    rules: [
      {
        test: /^(19|20)\d{2}-(0{0,1}[1-9]|1[0-2])-(0{0,1}[1-9]|1\d|2\d|3[01])$/, // between 1900 to 2099
        // test: /^\d{2}\/\d{2}\/\d{4}$/ ; // Simple mm/dd/yyyy format
        // test: /^(0{0,1}[1-9]|1[0-2])\/(0[1-9]|1\d|2\d|3[01])\/\d{4}$/,
        message: 'Enter a valid date between 1900 and 2099 in YYYY-MM-DD format.',
      },
      {
        test: (value) => {
          return (value !== null);
        },
        message: 'Date field must not be empty',
      },
    ],
    errors: [],
    valid: false,
    state: '',
  },
  declaration_abbreviation: {
    rules: [
      {
        test: (value) => {
          return (value !== null);
        },
        message: 'Declaration name must be selected.',
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
  }
};

export default resource_validators;
