/**
 * An API failure that still knows what the server said.
 *
 * This lives in `utils` rather than next to `fetchApi` for one hard reason: `api/auth.ts` has to be
 * able to throw it, and `api/client.ts` imports `refreshTokens` from there. Declared in the client,
 * the class would need an import back the other way — a cycle on a class used at runtime, whose
 * failure mode is an `undefined` constructor at module init depending on evaluation order.
 *
 * Keep `message` populated. Every catch block in the UI reads it verbatim, so an ApiError with an
 * empty message shows the user a blank toast.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  /** Seconds from the server's `Retry-After`, when it sent one (the rate limiter always does). */
  readonly retryAfterSeconds?: number

  constructor(message: string, status: number, code: string | null, retryAfterSeconds?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }

  /** A version conflict: the row moved under the form, so the UI needs a "reload" affordance. */
  get isConflict(): boolean {
    return this.status === 409
  }

  /** Rate limited. Always transient — the limiter's window is one fixed minute wide. */
  get isRateLimited(): boolean {
    return this.status === 429
  }

  /**
   * The server actively refused these credentials, and this is the ONLY shape that may end a
   * session. A 429, a 5xx or a dead network say nothing about whether the login is still good;
   * treating them as proof logged people out of accounts that were never in question.
   */
  get isAuthRejection(): boolean {
    return this.status === 401 || this.status === 403
  }
}

/**
 * `Retry-After` in seconds, or undefined when the header is absent or an HTTP-date.
 *
 * Only the seconds form is parsed, because that is the only form we send ("60"). A date would need
 * clock-skew handling to be worth anything, and nothing in the UI counts down anyway.
 */
export function parseRetryAfter(header: string | null): number | undefined {
  if (!header) return undefined
  const seconds = Number(header)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}
