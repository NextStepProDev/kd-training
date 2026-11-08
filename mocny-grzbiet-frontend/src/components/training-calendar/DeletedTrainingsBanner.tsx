import { useTranslation } from 'react-i18next'
import { TriangleAlert, X } from 'lucide-react'
import { formatLongDate } from '../../utils/calendarRange'
import type { DeletedTrainingNotice } from '../../types'

/**
 * A deleted future training leaves nothing on the grid to notice, so the loss has to be announced
 * explicitly. Dismissing is its own act — separate from "I opened the calendar", because seeing the
 * week is not the same as accepting that Tuesday is gone.
 */
export function DeletedTrainingsBanner({
  deletions, onDismiss, dismissing,
}: {
  deletions: DeletedTrainingNotice[]
  onDismiss: () => void
  dismissing: boolean
}) {
  const { t } = useTranslation('calendar')
  if (deletions.length === 0) return null

  return (
    <div className="rounded-lg border border-rose-500/40 bg-rose-500/10 px-3 py-2">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-rose-200">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="font-medium">{t('deletions.title', { count: deletions.length })}</p>
            <ul className="mt-1 space-y-0.5 text-rose-300/90">
              {deletions.map(d => (
                <li key={d.id}>
                  {formatLongDate(d.date)}
                  {d.startTime ? ` · ${d.startTime.slice(0, 5)}` : ''} — {d.title}
                </li>
              ))}
            </ul>
          </div>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          disabled={dismissing}
          aria-label={t('deletions.dismiss')}
          title={t('deletions.dismiss')}
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded text-rose-300 hover:text-rose-100 disabled:opacity-50"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
