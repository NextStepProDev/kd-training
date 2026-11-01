import { test, expect, type Page } from '@playwright/test'

/**
 * The coach's panel on a phone. Kacper manages his clients from the gym floor, so the panel has to
 * work at phone width — and the users table did not: seven columns made it a thousand pixels wide,
 * and despite its own scroll box it pushed the whole page sideways.
 *
 * The API is stubbed as in golden-path.spec.ts: a signed-in admin and a page of users, nothing
 * from a real backend. The tokens only have to exist and look unexpired; nothing checks them here.
 */

const ADMIN = {
  id: 'a0000000-0000-0000-0000-000000000001',
  email: 'kacper@example.test',
  firstName: 'Kacper',
  lastName: 'Dąbrowski',
  phone: '500100000',
  role: 'ADMIN',
  isAdmin: true,
  superAdmin: true,
  privacyAccepted: true,
  termsAccepted: true,
  marketingConsent: false,
  isAthlete: false,
  trainingConsent: false,
  preferredLanguage: 'pl',
  hasPassword: true,
  avatarUrl: null,
  createdAt: '2026-11-01T10:00:00Z',
}

const USERS = Array.from({ length: 6 }, (_, i) => ({
  id: `u0000000-0000-0000-0000-00000000000${i}`,
  email: `katarzyna.lewandowska.${i}@example.test`,
  firstName: 'Katarzyna',
  lastName: `Lewandowska-Wiśniewska ${i}`,
  phone: '500100400',
  role: i === 0 ? 'ADMIN' : 'USER',
  isAdmin: i === 0,
  superAdmin: false,
  emailVerified: i !== 2,
  marketingConsent: i % 2 === 0,
  isAthlete: false,
  createdAt: '2026-11-01T10:00:00Z',
}))

async function signInAsAdmin(page: Page) {
  await page.route(/^https?:\/\/[^/]+\/api\//, route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/me') return route.fulfill({ json: ADMIN })
    if (path === '/api/admin/users') {
      return route.fulfill({ json: { content: USERS, page: 0, size: 50, totalElements: USERS.length, totalPages: 1 } })
    }
    if (path === '/api/admin/athletes') return route.fulfill({ json: [] })
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  await page.addInitScript(() => {
    localStorage.setItem('p1_access_token', 'stub')
    localStorage.setItem('p1_refresh_token', 'stub')
    localStorage.setItem('p1_expires_at', String(Date.now() + 3_600_000))
  })
}

test.describe('Panel trenera na telefonie', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signInAsAdmin(page)
  })

  test('lista użytkowników mieści się w szerokości ekranu', async ({ page }) => {
    await page.goto('/admin/uzytkownicy')
    await expect(page.getByRole('button', { name: /Katarzyna Lewandowska-Wiśniewska 0/ })).toBeVisible()

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
