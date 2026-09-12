import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
const packageLock = JSON.parse(fs.readFileSync('package-lock.json', 'utf8'));

// The lowest Playwright minor this harness is written against. The download
// assertions below use waitForEvent('download') and suggestedFilename(), both
// present since well before it. Raise this only with a reason.
const LOWEST_SUPPORTED_MINOR = 55;

/**
 * Protects ARC-004, which constrains test tooling to a compatible range instead
 * of an exact pin, and requires every verification run to record the version it
 * actually resolved.
 *
 * Observable break: an equality assertion against a tool version turns any
 * upgrade into a red build, in a place no decision record can reach. That is
 * not hypothetical here. The declared range moved to ^1.62.1 in 73df96b while
 * this line still demanded 1.55.0, and because npm run test:ui runs in both
 * .gitea/workflows/verify.yml and container.yml, that one line blocked
 * verification and image publication on main.
 *
 * Expected values are derived from ARC-004, not from package.json: a caret
 * range on the major this harness targets, never an exact version, and a
 * lockfile that resolves inside that range.
 */
test('Playwright harness exposes the required deterministic commands', () => {
  const declared = packageJson.devDependencies['@playwright/test'];
  const declaredRange = /^\^(\d+)\.(\d+)\.(\d+)$/.exec(declared);
  assert.ok(
    declaredRange,
    `@playwright/test must be declared as a caret range so a compatible upgrade `
      + `needs no test edit; found ${declared}`);
  const declaredMajor = Number(declaredRange[1]);
  const declaredMinor = Number(declaredRange[2]);
  assert.equal(declaredMajor, 1, 'this harness is written against Playwright 1.x');
  assert.ok(
    declaredMinor >= LOWEST_SUPPORTED_MINOR,
    `the declared range must not fall below 1.${LOWEST_SUPPORTED_MINOR}; found ${declared}`);

  const resolved = packageLock.packages?.['node_modules/@playwright/test']?.version;
  assert.ok(resolved, 'package-lock.json must resolve @playwright/test for npm ci');
  const resolvedVersion = /^(\d+)\.(\d+)\.(\d+)$/.exec(resolved);
  assert.ok(resolvedVersion, `unreadable resolved version ${resolved}`);
  assert.equal(
    Number(resolvedVersion[1]), declaredMajor,
    `lockfile resolves ${resolved}, outside the declared range ${declared}`);
  assert.ok(
    Number(resolvedVersion[2]) >= declaredMinor,
    `lockfile resolves ${resolved}, below the declared range ${declared}`);
  console.log(`    @playwright/test declared ${declared}, resolved ${resolved}`);
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
