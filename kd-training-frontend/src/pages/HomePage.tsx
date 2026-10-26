import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Seo } from '../components/seo/Seo'
import { useAuth } from '../context/AuthContext'
import { CONTACT_EMAIL } from '../config/site'

/**
 * Placeholder business card. The real page (about, services, photos, contact) is built from the
 * client's content in the design phase; until then this says who the site belongs to and leads
 * clients into their own space.
 */
export function HomePage() {
  const { t } = useTranslation('common')
  const { isAuthenticated } = useAuth()

  return (
    <div className="mx-auto flex min-h-[70dvh] max-w-3xl flex-col items-center justify-center px-4 py-16 text-center">
      <Seo
        title={t('home.seoTitle')}
        description={t('home.seoDescription')}
        path="/"
        jsonLd={[
          {
            '@context': 'https://schema.org',
            '@type': 'Person',
            name: 'Kacper Dąbrowski',
            jobTitle: 'Trener personalny',
            url: window.location.origin,
            '@id': `${window.location.origin}/#person`,
          },
          {
            '@context': 'https://schema.org',
            '@type': 'WebSite',
            name: 'KD Training',
            url: window.location.origin,
            publisher: { '@id': `${window.location.origin}/#person` },
            inLanguage: 'pl-PL',
          },
        ]}
      />
      <p className="text-sm uppercase tracking-[0.3em] text-surface-400">{t('home.kicker')}</p>
      <h1 className="mt-4 text-4xl font-bold tracking-wide text-white md:text-6xl">{t('home.title')}</h1>
      <p className="mt-6 max-w-xl text-lg text-surface-300">{t('home.subtitle')}</p>

      <div className="mt-10 flex flex-col gap-3 sm:flex-row">
        <Link
          to={isAuthenticated ? '/moje-konto' : '/logowanie'}
          className="rounded-lg bg-white px-6 py-3 font-semibold text-black transition-colors hover:bg-surface-200"
        >
          {isAuthenticated ? t('home.myAccount') : t('home.login')}
        </Link>
        <a
          href={`mailto:${CONTACT_EMAIL}`}
          className="rounded-lg border border-surface-600 px-6 py-3 font-semibold text-white transition-colors hover:border-white"
        >
          {t('home.contact')}
        </a>
      </div>
    </div>
  )
}
