import { describe, it, expect } from 'vitest'

/**
 * nginx.conf lives outside src/, so it is pulled in as raw text the same way the date-input gate
 * reads sources — through import.meta.glob rather than node:fs, which this package has no types for.
 */
const files = import.meta.glob('/nginx.conf', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

const REQUIRED = ['X-Forwarded-For', 'X-Forwarded-Proto', 'X-Forwarded-Host']

/** Splits the config into `location` blocks by brace depth, keeping each block's opening line. */
function locationBlocks(config: string): { name: string; body: string }[] {
  const lines = config.split('\n')
  const blocks: { name: string; body: string }[] = []
  for (let i = 0; i < lines.length; i++) {
    if (!/^\s*location\s/.test(lines[i])) continue
    let depth = 0
    let body = ''
    for (let j = i; j < lines.length; j++) {
      depth += (lines[j].match(/{/g) ?? []).length
      depth -= (lines[j].match(/}/g) ?? []).length
      body += lines[j] + '\n'
      if (depth === 0 && j > i) break
    }
    blocks.push({ name: lines[i].trim(), body })
  }
  return blocks
}

describe('nginx forwarded headers', () => {
  const config = Object.values(files)[0]

  it('shouldActuallyFindTheConfig', () => {
    // A glob that matches nothing would make every assertion below pass while checking nothing —
    // the same trap the date-input gate guards against.
    expect(config).toBeDefined()
    expect(config).toContain('proxy_pass http://backend:8083')
  })

  it('shouldSetEveryForwardedHeaderItselfInEveryBlockThatProxiesToTheBackend', () => {
    // nginx replaces only the headers it names; anything else the caller invents is passed through,
    // and the backend trusts X-Forwarded-* when it builds absolute URLs (forward-headers-strategy:
    // framework). A proxying block missing these is a request header deciding what our own addresses
    // look like — which is exactly how the OAuth callback address became bendable.
    const proxying = locationBlocks(config).filter(b => b.body.includes('proxy_pass'))
    expect(proxying.length).toBeGreaterThan(5)

    const offenders = proxying
      .filter(b => REQUIRED.some(h => !b.body.includes(`proxy_set_header ${h} `)))
      .map(b => b.name)

    expect(offenders).toEqual([])
  })

  it('shouldNeverEchoTheCallersOwnForwardedProto', () => {
    // $http_x_forwarded_proto is the caller's word for it. Cloudflare reaches the origin over 443,
    // so $scheme is the honest answer and nothing needs the client's opinion. Comments are stripped
    // first: the note at the top of the config explains this rule by naming the variable, and a gate
    // that trips over its own documentation gets deleted rather than obeyed.
    const directives = config.split('\n').filter(line => !/^\s*#/.test(line)).join('\n')

    expect(directives).not.toContain('$http_x_forwarded_proto')
  })
})
