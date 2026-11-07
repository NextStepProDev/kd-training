import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminInvitations } from './AdminInvitations'

vi.mock('react-i18next', () => ({
  // validation.ts reaches the real i18n instance, which registers this plugin on import.
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, string>) => opts?.email ? `${key}:${opts.email}` : key,
  }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getInvitations: vi.fn(),
    invite: vi.fn(),
    cancelInvitation: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

const OPEN = { id: 'i1', email: 'anna@test.com', createdAt: '2026-11-07T10:00:00Z', expiresAt: '2026-11-14T10:00:00Z' }

async function renderInvitations(open = [OPEN]) {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getInvitations).mockResolvedValue(open)
  vi.mocked(adminApi.invite).mockImplementation(async (email: string) => ({ ...OPEN, id: 'new', email }))
  vi.mocked(adminApi.cancelInvitation).mockResolvedValue(undefined)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AdminInvitations /></QueryClientProvider>)
  return adminApi
}

describe('AdminInvitations', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shouldInviteTheTypedAddressTrimmed', async () => {
    const api = await renderInvitations([])

    await userEvent.type(screen.getByLabelText('invitations.email'), '  nowy@test.com ')
    await userEvent.click(screen.getByRole('button', { name: /invitations.send/ }))

    await waitFor(() => expect(api.invite).toHaveBeenCalledWith('nowy@test.com'))
  })

  it('shouldNotSendAnInvalidAddress', async () => {
    const api = await renderInvitations([])

    await userEvent.type(screen.getByLabelText('invitations.email'), 'nie-mail')
    await userEvent.click(screen.getByRole('button', { name: /invitations.send/ }))

    expect(screen.getByLabelText('invitations.email')).toHaveAttribute('aria-invalid', 'true')
    expect(api.invite).not.toHaveBeenCalled()
  })

  it('shouldResendAnOpenInvitationToTheSameAddress', async () => {
    const api = await renderInvitations()

    await userEvent.click(await screen.findByRole('button', { name: 'invitations.resendFor:anna@test.com' }))

    await waitFor(() => expect(api.invite).toHaveBeenCalledWith('anna@test.com'))
  })

  it('shouldCancelAnOpenInvitation', async () => {
    const api = await renderInvitations()

    await userEvent.click(await screen.findByRole('button', { name: 'invitations.cancelFor:anna@test.com' }))

    await waitFor(() => expect(api.cancelInvitation).toHaveBeenCalledWith('i1'))
  })
})
