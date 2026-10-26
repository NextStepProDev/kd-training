import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminPrivateNote } from './AdminPrivateNote'
import { notesApi } from '../../api/notes'

vi.mock('../../api/notes', async () => {
  const actual = await vi.importActual<typeof import('../../api/notes')>('../../api/notes')
  return { ...actual, notesApi: { get: vi.fn(), save: vi.fn(), remove: vi.fn(), markers: vi.fn() } }
})

const tMap: Record<string, string> = {
  'notes.title': 'Prywatna notatka',
  'notes.add': 'Dodaj notatkę',
  'notes.edit': 'Edytuj notatkę',
  'notes.delete': 'Usuń notatkę',
  'notes.showAll': 'Pokaż całość',
  'notes.showLess': 'Zwiń',
  'notes.loading': 'Wczytywanie…',
  'notes.loadError': 'Nie udało się wczytać notatki.',
  'notes.retry': 'Spróbuj ponownie',
  'form.save': 'Zapisz',
  'form.cancel': 'Anuluj',
}
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const api = vi.mocked(notesApi)

function renderNote(body: string | null) {
  api.get.mockResolvedValue({ body, updatedAt: body ? '2026-08-01T10:00:00Z' : null })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminPrivateNote anchor={{ target: 'training', id: 't1' }} />
    </QueryClientProvider>,
  )
}

function renderFailingNote() {
  api.get.mockRejectedValue(new Error('429'))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminPrivateNote anchor={{ target: 'training', id: 't1' }} />
    </QueryClientProvider>,
  )
}

/** Forces the measured "is it actually cut off" answer, independent of how long the text is. */
function stubClipping(clipped: boolean) {
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', { configurable: true, value: clipped ? 400 : 100 })
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, value: 100 })
}

beforeEach(() => {
  vi.clearAllMocks()
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} } as never
  stubClipping(false)
})

describe('AdminPrivateNote', () => {
  it('saves an edit and keeps Zapisz disabled until the text actually changes', async () => {
    const user = userEvent.setup()
    api.save.mockResolvedValue(undefined)
    renderNote('Stara treść')

    await user.click(await screen.findByLabelText('Edytuj notatkę'))
    const save = screen.getByRole('button', { name: 'Zapisz' })
    // Loaded value, untouched: nothing to save. Comparing against the loaded value rather than a
    // first-render snapshot is the point — the note arrives AFTER mount.
    expect(save).toBeDisabled()

    await user.type(screen.getByRole('textbox'), ' plus dopisek')
    expect(save).toBeEnabled()
    await user.click(save)

    await waitFor(() => expect(api.save).toHaveBeenCalledWith(
      { target: 'training', id: 't1' }, 'Stara treść plus dopisek'))
  })

  it('never sends an empty note, because the bin is what deletes', async () => {
    const user = userEvent.setup()
    renderNote(null)

    await user.click(await screen.findByRole('button', { name: 'Dodaj notatkę' }))
    await user.type(screen.getByRole('textbox'), '   ')
    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeDisabled()
    expect(api.save).not.toHaveBeenCalled()
  })

  it('keeps the form open and shows the reason when the server refuses', async () => {
    const user = userEvent.setup()
    api.save.mockRejectedValue(new Error('Notatka może mieć najwyżej 4000 znaków.'))
    renderNote('Stara treść')

    await user.click(await screen.findByLabelText('Edytuj notatkę'))
    await user.type(screen.getByRole('textbox'), '!')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('4000 znaków')
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('offers "pokaż całość" from the MEASUREMENT, not from the length of the text', async () => {
    // The bug this pins down: line-clamp counts LINES, and the sibling app gated the control on
    // `body.length > 300` — CHARACTERS. A short note bulleted onto ten lines was cut off with no
    // way to expand it. This note is 40 characters and clipped.
    stubClipping(true)
    renderNote('- a\n- b\n- c\n- d\n- e\n- f\n- g\n- h')

    expect(await screen.findByRole('button', { name: 'Pokaż całość' })).toBeInTheDocument()
  })

  it('never offers the empty editor when the note could not be read', async () => {
    // The failure this pins down: the component ignored isError, so a 429 from the limiter or a 502
    // mid-deploy rendered the "no note here" branch. Writing from there goes through the same
    // ON CONFLICT DO UPDATE as an edit, so the note that failed to load would be replaced by
    // whatever was typed over it — the one data-loss path in this feature.
    renderFailingNote()

    expect(await screen.findByRole('alert')).toHaveTextContent('Nie udało się wczytać notatki.')
    expect(screen.queryByRole('button', { name: 'Dodaj notatkę' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('reads the note again on demand, and edits it once it arrives', async () => {
    // The other half: a gate that only ever refuses is indistinguishable from a broken component.
    const user = userEvent.setup()
    renderFailingNote()

    await screen.findByRole('alert')
    api.get.mockResolvedValue({ body: 'Wróciła', updatedAt: '2026-08-01T10:00:00Z' })
    await user.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))

    expect(await screen.findByLabelText('Edytuj notatkę')).toBeInTheDocument()
    expect(screen.getByText('Wróciła')).toBeInTheDocument()
  })

  it('offers no expander when the preview is not actually cut off', async () => {
    stubClipping(false)
    renderNote('x'.repeat(2000))

    expect(await screen.findByLabelText('Edytuj notatkę')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pokaż całość' })).not.toBeInTheDocument()
  })
})
