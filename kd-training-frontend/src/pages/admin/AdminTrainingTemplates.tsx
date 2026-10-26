import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LayoutTemplate, Pencil, Trash2 } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import type { TrainingTemplate } from '../../types'
import { inputClass } from '../../utils/fieldClass'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'


/**
 * Reusable skeletons. Applying one COPIES its content into a training, so editing a template never
 * rewrites sessions already handed out — those describe what somebody actually did.
 */
export function AdminTrainingTemplates() {
  const { t } = useTranslation('calendar')
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState<TrainingTemplate | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [toDelete, setToDelete] = useState<TrainingTemplate | null>(null)

  const templatesQuery = useQuery({
    queryKey: ['admin', 'training-templates'],
    queryFn: () => adminApi.getTrainingTemplates(),
    staleTime: SHORT_STALE_MS,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'training-templates'] })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteTrainingTemplate(id),
    onSuccess: () => { setToDelete(null); invalidate(); showToast(t('templates.deleted')) },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <LayoutTemplate className="h-5 w-5 text-primary-400" />
          <div>
            <h2 className="text-lg font-semibold text-surface-100">{t('templates.title')}</h2>
            <p className="text-sm text-surface-400">{t('templates.subtitle')}</p>
          </div>
        </div>
        <Button variant="primary" size="sm" onClick={() => { setEditing(null); setFormOpen(true) }}>
          {t('templates.add')}
        </Button>
      </div>

      {templatesQuery.isLoading ? (
        <LoadingSpinner />
      ) : templatesQuery.data && templatesQuery.data.length === 0 ? (
        <p className="text-sm text-surface-500">{t('templates.empty')}</p>
      ) : (
        <ul className="space-y-2">
          {templatesQuery.data?.map(template => (
            <li key={template.id}
              className="flex items-center gap-3 rounded-lg border border-surface-800 bg-surface-800/50 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-surface-100">{template.title}</p>
                {template.description && (
                  <p className="truncate text-sm text-surface-400">{template.description}</p>
                )}
              </div>
              <Button variant="ghost" size="sm" aria-label={t('templates.edit')}
                onClick={() => { setEditing(template); setFormOpen(true) }}>
                <Pencil className="h-4 w-4" />
              </Button>
              <Button variant="danger" size="sm" aria-label={t('templates.delete')}
                onClick={() => setToDelete(template)}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {formOpen && (
        <TemplateFormModal
          key={editing?.id ?? 'new'}
          template={editing}
          onClose={() => setFormOpen(false)}
          onSaved={() => { setFormOpen(false); invalidate() }}
        />
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title={t('templates.deleteTitle')}
        message={t('templates.deleteMessage', { title: toDelete?.title ?? '' })}
        confirmLabel={t('templates.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}

function TemplateFormModal({
  template, onClose, onSaved,
}: {
  template: TrainingTemplate | null
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  const [title, setTitle] = useState(template?.title ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim() || saving) return
    setError(null)
    setSaving(true)
    try {
      const body = { title: title.trim(), description: description.trim() || null }
      if (template) await adminApi.updateTrainingTemplate(template.id, body)
      else await adminApi.createTrainingTemplate(body)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={template ? t('templates.editTitle') : t('templates.addTitle')}>
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label htmlFor="template-title" className="mb-1 block text-sm text-surface-300">
            {t('templates.name')}
          </label>
          <input id="template-title" className={inputClass} value={title} maxLength={150}
            onChange={e => setTitle(e.target.value)} autoFocus />
        </div>
        <div>
          <label htmlFor="template-description" className="mb-1 block text-sm text-surface-300">
            {t('templates.description')}
          </label>
          <textarea id="template-description" className={`${inputClass} min-h-32`} value={description}
            maxLength={2000} onChange={e => setDescription(e.target.value)} />
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving} disabled={!title.trim()}>
            {t('form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
