import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(path, "utf8");

test("the accessible report table is progressively enhanced by pinned local Chart.js", () => {
  const manifest = JSON.parse(read("package.json"));
  const layout = read("src/main/resources/templates/fragments/layout.html");
  const app = read("src/main/resources/static/assets/app.js");

  assert.equal(manifest.devDependencies["chart.js"], "4.5.1");
  assert.match(manifest.scripts.build, /build:chart/);
  assert.match(layout, /assets\/chart\.umd\.min\.js/);
  assert.ok(layout.indexOf("chart.umd.min.js") < layout.indexOf("app.js"));
  assert.match(app, /data-report-chart/);
  assert.match(app, /animation:\s*false/);
  assert.match(app, /--accent/);
  assert.match(app, /--border/);
  assert.match(app, /--muted/);
  assert.match(app, /chart\.update\('none'\)/);
});
