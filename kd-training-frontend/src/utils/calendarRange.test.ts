import { describe, it, expect } from 'vitest'
import {
  addDaysIso, eachDay, formatRangeLabel, isOutsideMonth, monthGridRange,
  rangeFor, shiftAnchor, weekRange, weekdayShort,
} from './calendarRange'

describe('calendarRange', () => {
  it('starts weeks on Monday', () => {
    // 2027-03-10 is a Wednesday
    expect(weekRange('2027-03-10')).toEqual({ from: '2027-03-08', to: '2027-03-14' })
    // A Sunday belongs to the week that started six days earlier, not the one about to begin
    expect(weekRange('2027-03-14')).toEqual({ from: '2027-03-08', to: '2027-03-14' })
    expect(weekRange('2027-03-08')).toEqual({ from: '2027-03-08', to: '2027-03-14' })
  })

  it('always builds a 42-day month grid starting on a Monday', () => {
    // March 2027 starts on a Monday — the grid still runs the full six rows
    const march = monthGridRange('2027-03-15')
    expect(march.from).toBe('2027-03-01')
    expect(eachDay(march)).toHaveLength(42)

    // February 2027 starts on a Monday too but is short; May 2027 starts on a Saturday
    const may = monthGridRange('2027-05-15')
    expect(may.from).toBe('2027-04-26')
    expect(eachDay(may)).toHaveLength(42)
  })

  it('keeps the grid height fixed so paging does not make it jump', () => {
    const lengths = ['2027-01-01', '2027-02-01', '2027-03-01', '2027-04-01', '2027-05-01']
      .map(m => eachDay(monthGridRange(m)).length)
    expect(new Set(lengths)).toEqual(new Set([42]))
  })

  it('crosses the year boundary', () => {
    expect(weekRange('2027-12-31')).toEqual({ from: '2027-12-27', to: '2028-01-02' })
    expect(addDaysIso('2027-12-31', 1)).toBe('2028-01-01')
    expect(monthGridRange('2027-12-05').from).toBe('2027-11-29')
  })

  it('crosses a daylight-saving change without losing or repeating a day', () => {
    // Poland springs forward on 2027-03-28 and falls back on 2027-10-31. Arithmetic on local Date
    // objects can silently produce a 23- or 25-hour day and drop or duplicate one here.
    const spring = eachDay(weekRange('2027-03-28'))
    expect(spring).toHaveLength(7)
    expect(spring).toContain('2027-03-28')
    expect(new Set(spring).size).toBe(7)

    const autumn = eachDay(weekRange('2027-10-31'))
    expect(autumn).toHaveLength(7)
    expect(autumn).toContain('2027-10-31')
    expect(new Set(autumn).size).toBe(7)
  })

  it('pages by a week or a month depending on the view', () => {
    expect(shiftAnchor('week', '2027-03-10', 1)).toBe('2027-03-17')
    expect(shiftAnchor('week', '2027-03-10', -1)).toBe('2027-03-03')
    expect(shiftAnchor('month', '2027-03-10', 1)).toBe('2027-04-10')
    // Month arithmetic clamps rather than spilling into the next month
    expect(shiftAnchor('month', '2027-03-31', -1)).toBe('2027-02-28')
  })

  it('marks the grey edges of a month grid', () => {
    expect(isOutsideMonth('2027-04-26', '2027-05-15')).toBe(true)
    expect(isOutsideMonth('2027-05-01', '2027-05-15')).toBe(false)
  })

  it('picks the range matching the view', () => {
    expect(rangeFor('week', '2027-03-10')).toEqual(weekRange('2027-03-10'))
    expect(rangeFor('month', '2027-03-10')).toEqual(monthGridRange('2027-03-10'))
  })

  it('labels ranges in Polish', () => {
    expect(formatRangeLabel('month', '2027-03-10')).toBe('marzec 2027')
    // Within one month the start needs no month name of its own
    expect(formatRangeLabel('week', '2027-03-10')).toBe('8–14 marca 2027')
    // Across a boundary it does
    expect(formatRangeLabel('week', '2027-03-30')).toMatch(/^29 mar/)
  })

  it('abbreviates weekdays for column headers', () => {
    expect(weekdayShort('2027-03-08')).toBe('pon')
    expect(weekdayShort('2027-03-14')).toBe('nie')
  })

  it('never returns a range wider than the API accepts', () => {
    // The backend caps a page at 62 days; the month grid is the widest thing we ask for
    expect(eachDay(monthGridRange('2027-03-15')).length).toBeLessThanOrEqual(62)
  })
})
