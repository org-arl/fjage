#!/usr/bin/env node

import { readFile } from 'fs/promises';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import semver from 'semver';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

(async () => {
  const fjsver = JSON.parse(await readFile(__dirname+'/../package.json','utf8')).version;
  const fver = (await readFile(__dirname+'/../../../VERSION', 'utf8')).trim();
  let d = semver.diff(fjsver, fver);
  if (d == 'patch' || d == null || d == 'prerelease') process.exit(0);
  console.log('Mismatch version of fjage.js and fjage : ', fjsver, '!~=', fver );
  process.exit(1);
})();
