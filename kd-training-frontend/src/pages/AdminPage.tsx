import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import clsx from 'clsx'
import { AdminUsers } from './admin/AdminUsers'
import { AdminAthletes } from './admin/AdminAthletes'
import { AdminExerciseVideos } from './admin/AdminExerciseVideos'
import { AdminTrainingTemplates } from './admin/AdminTrainingTemplates'
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
    <div className="max-w-7xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-bold text-surface-100 mb-6">{t('admin.title')}</h1>

      <div className="flex flex-wrap gap-1 mb-8 border-b border-surface-800 pb-1">
        {tabs.map(item => (
          <button
            key={item.key}
            onClick={() => { setOpenAthlete(null); navigate(`/admin/${item.key}`) }}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-t-lg transition-colors',
              tab === item.key
                ? 'bg-surface-800 text-primary-400 border-b-2 border-primary-500'
                : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800/50'
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
              <AdminExerciseVideos />
              <AdminTrainingTemplates />
            </div>
      )}
      {tab === 'uzytkownicy' && <AdminUsers />}
    </div>
  )
}
