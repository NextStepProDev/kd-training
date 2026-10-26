import type { QueryKey } from '@tanstack/react-query'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import type {
  CalendarRange, CreateTrainingBody, PasteMode, PersonalTraining, TrainingComment, UpdateTrainingBody,
} from '../../types'

/**
 * How the shared calendar reaches its data.
 *
 * The contract is deliberately limited to DATA ACCESS. No render functions, no labels, no "should I
 * show X" flags — the reference implementation started that way and the adapter grew into a second
 * component. Anything that differs visually between the coach and the client is rendered by the page
 * AROUND <TrainingCalendar/>, never inside it.
 *
 * Optional methods double as the permission matrix: an absent method means the action does not exist
 * for this role, so there is no separate list of capabilities to fall out of sync.
 */
export interface TrainingCalendarAdapter {
  role: 'coach' | 'athlete'
  athleteId: string
  rangeKey: (from: string, to: string) => QueryKey
  fetchRange: (from: string, to: string) => Promise<CalendarRange>
  createTraining: (body: CreateTrainingBody) => Promise<PersonalTraining>
  updateTraining: (id: string, body: UpdateTrainingBody) => Promise<PersonalTraining>
  deleteTraining: (id: string) => Promise<void>
  duplicateTraining: (id: string, offsetDays?: number) => Promise<PersonalTraining>
  pasteTraining: (sourceId: string, targetDate: string, mode: PasteMode) => Promise<PersonalTraining>
  /**
   * Ticking off is the client's act alone — absent for the coach, so no button renders. `rpe` goes
   * with a training and is left out for a task, which has nothing to rate.
   */
  completeTraining?: (id: string, body: { rpe?: number | null; feedback?: string | null }) => Promise<PersonalTraining>
  uncompleteTraining?: (id: string) => Promise<PersonalTraining>
  getComments: (trainingId: string) => Promise<TrainingComment[]>
  /**
   * One method for both kinds of reply, so the thread component never branches on it. The choice
   * between the JSON endpoint and the multipart one is made here, where it belongs: a plain comment
   * has no reason to travel as a file upload.
   */
  addComment: (trainingId: string, body: string, photo?: File | null) => Promise<TrainingComment>
  /** The words survive; only the picture goes. A photo-only comment goes with it. */
  deleteCommentPhoto: (commentId: string) => Promise<void>
  /**
   * Clears the dots as far as `to`, the last day of the window that was rendered. Claiming more than
   * was on screen is what used to swallow a month nobody opened.
   */
  markSeen: (to: string) => Promise<void>
  dismissDeletions: () => Promise<void>
}

/**
 * Whether this viewer may reshape this entry — edit it, copy it, cut it, repeat it or delete it.
 *
 * A client owns what they logged themselves and nothing else: what the coach assigned is theirs to
 * do, comment on and tick off. Unlike the rest of the permission matrix this cannot live as an
 * absent adapter method, because it is decided per ROW rather than per role — the same client's
 * calendar holds both kinds side by side.
 *
 * The server enforces the same rule; this only keeps buttons off the screen that could only ever
 * fail. Keyed on `createdByAdmin`, which is fixed at creation — never on `lastModifiedByAdmin`,
 * which flips every time either side ticks something off.
 */
export function canReshapeTraining(
  role: TrainingCalendarAdapter['role'],
  training: Pick<PersonalTraining, 'createdByAdmin'>,
): boolean {
  return role === 'coach' || !training.createdByAdmin
}

export function coachAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    role: 'coach',
    athleteId,
    // The athlete id is part of the key: switching between two clients is a different entity, not
    // fresh data for the same one, so their pages must never bleed into each other.
    rangeKey: (from, to) => ['admin', 'training-calendar', athleteId, from, to],
    fetchRange: (from, to) => adminApi.getTrainingCalendar(athleteId, from, to),
    createTraining: (body) => adminApi.createPersonalTraining(athleteId, body),
    updateTraining: (id, body) => adminApi.updatePersonalTraining(id, body),
    deleteTraining: (id) => adminApi.deletePersonalTraining(id),
    duplicateTraining: (id, offsetDays) => adminApi.duplicatePersonalTraining(id, offsetDays),
    // The paste lands in the calendar being viewed, which is not necessarily the one the entry was
    // copied from: the clipboard survives a switch of client on purpose.
    pasteTraining: (sourceId, targetDate, mode) =>
      adminApi.pastePersonalTraining(sourceId, targetDate, mode, athleteId),
    getComments: (id) => adminApi.getTrainingComments(id),
    addComment: (id, body, photo) => photo
      ? adminApi.addTrainingPhotoComment(id, photo, body)
      : adminApi.addTrainingComment(id, body),
    deleteCommentPhoto: (commentId) => adminApi.deleteTrainingCommentPhoto(commentId),
    markSeen: (to) => adminApi.markTrainingCalendarSeen(athleteId, to),
    dismissDeletions: () => adminApi.dismissTrainingDeletions(athleteId),
  }
}

export function athleteAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    role: 'athlete',
    athleteId,
    rangeKey: (from, to) => ['user', 'my-training', 'calendar', from, to],
    fetchRange: (from, to) => myTrainingApi.getCalendar(from, to),
    createTraining: (body) => myTrainingApi.createTraining(body),
    updateTraining: (id, body) => myTrainingApi.updateTraining(id, body),
    deleteTraining: (id) => myTrainingApi.deleteTraining(id),
    duplicateTraining: (id, offsetDays) => myTrainingApi.duplicateTraining(id, offsetDays),
    pasteTraining: (sourceId, targetDate, mode) => myTrainingApi.pasteTraining(sourceId, targetDate, mode),
    completeTraining: (id, body) => myTrainingApi.completeTraining(id, body),
    uncompleteTraining: (id) => myTrainingApi.uncompleteTraining(id),
    getComments: (id) => myTrainingApi.getComments(id),
    addComment: (id, body, photo) => photo
      ? myTrainingApi.addPhotoComment(id, photo, body)
      : myTrainingApi.addComment(id, body),
    deleteCommentPhoto: (commentId) => myTrainingApi.deleteCommentPhoto(commentId),
    markSeen: (to) => myTrainingApi.markSeen(to),
    dismissDeletions: () => myTrainingApi.dismissDeletions(),
  }
}
