import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowUpRight } from 'lucide-react'
import { CONTACT_EMAIL } from '../../config/site'
import { ShareButton } from '../ui/ShareButton'
import { Brand } from './Brand'

export function Footer() {
  const { t } = useTranslation('common')
  const year = new Date().getFullYear()

  return (
    <footer className="border-t border-white/[0.06] bg-surface-950">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-14 pb-10">
        <div className="grid grid-cols-1 gap-10 md:grid-cols-[1.4fr_1fr]">
          <div className="max-w-sm">
            <Brand />
            <p className="mt-5 text-sm text-surface-400 leading-relaxed">
              {t('footer.description')}
            </p>
          </div>

          <div>
            {/* h2, not h4: the page above ends on an h2, and a jump of two levels reads to a screen
                reader as a section that is missing. */}
            <h2 className="kicker mb-4">{t('footer.contact')}</h2>
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="group inline-flex items-center gap-2 text-base text-surface-100 hover:text-white transition-colors"
            >
              <span className="border-b border-white/20 group-hover:border-white transition-colors">{CONTACT_EMAIL}</span>
              <ArrowUpRight className="w-4 h-4 text-surface-500 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-white" />
            </a>
          </div>
        </div>

        <div className="mt-12 pt-6 border-t border-white/[0.06] flex flex-col-reverse gap-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs text-surface-500">
            {t('footer.copyright', { year })}
          </p>
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            <Link to="/polityka-prywatnosci" className="text-xs text-surface-400 hover:text-white transition-colors">
              {t('footer.privacy')}
            </Link>
            <Link to="/regulamin" className="text-xs text-surface-400 hover:text-white transition-colors">
              {t('footer.terms')}
            </Link>
            <span aria-hidden="true" className="w-px h-4 bg-white/10" />
            <div className="flex items-center gap-2">
              <span className="text-xs text-surface-500">{t('footer.shareLabel')}</span>
              <ShareButton url="/" title="KD Training" />
            </div>
          </div>
        </div>
      </div>
    </footer>
  )
}
