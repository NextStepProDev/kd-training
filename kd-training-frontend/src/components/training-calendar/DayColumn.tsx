import clsx from 'clsx'
import { Plus } from 'lucide-react'
import { dayOfMonth, isOutsideMonth, isWeekend, todayIso, weekdayShort } from '../../utils/calendarRange'
import type { PersonalTraining } from '../../types'
import { TrainingTile } from './TrainingTile'

interface DayColumnProps {
  date: string
  anchor: string
  trainings: PersonalTraining[]
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
    unread: string; comments: string; task: string; calories: string
    /** Shown on an empty Sat/Sun. Never on a day that carries an entry — see the render below. */
    weekendFree: string
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
  date, anchor, trainings, notedTrainingIds, compact = false, showWeekday = true, cutId, pasteArmed,
  onOpen, onAdd, onPaste, onCopy, onCut, canReshape = () => true, labels,
}: DayColumnProps) {
  const isToday = date === todayIso()
  const outside = compact && isOutsideMonth(date, anchor)

  return (
    <div
      className={clsx(
        // Named group: the cards inside carry one of their own, and an unnamed `group-hover` matches
        // ANY `.group` ancestor — hovering the day would then reveal every card's controls at once.
        'group/day flex flex-col rounded-lg border border-surface-800 bg-surface-900/60 p-1.5',
        compact ? 'min-h-24 gap-1' : 'min-h-40 gap-2',
        isToday && 'ring-1 ring-primary-500/40',
        outside && 'opacity-40',
        pasteArmed && 'cursor-copy hover:border-primary-600/60',
      )}
      // While the clipboard is armed the whole day is the drop target. The cards inside go inert so
      // one tap cannot both paste and open a card.
      onClick={pasteArmed && onPaste ? () => onPaste(date) : undefined}
    >
      <div className="flex items-baseline justify-between px-0.5">
        <span className="text-xs text-surface-400">
          {showWeekday && <span className="mr-1">{weekdayShort(date)}</span>}
          <span className={clsx('font-semibold', isToday ? 'text-primary-400' : 'text-surface-200')}>
            {dayOfMonth(date)}
          </span>
        </span>
        {isWeekend(date) && !compact && trainings.length === 0 && (
          // Only an ACTUALLY empty weekend day is "free". The flag used to hang on isWeekend alone,
          // so a Saturday session sat under a label saying the day was off, and the label
          // contradicted the card right below it.
          <span className="text-[10px] uppercase text-surface-600">{labels.weekendFree}</span>
        )}
      </div>

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
          />
        ))}
      </div>

      {pasteArmed ? (
        <span className="mt-auto rounded border border-dashed border-primary-600/50 px-1 py-1 text-center text-[11px] text-primary-300">
          {labels.pasteHere}
        </span>
      ) : (
        <button
          type="button"
          aria-label={`${labels.add} ${date}`}
          onClick={() => onAdd(date)}
          className={clsx(
            'mt-auto flex items-center justify-center rounded border border-dashed border-surface-700',
            // One `transition` for colour and opacity alike: two transition-* utilities would fight
            // over the same property and the winner would depend on stylesheet order, not on the
            // order written here.
            'text-surface-500 transition hover:border-primary-600/50 hover:text-primary-400',
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
