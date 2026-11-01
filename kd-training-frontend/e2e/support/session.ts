import type { Page, Route } from '@playwright/test'

/**
 * A signed-in session against a stubbed API, for the specs that need one. Shared because three specs
 * had grown three copies of the same fake accounts, and a field added to `User` would have to be
 * remembered in each.
 *
 * Tokens only have to exist and look unexpired: nothing checks them, every /api call is answered
 * here. A path the caller does not handle gets 404, so a page that reads more than the test cares
 * about shows its error state instead of hanging.
 */

const baseUser = {
  phone: '500100000',
  privacyAccepted: true,
  termsAccepted: true,
  marketingConsent: false,
  preferredLanguage: 'pl',
  hasPassword: true,
  avatarUrl: null,
  createdAt: '2026-01-01T10:00:00Z',
}

export const ADMIN = {
  ...baseUser,
  id: 'a0000000-0000-0000-0000-000000000001',
  email: 'kacper@example.test',
  firstName: 'Kacper',
  lastName: 'Dąbrowski',
  role: 'ADMIN',
  isAdmin: true,
  superAdmin: true,
  isAthlete: false,
  trainingConsent: false,
}

export const ATHLETE = {
  ...baseUser,
  id: 'b0000000-0000-0000-0000-000000000001',
  email: 'anna@example.test',
  firstName: 'Anna',
  lastName: 'Nowak',
  role: 'USER',
  isAdmin: false,
  superAdmin: false,
  isAthlete: true,
  trainingConsent: true,
}

export const USERS = Array.from({ length: 6 }, (_, i) => ({
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

/** 'YYYY-MM-DD' in the browser's (and this process's) local zone, like todayIso() in the app. */
export const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const STATS = {
  thisMonthCount: 3, prevMonthCount: 2, totalCount: 40, firstActivityDate: '2026-01-05',
  currentStreakWeeks: 2, bestStreakWeeks: 5, avgPerMonth: 4,
  heatmap: { [isoDate(new Date())]: 2 },
  attendancePercent: 80, avgRpeOverall: 7, avgRpeRecent: 7,
  rpeDistribution: { light: 1, medium: 2, hard: 1 },
  tasks: { thisMonthDone: 0, thisMonthDue: 0, windowDone: 0, windowDue: 0, completionPercent: null },
}

type Responses = Record<string, unknown>

/** Answers /api paths from `responses` (exact pathname → JSON body), 404 for the rest. */
export async function stubApi(page: Page, responses: Responses = {}) {
  await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path in responses) return route.fulfill({ json: responses[path] })
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
}

/** A session as `user`, plus whatever else the page under test reads. */
export async function signInAs(page: Page, user: typeof ADMIN | typeof ATHLETE, responses: Responses = {}) {
  await stubApi(page, { '/api/user/me': user, ...responses })
  await page.addInitScript(() => {
    localStorage.setItem('p1_access_token', 'stub')
    localStorage.setItem('p1_refresh_token', 'stub')
    localStorage.setItem('p1_expires_at', String(Date.now() + 3_600_000))
  })
}
