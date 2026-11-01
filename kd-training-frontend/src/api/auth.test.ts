import { describe, it, expect, afterEach, vi } from 'vitest'
import { verifyEmail } from './auth'

/**
 * One-time tokens from e-mailed links travel in the request body. As `?token=` on a POST they were
 * written into every proxy's access log on the way in; the password reset always used the body.
 */
describe('e-mailed link tokens', () => {
  afterEach(() => vi.restoreAllMocks())

  it('verifyEmail keeps the token out of the URL', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'ok' }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )

    await verifyEmail('secret-token-123')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/verify-email$/)
    expect(String(url)).not.toContain('secret-token-123')
    expect(JSON.parse(String(init?.body))).toEqual({ token: 'secret-token-123' })
  })
})
