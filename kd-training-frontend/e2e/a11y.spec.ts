import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { ADMIN, ATHLETE, STATS, USERS, signInAs, stubApi } from './support/session'

/**
 * Every key screen passes axe (WCAG 2.1 A/AA plus best practice), on a phone and a desktop.
 *
 * The first run found: buttons with no name (the delete icon in the users table, the marketing
 * switch), names that did not contain the visible text (the wordmark, every day in the month grid),
 * scroll boxes a keyboard could not reach, links told apart from text by colour alone, a footer h4
 * straight after an h2, login and register without an h1, and trainings in other-month days faded
 * to 2:1. Each of those is a person who cannot use a part of the site, and none showed on screen.
 *
 * The API is stubbed (support/session.ts), so the pages render the same way every run.
 */

const RULES = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice']

const today = new Date()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const CALENDAR = { from: iso(today), to: iso(today), trainings: [], deletions: [] }

async function expectNoViolations(page: Page) {
  // Wait for the page's own content rather than a fixed delay: an audit of a spinner proves nothing.
  await expect(page.locator('main h1').first()).toBeVisible()
  // And for the page to settle: on a cold first run the web fonts and lazy chunks were still arriving
  // when axe measured, and one run in several failed a page that passes once it has finished loading.
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  const { violations } = await new AxeBuilder({ page }).withTags(RULES).analyze()
  // Ids and targets in the message, so a failure says what to fix without opening a trace.
  expect(violations.map(v => `${v.id}: ${v.nodes.map(n => n.target.join(' ')).join(' | ')}`)).toEqual([])
}

for (const viewport of [{ width: 390, height: 844 }, { width: 1280, height: 900 }]) {
  test.describe(`Dostępność (${viewport.width}px)`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize(viewport)
      // Entrance animations off (the site honours this setting): axe measures colours as they are
      // painted, and a card halfway through fading in fails contrast that it passes a moment later.
      await page.emulateMedia({ reducedMotion: 'reduce' })
    })

    for (const path of ['/', '/logowanie', '/rejestracja', '/regulamin', '/polityka-prywatnosci', '/tego-adresu-nie-ma']) {
      test(`gość: ${path}`, async ({ page }) => {
        await stubApi(page)
        await page.goto(path)
        await expectNoViolations(page)
      })
    }

    test('gość: rejestracja z zaproszenia', async ({ page }) => {
      await stubApi(page, { '/api/auth/invitation': { email: 'anna@example.com' } })
      await page.goto('/rejestracja?token=stub')
      await expect(page.getByLabel('Email')).toHaveValue('anna@example.com')
      await expectNoViolations(page)
    })

    test('podopieczny: plan treningowy', async ({ page }) => {
      await signInAs(page, ATHLETE, {
        '/api/user/my-training/stats': STATS,
        '/api/user/my-training/calendar': CALENDAR,
        '/api/user/my-training/materials': [{
          id: 'm1', title: 'Oddech przeponowy', note: 'Wdech nosem, pięć minut dziennie.',
          video: {
            id: 'v1', name: 'Przysiad', url: 'https://youtu.be/abc12345678', description: null,
            embedUrl: 'https://www.youtube-nocookie.com/embed/abc12345678',
            thumbnailUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', archived: false,
          },
          createdAt: iso(today), unread: true,
          questions: [{ id: 'q1', fromCoach: true, body: 'Pamiętaj o żebrach.', createdAt: iso(today) }],
        }],
      })
      await page.goto('/moje-konto/plan-treningowy')
      await expect(page.getByText('Oddech przeponowy')).toBeVisible()
      await expect(page.getByRole('img', { name: 'Aktywność w ostatnim roku' })).toBeVisible()
      await expectNoViolations(page)
    })

    test('podopieczny: ustawienia', async ({ page }) => {
      await signInAs(page, ATHLETE)
      await page.goto('/settings')
      await expectNoViolations(page)
    })

    test('trener: użytkownicy', async ({ page }) => {
      await signInAs(page, ADMIN, {
        '/api/admin/users': { content: USERS, page: 0, size: 50, totalElements: USERS.length, totalPages: 1 },
        '/api/admin/invitations': [
          { id: 'i1', email: 'zaproszona@example.com', createdAt: iso(today), expiresAt: iso(today) },
        ],
      })
      await page.goto('/admin/uzytkownicy')
      await expect(page.getByRole('button', { name: 'Katarzyna Lewandowska-Wiśniewska 0', exact: true })).toBeVisible()
      await expectNoViolations(page)
    })
  })
}
