import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RegisterPage } from './RegisterPage'

vi.mock('../components/seo/Seo', () => ({ Seo: () => null }))
vi.mock('react-i18next', () => ({
  // validation.ts reaches the real i18n instance, which registers this plugin on import.
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

vi.mock('../api/auth', () => ({
  getInvitation: vi.fn(),
  registerUser: vi.fn(),
}))

// Renders nothing while Google sign-in is switched off, and reads config this test does not care about.
vi.mock('../components/auth/GoogleSignInButton', () => ({ GoogleSignInButton: () => null }))

async function renderAt(url: string) {
  const api = await import('../api/auth')
  const client = new QueryClient()
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}><RegisterPage /></MemoryRouter>
    </QueryClientProvider>,
  )
  return api
}

describe('RegisterPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shouldShowOnlyTheInviteNoticeWithoutALink', async () => {
    const api = await renderAt('/rejestracja')

    expect(screen.getByRole('heading', { name: 'register.inviteOnlyTitle' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'register.submit' })).not.toBeInTheDocument()
    expect(api.getInvitation).not.toHaveBeenCalled()
  })

  it('shouldExplainADeadLinkInsteadOfShowingTheForm', async () => {
    const api = await import('../api/auth')
    vi.mocked(api.getInvitation).mockRejectedValue(new Error('Zaproszenie jest nieważne albo wygasło.'))

    await renderAt('/rejestracja?token=dead')

    expect(await screen.findByText('Zaproszenie jest nieważne albo wygasło.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'register.submit' })).not.toBeInTheDocument()
  })

  it('shouldRegisterWithTheInvitationTokenAndShowTheInvitedAddress', async () => {
    const api = await import('../api/auth')
    vi.mocked(api.getInvitation).mockResolvedValue({ email: 'anna@test.com' })
    vi.mocked(api.registerUser).mockResolvedValue({ message: 'ok' })

    await renderAt('/rejestracja?token=good')

    const email = await screen.findByLabelText('register.email')
    expect(email).toHaveValue('anna@test.com')
    expect(email).toHaveAttribute('readonly')
    expect(api.getInvitation).toHaveBeenCalledWith('good')

    await userEvent.type(screen.getByLabelText('register.firstName'), 'Anna')
    await userEvent.type(screen.getByLabelText('register.lastName'), 'Nowak')
    await userEvent.type(screen.getByLabelText('register.phone'), '123456789')
    await userEvent.type(screen.getByLabelText('register.password'), 'MocneHaslo123')
    await userEvent.type(screen.getByLabelText('register.confirmPassword'), 'MocneHaslo123')
    await userEvent.click(screen.getByLabelText(/register.privacyLink/))
    await userEvent.click(screen.getByLabelText(/register.termsLink/))
    await userEvent.click(screen.getByRole('button', { name: 'register.submit' }))

    await waitFor(() => expect(api.registerUser).toHaveBeenCalledWith(expect.objectContaining({
      invitationToken: 'good', firstName: 'Anna', lastName: 'Nowak',
    })))
    expect(await screen.findByRole('heading', { name: 'register.successTitle' })).toBeInTheDocument()
  })
})
