import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Seo } from '../components/seo/Seo'

/**
 * The catch-all route. Without it an unmatched URL rendered an empty document — no navbar, no
 * footer, not one character of text — which reads as a broken site to a person and as a soft 404
 * to a crawler: an empty page answering 200, indexed as if it were real content.
 *
 * Every way in here is someone who wanted something specific: a mistyped address, a shared link to
 * a term that has since been removed, an old bookmark. So the page offers the three sections rather
 * than only a way home — the thing they were looking for is usually one of them.
 *
 * Note that a dead entity id does NOT land here: those routes match, fetch, and bounce back to
 * their listing on 404. This page is only for URLs that match no route at all.
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4 py-16 text-center">
      <Seo title={t('notFound.title')} path="/404" noIndex />
      <p className="text-6xl font-bold text-primary-500 mb-4">404</p>
      <h1 className="text-2xl font-bold text-surface-100 mb-3">{t('notFound.title')}</h1>
      <p className="text-surface-400 max-w-md mb-8">{t('notFound.description')}</p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          to="/"
          className="px-5 py-2.5 rounded-lg bg-primary-600 text-white font-semibold text-sm transition-colors hover:bg-primary-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-400"
        >
          {t('notFound.home')}
        </Link>
      </div>
    </div>
  )
}
