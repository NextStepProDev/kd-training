import { fetchApi } from './client'
import type {
  CalendarRange, PersonalTraining,
  CreateTrainingBody, UpdateTrainingBody, PasteMode, TrainingComment, MyTrainingSummary, AthleteGoals, TrainingStats,
} from '../types'

/**
 * The coaching client's own 1-on-1 calendar. Mirrors the admin surface one-for-one — the shared
 * calendar component swaps between the two through an adapter and must not care which it got.
 */
export const myTrainingApi = {
  getCalendar: (from: string, to: string) =>
    fetchApi<CalendarRange>(`/user/my-training/calendar?from=${from}&to=${to}`),
  createTraining: (body: CreateTrainingBody) =>
    fetchApi<PersonalTraining>('/user/my-training/trainings', {
      method: 'POST', body: JSON.stringify(body),
    }),
  updateTraining: (id: string, body: UpdateTrainingBody) =>
    fetchApi<PersonalTraining>(`/user/my-training/trainings/${id}`, {
      method: 'PUT', body: JSON.stringify(body),
    }),
  deleteTraining: (id: string) =>
    fetchApi<void>(`/user/my-training/trainings/${id}`, { method: 'DELETE' }),
  duplicateTraining: (id: string, offsetDays?: number) =>
    fetchApi<PersonalTraining>(`/user/my-training/trainings/${id}/duplicate`, {
      method: 'POST', body: JSON.stringify({ offsetDays }),
    }),
  pasteTraining: (sourceId: string, targetDate: string, mode: PasteMode) =>
    fetchApi<PersonalTraining>('/user/my-training/trainings/paste', {
      method: 'POST', body: JSON.stringify({ sourceId, targetDate, mode }),
    }),
  /** `rpe` on a training, omitted on a task — the server refuses the wrong one for the entry. */
  completeTraining: (id: string, body: { rpe?: number | null; feedback?: string | null }) =>
    fetchApi<PersonalTraining>(`/user/my-training/trainings/${id}/complete`, {
      method: 'POST', body: JSON.stringify(body),
    }),
  uncompleteTraining: (id: string) =>
    fetchApi<PersonalTraining>(`/user/my-training/trainings/${id}/complete`, { method: 'DELETE' }),
  getComments: (id: string) =>
    fetchApi<TrainingComment[]>(`/user/my-training/trainings/${id}/comments`),
  addComment: (id: string, body: string) =>
    fetchApi<TrainingComment>(`/user/my-training/trainings/${id}/comments`, {
      method: 'POST', body: JSON.stringify({ body }),
    }),
  /** Its own path, not `.../comments`, so the rate limiter can ration uploads separately. */
  addPhotoComment: (id: string, photo: File, body: string) => {
    const form = new FormData()
    form.append('trainingId', id)
    form.append('file', photo)
    if (body) form.append('body', body)
    return fetchApi<TrainingComment>('/user/my-training/photos', { method: 'POST', body: form })
  },
  deleteCommentPhoto: (commentId: string) =>
    fetchApi<void>(`/user/my-training/comments/${commentId}/photo`, { method: 'DELETE' }),
  /** `to` is the last day that was on screen — the server clears the dots only that far. */
  markSeen: (to: string) =>
    fetchApi<void>(`/user/my-training/mark-seen?to=${to}`, { method: 'POST' }),
  dismissDeletions: () =>
    fetchApi<void>('/user/my-training/deletions/dismiss', { method: 'POST' }),
  getSummary: () =>
    fetchApi<MyTrainingSummary>('/user/my-training/summary'),
  getGoals: () =>
    fetchApi<AthleteGoals>('/user/my-training/goals'),
  getStats: () =>
    fetchApi<TrainingStats>('/user/my-training/stats'),
}
