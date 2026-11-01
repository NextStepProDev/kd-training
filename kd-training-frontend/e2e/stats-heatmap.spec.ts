import { test, expect, type Page } from '@playwright/test'

/**
 * The year-long activity map on the client's plan page. A year is 53 week columns: at a fixed 10px
 * a cell it was wider than a phone, and its scroll box opened on the LEFT, so a client saw a year
 * ago and had to swipe to find last week. On a desktop the same fixed cells filled half the card.
 *
 * Layout only shows in a real browser, so this is measured here. The API is stubbed: a signed-in
 * client with consent, and stats with a training today. The rest of the page may fail to load; the
 * stats panel queries on its own.
 */

const today = new Date()
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const ATHLETE = {
  id: 'b0000000-0000-0000-0000-000000000001',
  email: 'anna@example.test',
  firstName: 'Anna',
  lastName: 'Nowak',
  phone: '500100200',
  role: 'USER',
  isAdmin: false,
  superAdmin: false,
  privacyAccepted: true,
  termsAccepted: true,
  marketingConsent: false,
  isAthlete: true,
  trainingConsent: true,
  preferredLanguage: 'pl',
  hasPassword: true,
  avatarUrl: null,
  createdAt: '2026-01-01T10:00:00Z',
}

const STATS = {
  thisMonthCount: 3, prevMonthCount: 2, totalCount: 40, firstActivityDate: '2026-01-05',
  currentStreakWeeks: 2, bestStreakWeeks: 5, avgPerMonth: 4,
  heatmap: { [iso(today)]: 2 },
  attendancePercent: 80, avgRpeOverall: 7, avgRpeRecent: 7,
  rpeDistribution: { light: 1, medium: 2, hard: 1 },
  tasks: { thisMonthDone: 0, thisMonthDue: 0, windowDone: 0, windowDue: 0, completionPercent: null },
}

async function signInAsAthlete(page: Page) {
  await page.route(/^https?:\/\/[^/]+\/api\//, route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/me') return route.fulfill({ json: ATHLETE })
    if (path === '/api/user/my-training/stats') return route.fulfill({ json: STATS })
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  await page.addInitScript(() => {
    localStorage.setItem('p1_access_token', 'stub')
    localStorage.setItem('p1_refresh_token', 'stub')
    localStorage.setItem('p1_expires_at', String(Date.now() + 3_600_000))
  })
}

test.describe('Mapa aktywności', () => {
  test.beforeEach(async ({ page }) => { await signInAsAthlete(page) })

  test('na telefonie dzisiejszy dzień widać bez przewijania', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/moje-konto/plan-treningowy')
    const todayCell = page.locator(`[title^="${iso(today)}:"]`)
    await expect(todayCell).toBeAttached()
    // Read the page vertically to the map, as a person would, without touching its horizontal
    // scroll: that scroll position is exactly what is under test.
    await todayCell.evaluate(el => el.closest('section')?.scrollIntoView({ block: 'center' }))

    // Measured against the viewport, not merely "visible": a cell scrolled out of its box to the
    // right is still visible to Playwright, it is just off the screen.
    const box = await todayCell.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.x + box!.width).toBeLessThanOrEqual(390)
    expect(box!.x).toBeGreaterThanOrEqual(0)
  })

  test('na komputerze wypełnia szerokość sekcji', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto('/moje-konto/plan-treningowy')
    const map = page.getByRole('img', { name: 'Aktywność w ostatnim roku' })
    await expect(map).toBeVisible()

    // How far the CELLS reach, not the box: the box was always full width, the cells inside it
    // stopped halfway — a check on the box passed against exactly the layout it was meant to catch.
    const { reach, available } = await map.evaluate(el => {
      const cells = [...el.querySelectorAll('span')].map(s => s.getBoundingClientRect())
      const left = Math.min(...cells.map(c => c.left))
      const right = Math.max(...cells.map(c => c.right))
      return { reach: right - left, available: el.getBoundingClientRect().width }
    })
    expect(reach).toBeGreaterThan(available * 0.95)
  })
})
