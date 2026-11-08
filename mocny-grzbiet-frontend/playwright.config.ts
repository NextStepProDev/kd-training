import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  // No retries, deliberately. The suite stubs the API, so a failure means the frontend changed, not
  // that the network wobbled — and a retry would turn exactly the kind of real breakage this suite
  // exists to catch into an intermittent one people learn to re-run.
  retries: 0,
  // A stray `.only` shrinks the suite to one test and still reports green. Locally that is a working
  // habit; in CI it is a silent hole.
  forbidOnly: !!process.env.CI,
  // In CI: `github` annotates the failing line straight onto the pull request, and `html` writes the
  // report the workflow uploads on failure — without it that artifact would be an empty directory.
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5175',
    headless: true,
    screenshot: 'only-on-failure',
  },
  projects: [
    // Runs first and gates the rest: reuse below only checks that SOMETHING answers on the port.
    // See e2e/app-identity.setup.ts.
    {
      name: 'identity',
      testMatch: /app-identity\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
      dependencies: ['identity'],
    },
  ],
  webServer: {
    command: 'npm run dev',
    port: 5175,
    // Locally, reuse the dev server that is probably already up. In CI there is never one to reuse,
    // and reusing something unexpected would be worse than starting clean.
    reuseExistingServer: !process.env.CI,
    // A cold runner installs nothing at this point but still has to boot Vite; 30s was tight enough
    // to fail on a busy machine for reasons that have nothing to do with the app.
    timeout: 120_000,
  },
})
