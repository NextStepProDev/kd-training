import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ExternalLink, ShieldCheck } from 'lucide-react'
import { Button } from '../ui/Button'
import { authApi } from '../../api/client'

// Same one-liner the auth pages use — the repo has no shared error helper.
const getErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err))

interface Props {
  /** Re-reads the profile so the stored consent flips the gate off. */
  onAccepted: () => Promise<void>
}

/**
 * One-time consent screen in front of the client's 1-on-1 calendar.
 *
 * The calendar holds weigh-ins, weight goals, daily calorie targets, effort ratings and notes on
 * how a session felt — health data under GDPR art. 9, which needs EXPLICIT consent: a statement the
 * client makes, never one inferred from them typing a number in. Hence a checkbox that starts
 * unticked and a submit button disabled until it is ticked. Shown to every client whose consent is
 * not on record, including those who have used the calendar for months (V38 backfills nothing).
 *
 * Rendered instead of the calendar rather than as a dialog over it: nothing may load calendar
 * content before consent (the API 409s regardless), the client needs room to read the policy, and
 * a dialog with no way out would make the consent look coerced.
 */
export function TrainingConsentGate({ onAccepted }: Props) {
  const { t } = useTranslation('calendar')
  const [checked, setChecked] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const accept = async () => {
    if (!checked) return
    setSaving(true)
    setError(null)
    try {
      await authApi.grantTrainingConsent()
      await onAccepted()
    } catch (e) {
      setError(getErrorMessage(e))
      setSaving(false)
    }
  }

  const items = t('consent.items', { returnObjects: true }) as string[]

  return (
    <div className="mx-auto max-w-2xl">
      <div className="rounded-2xl border border-surface-800 bg-surface-900 p-6 sm:p-8">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary-500/15">
          <ShieldCheck className="h-6 w-6 text-primary-400" />
        </div>

        <h2 className="text-center text-xl font-semibold text-surface-100">{t('consent.title')}</h2>
        <p className="mt-3 text-center text-sm leading-relaxed text-surface-400">{t('consent.intro')}</p>

        <div className="mt-6 rounded-xl bg-surface-800/50 p-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-surface-300">
            {t('consent.itemsTitle')}
          </p>
          <ul className="space-y-2">
            {items.map((item) => (
              <li key={item} className="flex items-start gap-2 text-sm leading-relaxed text-surface-400">
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary-500" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <p className="mt-4 text-sm leading-relaxed text-surface-400">{t('consent.coachAccess')}</p>

        {/* The policy is one click away, at the section covering exactly this processing —
            consent given without the chance to read what it covers is not informed consent. */}
        <Link
          to="/polityka-prywatnosci#plan-treningowy"
          target="_blank"
          rel="noopener noreferrer"
          className="mt-6 inline-flex items-center gap-1.5 text-sm text-primary-400 underline transition-colors hover:text-primary-300"
        >
          {t('consent.readPolicy')}
          <ExternalLink className="h-3.5 w-3.5" />
        </Link>

        <label className="mt-6 flex cursor-pointer items-start gap-3 rounded-xl bg-surface-800/50 p-4 transition-colors hover:bg-surface-800/80">
          <input
            type="checkbox"
            checked={checked}
            onChange={(e) => setChecked(e.target.checked)}
            className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer rounded border-surface-600 bg-surface-900 text-primary-600 focus:ring-2 focus:ring-primary-500"
          />
          <span className="text-sm leading-relaxed text-surface-300">
            <Trans
              i18nKey="consent.checkbox"
              ns="calendar"
              components={{
                1: (
                  <Link
                    to="/polityka-prywatnosci#plan-treningowy"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary-400 underline transition-colors hover:text-primary-300"
                    onClick={(e) => e.stopPropagation()}
                  />
                ),
              }}
            />
          </span>
        </label>

        {error && <p className="mt-4 text-center text-sm text-rose-400">{error}</p>}

        <Button
          variant="primary"
          size="lg"
          className="mt-6 w-full"
          disabled={!checked}
          loading={saving}
          onClick={accept}
        >
          {t('consent.accept')}
        </Button>

        <p className="mt-5 border-t border-surface-800 pt-4 text-center text-xs leading-relaxed text-surface-500">
          {t('consent.withdrawNote')}
        </p>
      </div>
    </div>
  )
}
