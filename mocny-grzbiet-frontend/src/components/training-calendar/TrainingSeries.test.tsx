import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingFormModal } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import type { TrainingCalendarAdapter } from './adapter'
import type { PersonalTraining } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/notes', () => ({
  notesApi: { get: vi.fn().mockResolvedValue({ body: '', updatedAt: null }), save: vi.fn(), remove: vi.fn(), markers: vi.fn() },
  noteKey: () => ['admin', 'notes', 'training', 't1'],
  notePath: () => '/admin/notes/training/t1',
  NOTES_KEY_PREFIX: ['admin', 'notes'],
}))

const training: PersonalTraining = {
  id: 't1', kind: 'TRAINING', date: '2027-03-10', startTime: null, endTime: null,
  title: 'Siła', description: null, targetCalories: null, status: 'PLANNED',
  completedAt: null, feedback: null, rpe: null,
  createdByAdmin: true, lastModifiedByAdmin: true,
  unread: false, commentCount: 0, attachments: [], seriesId: 's1',
  version: 0, createdAt: '2027-03-01T10:00:00Z', updatedAt: '2027-03-01T10:00:00Z',
}

function renderForm(props: { canRepeat: boolean; training?: PersonalTraining | null }) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  render(
    <TrainingFormModal training={props.training ?? null} date="2027-03-10" onClose={vi.fn()}
      onSubmit={onSubmit} onConflictRefresh={vi.fn()} canAttach={false} canRepeat={props.canRepeat} />,
  )
  return onSubmit
}

describe('Weekly series — creating', () => {
  it('shouldSendTheEndDateWhenTheCoachTicksRepeat', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ canRepeat: true })

    await user.type(screen.getByLabelText('form.title'), 'Siła')
    await user.click(screen.getByLabelText('form.repeatWeekly'))
    // Three weeks after the start by default: four occurrences.
    expect(screen.getByLabelText('form.repeatUntil')).toHaveValue('2027-03-31')
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ repeatWeeklyUntil: '2027-03-31' })))
  })

  it('shouldAskForTheEndDateInsteadOfSendingAnEmptyOne', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ canRepeat: true })

    await user.type(screen.getByLabelText('form.title'), 'Siła')
    await user.click(screen.getByLabelText('form.repeatWeekly'))
    await user.clear(screen.getByLabelText('form.repeatUntil'))
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    expect(await screen.findByText('form.repeatUntilRequired')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shouldSendNoEndDateWithoutTheTick', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ canRepeat: true })

    await user.type(screen.getByLabelText('form.title'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('repeatWeeklyUntil')
  })

  it('shouldNotOfferRepeatToTheClient', () => {
    renderForm({ canRepeat: false })
    expect(screen.queryByLabelText('form.repeatWeekly')).not.toBeInTheDocument()
  })

  it('shouldNotOfferRepeatWhenEditingAnExistingEntry', () => {
    renderForm({ canRepeat: true, training })
    expect(screen.queryByLabelText('form.repeatWeekly')).not.toBeInTheDocument()
  })
})

describe('Weekly series — deleting', () => {
  function renderDetail(entry: PersonalTraining) {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    const adapter: TrainingCalendarAdapter = {
      role: 'coach', athleteId: 'a1', rangeKey: (from, to) => ['test', from, to],
      fetchRange: vi.fn(), createTraining: vi.fn(), updateTraining: vi.fn(),
      deleteTraining: vi.fn(), duplicateTraining: vi.fn(), pasteTraining: vi.fn(),
      getComments: vi.fn().mockResolvedValue([]), addComment: vi.fn(),
      deleteCommentPhoto: vi.fn(), markSeen: vi.fn(), dismissDeletions: vi.fn(),
    }
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TrainingDetailModal training={entry} adapter={adapter} onClose={vi.fn()} onEdit={vi.fn()}
          onDelete={onDelete} onDuplicate={vi.fn()} />
      </QueryClientProvider>,
    )
    return onDelete
  }

  async function openDelete(user: ReturnType<typeof userEvent.setup>) {
    // The footer button and the dialog's confirm share a label; the footer one comes first.
    await user.click(screen.getAllByRole('button', { name: 'detail.delete' })[0])
  }

  it('shouldDeleteTheFollowingOccurrencesWhenAsked', async () => {
    const user = userEvent.setup()
    const onDelete = renderDetail(training)

    await openDelete(user)
    await user.click(screen.getByLabelText('detail.deleteFollowing'))
    const buttons = screen.getAllByRole('button', { name: 'detail.delete' })
    await user.click(buttons[buttons.length - 1])

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(training, true, false))
  })

  it('shouldDeleteOnlyThisOneByDefault', async () => {
    const user = userEvent.setup()
    const onDelete = renderDetail(training)

    await openDelete(user)
    const buttons = screen.getAllByRole('button', { name: 'detail.delete' })
    await user.click(buttons[buttons.length - 1])

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(training, false, false))
  })

  it('shouldNotOfferTheFollowingOnesForAOneOff', async () => {
    const user = userEvent.setup()
    renderDetail({ ...training, seriesId: null })

    await openDelete(user)
    expect(screen.queryByLabelText('detail.deleteFollowing')).not.toBeInTheDocument()
  })
})
