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

  test('regulamin jest dostępny ze stopki', async ({ page }) => {
    await page.goto('/')
    await page.locator('footer').getByRole('link', { name: 'Regulamin' }).click()
    await expect(page).toHaveURL(/\/regulamin$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Regulamin' })).toBeVisible()
  })

  // The phone menu used to be position: fixed INSIDE the bar, and the bar's backdrop-filter makes it
  // the containing block for fixed children: the menu collapsed to 0px high, a tap that showed nothing.
  // Only a real layout engine sees that, so it is measured here and not in a unit test.
  test('menu na telefonie otwiera się na cały ekran i prowadzi do logowania', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    for (const path of ['/', '/regulamin']) {
      await page.goto(path)
      await page.getByRole('button', { name: 'Otwórz menu' }).click()
      // No longer inside the bar's <nav> (see above), so it has to be a navigation landmark of its own.
      const menu = page.getByRole('navigation', { name: 'Menu' })
      await expect(menu).toBeVisible()
      const box = await menu.boundingBox()
      expect(box?.height ?? 0).toBeGreaterThan(600)
      await page.getByRole('button', { name: 'Zamknij menu' }).click()
    }
    await page.getByRole('button', { name: 'Otwórz menu' }).click()
    await page.locator('#mobile-menu').getByRole('link', { name: 'Zaloguj się' }).click()
    await expect(page).toHaveURL(/\/logowanie$/)
  })

  // Tailwind 4 moves the bar with the `translate` property, not `transform`: a transition listing
  // only `transform` made the bar jump out of view instead of sliding.
  test('pasek chowa się płynnie przy przewijaniu', async ({ page }) => {
    await page.goto('/regulamin')
    const transition = await page.locator('nav').first().evaluate(el => getComputedStyle(el).transitionProperty)
    expect(transition.split(',').map(p => p.trim())).toContain('translate')
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
