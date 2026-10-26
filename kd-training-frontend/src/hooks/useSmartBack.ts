import { useNavigate, useLocation } from 'react-router-dom'

/**
 * "Back" handler for detail pages. Returns the user to wherever they came from (their account, a category
 * tab, a search result…) via browser history — not to a hardcoded list. Falls back to `fallback` when the
 * page was opened directly (shared link / new tab), where there is no in-app history entry to pop
 * (`location.key === 'default'` marks that first entry).
 */
export function useSmartBack(fallback: string): () => void {
  const navigate = useNavigate()
  const location = useLocation()
  return () => {
    if (location.key !== 'default') navigate(-1)
    else navigate(fallback)
  }
}
