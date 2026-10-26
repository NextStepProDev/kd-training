import { format, parseISO } from 'date-fns'
import { pl } from 'date-fns/locale'

export function formatDate(iso: string): string {
  return format(parseISO(iso), 'd MMM yyyy', { locale: pl })
}

/**
 * Trims a wire time to what a reader wants to see: "09:00:00" → "09:00".
 * The API serialises `LocalTime` with seconds, and nobody schedules a class at 9:00:07 — the
 * seconds are always ":00" and always noise. Kept as one exported helper rather than a `.slice(0, 5)`
 * repeated at each call site: the four places that forgot the slice were all showing the seconds to
 * the customer, on the pages where they decide to buy.
 */
export function hhmm(time: string): string {
  return time.slice(0, 5)
}

export function formatDateRange(start: string, end: string | null): string {
  const startDate = parseISO(start)
  if (!end) return format(startDate, 'd MMM yyyy', { locale: pl })

  const endDate = parseISO(end)
  const sameMonth = startDate.getMonth() === endDate.getMonth() && startDate.getFullYear() === endDate.getFullYear()

  if (sameMonth) {
    return `${format(startDate, 'd', { locale: pl })} – ${format(endDate, 'd MMM yyyy', { locale: pl })}`
  }
  return `${format(startDate, 'd MMM', { locale: pl })} – ${format(endDate, 'd MMM yyyy', { locale: pl })}`
}

/**
 * Formats a schedule as a single continuous block "from start to end".
 * Multi-day with times: the time is stuck to its own date (start of the first day → end of
 * the last), so as not to suggest "every day during these hours", e.g.
 * "15 lip 2026, 09:00 – 18 lip 2026, 16:00". Single day: "30 maj 2026, 10:00 – 11:30".
 */
export function formatSchedule(
  startDate: string,
  endDate: string | null,
  startTime: string | null,
  endTime: string | null,
): string {
  const multiDay = !!endDate && endDate !== startDate

  if (multiDay) {
    if (startTime || endTime) {
      const from = `${formatDate(startDate)}${startTime ? `, ${hhmm(startTime)}` : ''}`
      const to = `${formatDate(endDate)}${endTime ? `, ${hhmm(endTime)}` : ''}`
      return `${from} – ${to}`
    }
    return formatDateRange(startDate, endDate)
  }

  const day = formatDate(startDate)
  if (startTime && endTime) return `${day}, ${hhmm(startTime)} – ${hhmm(endTime)}`
  if (startTime) return `${day}, ${hhmm(startTime)}`
  return day
}
