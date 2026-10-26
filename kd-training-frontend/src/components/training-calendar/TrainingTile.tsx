import clsx from 'clsx'
import { Check, ClipboardList, Copy, MessageSquare, NotebookPen, Scissors } from 'lucide-react'
import type { PersonalTraining } from '../../types'

/**
 * The card body: a button normally, a plain block while the clipboard is armed. Swapping the tag
 * rather than disabling a button is what lets the click fall through to the day behind it.
 */
function Content({ inert, onClick, className, children }: {
  inert: boolean
  onClick: () => void
  className: string
  children: React.ReactNode
}) {
  if (inert) {
    return <div className={className}>{children}</div>
  }
  return (
    <button type="button" onClick={onClick} className={className}>
      {children}
    </button>
  )
}

/** Left-border colour carries the status; the card itself stays neutral so a day reads at a glance. */
const statusBorder: Record<PersonalTraining['status'], string> = {
  PLANNED: 'border-l-surface-600',
  COMPLETED: 'border-l-emerald-500',
  MISSED: 'border-l-rose-500',
}

interface TrainingTileProps {
  training: PersonalTraining
  compact?: boolean
  cut?: boolean
  /**
   * The clipboard is armed, so the whole day is a drop target. The card goes inert: otherwise one
   * tap would both paste and open this card, which is two actions from one click. Escape disarms.
   */
  inert?: boolean
  onOpen: (training: PersonalTraining) => void
  onCopy?: (training: PersonalTraining) => void
  onCut?: (training: PersonalTraining) => void
  copyLabel: string
  cutLabel: string
  unreadLabel: string
  /** Whether the coach has already written a private note about this one. */
  hasNote?: boolean
  noteLabel?: string
  commentsLabel: string
  /** Names the clipboard icon on a task; the card itself says what it is by its dashed outline. */
  taskLabel: string
  caloriesLabel: string
}

export function TrainingTile({
  training, compact = false, cut = false, inert = false, onOpen, onCopy, onCut,
  copyLabel, cutLabel, unreadLabel, commentsLabel, taskLabel, caloriesLabel, hasNote = false, noteLabel,
}: TrainingTileProps) {
  const showClipboard = (onCopy || onCut) && !inert
  const isTask = training.kind === 'TASK'

  return (
    <div
      className={clsx(
        // Named group: the day column around this card carries one too, and an unnamed `group-hover`
        // would match either of them — the day's hover must not speak for every card standing in it.
        'group/tile relative w-full rounded-lg border border-surface-800 border-l-2 bg-surface-800 text-left',
        'transition-colors hover:border-primary-600/50',
        statusBorder[training.status],
        // A dashed outline for tasks. The left border still carries the status, so the two say
        // different things and neither has to give up its colour to the other.
        isTask && 'border-dashed',
        cut && 'opacity-50',
        compact ? 'px-1.5 py-1' : 'p-2',
      )}
    >
      {/*
        While the clipboard is armed this is genuinely not a control — the day underneath is the
        drop target — so it stops being a <button> rather than being a disabled one. That keeps the
        markup honest, keeps it off the keyboard path, and lets the click reach the day.
      */}
      <Content
        inert={inert}
        onClick={() => onOpen(training)}
        className={clsx(
          'block w-full text-left',
          // Room kept for the controls only where they are permanently on screen. A month cell is
          // roughly 130px wide; holding 56px of it empty would leave about seven characters of
          // title. Where they appear on hover they overlay instead — the card being covered is the
          // one under the cursor, and the title truncates there anyway.
          showClipboard && (compact ? 'pointer-coarse:pr-12' : 'pr-14'),
        )}
      >
        <span className="flex items-center gap-1.5">
          {training.unread && (
            <span
              aria-label={unreadLabel}
              title={unreadLabel}
              className="h-2 w-2 shrink-0 rounded-full bg-rose-400"
            />
          )}
          {hasNote && (
            <NotebookPen aria-label={noteLabel} className="h-3.5 w-3.5 shrink-0 text-amber-400" />
          )}
          {isTask && (
            <ClipboardList aria-label={taskLabel} className="h-3.5 w-3.5 shrink-0 text-sky-400" />
          )}
          {training.status === 'COMPLETED' && <Check className="w-3.5 h-3.5 shrink-0 text-emerald-400" />}
          <span className={clsx('font-medium text-surface-100 truncate', compact ? 'text-xs' : 'text-sm')}>
            {training.title}
          </span>
        </span>
        {/* An untimed training renders NOTHING here. It is the default case, so a dash on every
            card would be pure noise. */}
        {training.startTime && (
          <span className={clsx('block text-surface-400', compact ? 'text-[11px]' : 'text-xs')}>
            {training.startTime.slice(0, 5)}
            {training.endTime ? `–${training.endTime.slice(0, 5)}` : ''}
          </span>
        )}
        {!compact && (training.rpe != null || training.targetCalories != null || training.commentCount > 0) && (
          <span className="mt-1 flex items-center gap-1.5">
            {/* "≤ 2200 kcal" rather than a sentence: it has to survive a card two words wide. */}
            {training.targetCalories != null && (
              <span title={caloriesLabel}
                className="inline-block rounded-full bg-surface-900 px-1.5 py-0.5 text-[11px] text-sky-300">
                ≤ {training.targetCalories} kcal
              </span>
            )}
            {training.rpe != null && (
              <span className="inline-block rounded-full bg-surface-900 px-1.5 py-0.5 text-[11px] text-surface-300">
                RPE {training.rpe}
              </span>
            )}
            {training.commentCount > 0 && (
              <span aria-label={commentsLabel}
                className="inline-flex items-center gap-0.5 text-[11px] text-surface-400">
                <MessageSquare className="h-3 w-3" />
                {training.commentCount}
              </span>
            )}
          </span>
        )}
      </Content>

      {showClipboard && (
        // Always in the DOM, and on a touch device always visible: hover-only controls do not exist
        // there, and a tap aimed at one lands on the card instead. 24px is the minimum comfortable
        // target, so the buttons keep their size in a month cell — only their visibility changes.
        <div
          className={clsx(
            'absolute flex gap-0.5 transition-opacity',
            compact ? 'right-0.5 top-0.5' : 'right-1 top-1',
            compact
              // A month cell holds a two-word title. Two buttons parked on top of it, on every card,
              // would bury the one thing the month view is read for, so with a cursor present they
              // wait for it — and `pointer-fine` keeps them standing on a touch tablet, which is
              // wide enough for this layout and has no hover to offer.
              ? [
                  'pointer-fine:opacity-0',
                  'pointer-fine:group-hover/tile:opacity-100',
                  'pointer-fine:group-focus-within/tile:opacity-100',
                ]
              : 'opacity-70 group-hover/tile:opacity-100 focus-within:opacity-100',
          )}
        >
          {onCopy && (
            <button
              type="button"
              aria-label={copyLabel}
              title={copyLabel}
              onClick={() => onCopy(training)}
              className="flex h-6 w-6 items-center justify-center rounded border border-surface-700 bg-surface-900 text-surface-300 hover:text-primary-300"
            >
              <Copy className="h-3.5 w-3.5" />
            </button>
          )}
          {onCut && (
            <button
              type="button"
              aria-label={cutLabel}
              title={cutLabel}
              onClick={() => onCut(training)}
              className="flex h-6 w-6 items-center justify-center rounded border border-surface-700 bg-surface-900 text-surface-300 hover:text-primary-300"
            >
              <Scissors className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      )}
    </div>
  )
}
