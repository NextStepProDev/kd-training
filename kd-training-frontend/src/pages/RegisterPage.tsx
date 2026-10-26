import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { registerUser } from '../api/auth'
import { validatePassword, validatePhone, validateName, validateEmail } from '../utils/validation'
import { PasswordStrengthMeter } from '../components/ui/PasswordStrengthMeter'
import { Button } from '../components/ui/Button'
import { GoogleSignInButton } from '../components/auth/GoogleSignInButton'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

type FieldKey = 'firstName' | 'lastName' | 'email' | 'phone' | 'password' | 'confirmPassword'

const inputBase =
  'w-full px-3 py-2 bg-surface-800 border rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:border-transparent'
const inputNormal = 'border-surface-700 focus:ring-primary-500'
const inputError = 'border-rose-500 focus:ring-rose-500'

export function RegisterPage() {
  const { t, i18n } = useTranslation('auth')
  const [form, setForm] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    firstName: '',
    lastName: '',
    phone: '',
  })
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false)
  const [acceptedMarketing, setAcceptedMarketing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({})
  const [privacyError, setPrivacyError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const updateField = (field: FieldKey, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    // Clear the field error as soon as the user starts correcting it.
    setFieldErrors((prev) => {
      if (!prev[field]) return prev
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  /** Validates all fields at once and assigns the error to the specific field (not one generic message). */
  const validate = () => {
    const errs: Partial<Record<FieldKey, string>> = {}
    const fnErr = validateName(form.firstName)
    if (fnErr) errs.firstName = fnErr
    const lnErr = validateName(form.lastName)
    if (lnErr) errs.lastName = lnErr
    const emailErr = validateEmail(form.email)
    if (emailErr) errs.email = emailErr
    const phoneErr = validatePhone(form.phone)
    if (phoneErr) errs.phone = phoneErr
    const pwErr = validatePassword(form.password, form.confirmPassword)
    if (pwErr) {
      // A password mismatch highlights the confirm field; a too-short password — the password field.
      if (form.password !== form.confirmPassword) errs.confirmPassword = pwErr
      else errs.password = pwErr
    }
    const privErr = !acceptedPrivacy ? t('register.privacyRequired') : null
    return { errs, privErr }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    const { errs, privErr } = validate()
    setFieldErrors(errs)
    setPrivacyError(privErr)
    if (Object.keys(errs).length > 0 || privErr) return

    setLoading(true)
    try {
      await registerUser({
        email: form.email,
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
        phone: form.phone.replace(/\s/g, ''),
        preferredLanguage: i18n.language,
        acceptedPrivacy,
        acceptedMarketing,
      })
      setSuccess(true)
    } catch (err) {
      setError(getErrorMessage(err))
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
          <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-surface-100 mb-2">{t('register.successTitle')}</h2>
          <p className="text-surface-300 mb-3">{t('register.successMessage')}</p>
          <p className="text-sm text-surface-500 mb-6">
            {t('register.successHint')}{' '}
            <Link to="/resend-verification" className="text-primary-400 hover:text-primary-300">
              {t('login.resendVerification')}
            </Link>
          </p>
          <Link to="/logowanie" className="text-primary-400 hover:text-primary-300 font-medium">
            {t('register.goToLogin')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4 py-8">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800">
        <div className="text-center mb-6">
          <p className="text-2xl font-bold uppercase tracking-[0.2em] text-white">KD Training</p>
          <p className="text-surface-400 mt-1">{t('register.title')}</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="firstName" className="block text-sm font-medium text-surface-300 mb-1">
                {t('register.firstName')}
              </label>
              <input
                id="firstName"
                type="text"
                required
                minLength={3}
                value={form.firstName}
                onChange={(e) => updateField('firstName', e.target.value)}
                className={clsx(inputBase, fieldErrors.firstName ? inputError : inputNormal)}
                aria-invalid={!!fieldErrors.firstName}
                aria-describedby={fieldErrors.firstName ? 'firstName-error' : undefined}
              />
              {fieldErrors.firstName && <p id="firstName-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.firstName}</p>}
            </div>
            <div>
              <label htmlFor="lastName" className="block text-sm font-medium text-surface-300 mb-1">
                {t('register.lastName')}
              </label>
              <input
                id="lastName"
                type="text"
                required
                minLength={3}
                value={form.lastName}
                onChange={(e) => updateField('lastName', e.target.value)}
                className={clsx(inputBase, fieldErrors.lastName ? inputError : inputNormal)}
                aria-invalid={!!fieldErrors.lastName}
                aria-describedby={fieldErrors.lastName ? 'lastName-error' : undefined}
              />
              {fieldErrors.lastName && <p id="lastName-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.lastName}</p>}
            </div>
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-surface-300 mb-1">
              {t('register.email')}
            </label>
            <input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={(e) => updateField('email', e.target.value)}
              className={clsx(inputBase, fieldErrors.email ? inputError : inputNormal)}
              placeholder={t('register.emailPlaceholder')}
              aria-invalid={!!fieldErrors.email}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            />
            {fieldErrors.email && <p id="email-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.email}</p>}
          </div>

          <div>
            <label htmlFor="phone" className="block text-sm font-medium text-surface-300 mb-1">
              {t('register.phone')}
            </label>
            <input
              id="phone"
              type="tel"
              required
              value={form.phone}
              onChange={(e) => updateField('phone', e.target.value)}
              className={clsx(inputBase, fieldErrors.phone ? inputError : inputNormal)}
              placeholder={t('register.phonePlaceholder')}
              aria-invalid={!!fieldErrors.phone}
              aria-describedby={fieldErrors.phone ? 'phone-error' : undefined}
            />
            {fieldErrors.phone && <p id="phone-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.phone}</p>}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-surface-300 mb-1">
              {t('register.password')}
            </label>
            <input
              id="password"
              type="password"
              required
              minLength={10}
              value={form.password}
              onChange={(e) => updateField('password', e.target.value)}
              className={clsx(inputBase, fieldErrors.password ? inputError : inputNormal)}
              aria-invalid={!!fieldErrors.password}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            />
            <PasswordStrengthMeter password={form.password} />
            {fieldErrors.password
              ? <p id="password-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.password}</p>
              : <p className="text-xs text-surface-500 mt-1">{t('register.passwordHint')}</p>}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-surface-300 mb-1">
              {t('register.confirmPassword')}
            </label>
            <input
              id="confirmPassword"
              type="password"
              required
              value={form.confirmPassword}
              onChange={(e) => updateField('confirmPassword', e.target.value)}
              className={clsx(inputBase, fieldErrors.confirmPassword ? inputError : inputNormal)}
              aria-invalid={!!fieldErrors.confirmPassword}
              aria-describedby={fieldErrors.confirmPassword ? 'confirmPassword-error' : undefined}
            />
            {fieldErrors.confirmPassword && <p id="confirmPassword-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.confirmPassword}</p>}
          </div>

          <div>
            <div className="flex items-start gap-2">
              <input
                id="acceptPrivacy"
                type="checkbox"
                checked={acceptedPrivacy}
                onChange={(e) => { setAcceptedPrivacy(e.target.checked); setPrivacyError(null) }}
                className={clsx(
                  'mt-0.5 h-4 w-4 rounded bg-surface-800 text-primary-500 focus:ring-2',
                  privacyError ? 'border-rose-500 focus:ring-rose-500' : 'border-surface-600 focus:ring-primary-500',
                )}
                aria-invalid={!!privacyError}
                aria-describedby={privacyError ? 'acceptPrivacy-error' : undefined}
              />
              <label htmlFor="acceptPrivacy" className={clsx('text-sm', privacyError ? 'text-rose-300' : 'text-surface-300')}>
                {t('register.acceptPrivacyPrefix')}{' '}
                <Link to="/polityka-prywatnosci" target="_blank" className="text-primary-400 hover:text-primary-300 underline">
                  {t('register.privacyLink')}
                </Link>
              </label>
            </div>
            {privacyError && <p id="acceptPrivacy-error" className="text-xs text-rose-400/80 mt-1">{privacyError}</p>}
          </div>

          <div className="flex items-start gap-2 rounded-lg border border-surface-700/60 bg-surface-800/40 p-3">
            <input
              id="acceptMarketing"
              type="checkbox"
              checked={acceptedMarketing}
              onChange={(e) => setAcceptedMarketing(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-2 focus:ring-primary-500"
            />
            <label htmlFor="acceptMarketing" className="text-sm text-surface-300">
              {t('register.acceptMarketing')}
              <span className="block text-xs text-surface-500 mt-0.5">{t('register.acceptMarketingHint')}</span>
            </label>
          </div>

          {error && (
            <p className="text-sm text-rose-400/80">{error}</p>
          )}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('register.submit')}
          </Button>
        </form>

        <GoogleSignInButton />

        <p className="mt-6 text-center text-sm text-surface-400">
          {t('register.hasAccount')}{' '}
          <Link to="/logowanie" className="text-primary-400 hover:text-primary-300">
            {t('register.login')}
          </Link>
        </p>
      </div>
    </div>
  )
}
