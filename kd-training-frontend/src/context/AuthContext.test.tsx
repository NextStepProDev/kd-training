import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'pl', changeLanguage: () => {} },
  }),
}))

const getCurrentUser = vi.fn()
vi.mock('../api/client', () => ({
  authApi: {
    getCurrentUser: () => getCurrentUser(),
    logout: () => {},
  },
}))
vi.mock('../api/auth', () => ({ loginUser: vi.fn() }))

import { AuthProvider, useAuth } from './AuthContext'
import { ToastProvider } from './ToastContext'
import { ApiError } from '../utils/errors'
import { hasTokens, saveTokens } from '../utils/tokenStorage'

/**
 * Loading the session on mount is one `/user/me`, and it used to wipe the tokens whenever that call
 * failed for ANY reason. So a burst of clicks that tripped the 429 limiter, followed by a reload,
 * logged the user out of an account that was never in question.
 */

function Probe() {
  const { user, isLoading } = useAuth()
  if (isLoading) return <span>loading</span>
  return <span>{user ? `user:${user.email}` : 'anonymous'}</span>
}

function renderProvider() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AuthProvider session bootstrap', () => {
  beforeEach(() => {
    localStorage.clear()
    saveTokens({ accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900 })
    getCurrentUser.mockReset()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('shouldKeepTheTokensWhenTheProfileCallIsRateLimited', async () => {
    getCurrentUser.mockRejectedValue(new ApiError('Zbyt wiele żądań', 429, 'TOO_MANY_REQUESTS', 60))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a reload once the minute has passed must find the session intact').toBe(true)
  })

  it('shouldKeepTheTokensWhenTheProfileCallCannotReachTheServer', async () => {
    // What fetchApi throws when the network never answered: a plain Error with no status. "Not an
    // ApiError" has to mean "I don't know", not "log them out".
    getCurrentUser.mockRejectedValue(new Error('Błąd sieci. Sprawdź połączenie internetowe.'))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a network blip is not an expired session').toBe(true)
  })

  /**
   * The combination that ties the two halves of this fix together, and the one that is easy to get
   * wrong: the refresh was rate limited, so fetchApi reports the failure of a request whose original
   * status was 401. If that throw carried the 401 instead of the status that actually failed, this
   * catch would read it as a refusal and end a session that is still perfectly good.
   */
  it('shouldKeepTheTokensWhenTheRefreshWasRateLimitedDuringBootstrap', async () => {
    getCurrentUser.mockRejectedValue(
      new ApiError('Nie udało się odświeżyć sesji.', 429, 'REFRESH_UNAVAILABLE'))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a throttled refresh must leave the tokens alone').toBe(true)
  })

  it('shouldClearTheTokensWhenTheServerRefusesThem', async () => {
    getCurrentUser.mockRejectedValue(new ApiError('Brak dostępu', 401, null))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a refused token really is the end of the session').toBe(false)
  })

  it('shouldLoadTheUserWhenTheProfileCallSucceeds', async () => {
    getCurrentUser.mockResolvedValue({ email: 'trener@kdtraining.example', preferredLanguage: 'pl' })

    renderProvider()

    await waitFor(() => expect(screen.getByText('user:trener@kdtraining.example')).toBeInTheDocument())
    expect(hasTokens()).toBe(true)
  })
})
