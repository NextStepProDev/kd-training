import { test, expect, type Page } from '@playwright/test'

/**
 * The unauthenticated golden path: what a stranger sees before they have an account.
 *
 * The API is STUBBED rather than reached. CI runs the frontend alone, so a suite that talked to a
 * real backend would either need one booted alongside (slow, and flaky in a way that teaches people
 * to ignore red builds) or would quietly degrade into asserting error states. Stubbing makes the
 * run deterministic and keeps every assertion about something the frontend actually owns.
 *
 * Every test names something visible on screen: "the URL is /x" passes on a blank page.
 */

/**
 * Answers every API call with 404 rather than letting it hang — a guest's pages read nothing.
 *
 * The pattern is a regex anchored on the path, NOT the glob `**\/api\/**`. In dev Vite serves the
 * app's own modules over HTTP, and this project has a `src/api/` directory — so the glob matched
 * `/src/api/client.ts` too and handed the browser JSON where it expected JavaScript. Every page
 * then rendered blank, which looks exactly like a broken app rather than a broken stub.
 */
async function stubApi(page: Page) {
  await page.route(/^https?:\/\/[^/]+\/api\//, route =>
    route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }))
}

test.beforeEach(async ({ page }) => { await stubApi(page) })

test.describe('Golden Path — gość', () => {
  test('strona główna mówi, czyja to strona, i prowadzi do logowania', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/KD Training/)
    await expect(page.getByRole('heading', { level: 1, name: 'Kacper Dąbrowski' })).toBeVisible()

    await page.getByRole('main').getByRole('link', { name: 'Zaloguj się' }).click()
    await expect(page).toHaveURL(/\/logowanie$/)
    await expect(page.getByRole('button', { name: /Zaloguj/ })).toBeVisible()
  })

  test('polityka prywatności jest dostępna ze stopki', async ({ page }) => {
    await page.goto('/')
    await page.locator('footer').getByRole('link', { name: 'Polityka prywatności' }).click()
    await expect(page).toHaveURL(/\/polityka-prywatnosci$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('nieznany adres pokazuje stronę 404, nie pustkę', async ({ page }) => {
    await page.goto('/tego-adresu-nie-ma')
    await expect(page.getByRole('heading', { level: 1, name: /Nie ma takiej strony/i })).toBeVisible()
    // Navbar and footer stay: someone who got lost needs the way out most.
    await expect(page.locator('nav')).toBeVisible()
    await expect(page.locator('footer')).toBeVisible()
    // An empty page answering 200 is a soft 404; the noindex is what stops it being indexed as real.
    await expect(page.locator('meta[name="robots"][content*="noindex"]')).toHaveCount(1)
  })
})
