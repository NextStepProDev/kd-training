import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { PrivacyPolicyPage } from './PrivacyPolicyPage'

vi.mock('../components/seo/Seo', () => ({ Seo: () => null }))

describe('PrivacyPolicyPage', () => {
  it('shouldNotDescribeGoogleSignInWhileItIsSwitchedOff', () => {
    // GOOGLE_LOGIN_ENABLED is false: the policy describes what the site does, and a sign-in method the
    // client did not ask for has no place in it — not even marked "w przygotowaniu".
    render(<MemoryRouter><PrivacyPolicyPage /></MemoryRouter>)

    expect(screen.queryByText(/logowanie przez konto Google/i)).toBeNull()
    expect(screen.queryByText(/w przygotowaniu/i)).toBeNull()
    // Google stays where it really is: the encrypted backups on Google Drive.
    expect(screen.getByText('Google LLC (kopie zapasowe)')).toBeInTheDocument()
  })
})
