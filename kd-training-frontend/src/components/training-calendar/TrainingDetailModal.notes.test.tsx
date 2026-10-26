import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingDetailModal } from './TrainingDetailModal'
import type { TrainingCalendarAdapter } from './adapter'
import type { PersonalTraining } from '../../types'

vi.mock('../../api/notes', () => ({
  notesApi: { get: vi.fn().mockResolvedValue({ body: 'PRYWATNA', updatedAt: null }), save: vi.fn(), remove: vi.fn(), markers: vi.fn() },
  noteKey: () => ['admin', 'notes', 'training', 't1'],
  notePath: () => '/admin/notes/training/t1',
  NOTES_KEY_PREFIX: ['admin', 'notes'],
}))

const tMap: Record<string, string> = {
  'notes.title': 'Prywatna notatka',
  'notes.add': 'Dodaj notatkę',
  'detail.title': 'Szczegóły',
}
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const training: PersonalTraining = {
  id: 't1', date: '2026-09-02', startTime: null, endTime: null, title: 'Trening',
  description: null, kind: 'TRAINING', status: 'PLANNED', completedAt: null, rpe: null,
  feedback: null, targetCalories: null, createdByAdmin: true, unread: false,
  commentCount: 0, attachments: [], version: 0, lastModifiedByAdmin: true,
  createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z',
}

function adapter(role: 'coach' | 'athlete'): TrainingCalendarAdapter {
  return {
    role, athleteId: 'a1',
    rangeKey: (from, to) => ['test', from, to],
    fetchRange: vi.fn(), createTraining: vi.fn(), updateTraining: vi.fn(),
    deleteTraining: vi.fn(), duplicateTraining: vi.fn(), pasteTraining: vi.fn(),
    getComments: vi.fn().mockResolvedValue([]), addComment: vi.fn(),
    deleteCommentPhoto: vi.fn(), markSeen: vi.fn(), dismissDeletions: vi.fn(),
  }
}

function renderModal(role: 'coach' | 'athlete') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <TrainingDetailModal
        training={training} adapter={adapter(role)}
        onClose={vi.fn()} onEdit={vi.fn()}
        onDelete={vi.fn()} onDuplicate={vi.fn()}
      />
    </QueryClientProvider>,
  )
}

/**
 * The client-side half of the privacy story.
 *
 * The server refuses the athlete anyway — /api/admin/** is admin-only — so this is not the lock. It
 * is the thing that stops the athlete being shown a section about themselves that they cannot read:
 * the gate is one `adapter.role === 'coach'` in the modal, and nothing else would notice if it went.
 */
describe('TrainingDetailModal — the private note section', () => {
  it('shows it to the coach', async () => {
    renderModal('coach')
    expect(await screen.findByText('Prywatna notatka')).toBeInTheDocument()
  })

  it('never renders it for the athlete', () => {
    renderModal('athlete')
    expect(screen.queryByText('Prywatna notatka')).not.toBeInTheDocument()
    // Not even the empty-state affordance: the athlete must not learn the notebook exists.
    expect(screen.queryByText('Dodaj notatkę')).not.toBeInTheDocument()
  })
})
