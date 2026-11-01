import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ClipboardList, Pencil, User as UserIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { myTrainingApi } from '../api/user'
import { Seo } from '../components/seo/Seo'
import { Card } from '../components/ui/Card'
import { buttonClass } from '../components/ui/buttonClass'
import { Avatar } from '../components/ui/Avatar'
import { SHORT_STALE_MS } from '../utils/queryFreshness'

export function MyAccountPage() {
  const { t } = useTranslation('account')
  const { t: tCalendar } = useTranslation('calendar')
  const { user } = useAuth()

  // The read marker lives on the server per account, so clearing alerts on a phone has to reach a
  // laptop without a reload — hence refetchOnWindowFocus, which the global 5-minute staleTime would
  // have suppressed. A 30 s floor keeps that behaviour and drops the part that hurt: with staleTime 0
  // every single alt-tab refired this and every other live query at once. A badge half a minute
  // behind is invisible.
  const summaryQuery = useQuery({
    queryKey: ['user', 'my-training', 'summary'],
    queryFn: myTrainingApi.getSummary,
    enabled: user?.isAthlete ?? false,
    staleTime: SHORT_STALE_MS,
    refetchOnWindowFocus: true,
    refetchOnMount: 'always',
  })

  if (!user) return null

  return (
    <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16 space-y-10">
      <Seo title={t('title')} path="/moje-konto" />

      <div className="flex items-center gap-5 sm:gap-7 animate-rise">
        <Avatar
          src={user.avatarUrl}
          name={user.firstName}
          className="w-20 h-20 sm:w-24 sm:h-24 ring-1 ring-white/10"
          textClassName="text-2xl"
        />
        <div className="min-w-0">
          <p className="kicker">{t('greeting', { name: user.firstName })}</p>
          <h1 className="mt-3 text-display text-4xl sm:text-5xl text-white">{t('title')}</h1>
        </div>
      </div>

      {/* For coaching clients — the 1-on-1 plan, first: it is what a client opens this page for. */}
      {user.isAthlete && (
        // Only for 1-on-1 clients. The flag rides along on /me, so showing the tile costs no
        // extra request — and people without a coach never see a door they cannot open.
        <AccountTile
          to="/moje-konto/plan-treningowy"
          icon={<ClipboardList className="w-6 h-6" />}
          title={tCalendar('my.tileTitle')}
          description={tCalendar('my.tileDescription')}
          count={summaryQuery.data?.unreadCount || undefined}
          countLabel={summaryQuery.data?.unreadCount ? tCalendar('my.tileBadge') : undefined}
          cta={tCalendar('my.tileCta')}
        />
      )}

      {/* Profile */}
      <Card padding="lg">
        <div className="flex items-center justify-between gap-4 mb-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-white">
            <UserIcon className="w-5 h-5 text-surface-400" />
            {t('profile.title')}
          </h2>
          <Link to="/settings" className={buttonClass('secondary', 'sm')}>
            <Pencil className="w-4 h-4" />
            {t('profile.edit')}
          </Link>
        </div>

        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
          <div>
            <dt className="kicker !tracking-[0.2em]">{t('profile.firstName')}</dt>
            <dd className="text-surface-100 mt-1.5">{user.firstName}</dd>
          </div>
          <div>
            <dt className="kicker !tracking-[0.2em]">{t('profile.lastName')}</dt>
            <dd className="text-surface-100 mt-1.5">{user.lastName}</dd>
          </div>
          <div>
            <dt className="kicker !tracking-[0.2em]">{t('profile.email')}</dt>
            <dd className="text-surface-100 mt-1.5 break-all">{user.email}</dd>
          </div>
          <div>
            <dt className="kicker !tracking-[0.2em]">{t('profile.phone')}</dt>
            <dd className="text-surface-100 mt-1.5">{user.phone || t('profile.noPhone')}</dd>
          </div>
        </dl>
      </Card>
    </div>
  )
}

interface AccountTileProps {
  to: string
  icon: React.ReactNode
  title: string
  description: string
  count?: number
  /** Optional: a tile without a counted collection behind it has nothing to caption. */
  countLabel?: string
  cta: string
}

/**
 * The way into the training plan: a wide, photographic-feeling card with the unread count as the one
 * solid element on it, so new messages from the coach are the first thing seen.
 */
function AccountTile({ to, icon, title, description, count, countLabel, cta }: AccountTileProps) {
  return (
    <Link
      to={to}
      className="group grain relative block overflow-hidden rounded-3xl border border-white/10 bg-[radial-gradient(120%_120%_at_0%_0%,#333_0%,#1b1b1b_60%)] p-6 sm:p-8 shadow-glow transition-[border-color,transform] duration-300 hover:-translate-y-0.5 hover:border-white/25 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
    >
      <div className="relative z-10">
        <div className="flex items-start justify-between gap-3">
          <span className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-white/10 text-white">
            {icon}
          </span>
          {count != null && (
            <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1 text-sm font-semibold text-surface-950">
              {count}
              {countLabel && <span className="font-medium">{countLabel}</span>}
            </span>
          )}
        </div>
        <h2 className="mt-8 text-display text-3xl sm:text-4xl text-white">{title}</h2>
        <p className="mt-3 max-w-md text-surface-300">{description}</p>
        <span className="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-white">
          {cta}
          <ArrowRight className="w-4 h-4 transition-transform duration-300 group-hover:translate-x-1" />
        </span>
      </div>
    </Link>
  )
}
