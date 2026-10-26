import { useEffect, useState } from 'react'
import { useIsFetching, useIsMutating } from '@tanstack/react-query'

// Delay before the bar appears, so fast (cache-served) requests don't flash it.
const SHOW_DELAY_MS = 250
// Keep the bar up briefly after the last request ends, so back-to-back requests
// read as one continuous "working" state instead of a flicker.
const HIDE_DELAY_MS = 150

/**
 * A thin indeterminate progress bar pinned to the top of the viewport. It shows
 * whenever any React Query request (fetch or mutation) is in flight — page
 * navigation, background refetch, form submit. This is the global "I'm working"
 * signal the app was missing: when the backend is cold (waking from swap) a
 * click otherwise produces no visible change for seconds and looks frozen.
 */
export function GlobalLoadingBar() {
  const active = useIsFetching() + useIsMutating() > 0
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const timer = setTimeout(
      () => setVisible(active),
      active ? SHOW_DELAY_MS : HIDE_DELAY_MS,
    )
    return () => clearTimeout(timer)
  }, [active])

  return (
    <div
      aria-hidden="true"
      className={`fixed top-0 left-0 right-0 z-[100] h-0.5 overflow-hidden transition-opacity duration-200 ${
        visible ? 'opacity-100' : 'opacity-0'
      }`}
    >
      {visible && (
        <div
          className="global-loading-sweep absolute top-0 h-full rounded-r-full"
          style={{
            background:
              'linear-gradient(90deg, rgba(251,146,60,0) 0%, #fb923c 50%, #ea580c 100%)',
            boxShadow: '0 0 8px rgba(249,115,22,0.7)',
          }}
        />
      )}
    </div>
  )
}
