// Fencing React Query's keepPreviousData to one entity.
//
// The global default (main.tsx) keeps the previous result on screen while the next one loads, which
// is what makes paging and filtering feel instant instead of blanking to a spinner. That default is
// right whenever the key change means "another page of the same thing" and wrong whenever it means
// "another thing" — showing one client's goals under another client's name, even for a frame, is a
// worse failure than a spinner.
//
// Keys in this app are written so the two cases can be told apart by position: the entity comes
// first, the parameters that pick a page or window come last.

import type { QueryKey } from '@tanstack/react-query'

/**
 * Whether two keys address the same entity — they differ only in their last `paramCount` elements.
 */
export function sameEntity(a: QueryKey, b: QueryKey, paramCount: number): boolean {
  if (a.length !== b.length) return false
  const shared = a.length - paramCount
  for (let i = 0; i < shared; i++) {
    if (a[i] !== b[i]) return false
  }
  return true
}

/**
 * Previous data, but only from within the same entity — otherwise nothing, so the view falls back to
 * its loading state.
 *
 * Meant to be handed React Query's two placeholder arguments straight through:
 * `placeholderData: (previous, previousQuery) => keepWithinEntity(previous, previousQuery, key, 1)`
 */
export function keepWithinEntity<T>(
  previous: T | undefined,
  previousQuery: { queryKey: QueryKey } | undefined,
  key: QueryKey,
  paramCount: number,
): T | undefined {
  return previousQuery != null && sameEntity(previousQuery.queryKey, key, paramCount)
    ? previous
    : undefined
}
