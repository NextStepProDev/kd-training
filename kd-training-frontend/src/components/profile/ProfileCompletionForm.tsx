import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { useAuth } from '../../context/AuthContext'
import { authApi } from '../../api/client'
import { Button } from '../ui/Button'
import { getMissingProfileFields, type ProfileField } from '../../utils/profileCompletion'

const inputBase =
  'w-full px-3 py-2 bg-surface-800 border rounded-lg text-surface-100 focus:outline-none focus:ring-2'
const inputNormal = 'border-surface-700 focus:ring-primary-500'
const inputError = 'border-rose-500 focus:ring-rose-500'

const FIELD_INPUT_TYPE: Record<ProfileField, string> = { firstName: 'text', lastName: 'text', phone: 'tel' }
const FIELD_AUTOCOMPLETE: Record<ProfileField, string> = { firstName: 'given-name', lastName: 'family-name', phone: 'tel' }

interface ProfileCompletionFormProps {
  /** Submit button label (defaults to "Zapisz i kontynuuj"). */
  submitLabel?: string
}

/**
 * Account completion form after registration/login: missing required profile fields
 * (first name, last name, phone) and — for Google accounts without consent — mandatory acceptance
 * of the privacy policy (GDPR) and optional marketing consent. Renders only what is
 * missing, saves it to the account and refreshes the user in `AuthContext`; the parent component reacts
 * to the user change (the gaps disappear), so it doesn't need a callback.
 *
 * Validation is per-field: invalid fields get a red border and a message right below them
 * (rather than one generic error at the bottom), so it's immediately clear what's missing — e.g. the phone,
 * not the marketing consent.
 */
export function ProfileCompletionForm({ submitLabel }: ProfileCompletionFormProps) {
  const { t } = useTranslation(['settings', 'auth'])
  const { user, refreshUser } = useAuth()
  const missing = getMissingProfileFields(user)
  const needPrivacy = !!user && !user.privacyAccepted
  const [values, setValues] = useState<Record<ProfileField, string>>({ firstName: '', lastName: '', phone: '' })
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false)
  const [acceptedMarketing, setAcceptedMarketing] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<ProfileField, string>>>({})
  const [privacyError, setPrivacyError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!user || (missing.length === 0 && !needPrivacy)) return null

  const setField = (field: ProfileField, value: string) => {
    setValues((prev) => ({ ...prev, [field]: value }))
    // Clear the field error as soon as the user starts correcting it.
    setFieldErrors((prev) => {
      if (!prev[field]) return prev
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    const nextFieldErrors: Partial<Record<ProfileField, string>> = {}
    for (const field of missing) {
      const value = values[field].trim()
      if (!value) {
        nextFieldErrors[field] = t('completion.errors.fieldRequired')
      } else if ((field === 'firstName' || field === 'lastName') && value.length < 3) {
        nextFieldErrors[field] = t('completion.errors.nameTooShort')
      }
    }
    const nextPrivacyError = needPrivacy && !acceptedPrivacy ? t('register.privacyRequired', { ns: 'auth' }) : null

    setFieldErrors(nextFieldErrors)
    setPrivacyError(nextPrivacyError)
    if (Object.keys(nextFieldErrors).length > 0 || nextPrivacyError) return

    setLoading(true)
    try {
      if (missing.length > 0) {
        // Fields not in the missing list keep their existing account values.
        await authApi.updateProfile(
          missing.includes('firstName') ? values.firstName.trim() : user.firstName,
          missing.includes('lastName') ? values.lastName.trim() : user.lastName,
          missing.includes('phone') ? values.phone.trim() : (user.phone ?? ''),
        )
      }
      if (needPrivacy) {
        await authApi.submitConsents(true, acceptedMarketing)
      }
      await refreshUser()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {missing.map((field, index) => {
        const fieldError = fieldErrors[field]
        return (
          <div key={field}>
            <label htmlFor={`completion-${field}`} className="block text-sm font-medium text-surface-300 mb-1">
              {t(`profile.${field}`)}
            </label>
            <input
              id={`completion-${field}`}
              type={FIELD_INPUT_TYPE[field]}
              autoComplete={FIELD_AUTOCOMPLETE[field]}
              value={values[field]}
              onChange={(e) => setField(field, e.target.value)}
              className={clsx(inputBase, fieldError ? inputError : inputNormal)}
              autoFocus={index === 0}
              aria-invalid={!!fieldError}
              aria-describedby={fieldError ? `completion-${field}-error` : undefined}
            />
            {fieldError && (
              <p id={`completion-${field}-error`} className="mt-1 text-sm text-rose-400">
                {fieldError}
              </p>
            )}
          </div>
        )
      })}

      {needPrivacy && (
        <div className="space-y-3 pt-1">
          <div>
            <div className="flex items-start gap-2">
              <input
                id="completionPrivacy"
                type="checkbox"
                checked={acceptedPrivacy}
                onChange={(e) => { setAcceptedPrivacy(e.target.checked); setPrivacyError(null) }}
                className={clsx(
                  'mt-0.5 h-4 w-4 rounded bg-surface-800 text-primary-500 focus:ring-2',
                  privacyError ? 'border-rose-500 focus:ring-rose-500' : 'border-surface-600 focus:ring-primary-500',
                )}
                aria-invalid={!!privacyError}
                aria-describedby={privacyError ? 'completionPrivacy-error' : undefined}
              />
              <label htmlFor="completionPrivacy" className={clsx('text-sm', privacyError ? 'text-rose-300' : 'text-surface-300')}>
                {t('register.acceptPrivacyPrefix', { ns: 'auth' })}{' '}
                <Link to="/polityka-prywatnosci" target="_blank" className="text-primary-400 hover:text-primary-300 underline">
                  {t('register.privacyLink', { ns: 'auth' })}
                </Link>
              </label>
            </div>
            {privacyError && (
              <p id="completionPrivacy-error" className="mt-1 text-sm text-rose-400">
                {privacyError}
              </p>
            )}
          </div>
          <div className="flex items-start gap-2 rounded-lg border border-surface-700/60 bg-surface-800/40 p-3">
            <input
              id="completionMarketing"
              type="checkbox"
              checked={acceptedMarketing}
              onChange={(e) => setAcceptedMarketing(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-2 focus:ring-primary-500"
            />
            <label htmlFor="completionMarketing" className="text-sm text-surface-300">
              {t('register.acceptMarketing', { ns: 'auth' })}
              <span className="block text-xs text-surface-500 mt-0.5">{t('register.acceptMarketingHint', { ns: 'auth' })}</span>
            </label>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-rose-400/80">{error}</p>}
      <Button type="submit" variant="primary" className="w-full" loading={loading}>
        {submitLabel ?? t('completion.submit')}
      </Button>
    </form>
  )
}
