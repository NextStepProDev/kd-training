import { ApiError } from './errors'

/**
 * How long a live list or counter may be served from cache before a window focus refetches it.
 *
 * These queries used to carry `staleTime: 0`, and with React Query's default `refetchOnWindowFocus`
 * that meant every single alt-tab refired every mounted one of them at once. The admin panel is the
 * worst case by far, because two of its screens mount one query PER ROW: a list of fifteen events
 * spent fifteen requests on a bucket the backend rations at 60/min, which is how ordinary clicking
 * walked into the rate limiter that then ended the session.
 *
 * Half a minute is safe here for two reasons that are easy to forget:
 *
 *  - `staleTime` does NOT gate `invalidateQueries`. Every mutation on these screens still invalidates
 *    its own list and refetches immediately, so nothing a user just did is ever shown stale.
 *  - `refetchOnMount: 'always'` stays wherever it already is. Mounting is a deliberate navigation
 *    (opening a client, changing the month); regaining focus is not. Only the repeat is suppressed.
 *
 * What remains is a list that can be up to 30 s behind a change made by someone else, on another
 * device — a delay no human perceives, traded for not throttling ourselves.
 */
export const SHORT_STALE_MS = 30_000

/**
 * React Query's retry rule: one retry, except when the limiter said no.
 *
 * The 429 window is a fixed minute wide (Caffeine `expireAfterWrite`), so an immediate retry cannot
 * succeed — it only spends another request against a bucket that is already full, and the failure
 * feeds itself. Exported so the rule can be tested without mounting the app.
 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.isRateLimited) return false
  return failureCount < 1
}
