import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingFormModal } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import { TrainingTile } from './TrainingTile'
import type { PartnerCandidate, TrainingCalendarAdapter } from './adapter'
import type { PersonalTraining } from '../../types'

vi.mock('react-i18next', () => ({
  // Interpolation spelled out, so a test can see which names went into a sentence.
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) => vars?.names ? `${key}[${vars.names}]` : key,
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/notes', () => ({
  notesApi: { get: vi.fn().mockResolvedValue({ body: '', updatedAt: null }), save: vi.fn(), remove: vi.fn(), markers: vi.fn() },
  noteKey: () => ['admin', 'notes', 'training', 't1'],
  notePath: () => '/admin/notes/training/t1',
  NOTES_KEY_PREFIX: ['admin', 'notes'],
}))

const KASIA: PartnerCandidate = { id: 'u-kasia', name: 'Kasia Nowak' }
const OLA: PartnerCandidate = { id: 'u-ola', name: 'Ola Kowalska' }

const groupTraining: PersonalTraining = {
  id: 't1', kind: 'TRAINING', date: '2027-03-10', startTime: '18:00:00', endTime: null,
  title: 'Siła w parze', description: null, targetCalories: null, status: 'PLANNED',
  completedAt: null, feedback: null, rpe: null,
  createdByAdmin: true, lastModifiedByAdmin: true,
  unread: false, commentCount: 0, attachments: [], seriesId: null,
  groupId: 'g1', partners: ['Kasia', 'Ola'],
  version: 0, createdAt: '2027-03-01T10:00:00Z', updatedAt: '2027-03-01T10:00:00Z',
}

function renderForm(props: { partnerOptions?: PartnerCandidate[]; training?: PersonalTraining | null }) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  render(
    <TrainingFormModal training={props.training ?? null} date="2027-03-10" onClose={vi.fn()}
      onSubmit={onSubmit} onConflictRefresh={vi.fn()} canAttach={false} canRepeat
      partnerOptions={props.partnerOptions} />,
  )
  return onSubmit
}

describe('Group training — creating', () => {
  it('shouldSendThePickedPartners', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ partnerOptions: [KASIA, OLA] })

    await user.type(screen.getByLabelText('form.title'), 'Siła w parze')
    await user.click(screen.getByLabelText('Kasia Nowak'))
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ partnerIds: ['u-kasia'] })))
  })

  it('shouldSendNoPartnersForAOneToOne', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ partnerOptions: [KASIA] })

    await user.type(screen.getByLabelText('form.title'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('partnerIds')
  })

  it('shouldDropThePartnersWhenTheEntryBecomesATask', async () => {
    const user = userEvent.setup()
    const onSubmit = renderForm({ partnerOptions: [KASIA] })

    await user.click(screen.getByLabelText('Kasia Nowak'))
    await user.click(screen.getByRole('button', { name: 'form.kind.TASK' }))
    // The server refuses a group task; the picker goes and so does what was picked in it.
    expect(screen.queryByLabelText('Kasia Nowak')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('form.title'), 'Kalorie')
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('partnerIds')
  })

  it('shouldNotOfferPartnersToTheClientOrWhenEditing', () => {
    renderForm({})
    expect(screen.queryByText('group.pickLabel')).not.toBeInTheDocument()
  })

  it('shouldWarnThatAnEditReachesThePartners', () => {
    renderForm({ partnerOptions: [KASIA], training: groupTraining })

    expect(screen.queryByText('group.pickLabel')).not.toBeInTheDocument()
    expect(screen.getByText('group.editHint[Kasia, Ola]')).toBeInTheDocument()
  })
})

describe('Group training — seeing and deleting', () => {
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

  async function confirmDelete(user: ReturnType<typeof userEvent.setup>, onlyThis: boolean) {
    await user.click(screen.getAllByRole('button', { name: 'detail.delete' })[0])
    if (onlyThis) {
      await user.click(screen.getByLabelText('group.deleteOnlyThis[Kasia, Ola]'))
    }
    const buttons = screen.getAllByRole('button', { name: 'detail.delete' })
    await user.click(buttons[buttons.length - 1])
  }

  it('shouldNameThePartners', () => {
    renderDetail(groupTraining)
    expect(screen.getByText('group.with[Kasia, Ola]')).toBeInTheDocument()
  })

  it('shouldCancelForEveryoneByDefault', async () => {
    const user = userEvent.setup()
    const onDelete = renderDetail(groupTraining)

    await confirmDelete(user, false)

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(groupTraining, false, false))
  })

  it('shouldTakeOnlyThisPersonOffWhenAsked', async () => {
    const user = userEvent.setup()
    const onDelete = renderDetail(groupTraining)

    await confirmDelete(user, true)

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(groupTraining, false, true))
  })

  it('shouldNotOfferOnlyThisForAOneToOne', async () => {
    const user = userEvent.setup()
    renderDetail({ ...groupTraining, groupId: null, partners: [] })

    await user.click(screen.getAllByRole('button', { name: 'detail.delete' })[0])
    expect(screen.queryByText(/group\.deleteOnlyThis/)).not.toBeInTheDocument()
  })

  it('shouldShowThePartnersOnTheCardAndAnIconInAMonthCell', () => {
    const labels = {
      onOpen: vi.fn(), copyLabel: 'c', cutLabel: 'x', unreadLabel: 'u', commentsLabel: 'k',
      taskLabel: 't', caloriesLabel: 'kcal', groupLabel: 'Trening wspólny',
    }
    const { unmount } = render(<TrainingTile training={groupTraining} {...labels} />)
    expect(screen.getByText('Kasia, Ola')).toBeInTheDocument()
    unmount()

    render(<TrainingTile training={groupTraining} compact {...labels} />)
    expect(screen.queryByText('Kasia, Ola')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Trening wspólny')).toBeInTheDocument()
  })
})
