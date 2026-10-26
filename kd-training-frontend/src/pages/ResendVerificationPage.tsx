import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { resendVerification } from '../api/auth'
import { Button } from '../components/ui/Button'
import { inputClassMuted } from '../utils/fieldClass'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

export function ResendVerificationPage() {
  const { t } = useTranslation('auth')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      await resendVerification(email)
      setSuccess(true)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800">
        <h1 className="text-2xl font-bold text-surface-100 mb-2 text-center">{t('resendVerification.title')}</h1>
        <p className="text-surface-400 text-sm text-center mb-6">{t('resendVerification.description')}</p>

        {success ? (
          <div className="text-center">
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <p className="text-surface-300 mb-6">{t('resendVerification.successMessage')}</p>
            <Link to="/logowanie" className="text-primary-400 hover:text-primary-300 font-medium">
              {t('resendVerification.backToLogin')}
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-surface-300 mb-1">
                {t('resendVerification.email')}
              </label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClassMuted}
                placeholder={t('resendVerification.emailPlaceholder')}
              />
            </div>

            {error && <p className="text-sm text-rose-400/80">{error}</p>}

            <Button type="submit" variant="primary" className="w-full" loading={loading}>
              {t('resendVerification.submit')}
            </Button>

            <p className="text-center text-sm text-surface-400">
              <Link to="/logowanie" className="text-primary-400 hover:text-primary-300">
                {t('resendVerification.backToLogin')}
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
