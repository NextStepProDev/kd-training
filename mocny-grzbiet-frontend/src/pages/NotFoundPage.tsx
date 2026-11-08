import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Seo } from '../components/seo/Seo'
import { buttonClass } from '../components/ui/buttonClass'

/**
 * The catch-all route. Without it an unmatched URL rendered an empty document — no navbar, no
 * footer, not one character of text — which reads as a broken site to a person and as a soft 404
 * to a crawler: an empty page answering 200, indexed as if it were real content.
 *
 * Every way in here is someone who wanted something specific: a mistyped address, an old bookmark,
 * a shared link to something since removed. Every real destination (the home page, the account and
 * training plan, the coach's panel for an admin, the two legal pages) is one tap away in the navbar
 * or footer that stay on this page, so a single way home is enough here.
 *
 * Note that a dead entity id does NOT land here: those routes match, fetch, and bounce back to
 * their listing on 404. This page is only for URLs that match no route at all.
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100dvh-4rem)] px-4 py-16 text-center">
      <Seo title={t('notFound.title')} path="/404" noIndex />
      {/* The number as a contour in the poster face — the same signature as the surname on the home page. */}
      <p aria-hidden="true" className="text-display text-outline text-[clamp(7rem,30vw,14rem)] leading-none">404</p>
      <h1 className="mt-6 text-display text-3xl sm:text-4xl text-white">{t('notFound.title')}</h1>
      <p className="mt-4 text-surface-400 max-w-md">{t('notFound.description')}</p>
      <Link to="/" className={`mt-10 ${buttonClass('primary', 'lg')}`}>
        {t('notFound.home')}
      </Link>
    </div>
  )
}
