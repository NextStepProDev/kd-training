import { describe, expect, it } from 'vitest'
import { shouldRetryQuery } from './queryFreshness'
import { ApiError } from './errors'

/**
 * The retry rule used to be the constant `1`, which meant React Query answered a 429 by spending
 * another request on a bucket it already knew was full.
 */
describe('shouldRetryQuery', () => {
  it('shouldNotRetryWhenRateLimited', () => {
    expect(shouldRetryQuery(0, new ApiError('Zbyt wiele żądań', 429, 'TOO_MANY_REQUESTS', 60))).toBe(false)
  })

  it('shouldRetryOnceOnAServerError', () => {
    const error = new ApiError('Błąd serwera', 500, null)
    expect(shouldRetryQuery(0, error)).toBe(true)
    expect(shouldRetryQuery(1, error), 'one retry, not a loop').toBe(false)
  })

  /** A network failure arrives as a plain Error, and those are worth one more attempt. */
  it('shouldRetryOnceOnANetworkError', () => {
    expect(shouldRetryQuery(0, new Error('Błąd sieci'))).toBe(true)
  })
})
