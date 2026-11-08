import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MailPlus, RotateCw, X } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../context/ToastContext'
import { inputClassLg } from '../../utils/fieldClass'
import { formatDate } from '../../utils/dates'
import { validateEmail } from '../../utils/validation'

/**
 * The only way a new person gets an account: an invitation by e-mail. Lists the open ones, so the coach
 * sees who has not signed up yet and can send the link again (which replaces the old one) or withdraw it.
 */
export function AdminInvitations() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)

  const invitationsQuery = useQuery({
    queryKey: ['admin-invitations'],
    queryFn: adminApi.getInvitations,
  })
  const invitations = invitationsQuery.data ?? []

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-invitations'] })

  const inviteMutation = useMutation({
    mutationFn: (address: string) => adminApi.invite(address),
    onSuccess: (invitation) => {
      showToast(t('invitations.sent', { email: invitation.email }))
      setEmail('')
      invalidate()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => adminApi.cancelInvitation(id),
    onSuccess: () => { showToast(t('invitations.cancelled')); invalidate() },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const error = validateEmail(email)
    setEmailError(error)
    if (!error) inviteMutation.mutate(email.trim())
  }

  return (
    <section aria-labelledby="invitations-title" className="rounded-2xl border border-white/[0.07] bg-surface-900 p-4 sm:p-6 mb-6">
      <h3 id="invitations-title" className="text-lg font-semibold text-white">{t('invitations.title')}</h3>
      <p className="mt-1 text-sm text-surface-400">{t('invitations.hint')}</p>

      <form onSubmit={handleSubmit} className="mt-4 flex gap-3" noValidate>
        <label htmlFor="invite-email" className="sr-only">{t('invitations.email')}</label>
        <input
          id="invite-email"
          type="email"
          value={email}
          onChange={e => { setEmail(e.target.value); setEmailError(null) }}
          placeholder={t('invitations.email')}
          aria-invalid={!!emailError}
          aria-describedby={emailError ? 'invite-email-error' : undefined}
          // min-w-0: the same phone-width overflow as the search field below.
          className={`min-w-0 flex-1 ${inputClassLg}`}
        />
        <Button type="submit" variant="primary" loading={inviteMutation.isPending && inviteMutation.variables === email.trim()}>
          <MailPlus className="w-4 h-4" />
          {t('invitations.send')}
        </Button>
      </form>
      {emailError && <p id="invite-email-error" className="text-xs text-rose-400/80 mt-1">{emailError}</p>}

      {invitations.length > 0 && (
        <ul className="mt-4 divide-y divide-white/[0.07] border-t border-white/[0.07]">
          {invitations.map(invitation => (
            <li key={invitation.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
              {/* A row of its own on a phone: beside the buttons the address was cut to "katarzyna.l…",
                  and the address is the one thing this row is for. */}
              <div className="min-w-0 w-full sm:w-auto sm:flex-1">
                <p className="truncate text-sm text-white">{invitation.email}</p>
                <p className="text-xs text-surface-500">{t('invitations.validUntil', { date: formatDate(invitation.expiresAt) })}</p>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={inviteMutation.isPending}
                  onClick={() => inviteMutation.mutate(invitation.email)}
                  aria-label={t('invitations.resendFor', { email: invitation.email })}
                >
                  <RotateCw className="w-4 h-4" />
                  {t('invitations.resend')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate(invitation.id)}
                  aria-label={t('invitations.cancelFor', { email: invitation.email })}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {invitationsQuery.isError && <p className="mt-4 text-sm text-red-400">{t('invitations.loadError')}</p>}
    </section>
  )
}
