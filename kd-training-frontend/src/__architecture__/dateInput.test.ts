import { describe, it, expect } from 'vitest'

/**
 * `DateInput` is the only place allowed to render a raw `<input type="date">`.
 *
 * Safari on macOS keeps the calendar popover open after a day is clicked, so a plain date input
 * reads as "the click did nothing" until you click somewhere else. DateInput dismisses it and puts
 * focus straight back. Nothing about a bare `<input type="date">` looks wrong at the call site,
 * which is what makes this worth a gate rather than vigilance: all fourteen date fields in the app
 * had the same defect, and every new form starts out with it unless something says otherwise.
 *
 * Sources are read through `import.meta.glob` rather than `node:fs` so the gate needs no
 * `@types/node` in a frontend package that otherwise has no use for it.
 */

const OWNER = '../components/ui/DateInput.tsx'

const sources = import.meta.glob('../**/*.tsx', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

/** Strips comments so commentary about date inputs never trips the gate. */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

describe('date fields', () => {
  it('routes every date input through DateInput', () => {
    const offenders = Object.entries(sources)
      // Tests may name the attribute while explaining what they simulate.
      .filter(([path]) => path !== OWNER && !path.endsWith('.test.tsx'))
      .filter(([, source]) => /type=["']date["']/.test(stripComments(source)))
      .map(([path]) => path)

    expect(
      offenders,
      `A bare <input type="date"> leaves Safari's calendar popover open after a day is picked, ` +
        `so the pick looks like it did nothing. Use <DateInput> from components/ui/DateInput.\n` +
        offenders.join('\n'),
    ).toEqual([])
  })

  it('sees the files it is meant to guard', () => {
    // A glob that silently matches nothing would make the gate above pass forever.
    expect(Object.keys(sources).length).toBeGreaterThan(20)
    expect(sources[OWNER]).toBeDefined()
  })

  it('keeps DateInput dismissing the picker on a mouse pick', () => {
    // The blur IS the fix; without it this component is an alias for the bug it exists to prevent.
    // The focus put back after it is what keeps the fix free on browsers that close the popover
    // themselves: without it, every pick drops focus to <body>.
    const source = sources[OWNER]

    expect(source).toMatch(/pointerType !== 'touch'/)
    expect(source).toMatch(/dismissOnCommit\.current/)
    expect(source).toMatch(/\.blur\(\)[\s\S]{0,400}\.focus\(/)
  })
})
