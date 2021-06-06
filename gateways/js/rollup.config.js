import { nodeResolve } from "@rollup/plugin-node-resolve";
import { terser } from "rollup-plugin-terser";
import babel from "@rollup/plugin-babel";
import pkg from "./package.json"
import { spawn } from 'child_process'

const ls = spawn('git', ['describe', '--always', '--abbrev=8', '--match', 'NOT A TAG', '--dirty=*']);
var commit;
ls.stdout.on('data', (data) => { commit = data });

const input = ["src/fjage.js"];
export default [
  {
    // UMD
    input,
    plugins: [
      nodeResolve(),
      babel({
        babelHelpers: "bundled",
      }),
      terser(),
    ],
    output: {
      file: `dist/${pkg.name}.min.js`,
      format: "umd",
      name: "fjage",
      esModule: false,
      exports: "named",
      sourcemap: true,
    },
  },
// ESM and CJS
  {
    input,
    plugins: [nodeResolve()],
    output: [
      {
        format: "esm",
        exports: "named",
        dir : `dist/mjs`,
        chunkFileNames: "[name].js",
        banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} ${new Date().toISOString()} */\n`
      },
      {
        format: "cjs",
        exports: "named",
        dir : `dist/cjs`,
        chunkFileNames: "[name].cjs",
        entryFileNames: "[name].cjs",
        banner : `/* fjage.js v${pkg.version}${commit?'/'+commit:''} ${new Date().toISOString()} */\n`
      },
    ],
  }
];