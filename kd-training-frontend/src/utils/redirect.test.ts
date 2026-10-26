import { describe, it, expect, beforeEach } from 'vitest'
import { saveRedirectPath, consumeRedirectPath } from './redirect'

describe('redirect', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('hands back the saved path, once', () => {
    saveRedirectPath('/moje-konto/plan-treningowy?widok=miesiac')

    expect(consumeRedirectPath()).toBe('/moje-konto/plan-treningowy?widok=miesiac')
    // Consumed: a second read must not send someone back there again after an unrelated login.
    expect(consumeRedirectPath()).toBeNull()
  })

  it('refuses anything that is not a path inside this app', () => {
    // A leading slash is not enough. Browsers read both of these as addresses somewhere else
    // entirely, and sessionStorage is writable by anything running on this origin.
    for (const hostile of ['//evil.example', '/\\evil.example', 'https://evil.example', 'evil']) {
      saveRedirectPath(hostile)
      expect(consumeRedirectPath()).toBeNull()
    }
  })

  it('returns null when nothing was saved', () => {
    expect(consumeRedirectPath()).toBeNull()
  })
})
