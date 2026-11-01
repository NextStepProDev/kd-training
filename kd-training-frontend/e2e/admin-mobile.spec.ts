import { test, expect, type Page } from '@playwright/test'
import { ADMIN, USERS, signInAs } from './support/session'

/**
 * The coach's panel on a phone. Kacper manages his clients from the gym floor, so the panel has to
 * work at phone width — and the users table did not: seven columns made it a thousand pixels wide,
 * and despite its own scroll box it pushed the whole page sideways.
 *
 * The API is stubbed (support/session.ts): a signed-in admin and a page of users, nothing from a
 * real backend.
 */

async function signInAsAdmin(page: Page) {
  await signInAs(page, ADMIN, {
    '/api/admin/users': { content: USERS, page: 0, size: 50, totalElements: USERS.length, totalPages: 1 },
    '/api/admin/athletes': [],
  })
}

test.describe('Panel trenera na telefonie', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signInAsAdmin(page)
  })

  test('lista użytkowników mieści się w szerokości ekranu', async ({ page }) => {
    await page.goto('/admin/uzytkownicy')
    await expect(page.getByRole('button', { name: 'Katarzyna Lewandowska-Wiśniewska 0', exact: true })).toBeVisible()

    const { viewport, page: width } = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      page: document.documentElement.scrollWidth,
    }))
    expect(width).toBeLessThanOrEqual(viewport)
  })

  test('podopieczni mieszczą się w szerokości ekranu', async ({ page }) => {
    await page.goto('/admin/podopieczni')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    const overflow = await page.evaluate(() =>
      document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
