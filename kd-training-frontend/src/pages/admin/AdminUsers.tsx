import { useState, type FormEvent } from 'react'
import clsx from 'clsx'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Users, Search, Mail, MailCheck, MailX, Trash2, LogOut, ShieldCheck, ShieldMinus, BadgeCheck, ChevronLeft, ChevronRight, ArrowUp, ArrowDown, ChevronsUpDown } from 'lucide-react'
import { adminApi, type EmailAudience } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { useToast } from '../../context/ToastContext'
import { useAuth } from '../../context/AuthContext'
import { AdminUserDetail } from './AdminUserDetail'
import type { AdminUser } from '../../types'
import { inputClassLg, textareaClassLg } from '../../utils/fieldClass'

type SortField = 'name' | 'email' | 'role' | 'marketing' | 'created'
type SortDir = 'asc' | 'desc'

export function AdminUsers() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const isSuperAdmin = user?.superAdmin ?? false

  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [sortField, setSortField] = useState<SortField>('created')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const [emailOpen, setEmailOpen] = useState(false)
  const [audience, setAudience] = useState<EmailAudience>('MARKETING')
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')

  const [toDelete, setToDelete] = useState<AdminUser | null>(null)
  const [notifyOnDelete, setNotifyOnDelete] = useState(true)

  const [toForceLogout, setToForceLogout] = useState<AdminUser | null>(null)

  const usersQuery = useQuery({
    queryKey: ['admin-users', search, page, sortField, sortDir],
    queryFn: () => adminApi.getUsers({ search: search || undefined, page, sort: sortField, direction: sortDir }),
  })

  const users = usersQuery.data?.content ?? []
  const totalElements = usersQuery.data?.totalElements ?? 0
  const totalPages = usersQuery.data?.totalPages ?? 0
  const allSelected = users.length > 0 && users.every(u => selected.has(u.id))

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] })

  const promoteMutation = useMutation({
    mutationFn: (id: string) => adminApi.promoteUser(id),
    onSuccess: () => { showToast(t('users.promoteSuccess')); invalidate() },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const demoteMutation = useMutation({
    mutationFn: (id: string) => adminApi.demoteUser(id),
    onSuccess: () => { showToast(t('users.demoteSuccess')); invalidate() },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: ({ id, notify }: { id: string; notify: boolean }) => adminApi.deleteUser(id, notify),
    onSuccess: () => {
      showToast(t('users.deleteSuccess'))
      setToDelete(null)
      setSelected(new Set())
      invalidate()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const forceLogoutMutation = useMutation({
    mutationFn: (id: string) => adminApi.forceLogout(id),
    onSuccess: () => {
      showToast(t('users.forceLogoutSuccess'))
      setToForceLogout(null)
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const emailMutation = useMutation({
    mutationFn: () => adminApi.sendUserEmail({
      subject: subject.trim(),
      message: message.trim(),
      audience,
      userIds: audience === 'SELECTED' ? [...selected] : undefined,
    }),
    onSuccess: (data) => {
      showToast(t('users.emailSuccess', { count: data.recipientCount }))
      setEmailOpen(false)
      setSubject('')
      setMessage('')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const selectedCount = selected.size

  // MARKETING reaches people with consent — the count isn't known from the list (filtered backend-side),
  // so we don't block sending by a counter; ALL/SELECTED have countable recipients.
  const knownRecipientCount = audience === 'ALL' ? totalElements : audience === 'SELECTED' ? selectedCount : null
  const canSend = audience === 'MARKETING' || (knownRecipientCount ?? 0) > 0

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    setSearch(searchInput.trim())
    setPage(0)
    setSelected(new Set())
  }

  const toggleOne = (id: string) => {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(users.map(u => u.id)))
  }

  const handleSort = (field: SortField) => {
    if (field === sortField) {
      setSortDir(prev => (prev === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortField(field)
      setSortDir('asc')
    }
    setPage(0)
  }

  const sortHeader = (field: SortField, label: string, className?: string) => {
    const active = sortField === field
    const Icon = !active ? ChevronsUpDown : sortDir === 'asc' ? ArrowUp : ArrowDown
    return (
      <th className={clsx('px-4 py-3', className)}>
        <button
          type="button"
          onClick={() => handleSort(field)}
          aria-label={t('users.sortBy', { column: label })}
          className={clsx('inline-flex items-center gap-1 hover:text-white', active && 'text-white')}
        >
          {label}
          <Icon className="w-3.5 h-3.5" />
        </button>
      </th>
    )
  }

  const openEmail = (initial: EmailAudience) => {
    setAudience(initial)
    setEmailOpen(true)
  }

  if (selectedUserId) {
    return <AdminUserDetail userId={selectedUserId} onBack={() => setSelectedUserId(null)} />
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <Users className="w-6 h-6 text-surface-400" />
        <h2 className="text-display text-3xl text-white">{t('users.title')}</h2>
      </div>

      <div className="rounded-2xl border border-white/[0.07] bg-surface-900 p-4 sm:p-6 mb-6">
        <form onSubmit={handleSearch} className="flex gap-3">
          <input
            type="text"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder={t('users.searchPlaceholder')}
            // min-w-0: a text input's intrinsic width (~20 characters) is a flex item's floor, and on a
            // phone that pushed the button past the card's edge.
            className={`min-w-0 flex-1 ${inputClassLg}`}
          />
          <Button type="submit" variant="secondary" loading={usersQuery.isFetching}>
            <Search className="w-4 h-4" />
            {t('users.search')}
          </Button>
        </form>

        <div className="flex flex-wrap items-center gap-3 mt-4">
          <Button
            variant="primary"
            size="sm"
            disabled={totalElements === 0}
            onClick={() => openEmail(selectedCount > 0 ? 'SELECTED' : 'MARKETING')}
          >
            <Mail className="w-4 h-4" />
            {t('users.compose')}
          </Button>
          {selectedCount > 0 && (
            <span className="text-sm text-surface-400">{t('users.selectedCount', { count: selectedCount })}</span>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-white/[0.07] bg-surface-900 overflow-hidden">
        {usersQuery.isLoading ? (
          // "No users found" is a verdict, and it must not be delivered before the first page has
          // even arrived. Paging and searching keep the previous table on screen (keepPreviousData),
          // so this only stands in for the very first load.
          <LoadingSpinner className="p-6" />
        ) : usersQuery.isError ? (
          <p className="text-red-400 text-sm p-6">{t('users.loadError')}</p>
        ) : users.length === 0 ? (
          <p className="text-surface-500 text-sm p-6">{t('users.noResults')}</p>
        ) : (
          <div className={clsx('overflow-x-auto transition-opacity', usersQuery.isFetching && 'opacity-60')}>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/[0.07] text-left text-surface-400">
                  <th className="px-4 py-3 w-10">
                    <input
                      type="checkbox"
                      aria-label={t('users.selectAll')}
                      checked={allSelected}
                      onChange={toggleAll}
                      className="w-4 h-4 accent-white cursor-pointer"
                    />
                  </th>
                  {sortHeader('name', t('users.name'))}
                  {/* Below md the table keeps only who and what to do: the e-mail moves under the name and
                      the rest hides. Seven columns at phone width made the table a thousand pixels wide,
                      and it pushed the whole page sideways despite its own scroll box. */}
                  {sortHeader('email', t('users.email'), 'hidden md:table-cell')}
                  <th className="hidden px-4 py-3 lg:table-cell">{t('users.phone')}</th>
                  {sortHeader('role', t('users.role'), 'hidden md:table-cell')}
                  {sortHeader('marketing', t('users.marketing'), 'hidden lg:table-cell')}
                  {sortHeader('created', t('users.created'), 'hidden lg:table-cell')}
                  <th className="px-4 py-3 text-right">{t('users.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {users.map(u => {
                  const isSelf = u.id === user?.id
                  return (
                    <tr key={u.id} className="border-b border-white/[0.05] transition-colors hover:bg-white/[0.02]">
                      <td className="px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label={t('users.selectOne', { name: `${u.firstName} ${u.lastName}` })}
                          checked={selected.has(u.id)}
                          onChange={() => toggleOne(u.id)}
                          className="w-4 h-4 accent-white cursor-pointer"
                        />
                      </td>
                      <td className="px-4 py-3 text-surface-100">
                        <button
                          type="button"
                          onClick={() => setSelectedUserId(u.id)}
                          className="font-medium text-surface-100 hover:text-white hover:underline text-left"
                        >
                          {u.firstName} {u.lastName}
                        </button>
                        {!u.emailVerified && (
                          <span className="ml-2 rounded-full border border-dashed border-white/25 px-2 py-0.5 text-[11px] text-surface-300">{t('users.unverified')}</span>
                        )}
                        <span className="mt-0.5 block break-all text-xs text-surface-400 md:hidden">{u.email}</span>
                      </td>
                      <td className="hidden px-4 py-3 text-surface-300 md:table-cell">{u.email}</td>
                      <td className="hidden px-4 py-3 text-surface-300 lg:table-cell">{u.phone ?? '—'}</td>
                      <td className="hidden px-4 py-3 md:table-cell">
                        {u.role === 'ADMIN' ? (
                          <span className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-full bg-white text-surface-950 font-medium">
                            <BadgeCheck className="w-3.5 h-3.5" />
                            {u.superAdmin ? t('users.superAdmin') : t('users.admin')}
                          </span>
                        ) : (
                          <span className="text-xs px-2 py-1 rounded-full border border-white/10 text-surface-400">
                            {t('users.user')}
                          </span>
                        )}
                      </td>
                      <td className="hidden px-4 py-3 lg:table-cell">
                        {u.marketingConsent ? (
                          <span className="inline-flex items-center gap-1 text-xs text-white" title={t('users.marketingYes')}>
                            <MailCheck className="w-4 h-4" />
                            <span className="sr-only">{t('users.marketingYes')}</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-surface-500" title={t('users.marketingNo')}>
                            <MailX className="w-4 h-4" />
                            <span className="sr-only">{t('users.marketingNo')}</span>
                          </span>
                        )}
                      </td>
                      <td className="hidden px-4 py-3 text-surface-500 lg:table-cell">
                        {new Date(u.createdAt).toLocaleDateString('pl-PL')}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          {u.role === 'USER' && isSuperAdmin && (
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={promoteMutation.isPending && promoteMutation.variables === u.id}
                              aria-label={t('users.promote')}
                              onClick={() => promoteMutation.mutate(u.id)}
                            >
                              <ShieldCheck className="w-4 h-4" />
                              {/* Icon only below lg, named by aria-label; the label returns where it fits. */}
                              <span className="hidden lg:inline">{t('users.promote')}</span>
                            </Button>
                          )}
                          {u.role === 'ADMIN' && isSuperAdmin && !u.superAdmin && !isSelf && (
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={demoteMutation.isPending && demoteMutation.variables === u.id}
                              aria-label={t('users.demote')}
                              onClick={() => demoteMutation.mutate(u.id)}
                            >
                              <ShieldMinus className="w-4 h-4" />
                              <span className="hidden lg:inline">{t('users.demote')}</span>
                            </Button>
                          )}
                          {/* Force-logout is offered for every user, admins included (a deliberate scenario,
                              e.g. a compromised account) — unlike delete it is not blocked for admins. */}
                          <Button
                            variant="ghost"
                            size="sm"
                            title={t('users.forceLogout')}
                            aria-label={t('users.forceLogout')}
                            onClick={() => setToForceLogout(u)}
                          >
                            <LogOut className="w-4 h-4" />
                          </Button>
                          {!u.superAdmin && !isSelf && (
                            <Button
                              variant="danger"
                              size="sm"
                              aria-label={t('users.deleteAccount', { name: `${u.firstName} ${u.lastName}` })}
                              title={t('users.deleteAccount', { name: `${u.firstName} ${u.lastName}` })}
                              onClick={() => { setNotifyOnDelete(true); setToDelete(u) }}
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {totalElements > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 mt-4 text-sm text-surface-400">
          <span>{t('users.total', { count: totalElements })}</span>
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="sm"
              disabled={page === 0 || usersQuery.isFetching}
              onClick={() => setPage(p => Math.max(p - 1, 0))}
            >
              <ChevronLeft className="w-4 h-4" />
              {t('users.prevPage')}
            </Button>
            <span>{t('users.pageOf', { page: page + 1, total: Math.max(totalPages, 1) })}</span>
            <Button
              variant="ghost"
              size="sm"
              disabled={page >= totalPages - 1 || usersQuery.isFetching}
              onClick={() => setPage(p => p + 1)}
            >
              {t('users.nextPage')}
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}

      <Modal isOpen={emailOpen} onClose={() => setEmailOpen(false)} title={t('users.emailTitle')}>
        <form
          onSubmit={e => { e.preventDefault(); emailMutation.mutate() }}
          className="space-y-4"
        >
          <fieldset className="space-y-2">
            <legend className="text-sm text-surface-300 mb-1">{t('users.audienceLegend')}</legend>
            {([
              { value: 'MARKETING', label: t('users.audienceMarketing'), hint: t('users.audienceMarketingHint'), disabled: false },
              { value: 'ALL', label: t('users.audienceAll'), hint: t('users.audienceAllHint'), disabled: totalElements === 0 },
              { value: 'SELECTED', label: t('users.audienceSelected', { count: selectedCount }), hint: t('users.audienceSelectedHint'), disabled: selectedCount === 0 },
            ] as const).map(opt => (
              <label
                key={opt.value}
                className={clsx(
                  'flex items-start gap-2 rounded-lg border p-3 cursor-pointer',
                  audience === opt.value ? 'border-white/50 bg-white/[0.06]' : 'border-white/10 bg-surface-800/40',
                  opt.disabled && 'opacity-40 cursor-not-allowed',
                )}
              >
                <input
                  type="radio"
                  name="audience"
                  value={opt.value}
                  checked={audience === opt.value}
                  disabled={opt.disabled}
                  onChange={() => setAudience(opt.value)}
                  className="mt-0.5 accent-white"
                />
                <span>
                  <span className="block text-sm text-surface-200">{opt.label}</span>
                  <span className="block text-xs text-surface-500">{opt.hint}</span>
                </span>
              </label>
            ))}
          </fieldset>
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('users.emailSubject')}</label>
            <input
              type="text"
              value={subject}
              maxLength={200}
              required
              onChange={e => setSubject(e.target.value)}
              className={inputClassLg}
            />
          </div>
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('users.emailMessage')}</label>
            <textarea
              value={message}
              maxLength={10000}
              required
              rows={8}
              onChange={e => setMessage(e.target.value)}
              className={textareaClassLg}
            />
          </div>
          <p className="text-xs text-surface-500">{t('users.emailSignatureHint')}</p>
          {audience === 'MARKETING' && (
            <p className="text-xs text-surface-500">{t('users.emailUnsubscribeHint')}</p>
          )}
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setEmailOpen(false)}>
              {t('users.cancel')}
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              loading={emailMutation.isPending}
              disabled={!subject.trim() || !message.trim() || !canSend}
            >
              {t('users.emailSend')}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate({ id: toDelete.id, notify: notifyOnDelete })}
        title={t('users.deleteTitle')}
        message={toDelete ? t('users.deleteMessage', { name: `${toDelete.firstName} ${toDelete.lastName}`, email: toDelete.email }) : ''}
        confirmLabel={t('users.deleteConfirm')}
        danger
        loading={deleteMutation.isPending}
      >
        <label className="flex items-start gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={notifyOnDelete}
            onChange={e => setNotifyOnDelete(e.target.checked)}
            className="mt-0.5 w-4 h-4 shrink-0 accent-white"
          />
          <span className="text-sm text-surface-400">{t('users.deleteNotify')}</span>
        </label>
      </ConfirmDialog>

      <ConfirmDialog
        isOpen={toForceLogout !== null}
        onClose={() => setToForceLogout(null)}
        onConfirm={() => toForceLogout && forceLogoutMutation.mutate(toForceLogout.id)}
        title={t('users.forceLogoutTitle')}
        message={toForceLogout ? t('users.forceLogoutMessage', { name: `${toForceLogout.firstName} ${toForceLogout.lastName}` }) : ''}
        confirmLabel={t('users.confirmForceLogout')}
        loading={forceLogoutMutation.isPending}
      />
    </div>
  )
}
