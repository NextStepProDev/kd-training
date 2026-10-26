import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CommentThread } from './CommentThread'
import type { TrainingCalendarAdapter } from './adapter'
import type { TrainingComment } from '../../types'

const tMap: Record<string, string> = {
  'comments.title': 'Komentarze',
  'comments.empty': 'Brak komentarzy.',
  'comments.placeholder': 'Napisz wiadomość…',
  'comments.send': 'Wyślij',
  'comments.addPhoto': 'Dodaj zdjęcie',
  'comments.photoReady': 'Zdjęcie gotowe do wysłania',
  'comments.photoRemove': 'Usuń wybrane zdjęcie',
  'comments.photoAlt': 'Zdjęcie dołączone do komentarza',
  'comments.photoOpen': 'Otwórz podgląd',
  'comments.photoPreviewTitle': 'Podgląd zdjęcia',
  'comments.photoDelete': 'Usuń zdjęcie',
  'comments.photoHint': 'Nie wysyłaj cudzych danych.',
  'comments.deletedAuthor': 'Konto usunięte',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

// The photo component fetches its bytes through the API client; the thread tests only care that a
// photo renders and that the preview stacks correctly, not how the blob got there.
vi.mock('../../api/client', () => ({
  fetchApiBlob: vi.fn(() => Promise.resolve(new Blob(['x'], { type: 'image/jpeg' }))),
}))

function comment(over: Partial<TrainingComment> = {}): TrainingComment {
  return {
    id: 'c1', body: 'Dobra robota', fromCoach: true, authorName: 'Kacper',
    createdAt: '2027-03-01T10:00:00Z', photo: null, ...over,
  }
}

function stubAdapter(comments: TrainingComment[], over: Partial<TrainingCalendarAdapter> = {}) {
  return {
    role: 'athlete',
    athleteId: 'a1',
    rangeKey: () => ['test'],
    fetchRange: vi.fn(),
    createTraining: vi.fn(),
    updateTraining: vi.fn(),
    deleteTraining: vi.fn(),
    duplicateTraining: vi.fn(),
    pasteTraining: vi.fn(),
    getComments: vi.fn().mockResolvedValue(comments),
    addComment: vi.fn().mockResolvedValue(comment()),
    deleteCommentPhoto: vi.fn().mockResolvedValue(undefined),
    markSeen: vi.fn(),
    dismissDeletions: vi.fn(),
    ...over,
  } as TrainingCalendarAdapter
}

function renderThread(adapter: TrainingCalendarAdapter) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CommentThread trainingId="t1" adapter={adapter} />
    </QueryClientProvider>,
  )
}

/** A JPEG the stubbed canvas will hand back, so `compressImage` has something to resolve with. */
function stubImagePipeline() {
  vi.stubGlobal('Image', class {
    onload: (() => void) | null = null
    onerror: (() => void) | null = null
    width = 1179
    height = 2556
    set src(_v: string) { queueMicrotask(() => this.onload?.()) }
    get src() { return 'blob:stub' }
  })
  URL.createObjectURL = vi.fn(() => 'blob:stub')
  URL.revokeObjectURL = vi.fn()
  HTMLCanvasElement.prototype.toDataURL = vi.fn(() => 'data:image/png;base64,')
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
    drawImage: vi.fn(), fillRect: vi.fn(), fillStyle: '',
  })) as unknown as typeof HTMLCanvasElement.prototype.getContext
  HTMLCanvasElement.prototype.toBlob = function (cb: BlobCallback, type?: string) {
    cb(new Blob([new ArrayBuffer(110 * 1024)], { type }))
  }
}

describe('CommentThread', () => {
  beforeEach(() => stubImagePipeline())
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should send a plain comment without a photo', async () => {
    const adapter = stubAdapter([])
    renderThread(adapter)
    const user = userEvent.setup()

    await user.type(await screen.findByPlaceholderText('Napisz wiadomość…'), 'Nogi ciężkie')
    await user.click(screen.getByRole('button', { name: /Wyślij/ }))

    // Third argument null: the adapter must take the JSON path, not the multipart one
    await waitFor(() => expect(adapter.addComment).toHaveBeenCalledWith('t1', 'Nogi ciężkie', null))
  })

  it('should send the compressed photo alongside the text', async () => {
    const adapter = stubAdapter([])
    const { container } = renderThread(adapter)
    const user = userEvent.setup()

    const input = container.querySelector('input[type="file"]')!
    await user.upload(input as HTMLInputElement,
      new File([new ArrayBuffer(2_400_000)], 'garmin.png', { type: 'image/png' }))

    await screen.findByText('Zdjęcie gotowe do wysłania')
    await user.type(screen.getByPlaceholderText('Napisz wiadomość…'), 'Tak wyszło')
    await user.click(screen.getByRole('button', { name: /Wyślij/ }))

    await waitFor(() => expect(adapter.addComment).toHaveBeenCalled())
    const [id, body, file] = (adapter.addComment as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(id).toBe('t1')
    expect(body).toBe('Tak wyszło')
    // Converted on the way out — the server only reads JPEG on this path
    expect((file as File).type).toBe('image/jpeg')
  })

  /** A screenshot is a whole message on its own, so the button must not need words too. */
  it('should allow sending a photo with no text', async () => {
    const adapter = stubAdapter([])
    const { container } = renderThread(adapter)
    const user = userEvent.setup()

    expect(await screen.findByRole('button', { name: /Wyślij/ })).toBeDisabled()

    await user.upload(container.querySelector('input[type="file"]') as HTMLInputElement,
      new File([new ArrayBuffer(500_000)], 'g.jpg', { type: 'image/jpeg' }))
    await screen.findByText('Zdjęcie gotowe do wysłania')

    expect(screen.getByRole('button', { name: /Wyślij/ })).toBeEnabled()
  })

  /**
   * `accept="image/*"` is a filter in the picker, not a guarantee — a GIF sails through it and the
   * server has no reader for one, so the refusal has to happen here with a message.
   */
  it('should refuse an image format the server cannot read', async () => {
    const adapter = stubAdapter([])
    const { container } = renderThread(adapter)
    const user = userEvent.setup()

    await user.upload(container.querySelector('input[type="file"]') as HTMLInputElement,
      new File(['GIF89a'], 'anim.gif', { type: 'image/gif' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.queryByText('Zdjęcie gotowe do wysłania')).not.toBeInTheDocument()
    expect(adapter.addComment).not.toHaveBeenCalled()
  })

  it('should show the failure inline next to the button', async () => {
    const adapter = stubAdapter([], {
      addComment: vi.fn().mockRejectedValue(new Error('Do jednego treningu można dodać najwyżej 3 zdjęcia.')),
    })
    renderThread(adapter)
    const user = userEvent.setup()

    await user.type(await screen.findByPlaceholderText('Napisz wiadomość…'), 'x')
    await user.click(screen.getByRole('button', { name: /Wyślij/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('najwyżej 3 zdjęcia')
  })

  it('should render a photo-only comment without an empty text line', async () => {
    const adapter = stubAdapter([comment({
      body: null,
      photo: {
        url: '/api/user/my-training/comments/c1/photo',
        width: 590, height: 1280,
        expiresAt: '2027-04-01T10:00:00Z',
        canDelete: true,
      },
    })])
    renderThread(adapter)

    const image = await screen.findByAltText('Zdjęcie dołączone do komentarza')
    // Dimensions travel with the payload so the bubble reserves its box before the bytes land
    expect(image).toHaveAttribute('width', '590')
    expect(image).toHaveAttribute('height', '1280')
  })
})
