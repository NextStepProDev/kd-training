import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'

/**
 * The one search box for the exercise library, used both when browsing it in the admin panel and
 * when attaching a clip to a training. Two copies would mean two debounce timings and two
 * definitions of "empty query" to keep in step.
 */
export function VideoSearchInput({
  value, onChange, placeholder, autoFocus = false, delayMs = 250,
}: {
  value: string
  onChange: (query: string) => void
  placeholder: string
  autoFocus?: boolean
  delayMs?: number
}) {
  const [draft, setDraft] = useState(value)

  // Debounced: the library can grow to hundreds of entries and every keystroke would otherwise be
  // a query. The committed value stays in the parent so paging and refetching work off one source.
  useEffect(() => {
    if (draft === value) return
    const timer = setTimeout(() => onChange(draft), delayMs)
    return () => clearTimeout(timer)
  }, [draft, value, onChange, delayMs])

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-surface-500" />
      <input
        type="search"
        className="w-full rounded-lg border border-surface-700 bg-surface-800 py-2 pl-9 pr-3 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
        placeholder={placeholder}
        value={draft}
        autoFocus={autoFocus}
        onChange={e => setDraft(e.target.value)}
      />
    </div>
  )
}
