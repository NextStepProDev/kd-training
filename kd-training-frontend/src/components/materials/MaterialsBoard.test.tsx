import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MaterialsBoard } from './MaterialsBoard'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import type { AthleteMaterial } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) => (vars ? `${key} ${JSON.stringify(vars)}` : key),
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getAthleteMaterials: vi.fn(),
    createAthleteMaterial: vi.fn(),
    updateAthleteMaterial: vi.fn(),
    deleteAthleteMaterial: vi.fn(),
    replyToMaterial: vi.fn(),
    suggestExerciseVideos: vi.fn(),
  },
}))

vi.mock('../../api/user', () => ({
  myTrainingApi: {
    getMaterials: vi.fn(),
    askAboutMaterial: vi.fn(),
  },
}))

function material(over: Partial<AthleteMaterial> = {}): AthleteMaterial {
  return {
    id: 'm1', title: 'Oddech przeponowy', note: 'Wdech nosem, 5 minut dziennie', video: null,
    createdAt: '2026-11-07T10:00:00Z', questions: [], unread: false,
    ...over,
  }
}

function renderBoard(athleteId: string | null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MaterialsBoard athleteId={athleteId} />
    </QueryClientProvider>,
  )
}

describe('MaterialsBoard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shouldShowNothingToAClientWithoutMaterials', async () => {
    vi.mocked(myTrainingApi.getMaterials).mockResolvedValue([])
    renderBoard(null)

    await waitFor(() => expect(myTrainingApi.getMaterials).toHaveBeenCalled())
    expect(screen.queryByText('library.title')).not.toBeInTheDocument()
  })

  it('shouldLetTheClientReadAndAskButNotEdit', async () => {
    vi.mocked(myTrainingApi.getMaterials).mockResolvedValue([material({
      unread: true,
      questions: [{ id: 'q1', fromCoach: true, body: 'Pamiętaj o żebrach', createdAt: '2026-11-07T11:00:00Z' }],
    })])
    vi.mocked(myTrainingApi.askAboutMaterial).mockResolvedValue(
      { id: 'q2', fromCoach: false, body: 'Ile serii?', createdAt: '2026-11-07T12:00:00Z' })
    renderBoard(null)

    expect(await screen.findByText('Oddech przeponowy')).toBeInTheDocument()
    expect(screen.getByText('library.new')).toBeInTheDocument()
    expect(screen.getByText('Pamiętaj o żebrach')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /library.edit/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'library.add' })).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('library.askPlaceholder'), 'Ile serii?')
    await userEvent.click(screen.getByRole('button', { name: 'library.send' }))

    await waitFor(() => expect(myTrainingApi.askAboutMaterial).toHaveBeenCalledWith('m1', 'Ile serii?'))
  })

  it('shouldLetTheCoachAddAMaterialWithANote', async () => {
    vi.mocked(adminApi.getAthleteMaterials).mockResolvedValue([])
    vi.mocked(adminApi.createAthleteMaterial).mockResolvedValue(material())
    renderBoard('a1')

    await userEvent.click(await screen.findByRole('button', { name: 'library.add' }))
    await userEvent.type(screen.getByLabelText('library.titleLabel'), '  Rozciąganie ')
    await userEvent.type(screen.getByLabelText('library.noteLabel'), 'Po każdym treningu')
    await userEvent.click(screen.getByRole('button', { name: 'library.save' }))

    await waitFor(() => expect(adminApi.createAthleteMaterial).toHaveBeenCalledWith('a1', {
      title: 'Rozciąganie', note: 'Po każdym treningu', videoId: null,
    }))
  })

  it('shouldShowTheServersRefusalInTheForm', async () => {
    // A title alone is refused by the server; the coach has to see why the dialog did not close.
    vi.mocked(adminApi.getAthleteMaterials).mockResolvedValue([])
    vi.mocked(adminApi.createAthleteMaterial).mockRejectedValue(new Error('Dodaj film albo opis'))
    renderBoard('a1')

    await userEvent.click(await screen.findByRole('button', { name: 'library.add' }))
    await userEvent.type(screen.getByLabelText('library.titleLabel'), 'Sam tytuł')
    await userEvent.click(screen.getByRole('button', { name: 'library.save' }))

    expect(await screen.findByText('Dodaj film albo opis')).toBeInTheDocument()
  })

  it('shouldDeleteAMaterialAfterConfirming', async () => {
    vi.mocked(adminApi.getAthleteMaterials).mockResolvedValue([material()])
    vi.mocked(adminApi.deleteAthleteMaterial).mockResolvedValue(undefined)
    renderBoard('a1')

    await userEvent.click(await screen.findByRole('button', { name: 'library.delete: Oddech przeponowy' }))
    await userEvent.click(screen.getByRole('button', { name: 'library.delete' }))

    await waitFor(() => expect(adminApi.deleteAthleteMaterial).toHaveBeenCalledWith('m1'))
  })
})
