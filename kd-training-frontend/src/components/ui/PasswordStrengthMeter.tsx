import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

// Advisory strength meter powered by zxcvbn-ts (real pattern matching: sequences, keyboard walks,
// repeats, dictionary words, l33t). Lazy-loaded so the dictionary only ships when a password field
// is actually used. The real gate is the backend (length + personal data + HIBP breach check).
const LABELS = ['weak', 'weak', 'fair', 'good', 'strong'] as const
const COLORS = ['bg-rose-500', 'bg-rose-500', 'bg-amber-500', 'bg-lime-500', 'bg-green-500'] as const

type Scorer = (password: string) => number
let scorer: Scorer | null = null
let loadPromise: Promise<Scorer> | null = null

function loadScorer(): Promise<Scorer> {
  if (scorer) return Promise.resolve(scorer)
  if (!loadPromise) {
    loadPromise = Promise.all([
      import('@zxcvbn-ts/core'),
      import('@zxcvbn-ts/language-common'),
    ]).then(([core, common]) => {
      const factory = new core.ZxcvbnFactory({
        dictionary: { ...common.dictionary },
        graphs: common.adjacencyGraphs,
      })
      scorer = (password: string) => factory.check(password).score
      return scorer
    })
  }
  return loadPromise
}

export function PasswordStrengthMeter({ password }: { password: string }) {
  const { t } = useTranslation('auth')
  const [score, setScore] = useState<number | null>(null)

  useEffect(() => {
    if (!password) return
    let active = true
    loadScorer().then((scoreFn) => {
      if (active) setScore(scoreFn(password))
    })
    return () => {
      active = false
    }
  }, [password])

  // When the field is empty we render nothing, so a stale score value is irrelevant.
  if (!password || score === null) return null

  const widthPct = (Math.max(score, 1) / 4) * 100
  return (
    <div className="mt-1.5" aria-live="polite">
      <div className="h-1.5 w-full bg-surface-700 rounded">
        <div className={`h-1.5 rounded transition-all ${COLORS[score]}`} style={{ width: `${widthPct}%` }} />
      </div>
      <p className="text-xs text-surface-500 mt-1">
        {t('passwordStrength.label')}: {t(`passwordStrength.${LABELS[score]}`)}
      </p>
    </div>
  )
}
