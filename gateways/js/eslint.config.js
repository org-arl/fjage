import js from '@eslint/js';
import globals from 'globals';

export default [
  js.configs.recommended,
  {
    'languageOptions': {
      'globals': {
        ...globals.browser
      }
    },
    'rules': {
      'no-console': 0,
      'semi': 2,
      'quotes': [
        'error',
        'single'
      ]
    }
  }
];