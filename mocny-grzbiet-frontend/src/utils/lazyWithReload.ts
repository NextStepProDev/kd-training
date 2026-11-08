import { lazy, type ComponentType, type LazyExoticComponent } from 'react'

const RELOAD_FLAG = 'chunk-reload-attempted'

/**
 * Drop-in replacement for React.lazy that recovers from stale chunk loads.
 *
 * After a new deploy the hashed asset filenames change, so a browser holding an
 * old index.html (typical on mobile with aggressive caching) requests chunks that
 * no longer exist and the dynamic import rejects ("Failed to fetch dynamically
 * imported module" / ChunkLoadError). When that happens we reload the page once to
 * pull a fresh index.html + assets.
 *
 * The reload is guarded by a sessionStorage flag so it fires at most once per
 * session: if the chunk is still missing after the reload (a genuine problem, not
 * just a stale cache) the error falls through to the ErrorBoundary instead of
 * looping. The flag is cleared on every successful load so a later deploy in the
 * same session can retry.
 */
// ComponentType<any> mirrors React.lazy's own signature so the wrapped component's
// props are preserved exactly; a narrower constraint breaks inference (props become
// `never`). The any lives only in the type bound, never in a runtime value.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function lazyWithReload<T extends ComponentType<any>>(
  factory: () => Promise<{ default: T }>,
): LazyExoticComponent<T> {
  return lazy(async () => {
    try {
      const module = await factory()
      sessionStorage.removeItem(RELOAD_FLAG)
      return module
    } catch (error) {
      if (!sessionStorage.getItem(RELOAD_FLAG)) {
        sessionStorage.setItem(RELOAD_FLAG, '1')
        window.location.reload()
        // Never resolve: keep the Suspense fallback up until the reload happens.
        return new Promise<{ default: T }>(() => {})
      }
      throw error
    }
  })
}
