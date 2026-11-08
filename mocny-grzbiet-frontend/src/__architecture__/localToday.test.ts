import { describe, it, expect } from 'vitest'

/**
 * `todayIso()` is the only way to ask what today's date is.
 *
 * `new Date().toISOString().slice(0, 10)` is wrong here twice over, and both halves are quiet.
 * It reads the date in UTC while the club runs on Europe/Warsaw, so between midnight and 02:00
 * local it answers yesterday — on a fresh page load, with nothing to notice. And it is usually
 * written as a module constant, which freezes the answer at the moment the bundle was loaded: a
 * panel left open overnight then offers yesterday as "today". Where it feeds a `max` on a date
 * field, today's date stops being selectable at all and the only way out is a reload nobody
 * suggests.
 *
 * `todayIso()` (utils/calendarRange) formats in the local zone and is called during render, so it
 * has neither half. Sources are read through `import.meta.glob` rather than `node:fs` so the gate
 * needs no `@types/node` — same reasoning as the date-input gate next door.
 */

const OWNER = '../utils/calendarRange.ts'

const sources = import.meta.glob('../**/*.{ts,tsx}', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

/** `toISOString()` sliced or split down to a bare YYYY-MM-DD — the shape that means "today". */
const CALENDAR_DATE_FROM_UTC = /toISOString\(\)\s*\.\s*(slice\(\s*0\s*,\s*10\s*\)|split\(\s*['"]T['"]\s*\))/

describe('calendar dates', () => {
  it('reads today from todayIso(), never from toISOString()', () => {
    const offenders = Object.entries(sources)
      // Tests pin the wrong shape on purpose while proving the gate bites.
      .filter(([path]) => path !== OWNER && !/\.test\.tsx?$/.test(path))
      .filter(([, source]) => CALENDAR_DATE_FROM_UTC.test(stripComments(source)))
      .map(([path]) => path)

    expect(
      offenders,
      `toISOString() reads the date in UTC, so between midnight and 02:00 Warsaw time it answers ` +
        `yesterday — and as a module constant it also freezes at page load. Use todayIso() from ` +
        `utils/calendarRange.\n` +
        offenders.join('\n'),
    ).toEqual([])
  })

  it('sees the files it is meant to guard', () => {
    // A glob that silently matches nothing would make the gate above pass forever.
    expect(Object.keys(sources).length).toBeGreaterThan(20)
    expect(sources[OWNER]).toBeDefined()
  })

  it('bites on the shape it is meant to catch', () => {
    // Proof on red: without this, a regex that matches nothing would look identical to a clean repo.
    expect(CALENDAR_DATE_FROM_UTC.test("const TODAY = new Date().toISOString().slice(0, 10)")).toBe(true)
    expect(CALENDAR_DATE_FROM_UTC.test("const today = new Date().toISOString().split('T')[0]")).toBe(true)
    // A full timestamp is a different thing and stays allowed.
    expect(CALENDAR_DATE_FROM_UTC.test('createdAt: new Date().toISOString()')).toBe(false)
  })

  it('keeps todayIso() out of UTC', () => {
    // The gate is only worth anything if the sanctioned helper is actually local.
    const source = sources[OWNER]
    expect(source).toMatch(/export function todayIso/)
    expect(source).not.toMatch(/toISOString/)
  })
})
