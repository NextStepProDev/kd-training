import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingFormModal } from './TrainingFormModal'
import { ApiError } from '../../utils/errors'
import type { PersonalTraining } from '../../types'

const tMap: Record<string, string> = {
  'form.createTitle': 'Nowy trening',
  'form.editTitle': 'Edytuj trening',
  'form.createTaskTitle': 'Nowe zadanie',
  'form.editTaskTitle': 'Edytuj zadanie',
  'form.kindLabel': 'Rodzaj wpisu',
  'form.kind.TRAINING': 'Trening',
  'form.kind.TASK': 'Zadanie',
  'form.kindTrainingHint': 'Odhaczany z oceną wysiłku.',
  'form.kindTaskHint': 'Osobny wpis, odhaczany bez oceny wysiłku.',
  'form.calories': 'Limit kalorii (kcal)',
  'form.caloriesHint': 'Opcjonalnie.',
  'form.caloriesRange': 'Limit kalorii poza zakresem.',
  'form.title': 'Nazwa',
  'form.description': 'Opis',
  'form.setTime': 'Ustaw godzinę',
  'form.startTime': 'Od',
  'form.endTime': 'Do',
  'form.save': 'Zapisz',
  'form.cancel': 'Anuluj',
  'form.refresh': 'Odśwież i spróbuj ponownie',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function existing(over: Partial<PersonalTraining> = {}): PersonalTraining {
  return {
    id: 't1', kind: 'TRAINING', date: '2027-03-10', startTime: null, endTime: null,
    title: 'Siła', description: null, targetCalories: null, status: 'PLANNED',
    completedAt: null, feedback: null, rpe: null,
    createdByAdmin: true, lastModifiedByAdmin: true,
    unread: false, commentCount: 0, attachments: [],
    version: 0, createdAt: '2027-03-01T10:00:00Z', updatedAt: '2027-03-01T10:00:00Z',
    ...over,
  }
}

function renderForm(
  onSubmit: () => Promise<unknown>,
  onConflictRefresh = vi.fn(),
  training: PersonalTraining | null = null,
) {
  const onClose = vi.fn()
  render(
    <TrainingFormModal
      training={training}
      date="2027-03-10"
      onClose={onClose}
      onSubmit={onSubmit}
      onConflictRefresh={onConflictRefresh}
      canAttach={false}
    />
  )
  return { onClose, onConflictRefresh }
}

describe('TrainingFormModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps the form open and shows the error when saving fails', async () => {
    // The reference implementation collapsed the form the moment Save was clicked, before the server
    // answered. On a failed write the training stayed unsaved while the user believed it was done.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('Baza niedostępna'))
    const { onClose } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Baza niedostępna'))
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeInTheDocument()
  })

  it('closes only after the server confirms', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const { onClose } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('offers a refresh only on a version conflict', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError('Ten trening został w międzyczasie zmieniony.', 409, 'CONFLICT'))
    const { onConflictRefresh } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await user.click(await screen.findByRole('button', { name: 'Odśwież i spróbuj ponownie' }))
    expect(onConflictRefresh).toHaveBeenCalled()
  })

  it('does not offer a refresh for an ordinary failure', async () => {
    // Only a 409 means the row moved under us — a refresh would be meaningless noise otherwise.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError('Za długi tytuł', 400, 'VALIDATION_ERROR'))
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: 'Odśwież i spróbuj ponownie' })).toBeNull()
  })

  it('sends no times unless an hour was explicitly set', async () => {
    // Untimed is the default: the coach has to opt IN to a clock time, not out of one.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ startTime: null, endTime: null })))
  })

  it('sends the hour once the time checkbox is ticked', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Sparing')
    await user.click(screen.getByLabelText('Ustaw godzinę'))
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ startTime: '17:00' })))
  })

  it('refuses to submit without a title', async () => {
    const onSubmit = vi.fn()
    renderForm(onSubmit)

    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeDisabled()
  })

  it('creates a training by default, with no calorie field in sight', async () => {
    // The switch defaults to TRAINING because that is what almost every entry is.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    expect(screen.queryByLabelText('Limit kalorii (kcal)')).toBeNull()

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'TRAINING', targetCalories: null })))
  })

  it('sends a task with its calorie ceiling', async () => {
    // A task is its own entry, not a field on a training: the session and the diet are ticked off
    // separately, so the coach picks one kind here and the day carries two cards.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.click(screen.getByRole('button', { name: 'Zadanie' }))
    await user.type(screen.getByLabelText('Nazwa'), 'Limit kalorii')
    await user.type(screen.getByLabelText('Limit kalorii (kcal)'), '2200')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'TASK', title: 'Limit kalorii', targetCalories: 2200 })))
  })

  it('refuses a calorie ceiling that looks like a slipped digit', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.click(screen.getByRole('button', { name: 'Zadanie' }))
    await user.type(screen.getByLabelText('Nazwa'), 'Limit kalorii')
    await user.type(screen.getByLabelText('Limit kalorii (kcal)'), '220')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Limit kalorii poza zakresem.')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('does not offer to change what an existing entry is', async () => {
    // Flipping a ticked-off training into a task would have to throw its effort rating away to
    // satisfy the database. Delete and re-add instead.
    const onSubmit = vi.fn()
    renderForm(onSubmit, vi.fn(), existing({ kind: 'TASK', targetCalories: 2200 }))

    expect(screen.queryByRole('button', { name: 'Trening' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Zadanie' })).toBeNull()
    // Still a task, and its ceiling is there to be edited
    expect(screen.getByLabelText('Limit kalorii (kcal)')).toHaveValue(2200)
  })

  it('leaves the kind out of an update', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit, vi.fn(), existing())

    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('kind')
  })
})
