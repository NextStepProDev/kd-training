import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { forgotPassword } from '../api/auth'
import { Button } from '../components/ui/Button'
import { inputClassMuted } from '../utils/fieldClass'
import { authCardClass, authStageClass } from '../components/auth/authCard'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

export function ForgotPasswordPage() {
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
      await forgotPassword(email)
      setSuccess(true)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={authStageClass}>
      <div className={authCardClass}>
        <h1 className="text-2xl font-bold text-surface-100 mb-2 text-center">{t('forgotPassword.title')}</h1>
        <p className="text-surface-400 text-sm text-center mb-6">{t('forgotPassword.description')}</p>

        {success ? (
          <div className="text-center">
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <p className="text-surface-300 mb-6">{t('forgotPassword.successMessage')}</p>
            <Link to="/logowanie" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white font-medium">
              {t('forgotPassword.backToLogin')}
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-surface-300 mb-1">
                {t('forgotPassword.email')}
              </label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClassMuted}
                placeholder={t('forgotPassword.emailPlaceholder')}
              />
            </div>

            {error && <p className="text-sm text-rose-400/80">{error}</p>}

            <Button type="submit" variant="primary" className="w-full" loading={loading}>
              {t('forgotPassword.submit')}
            </Button>

            <p className="text-center text-sm text-surface-400">
              <Link to="/logowanie" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white">
                {t('forgotPassword.backToLogin')}
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
