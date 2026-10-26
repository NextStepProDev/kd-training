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

  it('shouldNotOfferADoorThatAnswersWithAnErrorWhileTheFeatureIsOff', async () => {
    // The whole bug in one assertion: production runs without the oauth2 profile, so this address
    // answers 401. Anything navigable here sends somebody on the sign-up path into raw JSON.
    await renderWith(false)

    expect(document.querySelector('a[href="/oauth2/authorization/google"]')).toBeNull()
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('shouldSayThatGoogleSignInIsStillBeingBuilt', async () => {
    // A control that is merely greyed out reads as broken. It has to say which of the two it is.
    await renderWith(false)

    expect(screen.getByText('oauth.soon')).toBeInTheDocument()
    expect(screen.getByText('oauth.inProgress')).toBeInTheDocument()
  })

  it('shouldLinkToGoogleOnceTheFeatureIsOn', async () => {
    // The other half of the flag: switching it on is the entire change, with no markup to rewrite.
    await renderWith(true)

    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/oauth2/authorization/google')
    expect(screen.queryByText('oauth.inProgress')).toBeNull()
  })
})
