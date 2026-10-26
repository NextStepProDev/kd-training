import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/admin'
import { Modal } from '../ui/Modal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { VideoSearchInput } from './VideoSearchInput'
import type { ExerciseVideo } from '../../types'

/**
 * Attaches a clip from the library to a training. Archived entries are never offered.
 * <p>
 * Rendered only while open — mounting it closed would still run its query, and would drag a
 * QueryClient requirement into every screen that merely CAN open the picker.
 */
export function VideoPickerModal({
  onClose, onPick,
}: {
  onClose: () => void
  onPick: (video: ExerciseVideo) => void
}) {
  const { t } = useTranslation('calendar')
  const [query, setQuery] = useState('')

  // The one live query in the app that keeps staleTime 0, while the lists and counters moved to a
  // 30 s floor to stop refetch storms. Here the key carries the query text (debounced in
  // VideoSearchInput), so each search is its own cache entry and a staleTime would apply to exactly
  // one case: reopening this modal with an identical phrase. A cache hit there means a clip added
  // minutes ago is missing from the list and cannot be attached — a wrong answer, not a stale one.
  const videosQuery = useQuery({
    queryKey: ['admin', 'exercise-videos', 'suggest', query],
    queryFn: () => adminApi.suggestExerciseVideos(query),
    staleTime: 0,
  })

  return (
    <Modal isOpen onClose={onClose} title={t('videos.pickTitle')}>
      <div className="space-y-3">
        <VideoSearchInput value={query} onChange={setQuery} placeholder={t('videos.searchPlaceholder')} autoFocus />

        {videosQuery.isLoading ? (
          <LoadingSpinner size="sm" />
        ) : videosQuery.data && videosQuery.data.length > 0 ? (
          <ul className="max-h-80 space-y-2 overflow-y-auto pr-1">
            {videosQuery.data.map(video => (
              <li key={video.id}>
                <button
                  type="button"
                  onClick={() => { onPick(video); onClose() }}
                  className="flex w-full items-center gap-3 rounded-lg border border-surface-800 bg-surface-800/50 p-2 text-left transition-colors hover:border-primary-600/50"
                >
                  <img src={video.thumbnailUrl} alt="" loading="lazy"
                    className="h-12 w-20 shrink-0 rounded object-cover" />
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-surface-100">{video.name}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          // An explicit empty state, never an endless spinner — the library legitimately has no
          // match for most queries once it grows.
          <p className="py-4 text-sm text-surface-500">{t('videos.noResults')}</p>
        )}
      </div>
    </Modal>
  )
}
