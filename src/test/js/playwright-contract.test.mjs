import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));

test('Playwright harness exposes the required deterministic commands', () => {
  assert.equal(packageJson.devDependencies['@playwright/test'], '1.55.0');
  assert.equal(packageJson.scripts['test:e2e'], 'playwright test');
  assert.equal(packageJson.scripts['test:e2e:headed'], 'playwright test --headed');
  assert.equal(packageJson.scripts['test:e2e:ui'], 'playwright test --ui');
  assert.equal(packageJson.scripts['test:e2e:smoke'], 'playwright test --grep @smoke');
  assert.ok(fs.existsSync('playwright.config.mjs'));
  assert.ok(fs.existsSync('src/test/e2e/smoke.spec.mjs'));
  const journeys = fs.readFileSync('src/test/e2e/report-journeys.spec.mjs', 'utf8');
  assert.match(journeys, /waitForEvent\('download'\)/);
  assert.match(journeys, /suggestedFilename\(\)/);
  assert.match(journeys, /application\/vnd\.openxmlformats-officedocument\.spreadsheetml\.sheet/);
  assert.match(journeys, /application\/pdf/);
  assert.match(journeys, /xl\/worksheets\/sheet1\.xml/);
  assert.match(journeys, /%PDF/);
  const config = fs.readFileSync('playwright.config.mjs', 'utf8');
  assert.match(config, /workers:\s*1/);
  assert.match(config, /trace:\s*'retain-on-failure'/);
  assert.match(config, /screenshot:\s*'only-on-failure'/);
  const testing = fs.readFileSync('TESTING.md', 'utf8');
  for (const command of [
    'npm ci',
    'npx playwright install chromium',
    'npx playwright install --with-deps chromium',
    'npm run test:e2e',
    'npm run test:e2e:headed',
    'npm run test:e2e:ui',
    'npm run test:e2e:smoke',
    'npx playwright show-report',
  ]) {
    assert.ok(testing.includes(command), `TESTING.md must document ${command}`);
  }
  assert.match(testing, /No browser extension\s+is required/);
});
