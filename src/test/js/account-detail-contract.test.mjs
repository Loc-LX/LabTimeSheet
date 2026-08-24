import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("account detail uses responsive operational detail components", () => {
  const template = readFileSync("src/main/resources/templates/accounts/index.html", "utf8");
  const css = readFileSync("src/main/frontend/app.css", "utf8");

  assert.match(template, /class="account-detail-header"/);
  assert.match(template, /account-detail-access/);
  assert.match(template, /account-detail-lifecycle/);
  assert.match(template, /Terminal-action readiness/);
  assert.match(css, /\.account-detail-header/);
  assert.match(css, /\.account-fact-grid/);
  assert.match(css, /\.account-readiness/);
  assert.match(css, /\.account-fact-grid\s*\{[^}]*grid-template-columns:\s*1fr/s);
});
