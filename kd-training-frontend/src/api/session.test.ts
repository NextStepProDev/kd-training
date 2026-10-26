import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// The real i18n bundle pulls in the whole locale set; these tests only need `t` to return something.
vi.mock('../i18n', () => ({
  default: { language: 'pl', t: (key: string) => key },
}))

import { authApi } from './client'
import { ApiError } from '../utils/errors'
import { hasTokens, saveTokens } from '../utils/tokenStorage'

/**
 * A failed request may end a session only when the server actually refused the credentials.
 *
 * The report behind this file: an admin clicking quickly around the panel tripped the 429 limiter and
 * was then thrown out to the login screen. Both places that can end a session — the `/user/me` load
 * and the token refresh — cleared the tokens on ANY error, and an Error carrying only a message gave
 * them nothing to tell "token rejected" from "too many requests" or "the network blinked".
 *
 * Note this file mocks the network and runs the real client and auth modules together, because the
 * bug lived in what one could learn from the other. AuthContext.test.tsx does the mirror image.
 */

const TOKENS = { accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900 }

function respond(status: number, body: unknown = {}, headers: Record<string, string> = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name: string) => headers[name] ?? null },
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response
}

const TOO_MANY = { code: 'TOO_MANY_REQUESTS', message: 'Zbyt wiele żądań. Spróbuj ponownie za chwilę.' }

describe('session survival', () => {
  beforeEach(() => {
    localStorage.clear()
    // expiresIn 900 keeps the access token fresh, so nothing refreshes pre-emptively: where a refresh
    // happens below, it is the mocked 401 that drove it.
    saveTokens(TOKENS)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('shouldKeepTheSessionWhenAReadIsRateLimited', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(429, TOO_MANY, { 'Retry-After': '60' })))

    const error = await authApi.getCurrentUser().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(429)
    expect((error as ApiError).isRateLimited).toBe(true)
    expect((error as ApiError).isAuthRejection).toBe(false)
    expect((error as ApiError).retryAfterSeconds).toBe(60)
    expect(hasTokens(), 'a rate limit must not log anybody out').toBe(true)
  })

  it('shouldKeepTheSessionWhenTheRefreshItselfIsRateLimited', async () => {
    // The access token 401s, so the client refreshes — and the refresh hits the limiter too.
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401))
      .mockResolvedValueOnce(respond(429, TOO_MANY, { 'Retry-After': '60' }))
    vi.stubGlobal('fetch', fetchMock)

    const error = await authApi.getCurrentUser().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    // Never 401/403 on this path: the same object reaches AuthContext, which reads exactly that to
    // decide whether the session is over.
    expect((error as ApiError).isAuthRejection).toBe(false)
    expect(hasTokens(), 'a throttled refresh is not an expired session').toBe(true)
  })

  it('shouldKeepTheSessionWhenTheRefreshCannotReachTheServer', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    const error = await authApi.getCurrentUser().catch((e: unknown) => e)

    expect((error as ApiError).isAuthRejection).toBe(false)
    expect(hasTokens(), 'a network blip is not an expired session').toBe(true)
  })

  /**
   * 401 here is the backend's contract, not a guess: `InvalidRefreshTokenException` maps to 401 so a
   * refused token can be told apart from a busy server. It used to be 400 (the default for
   * IllegalArgumentException), which this rule would read as transient — leaving the app certain the
   * user was logged in while nothing worked. `AuthControllerIntegrationTest` pins the status.
   */
  it('shouldEndTheSessionWhenTheServerRefusesTheRefreshToken', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401))
      .mockResolvedValueOnce(respond(401, { code: 'INVALID_REFRESH_TOKEN', message: 'Nieprawidłowy token odświeżania' }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.getCurrentUser().catch(() => undefined)

    expect(hasTokens(), 'a refused refresh token really is the end of the session').toBe(false)
  })

  /**
   * The 401 path used to call doRefresh directly, skipping the in-flight dedupe: a screen firing
   * several requests at once spent one refresh round-trip per request, all into the `auth` bucket the
   * backend rations at 15/min. The app was manufacturing the 429 that then logged people out.
   */
  it('shouldShareOneRefreshBetweenParallelRequests', async () => {
    const refreshUrls: string[] = []
    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/auth/refresh')) {
        refreshUrls.push(url)
        return respond(200, { accessToken: 'fresh', refreshToken: 'fresh-refresh', expiresIn: 900 })
      }
      // Both reads answer 401 first; after the refresh the replay is allowed through.
      return refreshUrls.length === 0 ? respond(401) : respond(200, { email: 'a@b.pl' })
    })
    vi.stubGlobal('fetch', fetchMock)

    await Promise.all([authApi.getCurrentUser(), authApi.getCurrentUser()])

    expect(refreshUrls, 'two 401s must share one refresh, not fire two').toHaveLength(1)
    expect(hasTokens()).toBe(true)
  })
})
