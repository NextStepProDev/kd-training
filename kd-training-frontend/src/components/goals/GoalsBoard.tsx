import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  Check, Flag, Medal, Mountain, Pencil, Plus, Target, Trash2, Trophy, Zap,
  type LucideIcon,
} from 'lucide-react'
import { differenceInCalendarDays, parseISO } from 'date-fns'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { formatLongDate, todayIso } from '../../utils/calendarRange'
import type { AthleteGoal, GoalHorizon, AthleteGoals } from '../../types'
import { inputClass } from '../../utils/fieldClass'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'
import { DateInput } from '../ui/DateInput'

const HORIZONS: GoalHorizon[] = ['SHORT', 'MEDIUM', 'LONG']

/**
 * Six cards in a grid all look alike, and the horizon is the one thing telling them apart. The
 * marking is an edge stripe and an icon, never a fill: the card's surface already says whether the
 * goal is reached, overdue or running, and two colour systems on one rectangle read as neither.
 * The icon carries the same distinction for anyone who does not separate the hues.
 */
const HORIZON_STYLE: Record<GoalHorizon, { stripe: string; accent: string; Icon: LucideIcon }> = {
  SHORT: { stripe: 'border-l-primary-500', accent: 'text-primary-400', Icon: Zap },
  MEDIUM: { stripe: 'border-l-sky-500', accent: 'text-sky-400', Icon: Flag },
  LONG: { stripe: 'border-l-violet-500', accent: 'text-violet-400', Icon: Mountain },
}

/**
 * Medals, and only here. On the board a horizon is a category — colour there has to separate three
 * things, so it uses hues that sit far apart. In the chest every goal is already won, so the
 * horizon can mean what a medal means: gold for the long haul, bronze for the sprint. Nothing
 * outside this modal wears these tones.
 */
const MEDAL: Record<GoalHorizon, { text: string; rule: string; stripe: string }> = {
  LONG: { text: 'text-medal-gold', rule: 'border-medal-gold/40', stripe: 'border-l-medal-gold/70' },
  MEDIUM: { text: 'text-medal-silver', rule: 'border-medal-silver/40', stripe: 'border-l-medal-silver/70' },
  SHORT: { text: 'text-medal-bronze', rule: 'border-medal-bronze/40', stripe: 'border-l-medal-bronze/70' },
}

/** Gold first: the case reads as a ranking, not as a list of enum values. */
const MEDAL_ORDER: GoalHorizon[] = ['LONG', 'MEDIUM', 'SHORT']

/** The horizon caption as it appears on every card and in the trophy case. */
function HorizonLabel({ horizon, className }: { horizon: GoalHorizon; className?: string }) {
  const { t } = useTranslation('calendar')
  const { accent, Icon } = HORIZON_STYLE[horizon]
  return (
    <p className={clsx('flex items-center gap-1.5 text-xs uppercase tracking-wide', accent, className)}>
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {t(`goals.horizon.${horizon}`)}
    </p>
  )
}

/** A freshly reached goal keeps its card for a week before the slot goes back to "set a new one". */
const CELEBRATION_DAYS = 7


/**
 * Three goal cards above the calendar, plus the trophy case behind them.
 * <p>
 * The coach writes; the client reads. Which of the two this is comes from `athleteId` being present
 * — the same shape as the calendar adapter, so there is no second notion of "am I allowed".
 */
export function GoalsBoard({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const isCoach = athleteId != null

  const [editing, setEditing] =
    useState<{ goal: AthleteGoal | null; horizon: GoalHorizon } | null>(null)
  const [toDelete, setToDelete] = useState<AthleteGoal | null>(null)
  const [toAchieve, setToAchieve] = useState<AthleteGoal | null>(null)
  const [chestOpen, setChestOpen] = useState(false)

  const goalsQuery = useQuery({
    queryKey: isCoach ? ['admin', 'goals', athleteId] : ['user', 'my-training', 'goals'],
    queryFn: () => (isCoach ? adminApi.getAthleteGoals(athleteId) : myTrainingApi.getGoals()),
    staleTime: SHORT_STALE_MS,
    refetchOnMount: 'always',
    // The key holds nothing but the person, so there is no page to smooth over and the global
    // keepPreviousData has nothing to offer here except the chance to show one client's goals under
    // another client's name for a frame.
    placeholderData: undefined,
  })

  const invalidate = () => queryClient.invalidateQueries({
    queryKey: isCoach ? ['admin', 'goals', athleteId] : ['user', 'my-training', 'goals'],
  })

  // Deleting is the one action on this board that fires straight from a click, with no form of
  // its own to carry a message. Without this line a refusal closed the dialog and left
  // the goal sitting there — indistinguishable from a dead button.
  const [actionError, setActionError] = useState<string | null>(null)

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteAthleteGoal(id),
    onMutate: () => setActionError(null),
    onSuccess: () => { setToDelete(null); invalidate() },
    onError: (e: Error) => setActionError(e.message),
  })

  const goals: AthleteGoals = goalsQuery.data ?? { active: [], achieved: [] }
  const today = todayIso()

  // A client whose coach has not set any goals should see nothing here, not three empty boxes
  // telling them so three times. The coach keeps the empty cards — for them they are the way in.
  if (!isCoach && goals.active.length === 0 && goals.achieved.length === 0) {
    return null
  }

  /** Achieved within the celebration window still occupies its horizon's card. */
  const celebrating = goals.achieved.filter(g =>
    g.achievedAt != null && differenceInCalendarDays(parseISO(today), parseISO(g.achievedAt)) <= CELEBRATION_DAYS)

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-sm font-medium text-surface-300">
          <Target className="h-4 w-4 text-primary-400" />
          {t('goals.title')}
        </h3>
        {goals.achieved.length > 0 && (
          <button type="button" onClick={() => setChestOpen(true)}
            className="inline-flex items-center gap-1.5 text-sm text-surface-400 transition-colors hover:text-primary-400">
            <Trophy className="h-4 w-4" />
            {t('goals.trophyCase', { count: goals.achieved.length })}
          </button>
        )}
      </div>

      {actionError && (
        <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{actionError}</p>
      )}

      <div className="grid gap-2 sm:grid-cols-3">
        {HORIZONS.map(horizon => {
          const active = goals.active.find(g => g.horizon === horizon)
          const fresh = celebrating.find(g => g.horizon === horizon)
          return (
            <GoalCard
              key={horizon}
              horizon={horizon}
              goal={active ?? fresh ?? null}
              achieved={active == null && fresh != null}
              isCoach={isCoach}
              onEdit={() => setEditing({ goal: active ?? null, horizon })}
              onDelete={() => active && setToDelete(active)}
              onAchieve={() => active && setToAchieve(active)}
            />
          )
        })}
      </div>

      {editing && (
        <GoalFormModal
          key={editing.goal?.id ?? `new-${editing.horizon}`}
          athleteId={athleteId!}
          goal={editing.goal}
          horizon={editing.horizon}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); invalidate() }}
        />
      )}

      {toAchieve && (
        <AchieveGoalModal
          goal={toAchieve}
          onClose={() => setToAchieve(null)}
          onSaved={() => { setToAchieve(null); invalidate() }}
        />
      )}

      {chestOpen && (
        <Modal isOpen onClose={() => setChestOpen(false)} title={t('goals.trophyTitle')} size="xl">
          <TrophyCase goals={goals.achieved} />
        </Modal>
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title={t('goals.deleteTitle')}
        message={t('goals.deleteMessage')}
        confirmLabel={t('goals.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}

function GoalCard({
  horizon, goal, achieved, isCoach, onEdit, onDelete, onAchieve,
}: {
  horizon: GoalHorizon
  goal: AthleteGoal | null
  achieved: boolean
  isCoach: boolean
  onEdit: () => void
  onDelete: () => void
  onAchieve: () => void
}) {
  const { t } = useTranslation('calendar')
  const today = todayIso()
  const daysLeft = goal?.targetDate
    ? differenceInCalendarDays(parseISO(goal.targetDate), parseISO(today))
    : null

  if (!goal) {
    return (
      <div className={clsx(
        'rounded-lg border border-dashed border-surface-700 border-l-2 p-3',
        HORIZON_STYLE[horizon].stripe,
      )}>
        <HorizonLabel horizon={horizon} className="opacity-70" />
        {isCoach ? (
          <button type="button" onClick={onEdit}
            className="mt-2 inline-flex items-center gap-1 text-sm text-surface-400 transition-colors hover:text-primary-400">
            <Plus className="h-4 w-4" />
            {t('goals.add')}
          </button>
        ) : (
          <p className="mt-2 text-sm text-surface-500">{t('goals.none')}</p>
        )}
      </div>
    )
  }

  return (
    <div className={clsx(
      'rounded-lg border border-l-2 p-3',
      HORIZON_STYLE[horizon].stripe,
      achieved
        ? 'border-emerald-500/40 bg-emerald-900/20'
        : daysLeft != null && daysLeft < 0
          ? 'border-surface-800 bg-surface-900/60 opacity-70'
          : 'border-surface-800 bg-surface-900/60',
    )}>
      <div className="flex items-start justify-between gap-2">
        <span className="flex items-center gap-1.5">
          {/* The same dot the calendar cards use. Without it the badge counted a new goal and
              nothing on the page said which one — a number with nowhere to go. */}
          {goal.unread && (
            <span aria-label={t('goals.unread')} title={t('goals.unread')}
              className="h-2 w-2 shrink-0 rounded-full bg-rose-400" />
          )}
          <HorizonLabel horizon={horizon} />
        </span>
        {achieved && <Trophy className="h-4 w-4 shrink-0 text-emerald-400" />}
      </div>
      <p className="mt-1 text-sm text-surface-100">{goal.content}</p>

      {goal.targetDate && !achieved && (
        <p className={clsx('mt-1 text-xs',
          daysLeft != null && daysLeft < 0 ? 'text-surface-500'
            : daysLeft === 0 ? 'text-amber-400' : 'text-surface-400')}>
          {daysLeft != null && daysLeft < 0
            ? t('goals.overdue')
            : daysLeft === 0
              ? t('goals.dueToday')
              : t('goals.daysLeft', { count: daysLeft ?? 0 })}
        </p>
      )}
      {achieved && goal.achievedAt && (
        <p className="mt-1 text-xs text-emerald-400">{formatLongDate(goal.achievedAt)}</p>
      )}

      {isCoach && !achieved && (
        <div className="mt-2 flex gap-1">
          <Button variant="ghost" size="sm" aria-label={t('goals.achieve')} onClick={onAchieve}>
            <Check className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" aria-label={t('goals.edit')} onClick={onEdit}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" aria-label={t('goals.delete')} onClick={onDelete}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      )}

      {isCoach && achieved && (
        <div className="mt-2 flex flex-wrap gap-1">
          {/* The week of celebration is for the client, and it must not hold the coach up. Without
              this, closing every goal at once leaves them with six trophies and nowhere to write
              the next thing until the window runs out. Setting one sends the trophy to the case. */}
          <Button variant="ghost" size="sm" onClick={onEdit}>
            <Plus className="mr-1.5 h-4 w-4" />
            {t('goals.addNext')}
          </Button>
        </div>
      )}
    </div>
  )
}

/**
 * Everything won so far, one column per horizon, gold to bronze.
 * <p>
 * A column tells you at a glance how the person's year went at each range — which is the question
 * the case is for. The medal lives in the column heading rather than on every row: repeating a
 * badge down a list turns a record into wallpaper.
 */
function TrophyCase({ goals }: { goals: AthleteGoal[] }) {
  const { t } = useTranslation('calendar')

  return (
    <div className="space-y-4">
      <p className="text-sm text-surface-400">{t('goals.trophySubtitle')}</p>

      <div className="grid gap-5 sm:grid-cols-3">
        {MEDAL_ORDER.map(horizon => {
          const medal = MEDAL[horizon]
          // Newest first: the last thing won is the thing worth reading first.
          const won = goals
            .filter(g => g.horizon === horizon)
            .sort((a, b) => (b.achievedAt ?? '').localeCompare(a.achievedAt ?? ''))

          return (
            <div key={horizon}>
              <div className={clsx('flex items-center gap-1.5 border-b pb-1.5', medal.rule, medal.text)}>
                <Medal className="h-4 w-4 shrink-0" aria-hidden="true" />
                <h4 className="text-xs font-medium uppercase tracking-wide">
                  {t(`goals.horizon.${horizon}`)}
                </h4>
                {won.length > 0 && (
                  <span className="ml-auto text-xs font-semibold [font-variant-numeric:tabular-nums]">
                    {won.length}
                  </span>
                )}
              </div>

              {won.length === 0 ? (
                <p className="mt-2 text-xs text-surface-500">{t('goals.trophyEmpty')}</p>
              ) : (
                <ul className="mt-2 space-y-1.5">
                  {won.map(goal => (
                    <li key={goal.id}
                      className={clsx('rounded-lg border border-surface-800 border-l-2 bg-surface-900/70 px-3 py-2',
                        medal.stripe)}>
                      <p className="text-sm text-surface-100">{goal.content}</p>
                      <p className="mt-0.5 text-xs text-surface-500 [font-variant-numeric:tabular-nums]">
                        {goal.achievedAt && formatLongDate(goal.achievedAt)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function GoalFormModal({
  athleteId, goal, horizon, onClose, onSaved,
}: {
  athleteId: string
  goal: AthleteGoal | null
  horizon: GoalHorizon
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  const [content, setContent] = useState(goal?.content ?? '')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!content.trim() || saving) return
    setError(null)
    setSaving(true)
    try {
      const body = {
        horizon,
        content: content.trim(),
        targetDate: targetDate || null,
      }
      if (goal) await adminApi.updateAthleteGoal(goal.id, body)
      else await adminApi.createAthleteGoal(athleteId, body)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={goal ? t('goals.editTitle') : t('goals.addTitle')}>
      <form onSubmit={submit} className="space-y-4">
        {/* The horizon is fixed: a short-term goal that became long-term is a different goal. */}
        <p className="text-sm text-surface-400">{t(`goals.horizon.${horizon}`)}</p>

        <div>
          <label htmlFor="goal-content" className="mb-1 block text-sm text-surface-300">
            {t('goals.content')}
          </label>
          <textarea id="goal-content" className={`${inputClass} min-h-24`} value={content}
            maxLength={500} onChange={e => setContent(e.target.value)} autoFocus />
        </div>

        <div>
          <label htmlFor="goal-target" className="mb-1 block text-sm text-surface-300">
            {t('goals.targetDate')}
          </label>
          <DateInput id="goal-target" className={inputClass} value={targetDate}
            onChange={setTargetDate} />
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving}
            disabled={!content.trim()}>
            {t('form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function AchieveGoalModal({
  goal, onClose, onSaved,
}: {
  goal: AthleteGoal
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  // Defaults to today but is back-datable: the coach usually notices some days later.
  const [date, setDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (saving) return
    setError(null)
    setSaving(true)
    try {
      await adminApi.achieveAthleteGoal(goal.id, date)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={t('goals.achieveTitle')}>
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-surface-200">{goal.content}</p>
        <div>
          <label htmlFor="achieve-date" className="mb-1 block text-sm text-surface-300">
            {t('goals.achievedOn')}
          </label>
          <DateInput id="achieve-date" className={inputClass} value={date}
            max={todayIso()} onChange={setDate} />
        </div>
        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving}>{t('goals.achieve')}</Button>
        </div>
      </form>
    </Modal>
  )
}
