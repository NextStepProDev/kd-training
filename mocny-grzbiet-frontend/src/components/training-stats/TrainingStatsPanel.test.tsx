import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingStatsPanel } from './TrainingStatsPanel'
import { myTrainingApi } from '../../api/user'
import type { TrainingStats } from '../../types'

const tMap: Record<string, string> = {
  'stats.tasks': 'Zadania',
  'stats.tasksKeptWindow': 'Utrzymane (90 dni)',
  'stats.tasksThisMonth': 'W tym miesiącu',
  'stats.tasksSeparate': 'Zadania liczą się osobno.',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      key === 'stats.tasksRatio' ? `${opts!.done} z ${opts!.due}` : (tMap[key] ?? key),
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/user', () => ({ myTrainingApi: { getStats: vi.fn() } }))

function stats(tasks: TrainingStats['tasks']): TrainingStats {
  return {
    thisMonthCount: 4, prevMonthCount: 3, totalCount: 20,
    firstActivityDate: '2027-01-05', currentStreakWeeks: 2, bestStreakWeeks: 5,
    avgPerMonth: 4.2, heatmap: {},
    attendancePercent: 90, avgRpeOverall: 6.1, avgRpeRecent: 6.4,
    rpeDistribution: { light: 3, medium: 8, hard: 4 },
    tasks,
  }
}

function renderPanel(value: TrainingStats) {
  vi.mocked(myTrainingApi.getStats).mockResolvedValue(value)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <TrainingStatsPanel athleteId={null} />
    </QueryClientProvider>
  )
}

describe('TrainingStatsPanel — tasks', () => {
  beforeEach(() => vi.clearAllMocks())

  it('gives every task count its denominator and its window', async () => {
    // Bare counts read as a set to be compared, and the two spans differ, so the comparison means
    // nothing: "w tym miesiącu 0" next to "łącznie 37" is the exact confusion this replaced.
    renderPanel(stats({
      thisMonthDone: 3, thisMonthDue: 4, windowDone: 12, windowDue: 15, completionPercent: 80,
    }))

    expect(await screen.findByText('12 z 15')).toBeInTheDocument()
    expect(screen.getByText('3 z 4')).toBeInTheDocument()
    expect(screen.getByText('80%')).toBeInTheDocument()
  })

  it('shows a dash rather than a zero when nothing came due', async () => {
    // A month with no tasks set is not a month of blown ceilings, and 0 cannot tell them apart.
    renderPanel(stats({
      thisMonthDone: 0, thisMonthDue: 0, windowDone: 9, windowDue: 10, completionPercent: 90,
    }))

    expect(await screen.findByText('9 z 10')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('hides the card entirely for a plan of plain trainings', async () => {
    renderPanel(stats({
      thisMonthDone: 0, thisMonthDue: 0, windowDone: 0, windowDue: 0, completionPercent: null,
    }))

    // The training numbers arrive; the task card never does
    expect(await screen.findByText('stats.title')).toBeInTheDocument()
    expect(screen.queryByText('Zadania')).toBeNull()
  })
})
