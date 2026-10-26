import { fetchApi } from './client'
import type { AdminUser, PagedUsers, AdminUserDetail, AthleteSummary, CalendarRange, PersonalTraining, CreateTrainingBody, UpdateTrainingBody, PasteMode, TrainingComment, ExerciseVideo, PagedExerciseVideos, ExerciseVideoInput, VideoMetadata, TrainingTemplate, TrainingTemplateInput, AthleteGoal, AthleteGoals, GoalInput, TrainingStats } from '../types'

export type EmailAudience = 'MARKETING' | 'ALL' | 'SELECTED'

interface SendUserEmailRequest {
  subject: string
  message: string
  audience: EmailAudience
  userIds?: string[]
}

export const adminApi = {
  // Users
  getUser: (id: string) =>
    fetchApi<AdminUserDetail>(`/admin/users/${id}`),
  getUsers: (params: { search?: string; page?: number; size?: number; sort?: string; direction?: 'asc' | 'desc' } = {}) => {
    const query = new URLSearchParams()
    if (params.search) query.set('search', params.search)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 50))
    query.set('sort', params.sort ?? 'created')
    query.set('direction', params.direction ?? 'desc')
    return fetchApi<PagedUsers>(`/admin/users?${query.toString()}`)
  },
  sendUserEmail: (data: SendUserEmailRequest) =>
    fetchApi<{ recipientCount: number }>('/admin/users/email', { method: 'POST', body: JSON.stringify(data) }),
  deleteUser: (id: string, notify = true) =>
    fetchApi<void>(`/admin/users/${id}?notify=${notify}`, { method: 'DELETE' }),
  /** Everything held about the person as a ZIP (GDPR art. 15/20) — the answer to a data request. */
  exportUserData: (id: string) =>
    fetchApi<Blob>(`/admin/users/${id}/export`, { responseType: 'blob' }),
  forceLogout: (id: string) =>
    fetchApi<void>(`/admin/users/${id}/logout-all`, { method: 'POST' }),
  promoteUser: (id: string) =>
    fetchApi<AdminUser>(`/admin/users/${id}/promote`, { method: 'POST' }),
  demoteUser: (id: string) =>
    fetchApi<AdminUser>(`/admin/users/${id}/demote`, { method: 'POST' }),

  // Personal training calendar (1-on-1)
  /** Erases the 1-on-1 plan for good. The account itself is untouched. */
  eraseTrainingPlan: (id: string) =>
    fetchApi<{ trainings: number; goals: number; notes: number }>(
      `/admin/users/${id}/training-plan`, { method: 'DELETE' }),
  setAthlete: (id: string, enabled: boolean) =>
    fetchApi<AdminUser>(`/admin/users/${id}/athlete`, { method: enabled ? 'POST' : 'DELETE' }),
  getAthletes: () =>
    fetchApi<AthleteSummary[]>('/admin/athletes'),

  getTrainingCalendar: (athleteId: string, from: string, to: string) =>
    fetchApi<CalendarRange>(`/admin/personal-trainings?athleteId=${athleteId}&from=${from}&to=${to}`),
  createPersonalTraining: (athleteId: string, body: CreateTrainingBody) =>
    fetchApi<PersonalTraining>(`/admin/personal-trainings?athleteId=${athleteId}`, {
      method: 'POST', body: JSON.stringify(body),
    }),
  updatePersonalTraining: (id: string, body: UpdateTrainingBody) =>
    fetchApi<PersonalTraining>(`/admin/personal-trainings/${id}`, {
      method: 'PUT', body: JSON.stringify(body),
    }),
  deletePersonalTraining: (id: string) =>
    fetchApi<void>(`/admin/personal-trainings/${id}`, { method: 'DELETE' }),
  duplicatePersonalTraining: (id: string, offsetDays?: number) =>
    fetchApi<PersonalTraining>(`/admin/personal-trainings/${id}/duplicate`, {
      method: 'POST', body: JSON.stringify({ offsetDays }),
    }),
  /**
   * `targetAthleteId` is the calendar on screen, not the one the entry came from — copying a session
   * from one client onto another is the point of the clipboard surviving a switch of client.
   */
  pastePersonalTraining: (sourceId: string, targetDate: string, mode: PasteMode, targetAthleteId: string) =>
    fetchApi<PersonalTraining>('/admin/personal-trainings/paste', {
      method: 'POST', body: JSON.stringify({ sourceId, targetDate, mode, targetAthleteId }),
    }),
  getTrainingComments: (id: string) =>
    fetchApi<TrainingComment[]>(`/admin/personal-trainings/${id}/comments`),
  addTrainingComment: (id: string, body: string) =>
    fetchApi<TrainingComment>(`/admin/personal-trainings/${id}/comments`, {
      method: 'POST', body: JSON.stringify({ body }),
    }),
  /** Its own path, not `.../comments`, so the rate limiter can ration uploads separately. */
  addTrainingPhotoComment: (id: string, photo: File, body: string) => {
    const form = new FormData()
    form.append('trainingId', id)
    form.append('file', photo)
    if (body) form.append('body', body)
    return fetchApi<TrainingComment>('/admin/training-photos', { method: 'POST', body: form })
  },
  deleteTrainingCommentPhoto: (commentId: string) =>
    fetchApi<void>(`/admin/personal-trainings/comments/${commentId}/photo`, { method: 'DELETE' }),
  /** `to` is the last day that was on screen — the server clears the dots only that far. */
  markTrainingCalendarSeen: (athleteId: string, to: string) =>
    fetchApi<void>(`/admin/personal-trainings/mark-seen?athleteId=${athleteId}&to=${to}`, { method: 'POST' }),
  dismissTrainingDeletions: (athleteId: string) =>
    fetchApi<void>(`/admin/personal-trainings/deletions/dismiss?athleteId=${athleteId}`, { method: 'POST' }),

  getAthleteStats: (athleteId: string) =>
    fetchApi<TrainingStats>(`/admin/personal-trainings/stats?athleteId=${athleteId}`),

  // Goals (coach sets them; the client only reads)
  getAthleteGoals: (athleteId: string) =>
    fetchApi<AthleteGoals>(`/admin/personal-trainings/goals?athleteId=${athleteId}`),
  createAthleteGoal: (athleteId: string, data: GoalInput) =>
    fetchApi<AthleteGoal>(`/admin/personal-trainings/goals?athleteId=${athleteId}`, {
      method: 'POST', body: JSON.stringify(data),
    }),
  updateAthleteGoal: (goalId: string, data: GoalInput) =>
    fetchApi<AthleteGoal>(`/admin/personal-trainings/goals/${goalId}`, {
      method: 'PUT', body: JSON.stringify(data),
    }),
  deleteAthleteGoal: (goalId: string) =>
    fetchApi<void>(`/admin/personal-trainings/goals/${goalId}`, { method: 'DELETE' }),
  achieveAthleteGoal: (goalId: string, achievedDate?: string | null) =>
    fetchApi<AthleteGoal>(`/admin/personal-trainings/goals/${goalId}/achieve`, {
      method: 'POST', body: JSON.stringify({ achievedDate: achievedDate ?? null }),
    }),

  // Exercise video library
  getExerciseVideos: (params: { query?: string; includeArchived?: boolean; page?: number; size?: number } = {}) => {
    const q = new URLSearchParams()
    if (params.query) q.set('query', params.query)
    if (params.includeArchived) q.set('includeArchived', 'true')
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 50))
    return fetchApi<PagedExerciseVideos>(`/admin/exercise-videos?${q.toString()}`)
  },
  suggestExerciseVideos: (query: string) =>
    fetchApi<ExerciseVideo[]>(`/admin/exercise-videos/search?query=${encodeURIComponent(query)}`),
  /** Title and embeddability straight from YouTube, so the form can fill in and warn early. */
  getVideoMetadata: (url: string) =>
    fetchApi<VideoMetadata>(`/admin/exercise-videos/metadata?url=${encodeURIComponent(url)}`),
  createExerciseVideo: (data: ExerciseVideoInput) =>
    fetchApi<ExerciseVideo>('/admin/exercise-videos', { method: 'POST', body: JSON.stringify(data) }),
  updateExerciseVideo: (id: string, data: ExerciseVideoInput) =>
    fetchApi<ExerciseVideo>(`/admin/exercise-videos/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteExerciseVideo: (id: string) =>
    fetchApi<void>(`/admin/exercise-videos/${id}`, { method: 'DELETE' }),
  setExerciseVideoArchived: (id: string, archived: boolean) =>
    fetchApi<ExerciseVideo>(`/admin/exercise-videos/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST' }),

  // Training templates
  getTrainingTemplates: () =>
    fetchApi<TrainingTemplate[]>('/admin/training-templates'),
  createTrainingTemplate: (data: TrainingTemplateInput) =>
    fetchApi<TrainingTemplate>('/admin/training-templates', { method: 'POST', body: JSON.stringify(data) }),
  updateTrainingTemplate: (id: string, data: TrainingTemplateInput) =>
    fetchApi<TrainingTemplate>(`/admin/training-templates/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteTrainingTemplate: (id: string) =>
    fetchApi<void>(`/admin/training-templates/${id}`, { method: 'DELETE' }),
}
