import { test, expect, type Page } from '@playwright/test'
import { ATHLETE, STATS, isoDate, signInAs } from './support/session'

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
const iso = isoDate

async function signInAsAthlete(page: Page) {
  await signInAs(page, ATHLETE, { '/api/user/my-training/stats': STATS })
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
