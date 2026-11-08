import { describe, it, expect } from 'vitest'

/**
 * /.well-known/security.txt (RFC 9116) tells whoever finds a vulnerability where to report it. Two
 * of the three places it depends on are checked here: the file, and the nginx block that serves it
 * as text instead of falling through to index.html. The third, the workflow upload (which skips
 * dot-directories by default), sits outside this package where Vite will not read, so the
 * "Verify the bundle" step in ci-frontend.yml checks it on the downloaded artifact instead.
 */
const securityTxt = Object.values(
  import.meta.glob('/public/.well-known/security.txt', { query: '?raw', import: 'default', eager: true }),
)[0] as string | undefined
const nginx = Object.values(
  import.meta.glob('/nginx.conf', { query: '?raw', import: 'default', eager: true }),
)[0] as string | undefined

describe('security.txt', () => {
  it('shouldShipAFileWithAContactAndAnExpiry', () => {
    expect(securityTxt).toBeDefined()
    expect(securityTxt).toMatch(/^Contact: mailto:\S+@\S+$/m)
    const expires = securityTxt?.match(/^Expires: (\S+)$/m)?.[1]
    expect(expires).toBeDefined()
    // A stale file is worse than none: RFC 9116 tells parsers to ignore it.
    expect(new Date(expires as string).getTime()).toBeGreaterThan(Date.now())
  })

  it('shouldServeItFromNginxWithoutFallingThroughToTheApp', () => {
    expect(nginx).toBeDefined()
    expect(nginx).toMatch(/location = \/\.well-known\/security\.txt \{[^}]*try_files \$uri =404;[^}]*\}/)
  })
})
