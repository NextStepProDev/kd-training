import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Archive, ArchiveRestore, Film, Pencil, Trash2 } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { VideoSearchInput } from '../../components/exercise-videos/VideoSearchInput'
import { useToast } from '../../context/ToastContext'
import { parseYouTubeId, youTubeEmbedUrl } from '../../utils/youtube'
import type { ExerciseVideo, VideoMetadata } from '../../types'
import { inputClass } from '../../utils/fieldClass'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'


export function AdminExerciseVideos() {
  const { t } = useTranslation('calendar')
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [query, setQuery] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [editing, setEditing] = useState<ExerciseVideo | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [toDelete, setToDelete] = useState<ExerciseVideo | null>(null)

  const videosQuery = useQuery({
    queryKey: ['admin', 'exercise-videos', query, includeArchived],
    queryFn: () => adminApi.getExerciseVideos({ query, includeArchived }),
    staleTime: SHORT_STALE_MS,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'exercise-videos'] })

  const archiveMutation = useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      adminApi.setExerciseVideoArchived(id, archived),
    onSuccess: () => { invalidate(); showToast(t('videos.saved')) },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteExerciseVideo(id),
    onSuccess: () => { setToDelete(null); invalidate(); showToast(t('videos.deleted')) },
    // A clip in use answers 409 with the "archive it instead" wording from the server.
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Film className="h-5 w-5 text-primary-400" />
          <div>
            <h2 className="text-lg font-semibold text-surface-100">{t('videos.title')}</h2>
            <p className="text-sm text-surface-400">{t('videos.subtitle')}</p>
          </div>
        </div>
        <Button variant="primary" size="sm" onClick={() => { setEditing(null); setFormOpen(true) }}>
          {t('videos.add')}
        </Button>
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-3">
        <div className="min-w-56 flex-1">
          <VideoSearchInput value={query} onChange={setQuery} placeholder={t('videos.searchPlaceholder')} />
        </div>
        <label className="flex items-center gap-2 text-sm text-surface-300">
          <input type="checkbox" className="h-4 w-4 accent-primary-500"
            checked={includeArchived} onChange={e => setIncludeArchived(e.target.checked)} />
          {t('videos.showArchived')}
        </label>
      </div>

      {videosQuery.isLoading ? (
        <LoadingSpinner />
      ) : videosQuery.data && videosQuery.data.content.length === 0 ? (
        <p className="text-sm text-surface-500">{t('videos.empty')}</p>
      ) : (
        <ul className={clsx('space-y-2 transition-opacity', videosQuery.isFetching && 'opacity-60')}>
          {videosQuery.data?.content.map(video => (
            <li key={video.id}
              className={clsx(
                'flex items-center gap-3 rounded-lg border border-surface-800 bg-surface-800/50 p-2',
                video.archived && 'opacity-60',
              )}>
              <img src={video.thumbnailUrl} alt="" loading="lazy"
                className="h-12 w-20 shrink-0 rounded object-cover" />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-surface-100">
                  {video.name}
                  {video.archived && (
                    <span className="ml-2 rounded-full bg-surface-800 px-2 py-0.5 text-xs text-surface-400">
                      {t('videos.archived')}
                    </span>
                  )}
                </p>
              </div>
              <Button variant="ghost" size="sm" aria-label={t('videos.edit')}
                onClick={() => { setEditing(video); setFormOpen(true) }}>
                <Pencil className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="sm"
                aria-label={video.archived ? t('videos.restore') : t('videos.archive')}
                onClick={() => archiveMutation.mutate({ id: video.id, archived: !video.archived })}>
                {video.archived ? <ArchiveRestore className="h-4 w-4" /> : <Archive className="h-4 w-4" />}
              </Button>
              <Button variant="danger" size="sm" aria-label={t('videos.delete')}
                onClick={() => setToDelete(video)}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {formOpen && (
        <VideoFormModal
          key={editing?.id ?? 'new'}
          video={editing}
          onClose={() => setFormOpen(false)}
          onSaved={() => { setFormOpen(false); invalidate() }}
        />
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title={t('videos.deleteTitle')}
        message={t('videos.deleteMessage', { name: toDelete?.name ?? '' })}
        confirmLabel={t('videos.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}

function VideoFormModal({
  video, onClose, onSaved,
}: {
  video: ExerciseVideo | null
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  const [name, setName] = useState(video?.name ?? '')
  const [url, setUrl] = useState(video?.url ?? '')
  const [description, setDescription] = useState(video?.description ?? '')
  // Keyed by the video id it describes, so a lookup for a link that has since been retyped is
  // ignored rather than cleared — no state written while the effect body runs.
  const [lookup, setLookup] = useState<{ videoId: string; data: VideoMetadata } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // Live preview straight from the pasted URL. This is the only practical way to catch YouTube's
  // "private" setting, which refuses to embed at all — the coach sees a blank player here rather
  // than the client discovering it a week later.
  const videoId = parseYouTubeId(url)

  /*
   * Ask YouTube what the clip is called as soon as the link resolves to an id.
   *
   * The title only ever fills an EMPTY name field: overwriting what somebody typed would undo their
   * own wording, and "Przysiad — wariant dla Kuby" beats "HOW TO SQUAT PROPERLY 2024" on a plan.
   * Editing an existing entry never triggers it either — that name was already chosen once.
   */
  const found = lookup?.videoId === videoId ? lookup.data : null

  useEffect(() => {
    if (!videoId) return
    let current = true
    adminApi.getVideoMetadata(url.trim())
      .then(data => {
        if (!current) return
        setLookup({ videoId, data })
        if (data.title && video == null) {
          setName(previous => (previous.trim() === '' ? data.title! : previous))
        }
      })
      // Silent by design: this is help, not a requirement. The form saves either way.
      .catch(() => {})
    return () => { current = false }
    // Keyed on the resolved id: retyping the same link in another shape is the same video.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoId])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim() || !url.trim() || saving) return
    setError(null)
    setSaving(true)
    try {
      const body = {
        name: name.trim(),
        url: url.trim(),
        description: description.trim() || null,
      }
      if (video) await adminApi.updateExerciseVideo(video.id, body)
      else await adminApi.createExerciseVideo(body)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={video ? t('videos.editTitle') : t('videos.addTitle')}>
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label htmlFor="video-url" className="mb-1 block text-sm text-surface-300">{t('videos.url')}</label>
          <input id="video-url" className={inputClass} value={url} onChange={e => setUrl(e.target.value)} />
          <p className="mt-1 text-xs text-surface-500">{t('videos.unlistedHint')}</p>
          {/* YouTube's own answer about the clip, not a guess from the empty player below. */}
          {found?.status === 'UNAVAILABLE' && (
            <p role="alert" className="mt-1 text-xs text-amber-300">{t('videos.notEmbeddable')}</p>
          )}
          {found?.duplicateName && (
            <p className="mt-1 text-xs text-amber-300">
              {t('videos.alreadyInLibrary', { name: found.duplicateName })}
            </p>
          )}
        </div>

        {videoId && (
          <div className="overflow-hidden rounded-lg border border-surface-800">
            <div className="aspect-video">
              <iframe src={youTubeEmbedUrl(videoId)} title={t('videos.preview')}
                className="h-full w-full" loading="lazy" allowFullScreen />
            </div>
            <p className="px-2 py-1 text-xs text-surface-400">{t('videos.previewHint')}</p>
          </div>
        )}

        <div>
          <label htmlFor="video-name" className="mb-1 block text-sm text-surface-300">{t('videos.name')}</label>
          <input id="video-name" className={inputClass} value={name} maxLength={150}
            onChange={e => setName(e.target.value)} />
          {found?.authorName && (
            <p className="mt-1 text-xs text-surface-500">{t('videos.channel', { name: found.authorName })}</p>
          )}
        </div>

        <div>
          <label htmlFor="video-description" className="mb-1 block text-sm text-surface-300">{t('videos.description')}</label>
          <textarea id="video-description" className={`${inputClass} min-h-20`} value={description}
            maxLength={1000} onChange={e => setDescription(e.target.value)} />
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving}
            disabled={!name.trim() || !url.trim()}>
            {t('form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
