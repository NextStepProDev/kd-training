import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

/** Renders the button with the feature flag forced to a given value. */
async function renderWith(enabled: boolean) {
  vi.doMock('../../config/features', () => ({ GOOGLE_LOGIN_ENABLED: enabled }))
  const { GoogleSignInButton } = await import('./GoogleSignInButton')
  render(<GoogleSignInButton />)
}

describe('GoogleSignInButton', () => {
  beforeEach(() => vi.resetModules())
  afterEach(() => vi.doUnmock('../../config/features'))

  it('shouldShowNothingAtAllWhileTheFeatureIsOff', async () => {
    // Off means absent: no link that answers 401 (production runs without the oauth2 profile), and no
    // greyed-out "soon" either — the client chose e-mail and password, and a promise on the sign-in
    // page reads as a feature that is missing.
    await renderWith(false)

    expect(document.body.textContent).toBe('')
    expect(screen.queryByRole('link')).toBeNull()
  })

  it('shouldLinkToGoogleOnceTheFeatureIsOn', async () => {
    // The other half of the flag: switching it on is the entire change, with no markup to rewrite.
    await renderWith(true)

    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/oauth2/authorization/google')
    expect(screen.queryByText('oauth.inProgress')).toBeNull()
  })
})
