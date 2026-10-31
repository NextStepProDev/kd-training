export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  privacyAccepted: boolean
  termsAccepted: boolean
  marketingConsent: boolean
  /** 1-on-1 coaching client — unlocks the personal training calendar. Set by an admin. */
  isAthlete: boolean
  /**
   * Explicit consent (GDPR art. 9) to the calendar's health data — weigh-ins, weight goals,
   * calorie targets, effort ratings. False for every client until they pass the consent screen;
   * /api/user/my-training answers 409 without it.
   */
  trainingConsent: boolean
  preferredLanguage: string
  hasPassword: boolean
  avatarUrl: string | null
  createdAt: string
}

export interface AdminUser {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  emailVerified: boolean
  marketingConsent: boolean
  isAthlete: boolean
  createdAt: string
}

export interface PagedUsers {
  content: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminUserDetail {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  emailVerified: boolean
  marketingConsent: boolean
  isAthlete: boolean
  preferredLanguage: string
  hasPassword: boolean
  oauthLinked: boolean
  avatarUrl: string | null
  createdAt: string
}

/** One row of the coach's 1-on-1 roster. */
export interface AthleteSummary {
  id: string
  firstName: string
  lastName: string
  email: string
  avatarUrl: string | null
  unreadCount: number
}

/** Computed server-side, never stored — the frontend only colours it. */
export type TrainingStatus = 'PLANNED' | 'COMPLETED' | 'MISSED'

/**
 * What an entry on the plan is. A TASK ("max 2200 kcal today") is a separate entry from the training
 * it shares a day with, so the session and the diet are ticked off — and fail — independently.
 * Fixed when the entry is created; there is no endpoint that changes it.
 */
export type TrainingKind = 'TRAINING' | 'TASK'

export interface PersonalTraining {
  id: string
  kind: TrainingKind
  date: string
  /** Null is the normal case — an untimed training means "do this that day". */
  startTime: string | null
  endTime: string | null
  title: string
  description: string | null
  /** Tasks only, and optional there too — a task can be "wypij 3 l wody" with no number. */
  targetCalories: number | null
  status: TrainingStatus
  completedAt: string | null
  feedback: string | null
  rpe: number | null
  createdByAdmin: boolean
  lastModifiedByAdmin: boolean
  /** The other side changed this since we last looked — drives the dot on the card. */
  unread: boolean
  commentCount: number
  attachments: Attachment[]
  /** Echoed back on update so a concurrent edit is caught instead of silently overwritten. */
  version: number
  createdAt: string
  updatedAt: string
}

export interface DeletedTrainingNotice {
  id: string
  date: string
  startTime: string | null
  title: string
  deletedAt: string
}

export interface CalendarRange {
  from: string
  to: string
  trainings: PersonalTraining[]
  deletions: DeletedTrainingNotice[]
}

export interface TrainingComment {
  /** Null when the comment is a bare photo — a screenshot says enough on its own. */
  body: string | null
  id: string
  fromCoach: boolean
  authorName: string | null
  createdAt: string
  photo: TrainingCommentPhoto | null
}

export interface TrainingCommentPhoto {
  /** Needs the bearer token — health data never goes through the public /api/files namespace. */
  url: string
  width: number
  height: number
  /** When the retention sweep removes it. Shown, so its disappearance is never a surprise. */
  expiresAt: string
  canDelete: boolean
}

export interface MyTrainingSummary {
  unreadCount: number
  deletedCount: number
  nextTrainingDate: string | null
}

export interface CreateTrainingBody {
  /** Omitted means TRAINING. */
  kind?: TrainingKind
  date: string
  startTime?: string | null
  endTime?: string | null
  title: string
  description?: string | null
  /** Ignored by the server on a training. */
  targetCalories?: number | null
  /** undefined = leave materials alone · [] = clear · list = replace. */
  attachments?: AttachmentInput[] | null
}

/** No kind: an entry is a training or a task from birth, and the server will not change it. */
export interface UpdateTrainingBody extends CreateTrainingBody {
  version: number
}

export type PasteMode = 'COPY' | 'MOVE'

export interface TrainingStats {
  thisMonthCount: number
  prevMonthCount: number
  totalCount: number
  firstActivityDate: string | null
  currentStreakWeeks: number
  bestStreakWeeks: number
  avgPerMonth: number | null
  /** Non-zero days only, keyed 'YYYY-MM-DD'. */
  heatmap: Record<string, number>
  /** Null when nothing was planned — 0% would claim a failure that never happened. */
  attendancePercent: number | null
  avgRpeOverall: number | null
  avgRpeRecent: number | null
  rpeDistribution: { light: number; medium: number; hard: number }
  /**
   * Tasks, counted apart from every training number above. Every count comes with the denominator it
   * belongs to: "3 done this month" could be three of three or three of twelve, and a bare 0 cannot
   * tell a month of blown ceilings from a month where none were set. `completionPercent` is null
   * until something has actually come due — 0% would report a failure nobody had.
   */
  tasks: {
    thisMonthDone: number
    thisMonthDue: number
    windowDone: number
    windowDue: number
    completionPercent: number | null
  }
  /** Absent from the client's response entirely — this signal is for the coach. */
  overtraining?: boolean
}

export type GoalHorizon = 'SHORT' | 'MEDIUM' | 'LONG'

export interface AthleteGoal {
  id: string
  horizon: GoalHorizon
  content: string
  targetDate: string | null
  achievedAt: string | null
  /**
   * Set since the client last opened the plan. Absent for the coach — a goal they wrote is never
   * news to them, so the field is missing from their JSON rather than false, same as `overtraining`.
   */
  unread?: boolean
}

export interface AthleteGoals {
  active: AthleteGoal[]
  achieved: AthleteGoal[]
}

export interface GoalInput {
  horizon: GoalHorizon
  content: string
  targetDate?: string | null
}

export type AttachmentKind = 'LINK' | 'VIDEO'

export interface Attachment {
  id: string
  kind: AttachmentKind
  label: string | null
  url: string | null
  videoId: string | null
  videoName: string | null
  /** Canonical player URL, built server-side from the video id. */
  embedUrl: string | null
  thumbnailUrl: string | null
}

export interface AttachmentInput {
  kind: AttachmentKind
  label?: string | null
  url?: string | null
  videoId?: string | null
}

export interface ExerciseVideo {
  id: string
  name: string
  url: string
  description: string | null
  embedUrl: string
  thumbnailUrl: string
  archived: boolean
}

export interface PagedExerciseVideos {
  content: ExerciseVideo[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** What YouTube says about a pasted link, before anything is saved. */
export interface VideoMetadata {
  /** OK · UNAVAILABLE (private, deleted, embedding off) · UNKNOWN (YouTube did not answer). */
  status: 'OK' | 'UNAVAILABLE' | 'UNKNOWN'
  title: string | null
  authorName: string | null
  thumbnailUrl: string
  /** Name of the clip already holding this video id — matching is on the id, not the URL. */
  duplicateName: string | null
}

export interface ExerciseVideoInput {
  name: string
  url: string
  description?: string | null
}

export interface TrainingTemplate {
  id: string
  title: string
  description: string | null
  defaultDurationMinutes: number | null
  attachments: Attachment[]
}

export interface TrainingTemplateInput {
  title: string
  description?: string | null
  defaultDurationMinutes?: number | null
  attachments?: AttachmentInput[] | null
}
