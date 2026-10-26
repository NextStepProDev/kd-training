import { useSyncExternalStore } from 'react'

/**
 * Matches Tailwind's `lg` breakpoint, the width at which seven day columns are still day columns.
 *
 * It used to be `sm` (640px), which is where seven columns FIT rather than where they work. A 768px
 * tablet held them at 84px each: a card there is 50px of copy/cut buttons and 9px of title — one
 * character, or none. That is the width the whole feature was reported broken at, and it is neither
 * the phone nor the desktop, which is why it survived both.
 */
const COMPACT_QUERY = '(max-width: 1023px)'

function subscribe(onChange: () => void): () => void {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {}
  const list = window.matchMedia(COMPACT_QUERY)
  list.addEventListener('change', onChange)
  return () => list.removeEventListener('change', onChange)
}

function isCompact(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(COMPACT_QUERY).matches
}

/**
 * Whether the viewport is too narrow to carry seven day columns — a phone or a tablet.
 * <p>
 * A media query in JS rather than CSS because the month view does not merely restyle at this
 * breakpoint — it swaps to a different component with different interactions. Rendering both trees
 * and hiding one would double the work and leave the hidden half reachable by keyboard and screen
 * readers.
 * <p>
 * Server-rendered output assumes desktop: the prerendered HTML is only a first paint, and the
 * client corrects it before anyone can tap anything.
 */
export function useCompactViewport(): boolean {
  return useSyncExternalStore(subscribe, isCompact, () => false)
}
