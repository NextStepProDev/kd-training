const REDIRECT_KEY = 'p1_redirect_after_login'

export function saveRedirectPath(path: string): void {
  sessionStorage.setItem(REDIRECT_KEY, path)
}

/**
 * The saved path, once, and only if it is genuinely a path within this app.
 *
 * A leading slash is not enough: `//evil.example` and `/\evil.example` also start with one, and
 * browsers read both as protocol-relative addresses pointing somewhere else entirely. Nothing here
 * writes such a value — the only writer stores `location.pathname` — and `navigate()` would refuse
 * to leave the origin anyway, so this is not a hole that is open today. It is a function whose one
 * job is to answer "is this safe to go to", and answering it properly costs a comparison.
 */
export function consumeRedirectPath(): string | null {
  const path = sessionStorage.getItem(REDIRECT_KEY)
  sessionStorage.removeItem(REDIRECT_KEY)
  if (!path?.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) {
    return null
  }
  return path
}
