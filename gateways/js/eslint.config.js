import globals from 'globals';
import js from '@eslint/js';
import jsdoc from 'eslint-plugin-jsdoc';

export default [
  {languageOptions: { globals: globals.browser }},
  js.configs.recommended,
  {
    rules: {
      'no-console': 0,
      'semi': 2,
      'quotes': [
        'error',
        'single'
      ],
      'no-unused-vars': [
        'error', {
          'caughtErrors': 'none',
        }
      ],
      'jsdoc/no-undefined-types': 'warn'
    },
    plugins : {
      jsdoc,
    },
  }
];