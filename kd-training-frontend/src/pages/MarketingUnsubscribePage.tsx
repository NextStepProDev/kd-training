import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { MailX, CheckCircle, XCircle } from 'lucide-react'
import { Button } from '../components/ui/Button'

type Status = 'idle' | 'loading' | 'done' | 'error'

/**
 * Public marketing unsubscribe page — opened from a link in an email
 * (no login required). Deliberately requires a button click (POST) so anti-spam scanners
 * don't unsubscribe users via an automatic GET prefetch.
 */
export function MarketingUnsubscribePage() {
  const { t } = useTranslation('marketing')
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState<Status>('idle')

  const handleUnsubscribe = async () => {
    if (!token) return
    setStatus('loading')
    try {
      const res = await fetch('/api/public/marketing/unsubscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      })
      setStatus(res.ok ? 'done' : 'error')
    } catch {
      setStatus('error')
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 py-16 text-center">
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-8">
        {status === 'done' ? (
          <>
            <CheckCircle className="w-12 h-12 text-emerald-400 mx-auto mb-4" />
            <h1 className="text-xl font-bold text-surface-100 mb-2">{t('unsubscribe.doneTitle')}</h1>
            <p className="text-surface-400">{t('unsubscribe.doneText')}</p>
          </>
        ) : !token ? (
          <>
            <XCircle className="w-12 h-12 text-amber-400 mx-auto mb-4" />
            <h1 className="text-xl font-bold text-surface-100 mb-2">{t('unsubscribe.invalidTitle')}</h1>
            <p className="text-surface-400">{t('unsubscribe.invalidText')}</p>
          </>
        ) : (
          <>
            <MailX className="w-12 h-12 text-primary-400 mx-auto mb-4" />
            <h1 className="text-xl font-bold text-surface-100 mb-2">{t('unsubscribe.title')}</h1>
            <p className="text-surface-400 mb-6">{t('unsubscribe.text')}</p>
            {status === 'error' && (
              <p className="text-sm text-rose-400/80 mb-4">{t('unsubscribe.error')}</p>
            )}
            <Button variant="primary" className="w-full" loading={status === 'loading'} onClick={handleUnsubscribe}>
              {t('unsubscribe.confirm')}
            </Button>
          </>
        )}
        <Link to="/" className="inline-block mt-6 text-sm text-primary-400 hover:text-primary-300">
          {t('unsubscribe.backHome')}
        </Link>
      </div>
    </div>
  )
}
