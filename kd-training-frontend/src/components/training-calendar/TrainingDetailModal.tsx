import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { CopyPlus, Pencil, Trash2 } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { RpeInput } from './RpeInput'
import { CommentThread } from './CommentThread'
import { AttachmentList } from './AttachmentList'
import { formatLongDate } from '../../utils/calendarRange'
import type { PersonalTraining, TrainingStatus } from '../../types'
import { canReshapeTraining, type TrainingCalendarAdapter } from './adapter'
import { AdminPrivateNote } from '../notes/AdminPrivateNote'

const statusChip: Record<TrainingStatus, string> = {
  PLANNED: 'bg-surface-800 text-surface-300',
  COMPLETED: 'bg-emerald-900/30 text-emerald-400',
  MISSED: 'bg-rose-500/10 text-rose-300',
}

interface TrainingDetailModalProps {
  training: PersonalTraining
  adapter: TrainingCalendarAdapter
  onClose: () => void
  onEdit: (training: PersonalTraining) => void
  onDelete: (training: PersonalTraining) => Promise<unknown>
  onDuplicate: (training: PersonalTraining) => Promise<unknown>
  /**
   * Absent for the coach — ticking off is the client's act alone, so no form renders. `rpe` is null
   * for a task: there is no effort to rate, and the server refuses one.
   */
  onComplete?: (training: PersonalTraining, rpe: number | null, feedback: string | null) => Promise<unknown>
  onUncomplete?: (training: PersonalTraining) => Promise<unknown>
}

export function TrainingDetailModal({
  training, adapter, onClose, onEdit, onDelete, onDuplicate, onComplete, onUncomplete,
}: TrainingDetailModalProps) {
  const { t } = useTranslation('calendar')

  // Keyed by training id at the call site, so opening another card mounts a fresh modal rather than
  // syncing props into state through an effect.
  const [rpe, setRpe] = useState<number | null>(training.rpe)
  const [feedback, setFeedback] = useState(training.feedback ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [footerError, setFooterError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const isTask = training.kind === 'TASK'
  // What the coach assigned is the client's to do and comment on, not to rewrite, repeat elsewhere
  // or clear away. The server refuses all three regardless; hiding them keeps the card honest about
  // what it offers rather than letting a click fail.
  const canReshape = canReshapeTraining(adapter.role, training)
  const canComplete = onComplete != null && training.status !== 'COMPLETED'
  const canUncomplete = onUncomplete != null && training.status === 'COMPLETED'
  // A training needs its effort rating before it can be ticked off; a task has nothing to rate.
  const readyToComplete = isTask || rpe != null

  /** Runs an action, keeping the surface that triggered it open when the server refuses. */
  const run = async (action: () => Promise<unknown>, target: 'form' | 'footer', close: boolean) => {
    setFormError(null)
    setFooterError(null)
    setBusy(true)
    try {
      await action()
      if (close) onClose()
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      if (target === 'form') setFormError(message)
      else setFooterError(message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Modal isOpen onClose={onClose} title={training.title}>
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className={clsx('rounded-full px-2 py-0.5 text-xs', statusChip[training.status])}>
              {t(`status.${training.status}`)}
            </span>
            <span className="text-sm text-surface-400">
              {formatLongDate(training.date)}
              {training.startTime && ` · ${training.startTime.slice(0, 5)}`}
              {training.startTime && training.endTime && `–${training.endTime.slice(0, 5)}`}
            </span>
            <span className="text-xs text-surface-500">
              {training.createdByAdmin ? t('detail.fromCoach') : t('detail.fromAthlete')}
            </span>
          </div>

          {training.targetCalories != null && (
            <p className="text-sm text-sky-300">
              {t('detail.calorieLimit', { value: training.targetCalories })}
            </p>
          )}

          {training.description && (
            <p className="whitespace-pre-wrap text-sm text-surface-300">{training.description}</p>
          )}

          {training.status === 'COMPLETED' && (training.rpe != null || training.feedback) && (
            <div className="rounded-lg border border-surface-800 bg-surface-800/50 p-3 text-sm">
              {/* A task has no rating, so the box carries only what the client wrote — printing
                  "Odczuwalny wysiłek: —" would invite an answer to a question nobody asked. */}
              {training.rpe != null && (
                <p className="text-surface-300">
                  {t('detail.rpeLabel')}: <span className="font-semibold text-surface-100">{training.rpe}</span>
                </p>
              )}
              {training.feedback && (
                <p className="mt-1 whitespace-pre-wrap text-surface-400">{training.feedback}</p>
              )}
            </div>
          )}

          {canComplete && (
            <div className="space-y-3 rounded-lg border border-surface-800 p-3">
              {!isTask && <RpeInput value={rpe} onChange={setRpe} label={t('detail.rpeQuestion')} />}
              <div>
                <label htmlFor="training-feedback" className="mb-1 block text-sm text-surface-300">
                  {t('detail.feedback')}
                </label>
                <textarea
                  id="training-feedback"
                  className="min-h-20 w-full rounded-lg border border-surface-700 bg-surface-800 px-3 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
                  value={feedback}
                  maxLength={2000}
                  onChange={e => setFeedback(e.target.value)}
                />
              </div>
              {/* Right above the button that failed — the reference implementation put this at the
                  bottom of the modal and people believed a failed save had succeeded. */}
              {formError && (
                <p role="alert" className="rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
                  {formError}
                </p>
              )}
              <Button
                variant="primary" size="sm" loading={busy} disabled={!readyToComplete}
                onClick={() => readyToComplete && run(
                  () => onComplete!(training, isTask ? null : rpe, feedback.trim() || null), 'form', false)}
              >
                {t(isTask ? 'detail.markTaskDone' : 'detail.markDone')}
              </Button>
            </div>
          )}

          {training.attachments.length > 0 && (
            <div className="border-t border-surface-800 pt-3">
              <AttachmentList attachments={training.attachments} />
            </div>
          )}

          {/* The coach's own notebook. Gated on the role the modal already carries rather than on a
              global useAuth() — the adapter is this component's source of truth for who is looking. */}
          {adapter.role === 'coach' && (
            <AdminPrivateNote anchor={{ target: 'training', id: training.id }} />
          )}

          <div className="border-t border-surface-800 pt-3">
            <CommentThread trainingId={training.id} adapter={adapter} />
          </div>

          {footerError && (
            <p role="alert" className="rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
              {footerError}
            </p>
          )}

          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-surface-800 pt-3">
            {/* Says why the buttons are missing. Without it the card just looks broken, and the
                client is left wondering whether the plan is theirs to touch at all. */}
            {!canReshape && (
              <p className="mr-auto text-xs text-surface-500">{t('detail.coachOnlyHint')}</p>
            )}
            {canUncomplete && (
              <Button variant="ghost" size="sm" loading={busy}
                onClick={() => run(() => onUncomplete!(training), 'footer', false)}>
                {t('detail.undoDone')}
              </Button>
            )}
            {canReshape && (
              <>
                <Button variant="ghost" size="sm" loading={busy}
                  onClick={() => run(() => onDuplicate(training), 'footer', true)}>
                  <CopyPlus className="mr-1.5 h-4 w-4" />
                  {t('detail.duplicate')}
                </Button>
                <Button variant="secondary" size="sm" onClick={() => onEdit(training)}>
                  <Pencil className="mr-1.5 h-4 w-4" />
                  {t('detail.edit')}
                </Button>
                <Button variant="danger" size="sm" onClick={() => setConfirmDelete(true)}>
                  <Trash2 className="mr-1.5 h-4 w-4" />
                  {t('detail.delete')}
                </Button>
              </>
            )}
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => run(() => onDelete(training), 'footer', true)}
        title={t(isTask ? 'detail.deleteTaskTitle' : 'detail.deleteTitle')}
        message={t('detail.deleteMessage', { title: training.title })}
        confirmLabel={t('detail.delete')}
        danger
        loading={busy}
      />
    </>
  )
}
