import { describe, it, expect } from 'vitest'

/**
 * The page declares itself dark to the browser. Without it native controls are drawn for a light
 * page: the calendar icon of the date fields (GoalsBoard) comes out black on a black field, and
 * scrollbars and autofill flash light. A meta tag rather than CSS because it applies before the
 * stylesheet arrives, and because Vitest reads CSS as an empty module, so only this can be checked.
 */
const indexHtml = Object.values(
  import.meta.glob('/index.html', { query: '?raw', import: 'default', eager: true }),
)[0] as string | undefined

describe('color scheme', () => {
  it('shouldDeclareTheDarkSchemeToTheBrowser', () => {
    expect(indexHtml).toBeDefined()
    expect(indexHtml).toMatch(/<meta name="color-scheme" content="dark" \/>/)
  })
})
