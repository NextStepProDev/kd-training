// Mirror of YouTubeUrl.java, kept in TypeScript so the add-video form can preview a link BEFORE it
// is saved. The two implementations are covered by the same case list on both sides; if you change
// one, change the other and its tests.

const SHAPES: RegExp[] = [
  /[?&]v=([A-Za-z0-9_-]{11})/,
  /youtu\.be\/([A-Za-z0-9_-]{11})/,
  /\/(?:embed|shorts|live|v)\/([A-Za-z0-9_-]{11})/,
]

const YOUTUBE_HOSTS = new Set(['youtube.com', 'youtu.be', 'youtube-nocookie.com'])

/** The 11-character video id, or null when this is not a YouTube link. */
export function parseYouTubeId(rawUrl: string): string | null {
  const url = rawUrl?.trim()
  if (!url) return null

  let host: string
  try {
    host = new URL(url.startsWith('http') ? url : `https://${url}`).hostname.toLowerCase()
  } catch {
    return null
  }
  // A lookalike host such as youtube.com.evil.example must not pass.
  const bare = host.replace(/^www\./, '').replace(/^m\./, '')
  if (!YOUTUBE_HOSTS.has(bare)) return null

  for (const shape of SHAPES) {
    const match = shape.exec(url)
    if (match) return match[1]
  }
  return null
}

/** Built from the id, so a messy pasted link never reaches an iframe src. */
export function youTubeEmbedUrl(videoId: string): string {
  return `https://www.youtube-nocookie.com/embed/${videoId}`
}

export function youTubeThumbnailUrl(videoId: string): string {
  return `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
}
