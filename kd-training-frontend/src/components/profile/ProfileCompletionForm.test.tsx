import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { ProfileCompletionForm } from './ProfileCompletionForm'
import { getMissingProfileFields } from '../../utils/profileCompletion'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'profile.firstName': 'Imię',
        'profile.lastName': 'Nazwisko',
        'profile.phone': 'Telefon',
        'completion.submit': 'Zapisz i kontynuuj',
        'completion.errors.fieldRequired': 'To pole jest wymagane.',
        'completion.errors.nameTooShort': 'Imię i nazwisko muszą mieć co najmniej 3 znaki.',
        'register.acceptPrivacyPrefix': 'Akceptuję',
        'register.privacyLink': 'Politykę prywatności',
        'register.privacyRequired': 'Musisz zaakceptować Politykę prywatności.',
        'register.acceptMarketing': 'Chcę otrzymywać wiadomości marketingowe.',
        'register.acceptMarketingHint': 'Bez spamu.',
      }
      return map[key] ?? key
    },
  }),
}))

const updateProfileMock = vi.fn()
const submitConsentsMock = vi.fn()
vi.mock('../../api/client', () => ({
  authApi: {
    updateProfile: (...args: unknown[]) => updateProfileMock(...args),
    submitConsents: (...args: unknown[]) => submitConsentsMock(...args),
  },
}))

const refreshUserMock = vi.fn()
type MockUser = { firstName: string; lastName: string; phone: string | null; privacyAccepted: boolean }
let mockUser: MockUser | null = null
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser, refreshUser: refreshUserMock }),
}))

const renderForm = (ui: ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('getMissingProfileFields', () => {
  it('should report no missing fields when all required values are present', () => {
    expect(getMissingProfileFields({ firstName: 'Anna', lastName: 'Nowak', phone: '111' })).toEqual([])
  })

  it('should report blank and whitespace-only required fields as missing', () => {
    expect(getMissingProfileFields({ firstName: 'Anna', lastName: '', phone: '   ' })).toEqual(['lastName', 'phone'])
  })

  it('should report nothing for a null user', () => {
    expect(getMissingProfileFields(null)).toEqual([])
  })
})

describe('ProfileCompletionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    updateProfileMock.mockResolvedValue(undefined)
    submitConsentsMock.mockResolvedValue(undefined)
  })

  it('should render only the missing fields', () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '', privacyAccepted: true }
    render(<ProfileCompletionForm />)
    expect(screen.getByText('Telefon')).toBeInTheDocument()
    expect(screen.queryByText('Imię')).not.toBeInTheDocument()
    expect(screen.queryByText('Nazwisko')).not.toBeInTheDocument()
  })

  it('should render nothing when the profile is already complete', () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '111', privacyAccepted: true }
    const { container } = render(<ProfileCompletionForm />)
    expect(container).toBeEmptyDOMElement()
  })

  it('should block submit and flag the empty field with an inline error', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '', privacyAccepted: true }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(screen.getByText('To pole jest wymagane.')).toBeInTheDocument()
    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    expect(phoneInput).toHaveAttribute('aria-invalid', 'true')
    expect(updateProfileMock).not.toHaveBeenCalled()
  })

  it('should clear a field error once the user starts editing', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '', privacyAccepted: true }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    await user.click(screen.getByText('Zapisz i kontynuuj'))
    expect(screen.getByText('To pole jest wymagane.')).toBeInTheDocument()

    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    await user.type(phoneInput, '5')

    expect(screen.queryByText('To pole jest wymagane.')).not.toBeInTheDocument()
    expect(phoneInput).toHaveAttribute('aria-invalid', 'false')
  })

  it('should reject a too-short name', async () => {
    mockUser = { firstName: 'Anna', lastName: '', phone: '111', privacyAccepted: true }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const lastNameInput = container.querySelector<HTMLInputElement>('input[autocomplete="family-name"]')!
    await user.type(lastNameInput, 'Ab')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(screen.getByText('Imię i nazwisko muszą mieć co najmniej 3 znaki.')).toBeInTheDocument()
    expect(updateProfileMock).not.toHaveBeenCalled()
  })

  it('should merge the filled field with existing account values and refresh the user', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '', privacyAccepted: true }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    await user.type(phoneInput, '111222333')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    await waitFor(() => expect(updateProfileMock).toHaveBeenCalledWith('Anna', 'Nowak', '111222333'))
    expect(refreshUserMock).toHaveBeenCalled()
  })

  it('should surface a server error and not refresh on failure', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '', privacyAccepted: true }
    updateProfileMock.mockRejectedValue(new Error('Numer zajęty'))
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    await user.type(phoneInput, '111222333')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(await screen.findByText('Numer zajęty')).toBeInTheDocument()
    expect(refreshUserMock).not.toHaveBeenCalled()
  })

  it('should show consent checkboxes for a Google user without privacy acceptance', () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '111', privacyAccepted: false }
    const { container } = renderForm(<ProfileCompletionForm />)
    expect(container.querySelector('#completionPrivacy')).toBeInTheDocument()
    expect(container.querySelector('#completionMarketing')).toBeInTheDocument()
  })

  it('should block submit when the mandatory privacy consent is unchecked', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '111', privacyAccepted: false }
    const user = userEvent.setup()
    renderForm(<ProfileCompletionForm />)

    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(screen.getByText('Musisz zaakceptować Politykę prywatności.')).toBeInTheDocument()
    expect(submitConsentsMock).not.toHaveBeenCalled()
  })

  it('should submit privacy and marketing consents when both are checked', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '111', privacyAccepted: false }
    const user = userEvent.setup()
    const { container } = renderForm(<ProfileCompletionForm />)

    await user.click(container.querySelector('#completionPrivacy')!)
    await user.click(container.querySelector('#completionMarketing')!)
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    await waitFor(() => expect(submitConsentsMock).toHaveBeenCalledWith(true, true))
    expect(updateProfileMock).not.toHaveBeenCalled()
    expect(refreshUserMock).toHaveBeenCalled()
  })
})
