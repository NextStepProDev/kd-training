// Date maths for the 1-on-1 training calendar.
// Dates are ISO 'YYYY-MM-DD' strings everywhere — the same shape the API speaks, so nothing has to
// be converted at the boundary. Weeks start on Monday (ISO), like the rest of the app.

import { addDays, addMonths, differenceInCalendarDays, format, parseISO, startOfMonth, startOfWeek } from 'date-fns'
import { pl } from 'date-fns/locale'

export type CalendarView = 'week' | 'month'

/** A month grid is always 6 rows of 7, so the grid never changes height as you page through months. */
const MONTH_GRID_DAYS = 42

export interface DateRange {
  from: string
  to: string
}

export function toIso(date: Date): string {
  return format(date, 'yyyy-MM-dd')
}

export function todayIso(): string {
  return toIso(new Date())
}

export function addDaysIso(iso: string, days: number): string {
  return toIso(addDays(parseISO(iso), days))
}

/** Monday–Sunday of the week containing `anchor`. */
export function weekRange(anchor: string): DateRange {
  const monday = startOfWeek(parseISO(anchor), { weekStartsOn: 1 })
  return { from: toIso(monday), to: toIso(addDays(monday, 6)) }
}

/**
 * The 6×7 grid covering `anchor`'s month: starts on the Monday on or before the 1st and always runs
 * 42 days, so late-starting months (and February) do not make the grid jump.
 */
export function monthGridRange(anchor: string): DateRange {
  const first = startOfMonth(parseISO(anchor))
  const gridStart = startOfWeek(first, { weekStartsOn: 1 })
  return { from: toIso(gridStart), to: toIso(addDays(gridStart, MONTH_GRID_DAYS - 1)) }
}

export function rangeFor(view: CalendarView, anchor: string): DateRange {
  return view === 'week' ? weekRange(anchor) : monthGridRange(anchor)
}

/** Every day in the range, inclusive. */
export function eachDay({ from, to }: DateRange): string[] {
  const start = parseISO(from)
  const count = differenceInCalendarDays(parseISO(to), start) + 1
  return Array.from({ length: count }, (_, i) => toIso(addDays(start, i)))
}

/** Steps the anchor by one page — a week for the week view, a month for the month view. */
export function shiftAnchor(view: CalendarView, anchor: string, direction: 1 | -1): string {
  const date = parseISO(anchor)
  return toIso(view === 'week' ? addDays(date, 7 * direction) : addMonths(date, direction))
}

/** True when the ISO date is not part of the month the grid is showing (the greyed-out edges). */
export function isOutsideMonth(iso: string, anchor: string): boolean {
  return iso.slice(0, 7) !== anchor.slice(0, 7)
}

export function isWeekend(iso: string): boolean {
  const day = parseISO(iso).getDay()
  return day === 0 || day === 6
}

/** Heading above the grid: "10–16 marca 2027" for a week, "marzec 2027" for a month. */
export function formatRangeLabel(view: CalendarView, anchor: string): string {
  if (view === 'month') {
    return format(parseISO(anchor), 'LLLL yyyy', { locale: pl })
  }
  const { from, to } = weekRange(anchor)
  const start = parseISO(from)
  const end = parseISO(to)
  const sameMonth = from.slice(0, 7) === to.slice(0, 7)
  const startLabel = sameMonth ? format(start, 'd') : format(start, 'd MMM', { locale: pl })
  return `${startLabel}–${format(end, 'd MMMM yyyy', { locale: pl })}`
}

/** Weekday abbreviation for a column header ("pon", "wt", …). */
export function weekdayShort(iso: string): string {
  return format(parseISO(iso), 'EEEEEE', { locale: pl })
}

export function dayOfMonth(iso: string): string {
  return format(parseISO(iso), 'd')
}

/** "10 marca 2027" — used in modal headings, where the full date has to stand on its own. */
export function formatLongDate(iso: string): string {
  return format(parseISO(iso), 'd MMMM yyyy', { locale: pl })
}
