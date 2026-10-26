import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CalendarDays, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { DayColumn } from './DayColumn'
import { MonthDotGrid } from './MonthDotGrid'
import { DaySheet } from './DaySheet'
import { TrainingFormModal } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import { DeletedTrainingsBanner } from './DeletedTrainingsBanner'
import { useTrainingClipboard } from '../../hooks/useTrainingClipboard'
import { useCompactViewport } from '../../hooks/useCompactViewport'
import {
  eachDay, formatRangeLabel, rangeFor, shiftAnchor, todayIso, weekdayShort,
  type CalendarView,
} from '../../utils/calendarRange'
import { keepWithinEntity } from '../../utils/queryEntity'
import type { CreateTrainingBody, PersonalTraining } from '../../types'
import { notesApi } from '../../api/notes'
import { canReshapeTraining, type TrainingCalendarAdapter } from './adapter'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'

/**
 * The shared 1-on-1 calendar: one component for the coach and the client, told apart only by the
 * adapter it is handed. Anything that differs visually between the two lives on the page around it.
 */
export function TrainingCalendar({ adapter }: { adapter: TrainingCalendarAdapter }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const { entry: clipboard, copy, clear: clearClipboard } = useTrainingClipboard()

  // The month view swaps component below the sm breakpoint rather than restyling: at phone width a
  // day cell can hold a dot, not a card, so the content moves into a sheet behind a tap.
  const compactViewport = useCompactViewport()

  // Which view the calendar opens on, decided once on mount. A wide screen gets the month: planning
  // happens in blocks longer than a week, and the range costs the same three queries either way. A
  // phone opens on the week, because there the month is a grid of dots — someone reaching for "what
  // am I doing today" should not have to tap to find out. Deliberately not reactive to a later
  // resize: this is a starting point, and yanking the view out from under a reader who rotated the
  // phone would be worse than a stale choice they can change with the switch above.
  const [view, setView] = useState<CalendarView>(() => (compactViewport ? 'week' : 'month'))

  const [anchor, setAnchor] = useState(todayIso())
  const [formDate, setFormDate] = useState<string | null>(null)
  const [editing, setEditing] = useState<PersonalTraining | null>(null)
  const [selected, setSelected] = useState<PersonalTraining | null>(null)
  const [openDay, setOpenDay] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const dotGrid = view === 'month' && compactViewport

  const range = useMemo(() => rangeFor(view, anchor), [view, anchor])
  // Which entries already carry a private note. Coach only, and identifiers only — the text stays
  // behind the per-entry endpoint. Without these a note is invisible until you happen to open the
  // day it is on, which is how you forget you wrote it.
  const isCoach = adapter.role === 'coach'
  const markersQuery = useQuery({
    queryKey: ['admin', 'notes', 'markers', adapter.athleteId, range.from, range.to],
    queryFn: () => notesApi.markers(range.from, range.to, adapter.athleteId),
    enabled: isCoach,
    staleTime: SHORT_STALE_MS,
  })
  const notedTrainingIds = useMemo(
    () => new Set(markersQuery.data?.trainingIds ?? []), [markersQuery.data])
  const days = useMemo(() => eachDay(range), [range])

  const rangeKey = useMemo(() => adapter.rangeKey(range.from, range.to), [adapter, range])

  const rangeQuery = useQuery({
    queryKey: rangeKey,
    queryFn: () => adapter.fetchRange(range.from, range.to),
    // The global 5-minute staleTime is wrong here: a plan the other side just changed must not sit
    // in cache that long. Half a minute is the compromise — own edits invalidate this key outright,
    // switching person/month/view is a new key and fetches anyway, and the other side's changes are
    // announced by the unread dots regardless.
    staleTime: SHORT_STALE_MS,
    refetchOnMount: 'always',
    // Previous data is kept ONLY inside one person's calendar (the trailing `from`/`to` pair is the
    // page, everything before it is whose calendar). Paging to an uncached month otherwise tears the
    // grid down to a spinner for a frame — the page collapses from six rows to nothing and snaps
    // back, which reads as a glitch rather than as loading. Across the boundary the spinner is right:
    // showing the previous person's trainings under a new name is the worse failure.
    placeholderData: (previous, previousQuery) =>
      keepWithinEntity(previous, previousQuery, rangeKey, 2),
  })

  const byDay = useMemo(() => {
    const map = new Map<string, PersonalTraining[]>()
    for (const training of rangeQuery.data?.trainings ?? []) {
      const list = map.get(training.date)
      if (list) list.push(training)
      else map.set(training.date, [training])
    }
    return map
  }, [rangeQuery.data])

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: rangeKey })
  }

  // Decided per card: a client's week holds both what they logged and what the coach assigned, and
  // only the first is theirs to reshape.
  const canReshape = (training: PersonalTraining) => canReshapeTraining(adapter.role, training)

  const markSeenMutation = useMutation({
    mutationFn: (to: string) => adapter.markSeen(to),
    onSuccess: () => {
      // refetchType 'none' on purpose: the dots the viewer is looking at RIGHT NOW must stay lit for
      // this visit — that is the whole point of them. Marking the cache stale means the next entry
      // fetches a clean page instead.
      void queryClient.invalidateQueries({ queryKey: ['user', 'my-training', 'summary'], refetchType: 'none' })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'athletes'], refetchType: 'none' })
    },
  })

  // Once per WINDOW PER PERSON, not once per mount. A single flag would report the first page and
  // then stay quiet, so paging to next month could never mark that month as read — and with the
  // server now clearing only as far as it is told, those dots would stand for good. The athlete id
  // belongs in the key because the coach switches clients without this component unmounting: keyed
  // on the window alone, the second client's page would be silently treated as already reported.
  //
  // Still gated on the page having genuinely arrived: on a cached revisit React Query reports
  // isSuccess in the same tick, and marking seen before the server has counted the dots clears them
  // before they are ever shown — the exact failure the reference implementation shipped.
  const seenRanges = useRef(new Set<string>())
  const canMarkSeen = rangeQuery.isSuccess && !rangeQuery.isFetching
  useEffect(() => {
    const reported = `${adapter.athleteId}|${range.to}`
    if (!canMarkSeen || seenRanges.current.has(reported)) return
    seenRanges.current.add(reported)
    markSeenMutation.mutate(range.to)
    // markSeenMutation is stable for the lifetime of the component
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canMarkSeen, range.to, adapter.athleteId])

  const dismissMutation = useMutation({
    mutationFn: () => adapter.dismissDeletions(),
    onSuccess: refresh,
    onError: (e: Error) => setActionError(e.message),
  })

  // Older clipboard entries (written before the id was stored) have no athlete — treat them as local.
  const crossAthletePaste = clipboard != null
    && clipboard.athleteId != null
    && clipboard.athleteId !== adapter.athleteId

  const pasteMutation = useMutation({
    mutationFn: ({ date }: { date: string }) =>
      adapter.pasteTraining(clipboard!.trainingId, date, clipboard!.mode),
    onSuccess: () => {
      // A cut is spent once pasted; a copy stays armed so the same session can be laid out repeatedly.
      if (clipboard?.mode === 'MOVE') clearClipboard()
      setActionError(null)
      refresh()
    },
    onError: (e: Error) => {
      // The source may have been deleted from another tab while the clipboard was armed.
      setActionError(e.message)
      clearClipboard()
    },
  })

  const submitForm = async (body: CreateTrainingBody) => {
    if (editing) {
      await adapter.updateTraining(editing.id, { ...body, version: editing.version })
    } else {
      await adapter.createTraining(body)
    }
    refresh()
  }

  // Seven columns from `lg` (1024px) and not a pixel below, in step with `useCompactViewport`. Seven
  // of anything fits at 640px; seven day columns do not — at 768px they are 84px wide, which is a
  // card of two buttons and a truncated first letter. Below the breakpoint the week stacks into
  // full-width rows, the same treatment the phone already had.
  const gridCols = 'grid gap-2 grid-cols-1 lg:grid-cols-7'

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="sm" aria-label={t('nav.previous')}
            onClick={() => setAnchor(a => shiftAnchor(view, a, -1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-44 text-center text-sm font-medium text-surface-100">
            {formatRangeLabel(view, anchor)}
          </span>
          <Button variant="ghost" size="sm" aria-label={t('nav.next')}
            onClick={() => setAnchor(a => shiftAnchor(view, a, 1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setAnchor(todayIso())}>
            {t('nav.today')}
          </Button>
        </div>

        <div className="flex gap-1" role="group" aria-label={t('nav.viewSwitch')}>
          {(['week', 'month'] as const).map(v => (
            <button
              key={v}
              type="button"
              aria-pressed={view === v}
              onClick={() => setView(v)}
              className={clsx(
                'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                view === v
                  ? 'border border-primary-500/40 bg-surface-800 text-primary-400'
                  : 'text-surface-400 hover:bg-surface-800/50 hover:text-surface-200',
              )}
            >
              {t(`nav.${v}`)}
            </button>
          ))}
        </div>
      </div>

      {clipboard && (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-primary-600/40 bg-primary-500/10 px-3 py-2 text-sm">
          <span className="text-primary-200">
            {t(clipboard.mode === 'MOVE' ? 'clipboard.cut' : 'clipboard.copied', { title: clipboard.title })}
            {/* Entries taken from another client's plan land HERE, so say so before the click rather
                than after. A cut across clients cannot carry the tick-off, effort rating or comment
                thread — those belong to the person it came from — so it leaves a fresh entry. */}
            {crossAthletePaste && (
              <span className="ml-1 text-primary-300/80">
                {t(clipboard.mode === 'MOVE' ? 'clipboard.movedFromOther' : 'clipboard.fromOther')}
              </span>
            )}
          </span>
          <button type="button" onClick={clearClipboard}
            className="flex h-6 w-6 items-center justify-center rounded text-primary-300 hover:text-primary-100"
            aria-label={t('clipboard.cancel')}>
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {actionError && (
        <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{actionError}</p>
      )}

      <DeletedTrainingsBanner
        deletions={rangeQuery.data?.deletions ?? []}
        onDismiss={() => dismissMutation.mutate()}
        dismissing={dismissMutation.isPending}
      />

      {rangeQuery.isLoading ? (
        <LoadingSpinner />
      ) : rangeQuery.isError ? (
        <QueryError error={rangeQuery.error as Error} onRetry={refresh} />
      ) : (
        <div className={clsx('transition-opacity', rangeQuery.isFetching && 'opacity-60')}>
          {dotGrid ? (
            <MonthDotGrid
              days={days}
              anchor={anchor}
              byDay={byDay}
              pasteArmed={clipboard != null}
              onSelectDay={setOpenDay}
              labels={{
                openDay: t('day.open'),
                unread: t('unread.dot'),
                pasteHere: t('clipboard.pasteHere'),
              }}
            />
          ) : (
        <>
          {view === 'month' && (
            // Weekday names once above the grid; repeating them in 42 cells would be noise.
            <div className={clsx(gridCols, 'mb-1 hidden lg:grid')}>
              {days.slice(0, 7).map(d => (
                <span key={d} className="px-1 text-center text-xs uppercase text-surface-500">
                  {weekdayShort(d)}
                </span>
              ))}
            </div>
          )}
          <div className={gridCols}>
            {days.map(date => (
              <DayColumn
                key={date}
                date={date}
                anchor={anchor}
                trainings={byDay.get(date) ?? []}
                notedTrainingIds={notedTrainingIds}
                compact={view === 'month'}
                showWeekday={view === 'week'}
                cutId={clipboard?.mode === 'MOVE' ? clipboard.trainingId : null}
                pasteArmed={clipboard != null}
                onOpen={setSelected}
                onAdd={d => { setEditing(null); setFormDate(d) }}
                onPaste={d => pasteMutation.mutate({ date: d })}
                onCopy={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'COPY', athleteId: adapter.athleteId })}
                onCut={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'MOVE', athleteId: adapter.athleteId })}
                canReshape={canReshape}
                labels={{
                  add: t('day.add'),
                  copy: t('clipboard.copy'),
                  cut: t('clipboard.cutAction'),
                  pasteHere: t('clipboard.pasteHere'),
                  unread: t('unread.dot'),
                  comments: t('comments.title'),
                  note: t('notes.marker'),
                  task: t('form.kind.TASK'),
                  calories: t('form.calories'),
                  weekendFree: t('day.weekendFree'),
                }}
              />
            ))}
          </div>
        </>
          )}
        </div>
      )}

      {rangeQuery.data && rangeQuery.data.trainings.length === 0 && !rangeQuery.isFetching && (
        <p className="flex items-center gap-2 text-sm text-surface-500">
          <CalendarDays className="h-4 w-4" />
          {t('empty')}
        </p>
      )}

      {openDay != null && (
        <DaySheet
          date={openDay}
          trainings={byDay.get(openDay) ?? []}
          notedTrainingIds={notedTrainingIds}
          pasteArmed={clipboard != null}
          onClose={() => setOpenDay(null)}
          onOpen={setSelected}
          onAdd={d => { setEditing(null); setFormDate(d) }}
          onPaste={d => pasteMutation.mutate({ date: d })}
          onCopy={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'COPY', athleteId: adapter.athleteId })}
          onCut={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'MOVE', athleteId: adapter.athleteId })}
          canReshape={canReshape}
          labels={{
            add: t('day.add'),
            copy: t('clipboard.copy'),
            cut: t('clipboard.cutAction'),
            pasteHere: t('clipboard.pasteHere'),
            unread: t('unread.dot'),
            comments: t('comments.title'),
            note: t('notes.marker'),
            task: t('form.kind.TASK'),
            calories: t('form.calories'),
            empty: t('day.empty'),
          }}
        />
      )}

      {formDate != null && (
        <TrainingFormModal
          key={editing?.id ?? `new-${formDate}`}
          training={editing}
          date={formDate}
          onClose={() => { setFormDate(null); setEditing(null) }}
          onSubmit={submitForm}
          onConflictRefresh={() => { refresh(); setFormDate(null); setEditing(null) }}
          canAttach={adapter.role === 'coach'}
        />
      )}

      {selected && (
        <TrainingDetailModal
          key={selected.id}
          training={selected}
          onClose={() => setSelected(null)}
          adapter={adapter}
          onEdit={tr => { setSelected(null); setEditing(tr); setFormDate(tr.date) }}
          onDelete={async tr => { await adapter.deleteTraining(tr.id); refresh() }}
          onDuplicate={async tr => { await adapter.duplicateTraining(tr.id); refresh() }}
          onComplete={adapter.completeTraining
            ? async (tr, rpe, feedback) => {
                await adapter.completeTraining!(tr.id, { rpe, feedback })
                refresh()
                setSelected(null)
              }
            : undefined}
          onUncomplete={adapter.uncompleteTraining
            ? async tr => {
                await adapter.uncompleteTraining!(tr.id)
                refresh()
                setSelected(null)
              }
            : undefined}
        />
      )}
    </div>
  )
}
