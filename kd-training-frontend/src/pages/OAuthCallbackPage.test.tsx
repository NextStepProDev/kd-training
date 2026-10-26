import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { OAuthCallbackPage } from './OAuthCallbackPage'

const navigate = vi.fn()
const loginWithTokens = vi.fn()

vi.mock('react-router-dom', () => ({ useNavigate: () => navigate }))
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ loginWithTokens }) }))
vi.mock('../components/ui/LoadingSpinner', () => ({ LoadingSpinner: () => <div /> }))

function landOn(hash: string) {
  window.history.replaceState(null, '', '/oauth-callback' + hash)
  render(<OAuthCallbackPage />)
}

describe('OAuthCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    loginWithTokens.mockResolvedValue({
      firstName: 'Kasia', lastName: 'Nowak', phone: '600100200', privacyAccepted: true,
    })
  })

  it('shouldReadTheTokensFromTheFragmentRatherThanTheQueryString', async () => {
    // The fragment never reaches a server, which is the entire reason the backend puts them there:
    // a seven-day refresh token must not end up in nginx's log or Cloudflare's.
    landOn('#accessToken=abc&refreshToken=def&expiresIn=900')

    await waitFor(() => expect(loginWithTokens).toHaveBeenCalledWith({
      accessToken: 'abc', refreshToken: 'def', expiresIn: 900,
    }))
  })

  it('shouldWipeTheTokensOutOfTheAddressBarImmediately', async () => {
    // Left in place, the credential sits in the address bar for the whole session — ready to be
    // copied into a chat along with the link, and kept in this page's history entry.
    landOn('#accessToken=abc&refreshToken=def&expiresIn=900')

    await waitFor(() => expect(window.location.hash).toBe(''))
    expect(window.location.pathname).toBe('/oauth-callback')
  })

  it('shouldSendPeopleToTheLoginPageWhenTheFragmentCarriesNothing', async () => {
    // A bare /oauth-callback is what a bookmarked or half-followed redirect looks like.
    landOn('')

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/logowanie', { replace: true }))
    expect(loginWithTokens).not.toHaveBeenCalled()
  })

  it('shouldSendPeopleToCompleteTheirProfileWhenGoogleLeftGapsInIt', async () => {
    // Google hands over no phone number, and the privacy consent is ours to collect, not theirs.
    loginWithTokens.mockResolvedValue({
      firstName: 'Kasia', lastName: 'Nowak', phone: null, privacyAccepted: false,
    })
    landOn('#accessToken=abc&refreshToken=def&expiresIn=900')

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/uzupelnij-profil', { replace: true }))
  })
})
