import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:8080';
const browserChannel = process.env.PLAYWRIGHT_CHANNEL;

export default defineConfig({
  testDir: './src/test/e2e',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results/playwright',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    ...(browserChannel ? { channel: browserChannel } : {}),
    ...devices['Desktop Chrome'],
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
