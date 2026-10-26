import clsx from 'clsx'
import { dayOfMonth, isOutsideMonth, todayIso, weekdayShort } from '../../utils/calendarRange'
import type { PersonalTraining } from '../../types'

/** Beyond this the dots stop being countable and a number says it better. */
const MAX_DOTS = 4

/** Same three states the cards carry, reduced to what survives at 6 pixels. */
const statusDot: Record<PersonalTraining['status'], string> = {
  PLANNED: 'bg-surface-300',
  COMPLETED: 'bg-emerald-400',
  MISSED: 'bg-rose-400',
}

interface MonthDotGridProps {
  days: string[]
  anchor: string
  byDay: Map<string, PersonalTraining[]>
  pasteArmed: boolean
  onSelectDay: (date: string) => void
  labels: { openDay: string; unread: string; pasteHere: string }
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
  days, anchor, byDay, pasteArmed, onSelectDay, labels,
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

          return (
            <button
              key={date}
              type="button"
              onClick={() => onSelectDay(date)}
              aria-label={`${labels.openDay} ${date}`}
              className={clsx(
                'relative flex aspect-square flex-col items-center justify-start gap-1 rounded-lg',
                'border border-surface-800 bg-surface-900/60 p-1 transition-colors',
                'hover:border-primary-600/50 focus-visible:border-primary-600/50',
                outside && 'opacity-40',
                isToday && 'ring-1 ring-primary-500/50',
                pasteArmed && 'cursor-copy border-dashed border-primary-600/50',
              )}
            >
              <span className={clsx('text-xs font-semibold leading-none',
                isToday ? 'text-primary-400' : 'text-surface-200')}>
                {dayOfMonth(date)}
              </span>

              {/* One row of dots. */}
              <span className="flex flex-wrap items-center justify-center gap-0.5">
                {trainings.slice(0, MAX_DOTS).map(training => (
                  // A task is a square, a training a circle. At six pixels the shape is the only
                  // thing that still reads — the colour is already spent on the status.
                  <span key={training.id}
                    className={clsx('h-1.5 w-1.5', statusDot[training.status],
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
                  className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-rose-400"
                />
              )}
            </button>
          )
        })}
      </div>

      {pasteArmed && (
        <p className="mt-2 text-center text-xs text-primary-300">{labels.pasteHere}</p>
      )}
    </div>
  )
}
