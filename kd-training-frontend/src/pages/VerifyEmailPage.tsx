import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { verifyEmail } from '../api/auth'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

export function VerifyEmailPage() {
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>(() => token ? 'loading' : 'error')
  const [errorMessage, setErrorMessage] = useState(() => token ? '' : t('verify.noToken'))

  useEffect(() => {
    if (!token) return

    verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err) => {
        setStatus('error')
        setErrorMessage(err instanceof Error ? err.message : t('verify.error'))
      })
  }, [token, t])

  if (status === 'loading') {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
        {status === 'success' ? (
          <>
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.successTitle')}</h2>
            <p className="text-surface-400 mb-6">{t('verify.successMessage')}</p>
            <Link to="/logowanie" className="text-primary-400 hover:text-primary-300 font-medium">
              {t('verify.goToLogin')}
            </Link>
          </>
        ) : (
          <>
            <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.errorTitle')}</h2>
            <p className="text-surface-400 mb-6">{errorMessage}</p>
            <Link to="/logowanie" className="text-primary-400 hover:text-primary-300 font-medium">
              {t('verify.goToLogin')}
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
