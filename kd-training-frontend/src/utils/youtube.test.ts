import { describe, it, expect } from 'vitest'
import { parseYouTubeId, youTubeEmbedUrl, youTubeThumbnailUrl } from './youtube'

// Deliberately the same case list as YouTubeUrlTest.java. The parser exists twice — once on the
// server for storage, once here for the live preview — and these tests are what keep the two honest.
const ID = 'dQw4w9WgXcQ'

describe('youtube', () => {
  it('parses every shape people actually paste', () => {
    expect(parseYouTubeId(`https://www.youtube.com/watch?v=${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://youtube.com/watch?v=${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://m.youtube.com/watch?v=${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://youtu.be/${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://www.youtube.com/embed/${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://www.youtube.com/shorts/${ID}`)).toBe(ID)
    expect(parseYouTubeId(`https://www.youtube.com/live/${ID}`)).toBe(ID)
  })

  it('ignores trailing query noise', () => {
    expect(parseYouTubeId(`https://youtu.be/${ID}?t=30`)).toBe(ID)
    expect(parseYouTubeId(`https://www.youtube.com/watch?v=${ID}&list=PL123`)).toBe(ID)
    expect(parseYouTubeId(`https://www.youtube.com/watch?list=PL123&v=${ID}`)).toBe(ID)
    expect(parseYouTubeId(`  https://youtu.be/${ID}  `)).toBe(ID)
  })

  it('treats the same video in different shapes as one', () => {
    expect(parseYouTubeId(`https://youtu.be/${ID}?t=12`))
      .toBe(parseYouTubeId(`https://www.youtube.com/watch?v=${ID}`))
  })

  it('rejects anything that is not YouTube', () => {
    expect(parseYouTubeId('https://vimeo.com/123456')).toBeNull()
    expect(parseYouTubeId(`https://example.com/watch?v=${ID}`)).toBeNull()
    // A lookalike host must not slip through
    expect(parseYouTubeId(`https://youtube.com.evil.example/watch?v=${ID}`)).toBeNull()
    expect(parseYouTubeId('nonsense')).toBeNull()
    expect(parseYouTubeId('')).toBeNull()
  })

  it('rejects ids of the wrong length', () => {
    expect(parseYouTubeId('https://youtu.be/short')).toBeNull()
  })

  it('builds player urls from the id rather than the pasted link', () => {
    expect(youTubeEmbedUrl(ID)).toBe(`https://www.youtube-nocookie.com/embed/${ID}`)
    expect(youTubeThumbnailUrl(ID)).toBe(`https://img.youtube.com/vi/${ID}/hqdefault.jpg`)
  })
})
