import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const names = [
  'bell', 'calendar-days', 'check-circle-2', 'chevron-left', 'chevron-right',
  'circle-user-round', 'clock', 'folder-kanban', 'folder-open', 'inbox',
  'layout-dashboard', 'list-check', 'log-out', 'monitor', 'moon', 'panel-left',
  'settings', 'sun', 'triangle-alert', 'users', 'x'
];
const output = resolve('src/main/resources/static/assets/icons.svg');
const symbols = await Promise.all(names.map(async (name) => {
  const svg = await readFile(resolve(`node_modules/lucide-static/icons/${name}.svg`), 'utf8');
  const viewBox = svg.match(/viewBox="([^"]+)"/)?.[1] ?? '0 0 24 24';
  const body = svg.match(/<svg[\s\S]*?>([\s\S]*?)<\/svg>/)?.[1];
  if (!body) throw new Error(`Invalid Lucide SVG: ${name}`);
  return `<symbol id="${name}" viewBox="${viewBox}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body.trim()}</symbol>`;
}));

await mkdir(dirname(output), { recursive: true });
await writeFile(output, `<svg xmlns="http://www.w3.org/2000/svg" style="display:none">${symbols.join('')}</svg>\n`);
