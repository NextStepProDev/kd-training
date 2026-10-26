import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ImagePlus, Send, X } from 'lucide-react'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { CommentPhoto } from './CommentPhoto'
import { compressImage, TRAINING_PHOTO_COMPRESSION, validateImageFile } from '../../utils/imageUtils'
import type { TrainingCalendarAdapter } from './adapter'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'

/** Phones shoot HEIC; the canvas pass turns it into the JPEG the server expects. */
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']

/** Two people ever, so bubbles left/right carry the whole "who said this" story. */
export function CommentThread({ trainingId, adapter }: { trainingId: string; adapter: TrainingCalendarAdapter }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const commentsQuery = useQuery({
    queryKey: ['training-comments', trainingId],
    queryFn: () => adapter.getComments(trainingId),
    staleTime: SHORT_STALE_MS,
  })

  const addMutation = useMutation({
    mutationFn: ({ text, file }: { text: string; file: File | null }) =>
      adapter.addComment(trainingId, text, file),
    onSuccess: () => {
      setBody('')
      clearPhoto()
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['training-comments', trainingId] })
    },
    onError: (e: Error) => setError(e.message),
  })

  const clearPhoto = () => {
    setPhoto(null)
    setPhotoPreview(previous => {
      if (previous) URL.revokeObjectURL(previous)
      return null
    })
  }

  const pickPhoto = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // Reset immediately, so picking the same file twice in a row still fires a change event.
    e.target.value = ''
    if (!file) return

    const validationError = validateImageFile(file, ACCEPTED_TYPES)
    if (validationError) {
      setError(validationError)
      return
    }

    setError(null)
    try {
      // Shrunk here rather than on the server: it saves the upload, and the canvas pass is also
      // what strips EXIF, so location never leaves the device in the first place.
      const compressed = await compressImage(file, TRAINING_PHOTO_COMPRESSION)
      clearPhoto()
      setPhoto(compressed)
      setPhotoPreview(URL.createObjectURL(compressed))
    } catch {
      setError(t('comments.photoUnreadable'))
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const text = body.trim()
    if ((!text && !photo) || addMutation.isPending) return
    addMutation.mutate({ text, file: photo })
  }

  const isMine = (fromCoach: boolean) => (adapter.role === 'coach') === fromCoach

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium text-surface-300">{t('comments.title')}</h4>

      {commentsQuery.isLoading ? (
        <LoadingSpinner size="sm" />
      ) : commentsQuery.data && commentsQuery.data.length > 0 ? (
        <ul className="max-h-56 space-y-2 overflow-y-auto pr-1">
          {commentsQuery.data.map(comment => (
            <li key={comment.id} className={clsx('flex', isMine(comment.fromCoach) ? 'justify-end' : 'justify-start')}>
              <div className={clsx(
                'max-w-[85%] rounded-lg px-3 py-2 text-sm',
                isMine(comment.fromCoach)
                  ? 'bg-primary-600/20 text-surface-100'
                  : 'bg-surface-800 text-surface-200',
              )}>
                {comment.body && <p className="whitespace-pre-wrap">{comment.body}</p>}
                {comment.photo && (
                  <CommentPhoto photo={comment.photo} commentId={comment.id} adapter={adapter} />
                )}
                <p className="mt-1 text-[11px] text-surface-500">
                  {comment.authorName ?? t('comments.deletedAuthor')}
                  {' · '}
                  {new Date(comment.createdAt).toLocaleString('pl-PL', {
                    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                  })}
                </p>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-surface-500">{t('comments.empty')}</p>
      )}

      <form onSubmit={submit} className="space-y-2">
        <label htmlFor="comment-body" className="sr-only">{t('comments.placeholder')}</label>
        <textarea
          id="comment-body"
          className="min-h-16 w-full rounded-lg border border-surface-700 bg-surface-800 px-3 py-2 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          placeholder={t('comments.placeholder')}
          value={body}
          maxLength={1000}
          onChange={e => setBody(e.target.value)}
        />

        {photoPreview && (
          <div className="flex items-center gap-3 rounded-lg bg-surface-800 p-2">
            <img src={photoPreview} alt={t('comments.photoAlt')} className="h-16 w-16 rounded object-cover" />
            <p className="flex-1 text-xs text-surface-400">{t('comments.photoReady')}</p>
            <button
              type="button"
              onClick={clearPhoto}
              className="rounded p-1 text-surface-400 transition-colors hover:bg-surface-700 hover:text-surface-200"
              aria-label={t('comments.photoRemove')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {error && (
          <p role="alert" className="rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}

        <div className="flex items-center justify-between gap-3">
          <div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={pickPhoto}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
              disabled={!!photo}
            >
              <ImagePlus className="mr-1.5 h-4 w-4" />
              {t('comments.addPhoto')}
            </Button>
          </div>
          <Button type="submit" variant="secondary" size="sm"
            loading={addMutation.isPending} disabled={!body.trim() && !photo}>
            <Send className="mr-1.5 h-4 w-4" />
            {t('comments.send')}
          </Button>
        </div>

        {/* Said before anything is picked, not after: a warning about other people's data is only
            useful while there is still a choice to make. */}
        <p className="text-[11px] leading-relaxed text-surface-500">{t('comments.photoHint')}</p>
      </form>
    </div>
  )
}
