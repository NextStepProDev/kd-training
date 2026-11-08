import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import clsx from 'clsx'
import { PageHeader } from '../components/ui/PageHeader'
import { AdminUsers } from './admin/AdminUsers'
import { AdminAthletes } from './admin/AdminAthletes'
import { AdminExerciseVideos } from './admin/AdminExerciseVideos'
import { AdminTrainingTemplates } from './admin/AdminTrainingTemplates'
import { AdminDaysOff } from './admin/AdminDaysOff'
import type { AthleteSummary } from '../types'

const tabs = [
  { key: 'podopieczni', ns: 'admin.tabs.athletes' },
  { key: 'uzytkownicy', ns: 'admin.tabs.users' },
] as const

const validTabs: Set<string> = new Set(tabs.map(item => item.key))

export function AdminPage() {
  const { t } = useTranslation('common')
  const { tab } = useParams<{ tab: string }>()
  const navigate = useNavigate()
  // An open client's calendar replaces the tab, so the library and template sections do not hang
  // underneath looking like they belong to that one person.
  const [openAthlete, setOpenAthlete] = useState<AthleteSummary | null>(null)

  if (!tab || !validTabs.has(tab)) {
    return <Navigate to="/admin/podopieczni" replace />
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-10 sm:py-14">
      <PageHeader kicker={t('admin.kicker')} title={t('admin.title')} className="mb-8" />

      {/* Segmented pills, the same shape as the calendar's week/month switch. */}
      <div className="mb-10 inline-flex gap-1 rounded-full border border-white/10 bg-surface-900 p-1">
        {tabs.map(item => (
          <button
            key={item.key}
            // Two sub-pages behind one URL segment, not an ARIA tab set (no arrow-key handling):
            // aria-current says which one is open without promising keyboard behaviour it lacks.
            aria-current={tab === item.key ? 'page' : undefined}
            onClick={() => { setOpenAthlete(null); navigate(`/admin/${item.key}`) }}
            className={clsx(
              'rounded-full px-4 py-2 text-sm font-semibold transition-colors',
              tab === item.key
                ? 'bg-white text-surface-950'
                : 'text-surface-400 hover:text-white'
            )}
          >
            {t(item.ns)}
          </button>
        ))}
      </div>

      {tab === 'podopieczni' && (
        openAthlete
          ? <AdminAthletes openAthlete={openAthlete} onOpen={setOpenAthlete} />
          : <div className="space-y-12">
              <AdminAthletes openAthlete={null} onOpen={setOpenAthlete} />
              <AdminDaysOff />
              <AdminExerciseVideos />
              <AdminTrainingTemplates />
            </div>
      )}
      {tab === 'uzytkownicy' && <AdminUsers />}
    </div>
  )
}
