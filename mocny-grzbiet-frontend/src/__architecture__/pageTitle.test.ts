import { describe, it, expect } from 'vitest'

/**
 * Every page names itself in the browser tab through `<Seo>`.
 *
 * Without it the tab keeps whatever the previous page set — found in manual testing: the login page
 * and the coach's panel both read "Kacper Dąbrowski — trener personalny", the home page's title, so
 * two open tabs of the app could not be told apart. Nothing looks wrong at the call site, which is
 * why this is a gate.
 *
 * Read through `import.meta.glob`, not `node:fs`, as the other gates here (no `@types/node`).
 */

const pages = import.meta.glob('../pages/*.tsx', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

/** A spinner for a split second before the redirect: nobody reads its tab. */
const EXEMPT = new Set(['../pages/OAuthCallbackPage.tsx'])

describe('page titles', () => {
  it('sees the pages at all', () => {
    // An empty glob would make the gate below pass for ever.
    expect(Object.keys(pages).length).toBeGreaterThan(10)
  })

  it('gives every page its own tab title', () => {
    const missing = Object.entries(pages)
      .filter(([path]) => !path.endsWith('.test.tsx') && !EXEMPT.has(path))
      .filter(([, source]) => !/<Seo\b/.test(source))
      .map(([path]) => path)

    expect(missing, 'Render <Seo title=… path=…> so the browser tab names the page.').toEqual([])
  })
})
