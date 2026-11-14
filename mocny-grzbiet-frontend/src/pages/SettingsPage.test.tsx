import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SettingsPage } from './SettingsPage'

const updateProfile = vi.fn()
vi.mock('../components/seo/Seo', () => ({ Seo: () => null }))
vi.mock('../api/client', () => ({
  authApi: { updateProfile: (...args: unknown[]) => updateProfile(...args) },
}))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 'u1', email: 'ania@test.pl', firstName: 'Ania', lastName: 'Kowalska', phone: '500100100',
      role: 'USER', isAdmin: false, hasPassword: true, avatarUrl: null, marketingConsent: false,
    },
    refreshUser: vi.fn(), logout: vi.fn(),
  }),
}))
vi.mock('../context/ToastContext', () => ({ useToast: () => ({ showToast: vi.fn() }) }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

describe('SettingsPage — profile', () => {
  beforeEach(() => {
    updateProfile.mockReset()
    updateProfile.mockResolvedValue(undefined)
  })

  async function openProfileAndSetPhone(phone: string) {
    const user = userEvent.setup()
    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    await user.click(screen.getByRole('button', { name: /profile\.title/ }))
    const field = screen.getByLabelText('profile.phone')
    await user.clear(field)
    if (phone) await user.type(field, phone)
    await user.click(screen.getByRole('button', { name: 'profile.save' }))
  }

  it('shouldRefuseAPhoneThatIsNotNineDigits', async () => {
    // Found in manual testing: any string was saved as the phone.
    await openProfileAndSetPhone('123')

    expect(screen.getByText('validation.phoneInvalid')).toBeInTheDocument()
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('shouldSendTheDigitsWithoutSpaces', async () => {
    await openProfileAndSetPhone('600 700 800')

    await waitFor(() => expect(updateProfile).toHaveBeenCalledWith('Ania', 'Kowalska', '600700800'))
  })
})
