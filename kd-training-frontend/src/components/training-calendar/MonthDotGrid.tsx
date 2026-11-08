import clsx from 'clsx'
import { dayOfMonth, formatLongDate, isOutsideMonth, todayIso, weekdayShort } from '../../utils/calendarRange'
import type { DayOff, PersonalTraining } from '../../types'

/** Beyond this the dots stop being countable and a number says it better. */
const MAX_DOTS = 4

/**
 * Same three states the cards carry, reduced to what survives at 6 pixels — and told apart by fill
 * and brightness, not hue: the site is black and white, and so is anyone reading it colour-blind.
 * Done is solid white, planned a grey dot, missed an empty ring.
 */
const statusDot: Record<PersonalTraining['status'], string> = {
  PLANNED: 'bg-surface-500',
  COMPLETED: 'bg-white',
  MISSED: 'border border-surface-400 bg-transparent',
}

interface MonthDotGridProps {
  days: string[]
  anchor: string
  byDay: Map<string, PersonalTraining[]>
  dayOffByDay?: Map<string, DayOff>
  pasteArmed: boolean
  onSelectDay: (date: string) => void
  labels: { openDay: string; unread: string; pasteHere: string; dayOff: string }
}

/**
 * The month on a phone.
 * <p>
 * Seven columns on a 360-pixel screen leave about 45 pixels per day — enough to say THAT something
 * is planned, never enough to say WHAT. So the cells carry dots and the day opens on tap. The
 * alternative the calendar shipped with was one column of 42 stacked cells: around 4400 pixels of
 * scrolling, most of it empty days, and no way to see a month at all.
 */
export function MonthDotGrid({
  days, anchor, byDay, dayOffByDay, pasteArmed, onSelectDay, labels,
}: MonthDotGridProps) {
  const today = todayIso()

  return (
    <div>
      <div className="mb-1 grid grid-cols-7 gap-1">
        {days.slice(0, 7).map(day => (
          <span key={day} className="text-center text-[11px] uppercase text-surface-500">
            {weekdayShort(day)}
          </span>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {days.map(date => {
          const trainings = byDay.get(date) ?? []
          const outside = isOutsideMonth(date, anchor)
          const isToday = date === today
          const unread = trainings.some(training => training.unread)
          const total = trainings.length
          const off = dayOffByDay?.has(date) ?? false

          return (
            <button
              key={date}
              type="button"
              onClick={() => onSelectDay(date)}
              // The date spelled out, so the cell's visible number ("2") is part of its name — "Pokaż
              // dzień 2026-11-02" read the ISO string aloud and did not contain the "2" on screen.
              aria-label={`${labels.openDay} ${formatLongDate(date)}${off ? `, ${labels.dayOff}` : ''}`}
              className={clsx(
                'relative flex aspect-square flex-col items-center justify-start gap-1 rounded-xl',
                'border p-1 transition-colors',
                // Today is the one filled cell: the eye lands on it before reading a single number.
                isToday ? 'border-white bg-white' : 'border-white/[0.07] bg-surface-900 hover:border-white/25 focus-visible:border-white/40',
                outside && 'opacity-35',
                pasteArmed && 'cursor-copy border-dashed border-white/40',
              )}
            >
              <span className={clsx('text-xs font-semibold leading-none',
                isToday ? 'text-surface-950' : 'text-surface-200')}>
                {dayOfMonth(date)}
              </span>

              {off && (
                // A short bar under the number: the day is the coach's off, whatever dots follow.
                <span aria-hidden="true"
                  className={clsx('h-0.5 w-3 rounded-full', isToday ? 'bg-surface-950' : 'bg-surface-300')} />
              )}

              {/* One row of dots. */}
              <span className="flex flex-wrap items-center justify-center gap-0.5">
                {trainings.slice(0, MAX_DOTS).map(training => (
                  // A task is a square, a training a circle. At six pixels the shape is the only
                  // thing that still reads — fill and brightness are already spent on the status.
                  <span key={training.id}
                    className={clsx('h-1.5 w-1.5', statusDot[training.status],
                      // On today's white cell the light states would vanish, so they invert there.
                      isToday && (training.status === 'COMPLETED' ? '!bg-surface-950' : '!border-surface-600'),
                      isToday && training.status === 'PLANNED' && '!bg-surface-600',
                      training.kind === 'TASK' ? 'rounded-[1px]' : 'rounded-full')} />
                ))}
                {total > MAX_DOTS && (
                  <span className="text-[9px] leading-none text-surface-400 [font-variant-numeric:tabular-nums]">
                    +{total - MAX_DOTS}
                  </span>
                )}
              </span>

              {unread && (
                <span
                  aria-label={labels.unread}
                  className={clsx('absolute right-1 top-1 h-2 w-2 rounded-full ring-2',
                    isToday ? 'bg-surface-950 ring-white' : 'bg-white ring-surface-900')}
                />
              )}
            </button>
          )
        })}
      </div>

      {pasteArmed && (
        <p className="mt-2 text-center text-xs text-surface-300">{labels.pasteHere}</p>
      )}
    </div>
  )
}
