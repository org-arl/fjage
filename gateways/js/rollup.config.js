import { nodeResolve } from "@rollup/plugin-node-resolve"
import pkg from "./package.json"
import { spawn } from 'child_process'

const ls = spawn('git', ['describe', '--always', '--abbrev=8', '--match', 'NOT A TAG', '--dirty=*']);
var commit;
ls.stdout.on('data', (data) => { commit = data });

export default [
// ESM and CJS
  {
    input : ["src/fjage.js"],
    plugins: [nodeResolve()],
    output: [
      {
        format: "esm",
        exports: "named",
        dir : `dist`,
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