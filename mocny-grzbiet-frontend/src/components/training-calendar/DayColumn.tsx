import clsx from 'clsx'
import { Plus } from 'lucide-react'
import { dayOfMonth, isOutsideMonth, isWeekend, todayIso, weekdayShort } from '../../utils/calendarRange'
import type { DayOff, PersonalTraining } from '../../types'
import { TrainingTile } from './TrainingTile'

interface DayColumnProps {
  date: string
  anchor: string
  trainings: PersonalTraining[]
  /** The coach's day off covering this date, if any. */
  dayOff?: DayOff
  /** Coach only: ids that already carry a private note. */
  notedTrainingIds?: Set<string>
  compact?: boolean
  showWeekday?: boolean
  cutId?: string | null
  pasteArmed?: boolean
  onOpen: (training: PersonalTraining) => void
  onAdd: (date: string) => void
  onPaste?: (date: string) => void
  onCopy?: (training: PersonalTraining) => void
  onCut?: (training: PersonalTraining) => void
  /** Per-card, not per-role: a client's own entries and the coach's sit side by side in one day. */
  canReshape?: (training: PersonalTraining) => boolean
  labels: {
    add: string; copy: string; cut: string; pasteHere: string
    unread: string; comments: string; task: string; calories: string; group: string
    /** Shown on an empty Sat/Sun. Never on a day that carries an entry — see the render below. */
    weekendFree: string
    dayOff: string
    note?: string
  }
}

/**
 * One day, in both the week and the month grid — the two views differ only in density, so they share
 * this component rather than diverging into two layouts that drift apart.
 *
 * Cards stack top-to-bottom in the order the API returns them (untimed first, then by hour). There is
 * no absolute positioning, no pixel maths and no overlap lanes: without an hour axis a day is just a
 * short list, and its height follows its content.
 */
export function DayColumn({
  date, anchor, trainings, dayOff, notedTrainingIds, compact = false, showWeekday = true, cutId, pasteArmed,
  onOpen, onAdd, onPaste, onCopy, onCut, canReshape = () => true, labels,
}: DayColumnProps) {
  const isToday = date === todayIso()
  const outside = compact && isOutsideMonth(date, anchor)

  return (
    <div
      className={clsx(
        // Named group: the cards inside carry one of their own, and an unnamed `group-hover` matches
        // ANY `.group` ancestor — hovering the day would then reveal every card's controls at once.
        'group/day flex flex-col rounded-2xl border p-1.5',
        isToday ? 'border-white/40' : 'border-white/[0.07]',
        // A day outside the shown month recedes by its frame, not by fading everything in it: the
        // old opacity took the trainings inside down to 2:1 contrast, and they are still real
        // sessions somebody may need to read.
        outside ? 'border-dashed bg-transparent' : 'bg-surface-900',
        // Below lg the week stacks into full-width rows, and a 160px floor on each made an empty
        // day half a phone screen of nothing. The tall floor is for the seven-column desktop grid,
        // where equal heights keep the row even.
        compact ? 'min-h-24 gap-1' : 'gap-2 lg:min-h-40',
        pasteArmed && 'cursor-copy hover:border-white/40',
      )}
      // While the clipboard is armed the whole day is the drop target. The cards inside go inert so
      // one tap cannot both paste and open a card.
      onClick={pasteArmed && onPaste ? () => onPaste(date) : undefined}
    >
      <div className="flex items-baseline justify-between px-0.5">
        <span className="text-xs text-surface-400">
          {showWeekday && <span className="mr-1">{weekdayShort(date)}</span>}
          <span className={clsx('font-semibold',
            isToday ? 'inline-flex min-w-5 justify-center rounded-full bg-white px-1.5 text-surface-950'
              : outside ? 'text-surface-500' : 'text-surface-200')}>
            {dayOfMonth(date)}
          </span>
        </span>
        {dayOff ? (
          // Says the coach is off, which the client cannot infer from an empty day. It does not hide
          // the trainings below: those were planned before the day off and the coach decides on each.
          <span className="rounded-full border border-white/25 px-1.5 text-[10px] uppercase tracking-wider text-surface-200">
            {labels.dayOff}
          </span>
        ) : isWeekend(date) && !compact && trainings.length === 0 && (
          // Only an ACTUALLY empty weekend day is "free". The flag used to hang on isWeekend alone,
          // so a Saturday session sat under a label saying the day was off, and the label
          // contradicted the card right below it.
          <span className="text-[10px] uppercase tracking-wider text-surface-500">{labels.weekendFree}</span>
        )}
      </div>

      {dayOff?.note && !compact && (
        <p className="px-0.5 text-xs text-surface-400">{dayOff.note}</p>
      )}

      <div className={clsx('flex flex-col', compact ? 'gap-1' : 'gap-2')}>
        {trainings.map(training => (
          <TrainingTile
            hasNote={notedTrainingIds?.has(training.id)}
            noteLabel={labels.note}
            key={training.id}
            training={training}
            compact={compact}
            cut={cutId === training.id}
            inert={pasteArmed}
            onOpen={onOpen}
            onCopy={canReshape(training) ? onCopy : undefined}
            onCut={canReshape(training) ? onCut : undefined}
            copyLabel={labels.copy}
            cutLabel={labels.cut}
            unreadLabel={labels.unread}
            commentsLabel={labels.comments}
            taskLabel={labels.task}
            caloriesLabel={labels.calories}
            groupLabel={labels.group}
          />
        ))}
      </div>

      {pasteArmed ? (
        <span className="mt-auto rounded-lg border border-dashed border-white/30 px-1 py-1 text-center text-[11px] text-surface-200">
          {labels.pasteHere}
        </span>
      ) : (
        <button
          type="button"
          aria-label={`${labels.add} ${date}`}
          onClick={() => onAdd(date)}
          className={clsx(
            'mt-auto flex items-center justify-center rounded-lg border border-dashed border-white/15',
            // One `transition` for colour and opacity alike: two transition-* utilities would fight
            // over the same property and the winner would depend on stylesheet order, not on the
            // order written here.
            'text-surface-500 transition hover:border-white/40 hover:text-white',
            // Faded out only where a pointer can bring it back. The base state has to be gated on the
            // input device, not on hover: `hover:` alone leaves the hidden base state standing on a
            // phone, and the week view stacks these same columns at phone width, where this button is
            // the only way to add anything. Opacity rather than display so the slot keeps its height —
            // the grid must not twitch as the cursor crosses it — and so the button stays tabbable,
            // which `group-focus-within` then reveals.
            'pointer-fine:opacity-0',
            'pointer-fine:group-hover/day:opacity-100 pointer-fine:group-focus-within/day:opacity-100',
            compact ? 'h-6' : 'h-7',
          )}
        >
          <Plus className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  )
}
