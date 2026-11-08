import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminUserDetail } from './AdminUserDetail'
import type { AdminUserDetail as AdminUserDetailType } from '../../types'

const tMap: Record<string, string> = {
  'users.detail.back': 'Wróć do listy',
  'users.detail.athleteEnable': 'Włącz kalendarz 1:1',
  'users.detail.athleteDisable': 'Wyłącz kalendarz 1:1',
  'users.detail.athleteDisableTitle': 'Wyłączyć kalendarz 1:1?',
  'users.detail.exportAction': 'Pobierz dane',
  'users.detail.blockAction': 'Zablokuj konto',
  'users.detail.unblockAction': 'Odblokuj konto',
}

vi.mock('react-i18next', () => ({
  // A deletion date is the one variable a test here needs to see in the text, so only it is appended.
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) =>
      (tMap[key] ?? key) + (vars?.deleteDate ? ` [${vars.deleteDate}]` : ''),
  }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getUser: vi.fn(),
    setAthlete: vi.fn(),
    exportUserData: vi.fn(),
    blockUser: vi.fn(),
    unblockUser: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

const baseUser: AdminUserDetailType = {
  id: 'u1', email: 'jan@test.com', firstName: 'Jan', lastName: 'Kowalski', phone: '123456789',
  role: 'USER', isAdmin: false, superAdmin: false, emailVerified: true,
  marketingConsent: false, isAthlete: false, preferredLanguage: 'pl', hasPassword: true, oauthLinked: false, avatarUrl: null, blockedAt: null, scheduledDeletionAt: null,
  createdAt: '2026-01-01T00:00:00Z',
}

async function renderDetail(overrides: Partial<AdminUserDetailType>) {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getUser).mockResolvedValue({ ...baseUser, ...overrides })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AdminUserDetail userId="u1" onBack={vi.fn()} />
    </QueryClientProvider>
  )
  return adminApi
}

describe('AdminUserDetail', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the profile', async () => {
    await renderDetail({})
    expect(await screen.findByText('Jan Kowalski')).toBeInTheDocument()
    expect(screen.getByText(/jan@test.com/)).toBeInTheDocument()
  })

  it('enables the 1-on-1 calendar without a confirmation step', async () => {
    const user = userEvent.setup()
    const adminApi = await renderDetail({ isAthlete: false })
    vi.mocked(adminApi.setAthlete).mockResolvedValue({ ...baseUser, isAthlete: true, blocked: false })

    await user.click(await screen.findByRole('button', { name: 'Włącz kalendarz 1:1' }))

    // Turning it ON adds an option — no confirm needed, nothing can be lost.
    await waitFor(() => expect(adminApi.setAthlete).toHaveBeenCalledWith('u1', true))
  })

  it('asks for confirmation before hiding the 1-on-1 calendar', async () => {
    const user = userEvent.setup()
    const adminApi = await renderDetail({ isAthlete: true })
    vi.mocked(adminApi.setAthlete).mockResolvedValue({ ...baseUser, isAthlete: false, blocked: false })

    await user.click(await screen.findByRole('button', { name: 'Wyłącz kalendarz 1:1' }))

    // The switch looks destructive even though it is not, so it must not fire on the first click.
    expect(adminApi.setAthlete).not.toHaveBeenCalled()
    expect(screen.getByText('Wyłączyć kalendarz 1:1?')).toBeInTheDocument()

    const confirmButtons = screen.getAllByRole('button', { name: 'Wyłącz kalendarz 1:1' })
    await user.click(confirmButtons[confirmButtons.length - 1])

    await waitFor(() => expect(adminApi.setAthlete).toHaveBeenCalledWith('u1', false))
  })

  it('downloads the GDPR export as a ZIP named after the person', async () => {
    const user = userEvent.setup()
    const api = await renderDetail({})
    vi.mocked(api.exportUserData).mockResolvedValue(new Blob(['zip'], { type: 'application/zip' }))
    const createObjectURL = vi.fn(() => 'blob:export')
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toMatch(/^dane-Kowalski-\d{4}-\d{2}-\d{2}\.zip$/)
      expect(this.href).toBe('blob:export')
    })

    await user.click(await screen.findByRole('button', { name: 'Pobierz dane' }))

    await waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    expect(api.exportUserData).toHaveBeenCalledWith('u1')
    click.mockRestore()
  })

  it('blocks the account only after the confirmation', async () => {
    const user = userEvent.setup()
    const api = await renderDetail({})

    await user.click(await screen.findByRole('button', { name: 'Zablokuj konto' }))
    expect(api.blockUser).not.toHaveBeenCalled()
    const buttons = screen.getAllByRole('button', { name: 'Zablokuj konto' })
    await user.click(buttons[buttons.length - 1])

    await waitFor(() => expect(api.blockUser).toHaveBeenCalledWith('u1'))
  })

  it('unblocks a blocked account straight away', async () => {
    // Giving access back destroys nothing, so it needs no confirmation.
    const user = userEvent.setup()
    const api = await renderDetail({ blockedAt: '2026-11-08T10:00:00Z' })

    await user.click(await screen.findByRole('button', { name: 'Odblokuj konto' }))

    await waitFor(() => expect(api.unblockUser).toHaveBeenCalledWith('u1'))
  })

  it('tells when a blocked account will be deleted', async () => {
    await renderDetail({ blockedAt: '2026-11-08T10:00:00Z', scheduledDeletionAt: '2027-11-08T10:00:00Z' })

    expect(await screen.findByText(/\[8\.11\.2027\]/)).toBeInTheDocument()
  })

  it('offers no block for an admin', async () => {
    await renderDetail({ role: 'ADMIN', isAdmin: true })
    await screen.findByText('Jan Kowalski')
    expect(screen.queryByRole('button', { name: 'Zablokuj konto' })).not.toBeInTheDocument()
  })
})
