import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { needsProfileCompletion } from '../utils/profileCompletion'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

/**
 * Reads the tokens the backend handed over and turns them into a session.
 *
 * They arrive in the URL <strong>fragment</strong>, not the query string — a fragment is never sent
 * to a server, so the refresh token (good for seven days) stays out of nginx's log, Cloudflare's log
 * and the Referer of anything this page loads. See OAuth2SuccessHandler for the other half.
 */
function readTokensFromHash(): { accessToken: string; refreshToken: string; expiresIn: string } | null {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const accessToken = params.get('accessToken')
  const refreshToken = params.get('refreshToken')
  const expiresIn = params.get('expiresIn')
  if (!accessToken || !refreshToken || !expiresIn) return null
  return { accessToken, refreshToken, expiresIn }
}

export function OAuthCallbackPage() {
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const tokens = readTokensFromHash()

    // Wiped the moment they are read, before anything else can happen. Otherwise the credential sits
    // in the address bar for the length of the session, ready to be copied into a chat window along
    // with the link, and it stays in the history entry for this page.
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
    }

    if (!tokens) {
      navigate('/logowanie', { replace: true })
      return
    }

    loginWithTokens({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresIn: Number(tokens.expiresIn),
    })
      .then((loggedInUser) => {
        // Missing required data (e.g. phone after Google registration) or pending
        // privacy policy consent → complete it; returnTo is preserved.
        if (needsProfileCompletion(loggedInUser)) {
          navigate('/uzupelnij-profil', { replace: true })
        } else {
          navigate(consumeRedirectPath() || '/', { replace: true })
        }
      })
      .catch(() => {
        navigate('/logowanie', { replace: true })
      })
  }, [loginWithTokens, navigate])

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <LoadingSpinner size="lg" />
    </div>
  )
}
