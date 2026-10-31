import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Mail } from 'lucide-react'
import { CONTACT_EMAIL } from '../../config/site'
import { ShareButton } from '../ui/ShareButton'

export function Footer() {
  const { t } = useTranslation('common')
  const year = new Date().getFullYear()

  return (
    <footer className="bg-surface-950 border-t border-surface-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
          <div>
            <p className="mb-4 text-lg font-bold uppercase tracking-[0.2em] text-white">KD Training</p>
            <p className="text-sm text-surface-400 leading-relaxed">
              {t('footer.description')}
            </p>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-surface-200 uppercase tracking-wider mb-4">
              {t('footer.contact')}
            </h4>
            <ul className="space-y-3">
              <li className="flex items-center gap-3">
                <Mail className="w-4 h-4 text-primary-500 shrink-0" />
                <a href={`mailto:${CONTACT_EMAIL}`} className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  {CONTACT_EMAIL}
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-10 pt-8 border-t border-surface-800 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs text-surface-500">
            {t('footer.copyright', { year })}
          </p>
          <div className="flex items-center gap-4 sm:gap-6">
            <Link to="/polityka-prywatnosci" className="inline-block text-xs text-surface-500 hover:text-surface-300 hover:-translate-y-0.5 transition-all duration-200">
              {t('footer.privacy')}
            </Link>
            <Link to="/regulamin" className="inline-block text-xs text-surface-500 hover:text-surface-300 hover:-translate-y-0.5 transition-all duration-200">
              {t('footer.terms')}
            </Link>
            <span aria-hidden="true" className="w-px h-4 bg-surface-700" />
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
