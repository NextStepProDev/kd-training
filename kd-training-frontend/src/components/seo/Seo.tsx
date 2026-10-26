import { Helmet } from 'react-helmet-async'

const SITE_NAME = 'KD Training'
const DEFAULT_DESCRIPTION = 'Trening personalny z Kacprem Dąbrowskim — indywidualny plan, materiały i kontakt z trenerem w jednym miejscu.'
const DEFAULT_IMAGE = '/og-default.png'

export interface Breadcrumb {
  name: string
  path: string
}

interface SeoProps {
  title: string
  description?: string
  path: string
  image?: string | null
  type?: 'website' | 'article'
  jsonLd?: Record<string, unknown> | Record<string, unknown>[]
  breadcrumbs?: Breadcrumb[]
  /**
   * Keep the page out of the index. For the 404 page: without it a crawler that follows a dead link
   * gets a 200 with real-looking content and files it away as a page of the site.
   */
  noIndex?: boolean
}

export function Seo({ title, description, path, image, type = 'website', jsonLd, breadcrumbs, noIndex }: SeoProps) {
  const fullTitle = title === SITE_NAME ? title : `${title} | ${SITE_NAME}`
  const desc = description || DEFAULT_DESCRIPTION
  const img = image || DEFAULT_IMAGE
  const canonical = `${window.location.origin}${path}`
  const imgUrl = img.startsWith('http') ? img : `${window.location.origin}${img}`

  const allJsonLd: Record<string, unknown>[] = []

  if (breadcrumbs && breadcrumbs.length > 0) {
    allJsonLd.push({
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: breadcrumbs.map((bc, i) => ({
        '@type': 'ListItem',
        position: i + 1,
        name: bc.name,
        item: `${window.location.origin}${bc.path}`,
      })),
    })
  }

  if (jsonLd) {
    if (Array.isArray(jsonLd)) {
      allJsonLd.push(...jsonLd)
    } else {
      allJsonLd.push(jsonLd)
    }
  }

  return (
    <Helmet>
      <title>{fullTitle}</title>
      <meta name="description" content={desc} />
      {/*
        Both names, not just "robots": index.html ships a static googlebot directive, and for Google
        the crawler-specific tag beats the generic one — leaving only `robots` here would have let
        Googlebot index the 404 anyway. Where two directives conflict the restrictive one wins, so
        emitting both is enough and the static tags can stay where they are.
      */}
      {noIndex && <meta name="robots" content="noindex,follow" />}
      {noIndex && <meta name="googlebot" content="noindex,follow" />}
      {!noIndex && <link rel="canonical" href={canonical} />}

      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={desc} />
      <meta property="og:image" content={imgUrl} />
      <meta property="og:url" content={canonical} />
      <meta property="og:type" content={type} />
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:locale" content="pl_PL" />

      {allJsonLd.length > 0 && (
        <script type="application/ld+json">
          {JSON.stringify(allJsonLd.length === 1 ? allJsonLd[0] : allJsonLd)}
        </script>
      )}
    </Helmet>
  )
}
