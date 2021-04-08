const path = require('path');

module.exports = {
  entry: './src/main/resources/org/arl/fjage/web/fjage.js',
  output: {
    filename: 'fjage.js',
    path: path.resolve(__dirname, 'dist'),
  },
};
