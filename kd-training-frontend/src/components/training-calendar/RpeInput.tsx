import clsx from 'clsx'

/**
 * Bands match how the coach reads the number: easy / working / hard — told apart by brightness, the
 * same three steps as the intensity bar in the stats, so the two read alike.
 */
function bandClass(value: number, selected: boolean): string {
  if (!selected) return 'bg-surface-800 text-surface-400 hover:bg-surface-700'
  if (value <= 4) return 'bg-surface-600 text-white'
  if (value <= 7) return 'bg-surface-400 text-surface-950'
  return 'bg-white text-surface-950'
}

interface RpeInputProps {
  value: number | null
  onChange: (value: number | null) => void
  label: string
}

export function RpeInput({ value, onChange, label }: RpeInputProps) {
  return (
    <fieldset>
      <legend className="mb-1 block text-sm text-surface-300">{label}</legend>
      {/* Clicking the picked number clears it. Nothing else here deselects, and a rating you
          cannot take back is worse than no rating. */}
      <div className="flex flex-wrap gap-1">
        {Array.from({ length: 10 }, (_, i) => i + 1).map(n => (
          <button
            key={n}
            type="button"
            aria-pressed={value === n}
            aria-label={`RPE ${n}`}
            onClick={() => onChange(value === n ? null : n)}
            className={clsx(
              'h-9 w-9 rounded-lg text-sm font-medium transition-colors',
              bandClass(n, value === n),
            )}
          >
            {n}
          </button>
        ))}
      </div>
    </fieldset>
  )
}
