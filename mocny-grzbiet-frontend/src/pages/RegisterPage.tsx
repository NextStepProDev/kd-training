import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { inputClassInvalid, inputClassMuted } from '../utils/fieldClass'
import { getInvitation, registerUser } from '../api/auth'
import { validatePassword, validatePhone, validateName } from '../utils/validation'
import { PasswordStrengthMeter } from '../components/ui/PasswordStrengthMeter'
import { Button } from '../components/ui/Button'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { GoogleSignInButton } from '../components/auth/GoogleSignInButton'
import { authCardClass, authStageClass } from '../components/auth/authCard'
import { Brand } from '../components/layout/Brand'
import { Seo } from '../components/seo/Seo'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

type FieldKey = 'firstName' | 'lastName' | 'phone' | 'password' | 'confirmPassword'

const linkClass = 'text-white underline decoration-white/30 underline-offset-4 hover:decoration-white'

/** A card with the brand, a heading and a message: no link, a dead link, or registration done. */
function Notice({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className={authStageClass}>
      <div className={`${authCardClass} text-center`}>
        <Brand size="lg" />
        <h1 className="mt-6 text-xl font-bold text-surface-100">{title}</h1>
        {children}
      </div>
    </div>
  )
}

/**
 * Registration is by invitation only: the coach's mail links here with ?token=, and the account gets
 * the address the invitation went to. Without a working link there is no form, only a notice.
 */
export function RegisterPage() {
  const { t } = useTranslation('auth')
  // One title for every branch below: the notices, the spinner, the form and its success screen.
  return (
    <>
      <Seo title={t('register.title')} path="/rejestracja" />
      <RegisterPageContent />
    </>
  )
}

function RegisterPageContent() {
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const invitation = useQuery({
    queryKey: ['invitation', token],
    queryFn: () => getInvitation(token!),
    enabled: !!token,
    // A dead link stays dead; asking again only delays the notice. And once the account is created
    // the invitation is gone, so a refetch must not swap the success screen for "invalid link".
    retry: false,
    staleTime: Infinity,
  })

  const loginLink = (
    <p className="mt-6 text-sm text-surface-400">
      {t('register.hasAccount')}{' '}
      <Link to="/logowanie" className={linkClass}>{t('register.login')}</Link>
    </p>
  )

  if (!token) {
    return (
      <Notice title={t('register.inviteOnlyTitle')}>
        <p className="mt-3 text-surface-300">{t('register.inviteOnlyMessage')}</p>
        {loginLink}
      </Notice>
    )
  }
  if (invitation.isPending) {
    return (
      <div className={authStageClass}>
        <LoadingSpinner />
      </div>
    )
  }
  if (invitation.isError) {
    return (
      <Notice title={t('register.invalidInvitationTitle')}>
        <p className="mt-3 text-surface-300">{getErrorMessage(invitation.error)}</p>
        {loginLink}
      </Notice>
    )
  }
  return <RegisterForm token={token} email={invitation.data.email} />
}

function RegisterForm({ token, email }: { token: string; email: string }) {
  const { t, i18n } = useTranslation('auth')
  const [form, setForm] = useState({
    password: '',
    confirmPassword: '',
    firstName: '',
    lastName: '',
    phone: '',
  })
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false)
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [acceptedMarketing, setAcceptedMarketing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({})
  const [privacyError, setPrivacyError] = useState<string | null>(null)
  const [termsError, setTermsError] = useState<string | null>(null)
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
    const phoneErr = validatePhone(form.phone)
    if (phoneErr) errs.phone = phoneErr
    const pwErr = validatePassword(form.password, form.confirmPassword)
    if (pwErr) {
      // A password mismatch highlights the confirm field; a too-short password — the password field.
      if (form.password !== form.confirmPassword) errs.confirmPassword = pwErr
      else errs.password = pwErr
    }
    const privErr = !acceptedPrivacy ? t('register.privacyRequired') : null
    const termsErr = !acceptedTerms ? t('register.termsRequired') : null
    return { errs, privErr, termsErr }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    const { errs, privErr, termsErr } = validate()
    setFieldErrors(errs)
    setPrivacyError(privErr)
    setTermsError(termsErr)
    if (Object.keys(errs).length > 0 || privErr || termsErr) return

    setLoading(true)
    try {
      await registerUser({
        invitationToken: token,
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
        phone: form.phone.replace(/\s/g, ''),
        preferredLanguage: i18n.language,
        acceptedPrivacy,
        acceptedTerms,
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
      <Notice title={t('register.successTitle')}>
        <p className="mt-3 mb-6 text-surface-300">{t('register.successMessage')}</p>
        <Link to="/logowanie" className={`${linkClass} font-medium`}>
          {t('register.goToLogin')}
        </Link>
      </Notice>
    )
  }

  return (
    <div className={authStageClass}>
      <div className={authCardClass}>
        <div className="text-center mb-6">
          <Brand size="lg" />
          <h1 className="text-surface-400 mt-1">{t('register.title')}</h1>
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
                className={fieldErrors.firstName ? inputClassInvalid : inputClassMuted}
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
                className={fieldErrors.lastName ? inputClassInvalid : inputClassMuted}
                aria-invalid={!!fieldErrors.lastName}
                aria-describedby={fieldErrors.lastName ? 'lastName-error' : undefined}
              />
              {fieldErrors.lastName && <p id="lastName-error" className="text-xs text-rose-400/80 mt-1">{fieldErrors.lastName}</p>}
            </div>
          </div>

          {/* The address is the invitation's, not the person's choice: shown, never edited. */}
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-surface-300 mb-1">
              {t('register.email')}
            </label>
            <input
              id="email"
              type="email"
              value={email}
              readOnly
              aria-describedby="email-hint"
              className={`${inputClassMuted} text-surface-400`}
            />
            <p id="email-hint" className="text-xs text-surface-500 mt-1">{t('register.emailFromInvitation')}</p>
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
              className={fieldErrors.phone ? inputClassInvalid : inputClassMuted}
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
              className={fieldErrors.password ? inputClassInvalid : inputClassMuted}
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
              className={fieldErrors.confirmPassword ? inputClassInvalid : inputClassMuted}
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
                <Link to="/polityka-prywatnosci" target="_blank" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white">
                  {t('register.privacyLink')}
                </Link>
              </label>
            </div>
            {privacyError && <p id="acceptPrivacy-error" className="text-xs text-rose-400/80 mt-1">{privacyError}</p>}
          </div>

          <div>
            <div className="flex items-start gap-2">
              <input
                id="acceptTerms"
                type="checkbox"
                checked={acceptedTerms}
                onChange={(e) => { setAcceptedTerms(e.target.checked); setTermsError(null) }}
                className={clsx(
                  'mt-0.5 h-4 w-4 rounded bg-surface-800 text-primary-500 focus:ring-2',
                  termsError ? 'border-rose-500 focus:ring-rose-500' : 'border-surface-600 focus:ring-primary-500',
                )}
                aria-invalid={!!termsError}
                aria-describedby={termsError ? 'acceptTerms-error' : undefined}
              />
              <label htmlFor="acceptTerms" className={clsx('text-sm', termsError ? 'text-rose-300' : 'text-surface-300')}>
                {t('register.acceptTermsPrefix')}{' '}
                <Link to="/regulamin" target="_blank" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white">
                  {t('register.termsLink')}
                </Link>
              </label>
            </div>
            {termsError && <p id="acceptTerms-error" className="text-xs text-rose-400/80 mt-1">{termsError}</p>}
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
          <Link to="/logowanie" className={linkClass}>
            {t('register.login')}
          </Link>
        </p>
      </div>
    </div>
  )
}
