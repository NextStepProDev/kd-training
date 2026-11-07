import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { BookOpen, Pencil, Play, Plus, Send, Trash2, X } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { VideoPickerModal } from '../exercise-videos/VideoPickerModal'
import { inputClass, textareaClass } from '../../utils/fieldClass'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'
import type { AthleteMaterial, ExerciseVideo } from '../../types'

/**
 * Materials the coach gives one client to keep: a clip from the library, a written tip, or both,
 * with a question thread under each (the client's answers: "na stałe", "pytanie przy materiale").
 * <p>
 * Same shape as GoalsBoard: `athleteId` present means the coach is looking and may write; null means
 * the client's own page, read-only apart from asking.
 */
export function MaterialsBoard({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const isCoach = athleteId != null
  const queryKey = isCoach ? ['admin', 'materials', athleteId] : ['user', 'my-training', 'materials']

  const [editing, setEditing] = useState<AthleteMaterial | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<AthleteMaterial | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const materialsQuery = useQuery({
    queryKey,
    queryFn: () => (isCoach ? adminApi.getAthleteMaterials(athleteId) : myTrainingApi.getMaterials()),
    staleTime: SHORT_STALE_MS,
    refetchOnMount: 'always',
    // As on the goals board: the key is the person, and a kept placeholder could show one client's
    // materials under another client's name for a frame.
    placeholderData: undefined,
  })
  const materials = materialsQuery.data ?? []

  const invalidate = () => queryClient.invalidateQueries({ queryKey })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteAthleteMaterial(id),
    onMutate: () => setActionError(null),
    onSuccess: () => { setToDelete(null); void invalidate() },
    onError: (e: Error) => setActionError(e.message),
  })

  // A client with nothing from the coach sees nothing here; the coach keeps the empty board as the
  // way in.
  if (!isCoach && materials.length === 0) return null

  return (
    // Same head as the goals board right above it: one page, one way of titling a section.
    <section aria-labelledby="materials-title" className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="materials-title" className="flex items-center gap-3 text-display text-3xl text-white">
            <BookOpen className="h-6 w-6 text-surface-400" aria-hidden="true" />
            {t('library.title')}
          </h2>
          <p className="mt-1 text-sm text-surface-400">{t(isCoach ? 'library.coachSubtitle' : 'library.subtitle')}</p>
        </div>
        {isCoach && (
          <Button variant="primary" size="sm" onClick={() => setEditing('new')}>
            <Plus className="h-4 w-4" />
            {t('library.add')}
          </Button>
        )}
      </div>

      {actionError && (
        <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{actionError}</p>
      )}

      {isCoach && materials.length === 0 && !materialsQuery.isLoading && (
        <p className="rounded-xl border border-dashed border-white/[0.1] p-4 text-sm text-surface-500">{t('library.empty')}</p>
      )}

      {materials.length > 0 && (
        <ul className="space-y-3">
          {materials.map(material => (
            <li key={material.id}>
              <MaterialCard
                material={material}
                isCoach={isCoach}
                onEdit={() => setEditing(material)}
                onDelete={() => setToDelete(material)}
                onAsked={invalidate}
              />
            </li>
          ))}
        </ul>
      )}

      {isCoach && editing && (
        <MaterialFormModal
          athleteId={athleteId}
          material={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void invalidate() }}
        />
      )}

      <ConfirmDialog
        isOpen={toDelete != null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title={t('library.deleteTitle')}
        message={t('library.deleteMessage', { title: toDelete?.title ?? '' })}
        confirmLabel={t('library.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}

function MaterialCard({ material, isCoach, onEdit, onDelete, onAsked }: {
  material: AthleteMaterial
  isCoach: boolean
  onEdit: () => void
  onDelete: () => void
  onAsked: () => void
}) {
  const { t } = useTranslation('calendar')

  return (
    <article className="rounded-xl border border-white/[0.07] bg-surface-900 p-4">
      <div className="flex items-start justify-between gap-3">
        <h3 className="min-w-0 font-semibold text-white">
          {material.title}
          {material.unread && (
            <span className="ml-2 rounded-full bg-white px-2 py-0.5 align-middle text-xs font-semibold text-surface-950">
              {t('library.new')}
            </span>
          )}
        </h3>
        {isCoach && (
          <div className="flex shrink-0 gap-1">
            <button type="button" onClick={onEdit} aria-label={`${t('library.edit')}: ${material.title}`}
              className="rounded-lg p-2 text-surface-400 transition-colors hover:bg-white/5 hover:text-white">
              <Pencil className="h-4 w-4" />
            </button>
            <button type="button" onClick={onDelete} aria-label={`${t('library.delete')}: ${material.title}`}
              className="rounded-lg p-2 text-surface-400 transition-colors hover:bg-white/5 hover:text-white">
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {material.video && <VideoPoster video={material.video} />}
      {material.note && <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-surface-300">{material.note}</p>}

      <QuestionThread material={material} isCoach={isCoach} onAsked={onAsked} />
    </article>
  )
}

/** A poster that turns into the player on click: a page of clips must not load every player at once. */
function VideoPoster({ video }: { video: ExerciseVideo }) {
  const { t } = useTranslation('calendar')
  const [playing, setPlaying] = useState(false)

  if (playing) {
    return (
      <div className="mt-3 aspect-video overflow-hidden rounded-lg border border-surface-800">
        <iframe src={video.embedUrl} title={video.name} className="h-full w-full" loading="lazy"
          allow="accelerometer; encrypted-media; picture-in-picture" allowFullScreen />
      </div>
    )
  }
  return (
    <button type="button" onClick={() => setPlaying(true)} aria-label={`${t('materials.play')}: ${video.name}`}
      className="group mt-3 flex w-full items-center gap-3 rounded-lg border border-surface-800 bg-surface-800/50 p-2 text-left transition-colors hover:border-white/25">
      <span className="relative shrink-0">
        <img src={video.thumbnailUrl} alt="" loading="lazy" className="h-16 w-28 rounded object-cover" />
        <Play className="absolute inset-0 m-auto h-6 w-6 text-white drop-shadow" aria-hidden="true" />
      </span>
      <span className="min-w-0 truncate text-sm text-surface-200">{video.name}</span>
    </button>
  )
}

/** Two people ever, so who wrote a message is the whole story: left for the other side, right for you. */
function QuestionThread({ material, isCoach, onAsked }: {
  material: AthleteMaterial
  isCoach: boolean
  onAsked: () => void
}) {
  const { t } = useTranslation('calendar')
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fieldId = `material-question-${material.id}`

  const askMutation = useMutation({
    mutationFn: (text: string) => (isCoach
      ? adminApi.replyToMaterial(material.id, text)
      : myTrainingApi.askAboutMaterial(material.id, text)),
    onSuccess: () => { setBody(''); setError(null); onAsked() },
    onError: (e: Error) => setError(e.message),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const text = body.trim()
    if (text) askMutation.mutate(text)
  }

  return (
    <div className="mt-4 border-t border-white/[0.07] pt-3">
      {material.questions.length > 0 && (
        <>
          <h4 className="mb-2 text-xs uppercase tracking-wide text-surface-500">{t('library.questions')}</h4>
          <ul className="mb-3 space-y-2">
            {material.questions.map(q => {
              const mine = q.fromCoach === isCoach
              return (
                <li key={q.id} className={clsx('flex', mine ? 'justify-end' : 'justify-start')}>
                  <div className={clsx('max-w-[85%] rounded-2xl px-3 py-2 text-sm',
                    mine ? 'bg-white text-surface-950' : 'bg-surface-800 text-surface-100')}>
                    <p className={clsx('mb-0.5 text-xs font-medium', mine ? 'text-surface-600' : 'text-surface-400')}>
                      {mine ? t('library.you') : t(q.fromCoach ? 'library.coach' : 'library.client')}
                    </p>
                    <p className="whitespace-pre-line">{q.body}</p>
                  </div>
                </li>
              )
            })}
          </ul>
        </>
      )}
      <form onSubmit={submit} className="flex items-end gap-2">
        <label htmlFor={fieldId} className="sr-only">{t(isCoach ? 'library.replyPlaceholder' : 'library.askPlaceholder')}</label>
        <textarea
          id={fieldId}
          rows={1}
          maxLength={1000}
          value={body}
          onChange={e => setBody(e.target.value)}
          placeholder={t(isCoach ? 'library.replyPlaceholder' : 'library.askPlaceholder')}
          className={`min-w-0 flex-1 ${textareaClass}`}
        />
        <Button type="submit" variant="secondary" size="sm" disabled={!body.trim()} loading={askMutation.isPending}
          aria-label={t('library.send')}>
          <Send className="h-4 w-4" />
        </Button>
      </form>
      {error && <p role="alert" className="mt-1 text-xs text-rose-400">{error}</p>}
    </div>
  )
}

function MaterialFormModal({ athleteId, material, onClose, onSaved }: {
  athleteId: string
  material: AthleteMaterial | null
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  const [title, setTitle] = useState(material?.title ?? '')
  const [note, setNote] = useState(material?.note ?? '')
  const [video, setVideo] = useState<ExerciseVideo | null>(material?.video ?? null)
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: () => {
      const input = { title: title.trim(), note: note.trim() || null, videoId: video?.id ?? null }
      return material
        ? adminApi.updateAthleteMaterial(material.id, input)
        : adminApi.createAthleteMaterial(athleteId, input)
    },
    onSuccess: onSaved,
    onError: (e: Error) => setError(e.message),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    saveMutation.mutate()
  }

  return (
    <>
      <Modal isOpen onClose={onClose} title={t(material ? 'library.formTitleEdit' : 'library.formTitleNew')}>
        <form onSubmit={submit} className="space-y-4">
          <div>
            <label htmlFor="material-title" className="mb-1 block text-sm font-medium text-surface-300">{t('library.titleLabel')}</label>
            <input id="material-title" required maxLength={150} value={title}
              onChange={e => setTitle(e.target.value)} className={inputClass} />
          </div>
          <div>
            <p className="mb-1 text-sm font-medium text-surface-300">{t('library.videoLabel')}</p>
            {video ? (
              <div className="flex items-center gap-3 rounded-lg border border-surface-800 p-2">
                <img src={video.thumbnailUrl} alt="" className="h-12 w-20 shrink-0 rounded object-cover" />
                <span className="min-w-0 flex-1 truncate text-sm text-surface-200">{video.name}</span>
                <button type="button" onClick={() => setVideo(null)} aria-label={t('library.removeVideo')}
                  className="rounded-lg p-2 text-surface-400 hover:bg-white/5 hover:text-white">
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <Button type="button" variant="secondary" size="sm" onClick={() => setPicking(true)}>
                {t('library.pickVideo')}
              </Button>
            )}
          </div>
          <div>
            <label htmlFor="material-note" className="mb-1 block text-sm font-medium text-surface-300">{t('library.noteLabel')}</label>
            <textarea id="material-note" rows={4} maxLength={2000} value={note}
              onChange={e => setNote(e.target.value)} className={textareaClass} />
          </div>
          {error && <p role="alert" className="text-sm text-rose-400">{error}</p>}
          <div className="flex justify-end">
            <Button type="submit" variant="primary" loading={saveMutation.isPending}>{t('library.save')}</Button>
          </div>
        </form>
      </Modal>
      {picking && <VideoPickerModal onClose={() => setPicking(false)} onPick={setVideo} />}
    </>
  )
}
