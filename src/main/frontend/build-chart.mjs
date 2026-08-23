import { copyFile, mkdir } from "node:fs/promises";

await mkdir("src/main/resources/static/assets", { recursive: true });
await copyFile(
  "node_modules/chart.js/dist/chart.umd.min.js",
  "src/main/resources/static/assets/chart.umd.min.js",
);
