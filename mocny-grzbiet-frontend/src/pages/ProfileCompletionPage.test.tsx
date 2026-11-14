import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ProfileCompletionPage } from './ProfileCompletionPage'
import type { User } from '../types'

let currentUser: User | null = null
vi.mock('../components/seo/Seo', () => ({ Seo: () => null }))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: currentUser, logout: vi.fn(), refreshUser: vi.fn() }),
}))
vi.mock('../context/ToastContext', () => ({ useToast: () => ({ showToast: vi.fn() }) }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  Trans: ({ i18nKey }: { i18nKey: string }) => i18nKey,
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function member(over: Partial<User> = {}): User {
  return {
    id: 'u1', email: 'ala@test.pl', firstName: 'Ala', lastName: 'Nowak', phone: '600700800',
    role: 'USER', isAdmin: false, superAdmin: false, privacyAccepted: true, termsAccepted: true,
    marketingConsent: false, isAthlete: true, trainingConsent: true, preferredLanguage: 'pl',
    hasPassword: true, avatarUrl: null, createdAt: '2026-01-01T10:00:00Z',
    ...over,
  }
}

describe('ProfileCompletionPage', () => {
  beforeEach(() => { currentUser = null })

  it('says the terms changed when the new version is all that is missing', () => {
    currentUser = member({ termsAccepted: false, termsUpdated: true })
    render(<MemoryRouter><ProfileCompletionPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'completion.termsUpdatedTitle' })).toBeInTheDocument()
    expect(screen.getByText('completion.termsUpdatedText')).toBeInTheDocument()
  })

  it('keeps the usual heading for an account that is genuinely incomplete', () => {
    currentUser = member({ termsAccepted: false, termsUpdated: false, phone: '' })
    render(<MemoryRouter><ProfileCompletionPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'completion.pageTitle' })).toBeInTheDocument()
  })
})
