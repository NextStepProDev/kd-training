import { describe, it, expect } from 'vitest'

/**
 * Fonts are bundled, never fetched from a font CDN. The CSP in nginx.conf allows `font-src 'self'`
 * and `style-src 'self'`, so a Google Fonts link is blocked in production — with no error anyone
 * sees, just the system font where the design expected another. The dev server sends no CSP, so
 * locally it always looked right; that is how Montserrat sat in index.html without ever loading.
 */
// CSS is not in the glob: Vitest hands a stylesheet back as an empty module, so a check over it
// would pass while reading nothing. The font imports live in main.tsx for the same reason.
const files = import.meta.glob(['/index.html', '/src/main.tsx'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

describe('self-hosted fonts', () => {
  it('shouldActuallyFindTheFiles', () => {
    expect(Object.keys(files)).toContain('/index.html')
    expect(files['/src/main.tsx']).toContain('createRoot')
  })

  it('shouldNotLoadFontsFromAnotherHost', () => {
    const offenders = Object.entries(files)
      .filter(([, text]) => /fonts\.(googleapis|gstatic)\.com|use\.typekit\.net|fonts\.bunny\.net/.test(text))
      .map(([path]) => path)
    expect(offenders).toEqual([])
  })

  it('shouldBundleTheDisplayAndTextFaces', () => {
    expect(files['/src/main.tsx']).toContain("import '@fontsource-variable/archivo/wdth.css'")
    expect(files['/src/main.tsx']).toContain("import '@fontsource-variable/inter/wght.css'")
  })
})
