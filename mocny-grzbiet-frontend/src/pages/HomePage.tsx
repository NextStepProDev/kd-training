import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowDown, ArrowUpRight } from 'lucide-react'
import { Seo } from '../components/seo/Seo'
import { useAuth } from '../context/AuthContext'
import { CONTACT_EMAIL, HOME_PHOTOS, HOME_WORK_SLOTS } from '../config/site'
import { PhotoSlot } from '../components/ui/PhotoSlot'
import { SectionTitle } from '../components/ui/SectionTitle'
import { buttonClass } from '../components/ui/buttonClass'

interface OfferItem {
  title: string
  body: string
}

/**
 * The business card (brief, point 2): who Kacper is, what he does, the services, photos from work and
 * a way to get in touch. Dark and photographic, one typeface for headlines, white as the only accent.
 *
 * The "about" text is the client's own, verbatim; the services are built from it. Photos come from
 * HOME_PHOTOS in config/site.ts.
 */
export function HomePage() {
  const { t } = useTranslation('common')
  const { isAuthenticated } = useAuth()
  const offer = t('home.offer.items', { returnObjects: true }) as OfferItem[]
  const [firstName, ...rest] = t('home.title').split(' ')
  const surname = rest.join(' ')
  const workSlots = Array.from({ length: HOME_WORK_SLOTS }, (_, i) => HOME_PHOTOS.work[i])
  // One alt per photo, same index as HOME_PHOTOS.work; src/config/site.test.ts keeps the two in step.
  const workAlts = t('home.work.photos', { returnObjects: true }) as string[]

  return (
    <>
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
            name: 'Mocny Grzbiet',
            url: window.location.origin,
            publisher: { '@id': `${window.location.origin}/#person` },
            inLanguage: 'pl-PL',
          },
        ]}
      />

      {/* Hero — full screen, the photo carries the page; the navbar floats over it. */}
      <section className="relative flex min-h-[100svh] items-end overflow-hidden">
        <PhotoSlot src={HOME_PHOTOS.hero} alt={t('home.heroPhotoAlt')} loading="eager" shade monogram={false} className="!absolute inset-0" />
        {/* The studio photos are bright (white walls), and the shade only darkens the bottom. Without
            this, the floating navbar and the kicker sat light-on-light: "Grzbiet" all but vanished. */}
        <div aria-hidden="true" className="absolute inset-0 bg-surface-950/35" />
        <div aria-hidden="true" className="absolute inset-x-0 top-0 h-2/5 bg-gradient-to-b from-surface-950/85 to-transparent" />
        <div className="relative z-10 mx-auto w-full max-w-7xl px-4 pb-14 pt-32 sm:px-6 sm:pb-20 lg:px-8">
          <p className="kicker animate-rise">{t('home.kicker')}</p>
          {/* Solid first name, the surname as a contour. The space between the two spans keeps the
              accessible name "Kacper Dąbrowski" rather than "KacperDąbrowski". pb-[0.3em] leaves room
              for the ogonek of "Ą", which hangs below the line at this line-height. */}
          <h1 className="mt-4 pb-[0.3em] text-display text-[clamp(3.75rem,17vw,10.5rem)] text-white animate-rise [animation-delay:80ms]">
            <span className="block">{firstName}</span>{' '}
            <span className="block text-outline">{surname}</span>
          </h1>
          <p className="mt-2 max-w-xl text-lg leading-relaxed text-surface-300 animate-rise [animation-delay:160ms]">
            {t('home.subtitle')}
          </p>
          <div className="mt-10 flex flex-col gap-3 sm:flex-row animate-rise [animation-delay:240ms]">
            <a href={`mailto:${CONTACT_EMAIL}`} className={buttonClass('primary', 'lg')}>
              {t('home.contact')}
              <ArrowUpRight className="h-5 w-5" />
            </a>
            <Link to={isAuthenticated ? '/moje-konto' : '/logowanie'} className={buttonClass('secondary', 'lg')}>
              {isAuthenticated ? t('home.myAccount') : t('home.login')}
            </Link>
          </div>
        </div>
        <a
          href="#o-mnie"
          aria-label={t('home.scroll')}
          className="absolute bottom-6 right-4 z-10 hidden h-12 w-12 items-center justify-center rounded-full border border-white/15 text-surface-300 transition-colors hover:border-white/40 hover:text-white sm:flex lg:right-8"
        >
          <ArrowDown className="h-5 w-5" />
        </a>
      </section>

      {/* About */}
      <section id="o-mnie" className="mx-auto max-w-7xl scroll-mt-20 px-4 py-24 sm:px-6 sm:py-32 lg:px-8">
        <div className="grid gap-10 md:grid-cols-[1fr_1.4fr] md:gap-16">
          <SectionTitle kicker={t('home.about.kicker')}>{t('home.about.title')}</SectionTitle>
          <p className="text-xl leading-relaxed text-surface-300 sm:text-2xl">{t('home.about.body')}</p>
        </div>
      </section>

      {/* Offer — numbered rows rather than boxes: the type does the work, the lines keep the rhythm. */}
      <section className="border-t border-white/[0.06]">
        <div className="mx-auto max-w-7xl px-4 py-24 sm:px-6 sm:py-32 lg:px-8">
          <SectionTitle kicker={t('home.offer.kicker')}>{t('home.offer.title')}</SectionTitle>
          <ol className="mt-12 divide-y divide-white/[0.07] border-y border-white/[0.07]">
            {offer.map((item, i) => (
              <li key={item.title} className="group grid gap-3 py-8 sm:grid-cols-[6rem_1fr_1.3fr] sm:items-baseline sm:gap-8">
                <span className="text-sm font-semibold tracking-[0.2em] text-surface-500">
                  {String(i + 1).padStart(2, '0')}
                </span>
                <h3 className="text-display text-2xl text-white sm:text-3xl">{item.title}</h3>
                <p className="leading-relaxed text-surface-400">{item.body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Work photos — a swipeable strip on a phone, a grid from md up. */}
      <section className="border-t border-white/[0.06]">
        <div className="mx-auto max-w-7xl px-4 pt-24 sm:px-6 sm:pt-32 lg:px-8">
          <SectionTitle kicker={t('home.work.kicker')}>{t('home.work.title')}</SectionTitle>
        </div>
        <div className="mx-auto mt-12 max-w-7xl pb-24 sm:pb-32 md:px-6 lg:px-8">
          {/* A keyboard has to be able to scroll the strip too: focusable, and named as a region. */}
          <div
            tabIndex={0}
            role="region"
            aria-label={t('home.work.title')}
            className="flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 md:grid md:grid-cols-4 md:overflow-visible md:px-0 [scrollbar-width:none]"
          >
            {workSlots.map((src, i) => (
              <PhotoSlot
                key={i}
                src={src}
                alt={workAlts[i] ?? ''}
                className={`aspect-[3/4] w-[72vw] shrink-0 snap-start rounded-2xl sm:w-[45vw] md:w-auto ${i % 2 === 1 ? 'md:translate-y-10' : ''}`}
              />
            ))}
          </div>
        </div>
      </section>

      {/* Contact */}
      <section className="relative overflow-hidden border-t border-white/[0.06] bg-[radial-gradient(70%_60%_at_50%_100%,rgb(255_255_255/0.07),transparent)]">
        <div className="mx-auto max-w-7xl px-4 py-24 text-center sm:px-6 sm:py-36 lg:px-8">
          <p className="kicker">{t('home.cta.kicker')}</p>
          <h2 className="mt-4 text-display text-[clamp(2.75rem,10vw,7rem)] text-white">{t('home.cta.title')}</h2>
          <p className="mx-auto mt-6 max-w-md text-lg text-surface-400">{t('home.cta.body')}</p>
          <a href={`mailto:${CONTACT_EMAIL}`} className={`mt-10 ${buttonClass('primary', 'lg')}`}>
            {CONTACT_EMAIL}
            <ArrowUpRight className="h-5 w-5" />
          </a>
        </div>
      </section>
    </>
  )
}
