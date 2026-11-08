import { useCallback, useEffect, useState } from 'react'
import type { PasteMode } from '../types'

const STORAGE_KEY = 'fa.training-clipboard'

export interface TrainingClipboardEntry {
  trainingId: string
  title: string
  mode: PasteMode
  /** Whose calendar it was taken from — the coach pasting elsewhere is told so before clicking. */
  athleteId: string
}

/**
 * Copy/cut/paste state for the calendar.
 *
 * Kept in sessionStorage rather than component state so it survives paging between weeks and months
 * — copying a training and then navigating to the target week is the whole point, and a state reset
 * on the way there would make the feature useless. Cleared when the tab closes, which is the right
 * lifetime for a clipboard: an entry armed yesterday, pasted by accident today, is a session in
 * someone's plan they never asked for.
 *
 * Deliberately NOT synchronised across tabs. There used to be a `storage` listener here meaning to
 * do that, and it could never have fired: sessionStorage is per-tab, so nothing else is watching
 * the same store. Two tabs each keep their own clipboard, which is also what someone comparing two
 * clients side by side would expect.
 */
export function useTrainingClipboard() {
  const [entry, setEntry] = useState<TrainingClipboardEntry | null>(() => read())

  const clear = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setEntry(null)
  }, [])

  const put = useCallback((next: TrainingClipboardEntry) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setEntry(next)
  }, [])

  // Escape disarms the clipboard — otherwise every empty day stays a paste target with no way out.
  useEffect(() => {
    if (!entry) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') clear()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [entry, clear])

  return { entry, copy: put, clear }
}

function read(): TrainingClipboardEntry | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as TrainingClipboardEntry) : null
  } catch {
    return null
  }
}
