import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ChevronRight, Dumbbell } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Avatar } from '../../components/ui/Avatar'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { TrainingCalendar } from '../../components/training-calendar/TrainingCalendar'
import { coachAdapter } from '../../components/training-calendar/adapter'
import { GoalsBoard } from '../../components/goals/GoalsBoard'
import { MaterialsBoard } from '../../components/materials/MaterialsBoard'
import { TrainingStatsPanel } from '../../components/training-stats/TrainingStatsPanel'
import type { AthleteSummary } from '../../types'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'

/**
 * Coach's entry point: pick a client, then their calendar takes over the whole tab.
 * <p>
 * The selection is lifted to AdminPage — the same idiom the trainings tab already uses — so the
 * library and template sections below can disappear while one person's plan is open, instead of
 * hanging under it and looking like they belong to that person.
 */
export function AdminAthletes({ openAthlete, onOpen }: {
  openAthlete: AthleteSummary | null
  onOpen: (athlete: AthleteSummary | null) => void
}) {
  const { t } = useTranslation('calendar')

  // Memoised because <TrainingCalendar/> keys its own memos off the adapter's identity: built inline
  // it would be a new object every render, and every one of those memos would recompute for nothing.
  const adapter = useMemo(
    () => (openAthlete ? coachAdapter(openAthlete.id) : null),
    [openAthlete],
  )

  const athletesQuery = useQuery({
    queryKey: ['admin', 'athletes'],
    queryFn: () => adminApi.getAthletes(),
    // Roster counters (unread, overtraining) live here, so this refetches on focus rather than
    // waiting out the global 5 minutes — but with a 30 s floor, because the coach's panel alt-tabs
    // constantly and this bucket is rationed at 60/min.
    staleTime: SHORT_STALE_MS,
    refetchOnWindowFocus: true,
    refetchOnMount: 'always',
  })

  if (openAthlete && adapter) {
    return (
      <div>
        <button
          type="button"
          onClick={() => onOpen(null)}
          className="mb-4 flex items-center gap-2 text-sm text-surface-400 transition-colors hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" />
          {t('athletes.back')}
        </button>
        <div className="mb-6 flex items-center gap-3">
          <Avatar src={openAthlete.avatarUrl} name={`${openAthlete.firstName} ${openAthlete.lastName}`}
            className="h-12 w-12" />
          <div>
            <h2 className="text-display text-2xl text-white">
              {openAthlete.firstName} {openAthlete.lastName}
            </h2>
            <p className="text-sm text-surface-400">{openAthlete.email}</p>
          </div>
        </div>
        <div className="space-y-6">
          <GoalsBoard athleteId={openAthlete.id} />
          <MaterialsBoard athleteId={openAthlete.id} />
          <TrainingCalendar adapter={adapter} />
          <TrainingStatsPanel athleteId={openAthlete.id} />
        </div>
      </div>
    )
  }

  return (
    <section>
      <div className="mb-4 flex items-center gap-2">
        <Dumbbell className="h-6 w-6 text-surface-400" />
        <div>
          <h2 className="text-display text-2xl text-white">{t('athletes.title')}</h2>
          <p className="text-sm text-surface-400">{t('athletes.subtitle')}</p>
        </div>
      </div>

      {athletesQuery.isLoading ? (
        <LoadingSpinner />
      ) : athletesQuery.isError ? (
        <QueryError error={athletesQuery.error as Error} onRetry={() => athletesQuery.refetch()} />
      ) : athletesQuery.data && athletesQuery.data.length === 0 ? (
        <p className="text-sm text-surface-500">{t('athletes.empty')}</p>
      ) : (
        <ul className="space-y-2">
          {athletesQuery.data?.map(athlete => (
            <li key={athlete.id}>
              <button
                type="button"
                onClick={() => onOpen(athlete)}
                className="flex w-full items-center gap-3 rounded-2xl border border-white/[0.07] bg-surface-900 px-4 py-3.5 text-left transition-colors hover:border-white/25"
              >
                <Avatar src={athlete.avatarUrl} name={`${athlete.firstName} ${athlete.lastName}`}
                  className="h-9 w-9" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-surface-100">
                    {athlete.firstName} {athlete.lastName}
                  </span>
                  <span className="block truncate text-sm text-surface-400">{athlete.email}</span>
                </span>
                {athlete.unreadCount > 0 && (
                  <span
                    aria-label={t('athletes.unread', { count: athlete.unreadCount })}
                    className="inline-flex min-w-6 shrink-0 items-center justify-center rounded-full bg-white px-2 py-0.5 text-sm font-semibold text-surface-950"
                  >
                    {athlete.unreadCount}
                  </span>
                )}
                <ChevronRight className="h-4 w-4 shrink-0 text-surface-500" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
