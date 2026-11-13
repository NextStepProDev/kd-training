import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { format, addDays } from 'date-fns'
import { TrainingDetailModal } from './TrainingDetailModal'
import { hasTrainingStarted } from './trainingStart'
import type { TrainingCalendarAdapter } from './adapter'
import type { PersonalTraining } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function entry(date: string, startTime: string | null = null): PersonalTraining {
  return {
    id: 't1', kind: 'TRAINING', date, startTime, endTime: null,
    title: 'Siła', description: null, targetCalories: null, status: 'PLANNED',
    completedAt: null, feedback: null, rpe: null,
    createdByAdmin: true, lastModifiedByAdmin: true,
    unread: false, commentCount: 0, attachments: [],
    version: 0, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
  }
}

describe('hasTrainingStarted', () => {
  const now = new Date(2027, 2, 10, 12, 0) // 10 March 2027, 12:00 local

  it('shouldTreatAnUntimedTrainingAsStartedFromMidnightOfItsDay', () => {
    expect(hasTrainingStarted(entry('2027-03-10'), now)).toBe(true)
    expect(hasTrainingStarted(entry('2027-03-11'), now)).toBe(false)
  })

  it('shouldWaitForTheHourOfATimedTraining', () => {
    expect(hasTrainingStarted(entry('2027-03-10', '11:59:00'), now)).toBe(true)
    expect(hasTrainingStarted(entry('2027-03-10', '12:00:00'), now)).toBe(true)
    expect(hasTrainingStarted(entry('2027-03-10', '18:00:00'), now)).toBe(false)
  })
})

describe('TrainingDetailModal — ticking off', () => {
  function renderFor(training: PersonalTraining) {
    const adapter: TrainingCalendarAdapter = {
      role: 'athlete', athleteId: 'a1', rangeKey: (from, to) => ['test', from, to],
      fetchRange: vi.fn(), createTraining: vi.fn(), updateTraining: vi.fn(),
      deleteTraining: vi.fn(), duplicateTraining: vi.fn(), pasteTraining: vi.fn(),
      getComments: vi.fn().mockResolvedValue([]), addComment: vi.fn(),
      deleteCommentPhoto: vi.fn(), markSeen: vi.fn(), dismissDeletions: vi.fn(),
    }
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TrainingDetailModal training={training} adapter={adapter} onClose={vi.fn()} onEdit={vi.fn()}
          onDelete={vi.fn()} onDuplicate={vi.fn()} onComplete={vi.fn()} />
      </QueryClientProvider>,
    )
  }

  it('shouldNotOfferTheCompletionFormBeforeTheTrainingStarts', () => {
    // Found in manual testing: the form was there for next week's session, and only the server's
    // refusal said it could not be ticked off yet — after the client had rated it.
    renderFor(entry(format(addDays(new Date(), 2), 'yyyy-MM-dd')))

    expect(screen.queryByRole('button', { name: 'detail.markDone' })).toBeNull()
    expect(screen.getByText('detail.completeLater')).toBeInTheDocument()
  })

  it('shouldSayWhenATaskCanBeTickedOffWithoutMentioningEffort', () => {
    renderFor({ ...entry(format(addDays(new Date(), 2), 'yyyy-MM-dd')), kind: 'TASK' })

    expect(screen.getByText('detail.completeTaskLater')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'detail.markTaskDone' })).toBeNull()
  })

  it('shouldOfferItOnceTheTrainingHasStarted', () => {
    renderFor(entry(format(addDays(new Date(), -1), 'yyyy-MM-dd')))

    expect(screen.getByRole('button', { name: 'detail.markDone' })).toBeInTheDocument()
    expect(screen.queryByText('detail.completeLater')).toBeNull()
  })
})
