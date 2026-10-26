import { useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { NotebookPen, Pencil, Trash2 } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { textareaClass } from '../../utils/fieldClass'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'
import { NOTES_KEY_PREFIX, noteKey, notesApi, type NoteAnchor } from '../../api/notes'

const MAX_BODY = 4000

/**
 * The owner's private note about one session. Nobody else ever sees it.
 *
 * One component, five mounting points; the host passes an address and nothing else. That is what
 * makes "change how notes behave" one edit rather than five, and it is why the query and the
 * mutations live in here rather than in each host.
 *
 * The host decides where the viewer is the owner. It is never gated on "the session has already
 * happened" — that would remove it from exactly the sessions this feature exists for.
 */
export function AdminPrivateNote({ anchor }: { anchor: NoteAnchor }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [clipped, setClipped] = useState(false)
  const previewRef = useRef<HTMLParagraphElement>(null)

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: noteKey(anchor),
    queryFn: () => notesApi.get(anchor),
    // Not 0. "No cache" here means the author sees their own save at once, and invalidation below
    // delivers that; a zero stale time instead refires every mounted note on each window focus, and
    // three of the hosts mount one per row.
    staleTime: SHORT_STALE_MS,
  })

  const saved = data?.body ?? ''

  /**
   * A read that failed with nothing to fall back on. Narrower than `isError` on purpose: when a
   * background refetch fails, React Query reports an error AND keeps the last good note, and hiding
   * a note we actually hold would be its own kind of wrong. Only the case where the stored text is
   * genuinely unknown blocks the editor.
   */
  const loadFailed = isError && data === undefined

  // `draft` is seeded when edit mode opens, never synced from an effect: outside edit mode the
  // preview reads `saved` directly, so there is nothing to keep in step. Hooks below stay above
  // every early return — one placed after a return breaks the hook-order rule.

  /**
   * Whether the preview is actually cut off — MEASURED, never inferred from the character count.
   * A note bulleted onto ten short lines is clipped at 200 characters; a length threshold would
   * leave the rest of it unreachable behind no control at all.
   */
  useLayoutEffect(() => {
    const el = previewRef.current
    if (!el || editing || expanded) return
    const measure = () => setClipped(el.scrollHeight > el.clientHeight + 1)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    return () => observer.disconnect()
  }, [saved, editing, expanded])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: NOTES_KEY_PREFIX })

  const saveMut = useMutation({
    mutationFn: () => notesApi.save(anchor, draft.trim()),
    onSuccess: () => { invalidate(); setEditing(false); setError(null) },
    onError: (e: Error) => setError(e.message),
  })

  const deleteMut = useMutation({
    mutationFn: () => notesApi.remove(anchor),
    onSuccess: () => { invalidate(); setConfirmDelete(false); setEditing(false); setError(null) },
    onError: (e: Error) => { setConfirmDelete(false); setError(e.message) },
  })

  const trimmed = draft.trim()
  // Plain comparison against the loaded value. A hook snapshotting on first render would freeze the
  // empty state, because the note arrives after mount — everything would then read as "changed".
  const unchanged = trimmed === saved.trim()

  return (
    <div className="mt-4 border-t border-surface-800 pt-3">
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-surface-400">
        <NotebookPen className="h-3.5 w-3.5 shrink-0" />
        {t('notes.title')}
      </p>

      {isLoading ? (
        <p className="text-sm text-surface-500">{t('notes.loading')}</p>
      ) : loadFailed ? (
        /*
         * A failed read must NOT fall through to the "add a note" state. That state offers an empty
         * editor, and saving from it goes through the same upsert as an edit — so a note that failed
         * to load would be silently replaced by whatever was typed over it. The only honest answer
         * while the stored text is unknown is to say so and offer another try.
         */
        <div className="space-y-2">
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
            {t('notes.loadError')}
          </p>
          <Button variant="ghost" size="sm" loading={isFetching} onClick={() => { void refetch() }}>
            {t('notes.retry')}
          </Button>
        </div>
      ) : editing ? (
        <div className="space-y-2">
          <textarea
            className={`${textareaClass} min-h-28`}
            value={draft}
            maxLength={MAX_BODY}
            autoFocus
            placeholder={t('notes.placeholder')}
            onChange={e => setDraft(e.target.value)}
          />
          <p className="text-right text-xs text-surface-500">{draft.length} / {MAX_BODY}</p>
          {error && (
            <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm"
              onClick={() => { setEditing(false); setDraft(saved); setError(null) }}>
              {t('form.cancel')}
            </Button>
            <Button variant="primary" size="sm" loading={saveMut.isPending}
              disabled={!trimmed || unchanged}
              onClick={() => saveMut.mutate()}>
              {t('form.save')}
            </Button>
          </div>
        </div>
      ) : saved ? (
        <div className="space-y-2">
          <p ref={previewRef}
            className={`whitespace-pre-wrap rounded-lg bg-surface-800 p-3 text-sm text-surface-300 ${expanded ? '' : 'line-clamp-6'}`}>
            {saved}
          </p>
          {(clipped || expanded) && (
            <button type="button" onClick={() => setExpanded(v => !v)}
              className="text-xs text-primary-400 hover:text-primary-300">
              {expanded ? t('notes.showLess') : t('notes.showAll')}
            </button>
          )}
          {error && (
            <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
          )}
          <div className="flex justify-end gap-1">
            <button type="button" aria-label={t('notes.edit')} title={t('notes.edit')}
              onClick={() => { setDraft(saved); setEditing(true) }}
              className="p-1 text-surface-400 hover:text-primary-400">
              <Pencil className="h-4 w-4" />
            </button>
            <button type="button" aria-label={t('notes.delete')} title={t('notes.delete')}
              onClick={() => setConfirmDelete(true)}
              className="p-1 text-surface-400 hover:text-rose-400">
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          {error && (
            <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
          )}
          <Button variant="ghost" size="sm" onClick={() => { setDraft(''); setEditing(true) }}>
            <Pencil className="mr-1.5 h-4 w-4" />
            {t('notes.add')}
          </Button>
        </div>
      )}

      <ConfirmDialog
        isOpen={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => deleteMut.mutate()}
        title={t('notes.deleteTitle')}
        message={t('notes.deleteMessage')}
        confirmLabel={t('notes.delete')}
        danger
        loading={deleteMut.isPending}
      />
    </div>
  )
}
