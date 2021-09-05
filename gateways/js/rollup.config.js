import { nodeResolve } from '@rollup/plugin-node-resolve';
import { terser } from 'rollup-plugin-terser';
import pkg from './package.json';
import { spawn } from 'child_process';

const ls = spawn('git', ['describe', '--always', '--abbrev=8', '--match', 'NOT A TAG', '--dirty=*']);
var commit;
ls.stdout.on('data', (data) => { commit = data; });

const input = ['src/fjage.js'];
export default [
  {
    // UMD
    input,
    plugins: [
      nodeResolve(),
    ],
    output: [{
      file: `dist/${pkg.name}.js`,
      format: 'umd',
      name: 'fjage',
      esModule: false,
      exports: 'named',
      sourcemap: true,
      banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} */\n`
    },{
      file: `dist/${pkg.name}.min.js`,
      format: 'umd',
      name: 'fjage',
      esModule: false,
      exports: 'named',
      sourcemap: true,
      banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} */\n`,
      plugins: [terser()]
    }],
  },
  // ESM and CJS
  {
    input,
    plugins: [nodeResolve()],
    output: [
      {
        format: 'esm',
        exports: 'named',
        dir : 'dist/esm',
        chunkFileNames: '[name].js',
        banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} */\n`
      },
      {
        format: 'cjs',
        exports: 'named',
        dir : 'dist/cjs',
        chunkFileNames: '[name].cjs',
        entryFileNames: '[name].cjs',
        banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} */\n`
      },
    ],
  }
];