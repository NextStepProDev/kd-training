import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { GoogleSignInButton } from '../components/auth/GoogleSignInButton'
import { Button } from '../components/ui/Button'
import { inputClassMuted } from '../utils/fieldClass'
import { authCardClass, authStageClass } from '../components/auth/authCard'
import { Brand } from '../components/layout/Brand'
import { Seo } from '../components/seo/Seo'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

export function LoginPage() {
  const { t } = useTranslation('auth')
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const loggedInUser = await login(email, password)
      const redirect = consumeRedirectPath()
      // Admin lands on the admin panel (Trainings tab) by default — honor a redirect only if it points there,
      // so a stray "/moje-konto" bounce doesn't drop an admin on the user account page.
      const target = loggedInUser.isAdmin
        ? (redirect?.startsWith('/admin') ? redirect : '/admin/treningi')
        : (redirect || '/moje-konto')
      navigate(target, { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
      setLoading(false)
    }
  }

  return (
    <div className={authStageClass}>
      <Seo title={t('login.title')} path="/logowanie" />
      <div className={authCardClass}>
        <div className="text-center mb-6">
          <Brand size="lg" />
          <h1 className="text-surface-400 mt-1">{t('login.title')}</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-surface-300 mb-1">
              {t('login.email')}
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClassMuted}
              placeholder={t('login.emailPlaceholder')}
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-surface-300 mb-1">
              {t('login.password')}
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputClassMuted}
              placeholder={t('login.passwordPlaceholder')}
            />
          </div>

          {error && (
            <p className="text-sm text-rose-400/80">{error}</p>
          )}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('login.submit')}
          </Button>
        </form>

        <GoogleSignInButton />

        <div className="mt-6 space-y-2 text-center text-sm">
          <p className="text-surface-400">{t('login.noAccount')}</p>
          <p className="text-surface-400">
            <Link to="/forgot-password" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white">
              {t('login.forgotPassword')}
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
