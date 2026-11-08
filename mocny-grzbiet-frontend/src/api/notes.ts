import { fetchApi } from './client'

/**
 * Where a private note is pinned. A training is the only target today; the shape (and the
 * `target` segment in the URL) keeps room for another one without changing every caller.
 */
export type NoteAnchor = { target: 'training'; id: string }

export interface PrivateNote {
  body: string | null
  updatedAt: string | null
}

export interface NoteMarkers {
  trainingIds: string[]
}

/** URL and cache key come from the same anchor, so they cannot drift apart. */
export function notePath(anchor: NoteAnchor): string {
  return `/admin/notes/${anchor.target}/${anchor.id}`
}

export function noteKey(anchor: NoteAnchor): string[] {
  return ['admin', 'notes', anchor.target, anchor.id]
}

/** Invalidating this prefix relights markers too — invalidating one note's key would not. */
export const NOTES_KEY_PREFIX = ['admin', 'notes']

export const notesApi = {
  get: (anchor: NoteAnchor) => fetchApi<PrivateNote>(notePath(anchor)),

  save: (anchor: NoteAnchor, body: string) =>
    fetchApi<void>(notePath(anchor), { method: 'PUT', body: JSON.stringify({ body }) }),

  remove: (anchor: NoteAnchor) => fetchApi<void>(notePath(anchor), { method: 'DELETE' }),

  /** Markers for one calendar page: which of this client's trainings carry one of MY notes. */
  markers: (from: string, to: string, athleteId: string) => {
    const params = new URLSearchParams({ from, to, athleteId })
    return fetchApi<NoteMarkers>(`/admin/notes/markers?${params}`)
  },
}
