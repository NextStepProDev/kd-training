import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BadgeCheck, CheckCircle, Download, Dumbbell, KeyRound, Mail, MailCheck, MailX, Phone, Trash2, XCircle } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Avatar } from '../../components/ui/Avatar'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import clsx from 'clsx'
import { todayIso } from '../../utils/calendarRange'

export function AdminUserDetail({ userId, onBack }: { userId: string; onBack: () => void }) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [confirmUnflagAthlete, setConfirmUnflagAthlete] = useState(false)
  const [confirmErasePlan, setConfirmErasePlan] = useState(false)

  const userQuery = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: () => adminApi.getUser(userId),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-user', userId] })

  const exportMutation = useMutation({
    mutationFn: () => adminApi.exportUserData(userId),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `dane-${userQuery.data?.lastName ?? 'uzytkownik'}-${todayIso()}.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      // Revoking right after click() cancels the download in some browsers; give it a moment.
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const eraseP1Mutation = useMutation({
    mutationFn: () => adminApi.eraseTrainingPlan(userId),
    onSuccess: (erased) => {
      // The counts, not just a "done": this is the one irreversible action on the page, and a dialog
      // that simply closes leaves the admin guessing whether it ran at all. `notes` counts only the
      // caller's own.
      showToast(t('users.detail.erasePlanDone', erased))
      setConfirmErasePlan(false)
      // invalidate(), not a hand-written key: this panel is registered as ['admin-user', userId],
      // so ['admin', 'user', userId] matched no query at all and the one irreversible action on the
      // page refreshed nothing. Harmless today only because the payload carries nothing the erasure
      // changes — the first field that does would fail silently.
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['admin', 'athletes'] })
      // The notebook loses rows about this person, so every note query and marker is now stale.
      queryClient.invalidateQueries({ queryKey: ['admin', 'notes'] })
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  // Flagging is non-destructive both ways: clearing it only hides the calendar, the plan and its
  // history stay in the database. The confirm below says so, so nobody hesitates over the switch.
  const athleteMutation = useMutation({
    mutationFn: (enabled: boolean) => adminApi.setAthlete(userId, enabled),
    onSuccess: (_data, enabled) => {
      showToast(t(enabled ? 'users.detail.athleteEnabled' : 'users.detail.athleteDisabled'))
      setConfirmUnflagAthlete(false)
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['admin', 'athletes'] })
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  if (userQuery.isLoading) return <LoadingSpinner />
  if (userQuery.isError || !userQuery.data) {
    return (
      <div>
        <BackButton onBack={onBack} label={t('users.detail.back')} />
        <p className="text-red-400 text-sm mt-4">{t('users.detail.loadError')}</p>
      </div>
    )
  }

  const user = userQuery.data

  return (
    <div>
      <BackButton onBack={onBack} label={t('users.detail.back')} />

      {/* Profile header */}
      <div className="flex items-center gap-4 mt-4 mb-6">
        <Avatar src={user.avatarUrl} name={`${user.firstName} ${user.lastName}`} className="w-16 h-16" textClassName="text-xl" />
        <div>
          <h2 className="text-xl font-semibold text-surface-100">{user.firstName} {user.lastName}</h2>
          <p className="text-surface-400 text-sm flex items-center gap-1"><Mail className="w-4 h-4" /> {user.email}</p>
          {user.role === 'ADMIN' && (
            <span className="inline-flex items-center gap-1 mt-1 text-xs px-2 py-1 rounded-full bg-primary-500/15 text-primary-300">
              <BadgeCheck className="w-3.5 h-3.5" />
              {user.superAdmin ? t('users.superAdmin') : t('users.admin')}
            </span>
          )}
        </div>
      </div>

      {/* Profile details (read-only) */}
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-8 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <ProfileRow icon={<Phone className="w-4 h-4" />} label={t('users.phone')} value={user.phone ?? '—'} />
        <ProfileRow
          icon={user.emailVerified ? <CheckCircle className="w-4 h-4 text-green-400" /> : <XCircle className="w-4 h-4 text-amber-400" />}
          label={t('users.detail.emailVerified')}
          value={user.emailVerified ? t('users.detail.yes') : t('users.detail.no')}
        />
        <ProfileRow
          icon={user.marketingConsent ? <MailCheck className="w-4 h-4 text-emerald-400" /> : <MailX className="w-4 h-4 text-surface-500" />}
          label={t('users.detail.marketing')}
          value={user.marketingConsent ? t('users.detail.yes') : t('users.detail.no')}
        />
        <ProfileRow icon={<KeyRound className="w-4 h-4" />} label={t('users.detail.accountType')} value={user.oauthLinked ? 'Google' : (user.hasPassword ? t('users.detail.password') : '—')} />
        <ProfileRow label={t('users.created')} value={new Date(user.createdAt).toLocaleDateString('pl-PL')} />
      </div>

      {/* GDPR access request: one ZIP with everything held about the person, ready to send them. */}
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <Download className="w-5 h-5 mt-0.5 shrink-0 text-surface-500" />
          <div>
            <p className="font-medium text-surface-100">{t('users.detail.exportTitle')}</p>
            <p className="text-sm text-surface-400 mt-0.5">{t('users.detail.exportDesc')}</p>
          </div>
        </div>
        <Button variant="ghost" size="sm" loading={exportMutation.isPending}
          onClick={() => exportMutation.mutate()}>
          {t('users.detail.exportAction')}
        </Button>
      </div>

      {/* 1-on-1 coaching. Separate from the admin role: it grants no rights, it unlocks a calendar. */}
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <Dumbbell className={clsx('w-5 h-5 mt-0.5 shrink-0', user.isAthlete ? 'text-primary-400' : 'text-surface-500')} />
          <div>
            <p className="font-medium text-surface-100">{t('users.detail.athleteTitle')}</p>
            <p className="text-sm text-surface-400 mt-0.5">
              {user.isAthlete ? t('users.detail.athleteOnDesc') : t('users.detail.athleteOffDesc')}
            </p>
          </div>
        </div>
        <Button
          variant={user.isAthlete ? 'ghost' : 'primary'}
          size="sm"
          loading={athleteMutation.isPending}
          onClick={() => (user.isAthlete ? setConfirmUnflagAthlete(true) : athleteMutation.mutate(true))}
        >
          {user.isAthlete ? t('users.detail.athleteDisable') : t('users.detail.athleteEnable')}
        </Button>
      </div>

      {/* Erasing the plan is a different act from hiding it, so it gets a different button and a
          different colour. Shown regardless of the athlete flag: an account that already lost the
          flag is precisely the one whose plan data has no other route out of the panel. */}
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <Trash2 className="w-5 h-5 mt-0.5 shrink-0 text-surface-500" />
          <div>
            <p className="font-medium text-surface-100">{t('users.detail.erasePlanTitle')}</p>
            <p className="text-sm text-surface-400 mt-0.5">{t('users.detail.erasePlanDesc')}</p>
          </div>
        </div>
        <Button variant="danger" size="sm" loading={eraseP1Mutation.isPending}
          onClick={() => setConfirmErasePlan(true)}>
          {t('users.detail.erasePlanAction')}
        </Button>
      </div>

      {/* Not marked danger: nothing is deleted, the calendar just stops being reachable. */}
      <ConfirmDialog
        isOpen={confirmUnflagAthlete}
        onClose={() => setConfirmUnflagAthlete(false)}
        onConfirm={() => athleteMutation.mutate(false)}
        title={t('users.detail.athleteDisableTitle')}
        message={t('users.detail.athleteDisableMessage', { name: `${user.firstName} ${user.lastName}` })}
        confirmLabel={t('users.detail.athleteDisable')}
        loading={athleteMutation.isPending}
      />

      <ConfirmDialog
        isOpen={confirmErasePlan}
        onClose={() => setConfirmErasePlan(false)}
        onConfirm={() => eraseP1Mutation.mutate()}
        title={t('users.detail.erasePlanTitle')}
        message={t('users.detail.erasePlanMessage', { name: `${user.firstName} ${user.lastName}` })}
        confirmLabel={t('users.detail.erasePlanAction')}
        danger
        loading={eraseP1Mutation.isPending}
      />
    </div>
  )
}

function BackButton({ onBack, label }: { onBack: () => void; label: string }) {
  return (
    <button onClick={onBack} className="inline-flex items-center gap-1 text-sm text-surface-400 hover:text-surface-200">
      <ArrowLeft className="w-4 h-4" /> {label}
    </button>
  )
}

function ProfileRow({ icon, label, value }: { icon?: ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      {icon && <span className="text-surface-500">{icon}</span>}
      <span className="text-surface-400">{label}:</span>
      <span className="text-surface-100">{value}</span>
    </div>
  )
}
