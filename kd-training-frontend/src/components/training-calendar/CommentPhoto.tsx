import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageOff, Trash2 } from 'lucide-react'
import { fetchApiBlob } from '../../api/client'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { Modal } from '../ui/Modal'
import type { TrainingCalendarAdapter } from './adapter'
import type { TrainingCommentPhoto } from '../../types'

/**
 * A photo inside a comment bubble: thumbnail in the thread, full size on click.
 *
 * It cannot be a plain `<img src>`. The endpoint requires a bearer token — these are screenshots of
 * someone's health data and never enter the public file namespace — and an `<img>` sends no
 * Authorization header. So the bytes are fetched, and an object URL is made from the blob.
 *
 * The BLOB is what React Query caches, never the object URL: two bubbles rendering the same photo
 * would otherwise share one URL, and the first to unmount would revoke it out from under the other.
 * Each component makes its own URL and revokes only that one.
 */
export function CommentPhoto({
  photo, commentId, adapter,
}: {
  photo: TrainingCommentPhoto
  commentId: string
  adapter: TrainingCalendarAdapter
}) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const inView = useInView(containerRef)

  const photoQuery = useQuery({
    queryKey: ['training-photo', commentId],
    queryFn: () => fetchApiBlob(photo.url),
    // Only once it scrolls into the thread. A months-old conversation should not pull down every
    // screenshot in it the moment the training is opened.
    enabled: inView,
    staleTime: Infinity,
    retry: false,
  })

  const objectUrl = useObjectUrl(photoQuery.data)

  const deleteMutation = useMutation({
    mutationFn: () => adapter.deleteCommentPhoto(commentId),
    onSuccess: async () => {
      setOpen(false)
      setConfirmingDelete(false)
      queryClient.removeQueries({ queryKey: ['training-photo', commentId] })
      await queryClient.invalidateQueries({ queryKey: ['training-comments'] })
    },
  })

  // Height of the thumbnail box, so the bubble does not jump when the bytes land. The server sends
  // the stored dimensions precisely for this.
  const ratio = photo.height > 0 ? photo.width / photo.height : 1

  return (
    <div ref={containerRef} className="mt-2">
      {objectUrl ? (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="block overflow-hidden rounded-lg border border-surface-700 transition-opacity hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-500"
          title={t('comments.photoOpen')}
        >
          <img
            src={objectUrl}
            width={photo.width}
            height={photo.height}
            alt={t('comments.photoAlt')}
            className="max-h-44 w-auto object-cover"
          />
        </button>
      ) : photoQuery.isError ? (
        <p className="flex items-center gap-1.5 rounded-lg bg-surface-800 px-3 py-2 text-xs text-surface-400">
          <ImageOff className="h-3.5 w-3.5 shrink-0" />
          {t('comments.photoUnavailable')}
        </p>
      ) : (
        <div
          className="flex max-h-44 items-center justify-center rounded-lg bg-surface-800"
          style={{ aspectRatio: ratio, height: '11rem' }}
        >
          <LoadingSpinner size="sm" />
        </div>
      )}

      <p className="mt-1 text-[11px] text-surface-500">
        {t('comments.photoExpires', { date: new Date(photo.expiresAt).toLocaleDateString('pl-PL') })}
      </p>

      {/* Reuses <Modal> rather than a bespoke overlay: this opens ON TOP of the training detail
          modal, and the shared stack is what makes Escape close only this one and the page scroll
          stay locked underneath. */}
      <Modal isOpen={open} onClose={() => setOpen(false)} title={t('comments.photoPreviewTitle')} size="2xl">
        {objectUrl && (
          <img
            src={objectUrl}
            alt={t('comments.photoAlt')}
            className="mx-auto max-h-[70vh] w-auto rounded-lg object-contain"
          />
        )}
        <div className="mt-4 flex items-center justify-between gap-3 border-t border-surface-800 pt-4">
          <p className="text-xs text-surface-500">
            {t('comments.photoExpires', { date: new Date(photo.expiresAt).toLocaleDateString('pl-PL') })}
          </p>
          {photo.canDelete && (
            confirmingDelete ? (
              <div className="flex items-center gap-2">
                <span className="text-xs text-surface-300">{t('comments.photoDeleteConfirm')}</span>
                <Button variant="secondary" size="sm" onClick={() => setConfirmingDelete(false)}>
                  {t('comments.photoDeleteCancel')}
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  loading={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate()}
                >
                  {t('comments.photoDelete')}
                </Button>
              </div>
            ) : (
              <Button variant="secondary" size="sm" onClick={() => setConfirmingDelete(true)}>
                <Trash2 className="mr-1.5 h-4 w-4" />
                {t('comments.photoDelete')}
              </Button>
            )
          )}
        </div>
        {deleteMutation.isError && (
          <p role="alert" className="mt-2 rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
            {deleteMutation.error.message}
          </p>
        )}
      </Modal>
    </div>
  )
}

/** True once the element has been on screen — it never flips back, so scrolling past does not unload. */
function useInView(ref: React.RefObject<HTMLElement | null>): boolean {
  const [seen, setSeen] = useState(false)

  useEffect(() => {
    if (seen) return
    const element = ref.current
    // jsdom and very old browsers have no observer: show everything rather than nothing.
    if (!element || typeof IntersectionObserver === 'undefined') {
      setSeen(true)
      return
    }
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        setSeen(true)
        observer.disconnect()
      }
    }, { rootMargin: '200px' })
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref, seen])

  return seen
}

/** Per-component object URL over a shared blob, revoked when this component is done with it. */
function useObjectUrl(blob: Blob | undefined): string | null {
  const url = useMemo(() => (blob ? URL.createObjectURL(blob) : null), [blob])

  useEffect(() => {
    if (!url) return
    return () => URL.revokeObjectURL(url)
  }, [url])

  return url
}
