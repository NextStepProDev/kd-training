import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { Award, Flame, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { addDays, format, parseISO, startOfWeek, subDays } from 'date-fns'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { HEAT_CLASSES, HEAT_LEGEND, heatLevel } from './heatmapScale'
import { todayIso } from '../../utils/calendarRange'
import type { TrainingStats } from '../../types'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'

/** Badge thresholds are computed here from the totals — the server sends numbers, not decorations. */
const TOTAL_MILESTONES = [10, 25, 50, 100, 250]
const STREAK_MILESTONES = [4, 8, 12]

const HEATMAP_WEEKS = 53

/**
 * Statistics under the calendar.
 * <p>
 * Same numbers for both roles; only the overtraining warning differs, and it differs because the
 * server omits the field for the client rather than because this component hides it.
 */
export function TrainingStatsPanel({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const isCoach = athleteId != null

  const statsQuery = useQuery({
    queryKey: isCoach ? ['admin', 'training-stats', athleteId] : ['user', 'my-training', 'stats'],
    queryFn: () => (isCoach ? adminApi.getAthleteStats(athleteId) : myTrainingApi.getStats()),
    // Unticking a session has to move these numbers at once, or nobody trusts them — and it does,
    // because the mutation invalidates this key and invalidation ignores staleTime. The 30 s floor
    // only stops a window focus from refetching numbers nobody has touched.
    staleTime: SHORT_STALE_MS,
    refetchOnMount: 'always',
    // Nothing but the person in the key, so keeping the previous result could only ever mean showing
    // one client's streak and attendance under another client's name.
    placeholderData: undefined,
  })

  if (statsQuery.isLoading) return <LoadingSpinner />
  const stats = statsQuery.data
  if (!stats) return null

  const trend = stats.thisMonthCount - stats.prevMonthCount

  return (
    <section className="space-y-4">
      <h3 className="text-sm font-medium text-surface-300">{t('stats.title')}</h3>

      {stats.overtraining && (
        // Coach-only: a page telling a client they are overreaching turns a conversation starter
        // into a verdict.
        <p role="alert" className="flex items-start gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-200">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
          {t('stats.overtraining')}
        </p>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Tile label={t('stats.thisMonth')} value={String(stats.thisMonthCount)}
          hint={trend === 0 ? undefined : (
            <span className={clsx('inline-flex items-center gap-0.5', trend > 0 ? 'text-emerald-400' : 'text-surface-400')}>
              {trend > 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
              {trend > 0 ? `+${trend}` : trend}
            </span>
          )} />
        <Tile label={t('stats.total')} value={String(stats.totalCount)} />
        <Tile label={t('stats.streak')} value={t('stats.weeks', { count: stats.currentStreakWeeks })}
          hint={stats.bestStreakWeeks > 0
            ? <span className="text-surface-400">{t('stats.best', { count: stats.bestStreakWeeks })}</span>
            : undefined} />
        <Tile
          label={t('stats.attendance')}
          value={stats.attendancePercent == null ? '—' : `${stats.attendancePercent}%`}
          hint={<span className="text-surface-400">{t('stats.attendanceWindow')}</span>}
        />
      </div>

      <Heatmap heatmap={stats.heatmap} label={t('stats.heatmap')} legendLabel={t('stats.heatmapLegend')} />

      <div className="grid gap-3 sm:grid-cols-2">
        <RpeDistribution stats={stats} />
        {stats.avgPerMonth != null && <MonthlyAverage value={stats.avgPerMonth} />}
        <Tasks stats={stats} />
      </div>

      <Badges stats={stats} />
    </section>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint?: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-surface-100">{value}</p>
      {hint && <p className="mt-0.5 text-xs">{hint}</p>}
    </div>
  )
}

function Heatmap({ heatmap, label, legendLabel }: {
  heatmap: Record<string, number>
  label: string
  legendLabel: string
}) {
  // Columns are ISO weeks, so each row is one weekday and the shape reads like a calendar.
  const weeks = useMemo(() => {
    const end = parseISO(todayIso())
    const firstMonday = startOfWeek(subDays(end, (HEATMAP_WEEKS - 1) * 7), { weekStartsOn: 1 })
    return Array.from({ length: HEATMAP_WEEKS }, (_, w) =>
      Array.from({ length: 7 }, (_, d) => format(addDays(firstMonday, w * 7 + d), 'yyyy-MM-dd')))
  }, [])

  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <p className="text-xs text-surface-400">{label}</p>
        <div className="flex items-center gap-1.5 text-[11px] text-surface-500">
          <span>{legendLabel}</span>
          {HEAT_LEGEND.map(({ level, label: swatch }) => (
            <span key={level} className="flex items-center gap-0.5">
              <span className={clsx('inline-block h-2.5 w-2.5 rounded-sm', HEAT_CLASSES[level])} />
              {swatch}
            </span>
          ))}
        </div>
      </div>
      <div className="overflow-x-auto">
        <div className="flex gap-0.5">
          {weeks.map((week, i) => (
            <div key={i} className="flex flex-col gap-0.5">
              {week.map(day => (
                <span
                  key={day}
                  title={`${day}: ${heatmap[day] ?? 0}`}
                  className={clsx('h-2.5 w-2.5 rounded-sm', HEAT_CLASSES[heatLevel(heatmap[day] ?? 0)])}
                />
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function RpeDistribution({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  const { light, medium, hard } = stats.rpeDistribution
  const total = light + medium + hard

  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{t('stats.intensity')}</p>
      {total === 0 ? (
        <p className="mt-2 text-sm text-surface-500">{t('stats.noRatings')}</p>
      ) : (
        <>
          <div className="mt-2 flex h-3 overflow-hidden rounded-full">
            <span className="bg-emerald-600" style={{ width: `${(light / total) * 100}%` }} />
            <span className="bg-amber-600" style={{ width: `${(medium / total) * 100}%` }} />
            <span className="bg-rose-600" style={{ width: `${(hard / total) * 100}%` }} />
          </div>
          <div className="mt-2 flex flex-wrap gap-3 text-xs text-surface-400">
            <span>{t('stats.light')}: {light}</span>
            <span>{t('stats.medium')}: {medium}</span>
            <span>{t('stats.hard')}: {hard}</span>
          </div>
        </>
      )}
      {stats.avgRpeRecent != null && (
        <p className="mt-2 text-xs text-surface-400">
          {t('stats.avgRpeRecent', { value: stats.avgRpeRecent })}
        </p>
      )}
    </div>
  )
}

function MonthlyAverage({ value }: { value: number }) {
  const { t } = useTranslation('calendar')
  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{t('stats.monthlyAverage')}</p>
      <p className="mt-2 text-sm text-surface-100">{t('stats.avgPerMonth', { value })}</p>
    </div>
  )
}

/**
 * Tasks, on their own. Nothing above this card counts them — a held calorie ceiling is not a
 * session, and folding it into the streak would turn "8 tygodni z rzędu" into a sentence about
 * nothing in particular. The card stays hidden entirely until a task has actually come due, so a
 * plan of plain trainings looks exactly as it did before.
 *
 * Every line is a RATIO with its window named in the label. Bare counts here read as a set to be
 * compared — "w tym miesiącu 0" next to "łącznie 37" invites exactly the arithmetic that means
 * nothing, since the two cover different spans. "3 z 4" needs no neighbour to be understood.
 */
function Tasks({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  const { thisMonthDone, thisMonthDue, windowDone, windowDue, completionPercent } = stats.tasks

  if (windowDue === 0 && thisMonthDue === 0) return null

  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{t('stats.tasks')}</p>

      <div className="mt-2 flex items-baseline justify-between text-sm">
        <span className="text-surface-400">{t('stats.tasksKeptWindow')}</span>
        <span className="text-surface-100">
          {/* A dash, not 0 of 0: nothing came due, which is not a score. */}
          {windowDue === 0 ? '—' : t('stats.tasksRatio', { done: windowDone, due: windowDue })}
        </span>
      </div>
      {completionPercent != null && (
        <div className="mt-2 flex items-center gap-2">
          <span className="h-3 flex-1 overflow-hidden rounded-full bg-surface-800">
            <span className="block h-full bg-emerald-600" style={{ width: `${completionPercent}%` }} />
          </span>
          <span className="text-xs text-surface-400">{completionPercent}%</span>
        </div>
      )}

      <div className="mt-3 flex items-baseline justify-between text-sm">
        <span className="text-surface-400">{t('stats.tasksThisMonth')}</span>
        <span className="text-surface-100">
          {thisMonthDue === 0 ? '—' : t('stats.tasksRatio', { done: thisMonthDone, due: thisMonthDue })}
        </span>
      </div>

      <p className="mt-2 text-xs text-surface-500">{t('stats.tasksSeparate')}</p>
    </div>
  )
}

function Badges({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  const earnedTotals = TOTAL_MILESTONES.filter(m => stats.totalCount >= m)
  const earnedStreaks = STREAK_MILESTONES.filter(m => stats.bestStreakWeeks >= m)
  const nextTotal = TOTAL_MILESTONES.find(m => m > stats.totalCount)

  if (earnedTotals.length === 0 && earnedStreaks.length === 0 && nextTotal == null) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {earnedTotals.map(m => (
        <span key={`t${m}`}
          className="inline-flex items-center gap-1 rounded-full bg-primary-500/15 px-2 py-0.5 text-xs text-primary-300">
          <Award className="h-3.5 w-3.5" />
          {t('stats.badgeTotal', { count: m })}
        </span>
      ))}
      {earnedStreaks.map(m => (
        <span key={`s${m}`}
          className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2 py-0.5 text-xs text-amber-300">
          <Flame className="h-3.5 w-3.5" />
          {t('stats.badgeStreak', { count: m })}
        </span>
      ))}
      {nextTotal != null && (
        <span className="text-xs text-surface-500">
          {t('stats.nextBadge', { count: nextTotal - stats.totalCount })}
        </span>
      )}
    </div>
  )
}
