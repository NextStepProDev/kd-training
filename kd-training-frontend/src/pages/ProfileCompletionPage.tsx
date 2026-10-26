import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { authApi } from '../api/client'
import { consumeRedirectPath } from '../utils/redirect'
import { needsProfileCompletion } from '../utils/profileCompletion'
import { ProfileCompletionForm } from '../components/profile/ProfileCompletionForm'
import { Button } from '../components/ui/Button'

/**
 * Profile completion page after registration/login, when required data is missing
 * (typically the phone after Google registration) or the privacy policy hasn't been accepted.
 * Acts as a hard gate (see Layout): without completing the account the user can't go anywhere else.
 * When the policy isn't accepted, we also offer an exit "I don't accept — delete account",
 * so the user can erase their data without having to use the rest of the app (GDPR).
 * After saving the data the user is redirected to where they came from (returnTo) or to "My account".
 */
export function ProfileCompletionPage() {
  const { t } = useTranslation('settings')
  const { user, logout } = useAuth()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const incomplete = needsProfileCompletion(user)
  const needPrivacy = !!user && !user.privacyAccepted
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    // Profile complete and consents settled (direct entry or after saving) → continue the path.
    if (user && !incomplete) {
      navigate(consumeRedirectPath() || '/moje-konto', { replace: true })
    }
  }, [user, incomplete, navigate])

  if (!user || !incomplete) return null

  // Google accounts have no password, so deletion doesn't require confirming it.
  const handleDecline = async () => {
    setDeleting(true)
    try {
      await authApi.deleteAccount(null)
      logout()
      showToast(t('completion.decline.deleted'))
      navigate('/', { replace: true })
    } catch (err) {
      showToast(err instanceof Error ? err.message : String(err), 'error')
      setDeleting(false)
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <h1 className="text-2xl font-bold text-surface-100">{t('completion.pageTitle')}</h1>
      <p className="text-surface-400 mt-2 mb-6">{t('completion.pageText')}</p>
      <ProfileCompletionForm />

      {needPrivacy && (
        <div className="mt-8 pt-6 border-t border-surface-800">
          <h2 className="text-sm font-semibold text-surface-200">{t('completion.decline.title')}</h2>
          <p className="text-sm text-surface-400 mt-1 mb-4">{t('completion.decline.text')}</p>
          {confirmingDelete ? (
            <div className="space-y-3">
              <p className="text-sm text-rose-400/90">{t('completion.decline.confirm')}</p>
              <div className="flex gap-3">
                <Button type="button" variant="danger" loading={deleting} onClick={handleDecline}>
                  {t('completion.decline.confirmYes')}
                </Button>
                <Button type="button" variant="ghost" disabled={deleting} onClick={() => setConfirmingDelete(false)}>
                  {t('completion.decline.cancel')}
                </Button>
              </div>
            </div>
          ) : (
            <Button type="button" variant="ghost" onClick={() => setConfirmingDelete(true)}>
              {t('completion.decline.button')}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
