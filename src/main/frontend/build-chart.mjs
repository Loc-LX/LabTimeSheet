import { copyFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const output = resolve('src/main/resources/static/assets/chart.umd.js');
await mkdir(dirname(output), { recursive: true });
await copyFile(resolve('node_modules/chart.js/dist/chart.umd.js'), output);