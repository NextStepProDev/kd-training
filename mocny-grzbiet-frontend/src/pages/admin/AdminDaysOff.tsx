import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarOff, Plus, X } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { DateInput } from '../../components/ui/DateInput'
import { useToast } from '../../context/ToastContext'
import { inputClass } from '../../utils/fieldClass'
import { formatDate, formatDateRange } from '../../utils/dates'
import { todayIso } from '../../utils/calendarRange'
import type { DayOff } from '../../types'

const NOTE_MAX = 200

function rangeLabel(dayOff: DayOff): string {
  return dayOff.startDate === dayOff.endDate
    ? formatDate(dayOff.startDate)
    : formatDateRange(dayOff.startDate, dayOff.endDate)
}

/**
 * The coach's days off: marked once here, shown in every client's calendar. Lists only what is still
 * ahead or going on — a past holiday has nothing left to manage.
 */
export function AdminDaysOff() {
  const { t } = useTranslation('calendar')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [startDate, setStartDate] = useState(todayIso())
  const [endDate, setEndDate] = useState(todayIso())
  const [note, setNote] = useState('')
  const [rangeError, setRangeError] = useState<string | null>(null)

  const daysOffQuery = useQuery({
    queryKey: ['admin', 'days-off'],
    queryFn: adminApi.getDaysOff,
  })
  const daysOff = daysOffQuery.data ?? []

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'days-off'] })
    // Every open calendar shows the days off, so its pages are stale too.
    void queryClient.invalidateQueries({ queryKey: ['admin', 'training-calendar'] })
  }

  const addMutation = useMutation({
    mutationFn: () => adminApi.addDayOff({ startDate, endDate, note: note.trim() || null }),
    onSuccess: () => {
      showToast(t('dayOff.added'))
      setNote('')
      invalidate()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteDayOff(id),
    onSuccess: () => { showToast(t('dayOff.deleted')); invalidate() },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!startDate || !endDate || endDate < startDate) {
      setRangeError(t('dayOff.endBeforeStart'))
      return
    }
    setRangeError(null)
    addMutation.mutate()
  }

  return (
    <section aria-labelledby="days-off-title">
      <div className="mb-4 flex items-center gap-2">
        <CalendarOff className="h-6 w-6 text-surface-400" />
        <div>
          <h2 id="days-off-title" className="text-display text-2xl text-white">{t('dayOff.title')}</h2>
          <p className="text-sm text-surface-400">{t('dayOff.hint')}</p>
        </div>
      </div>

      <form onSubmit={handleSubmit} noValidate
        className="grid gap-3 rounded-2xl border border-white/[0.07] bg-surface-900 p-4 sm:grid-cols-[auto_auto_1fr_auto] sm:items-end">
        <div>
          <label htmlFor="day-off-from" className="mb-1 block text-xs text-surface-400">{t('dayOff.from')}</label>
          <DateInput id="day-off-from" className={inputClass} value={startDate}
            onChange={value => {
              setStartDate(value)
              // Moving the start past the end drags the end along: a one-day off is the usual case.
              if (value && endDate < value) setEndDate(value)
              setRangeError(null)
            }} />
        </div>
        <div>
          <label htmlFor="day-off-to" className="mb-1 block text-xs text-surface-400">{t('dayOff.to')}</label>
          <DateInput id="day-off-to" className={inputClass} value={endDate} min={startDate}
            aria-invalid={!!rangeError}
            aria-describedby={rangeError ? 'day-off-range-error' : undefined}
            onChange={value => { setEndDate(value); setRangeError(null) }} />
        </div>
        <div className="min-w-0">
          <label htmlFor="day-off-note" className="mb-1 block text-xs text-surface-400">{t('dayOff.note')}</label>
          <input id="day-off-note" type="text" className={inputClass} value={note}
            maxLength={NOTE_MAX} placeholder={t('dayOff.notePlaceholder')}
            onChange={e => setNote(e.target.value)} />
        </div>
        <Button type="submit" variant="primary" loading={addMutation.isPending}>
          <Plus className="h-4 w-4" />
          {t('dayOff.add')}
        </Button>
        {rangeError && (
          <p id="day-off-range-error" role="alert" className="text-xs text-rose-400/80 sm:col-span-4">{rangeError}</p>
        )}
      </form>

      {daysOffQuery.isError ? (
        <p className="mt-4 text-sm text-red-400">{t('dayOff.loadError')}</p>
      ) : daysOffQuery.isSuccess && daysOff.length === 0 ? (
        <p className="mt-4 text-sm text-surface-500">{t('dayOff.empty')}</p>
      ) : (
        <ul className="mt-4 space-y-2">
          {daysOff.map(dayOff => (
            <li key={dayOff.id}
              className="flex items-center gap-3 rounded-2xl border border-white/[0.07] bg-surface-900 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-surface-100">{rangeLabel(dayOff)}</p>
                {dayOff.note && <p className="truncate text-sm text-surface-400">{dayOff.note}</p>}
              </div>
              <Button variant="secondary" size="sm"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(dayOff.id)}
                aria-label={t('dayOff.deleteFor', { range: rangeLabel(dayOff) })}>
                <X className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
