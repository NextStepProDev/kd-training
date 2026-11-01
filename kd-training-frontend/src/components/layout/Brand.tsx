import clsx from 'clsx'

interface BrandProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const SIZES = { sm: 'text-base', md: 'text-lg', lg: 'text-3xl' } as const

/**
 * The wordmark, until the client has a logo: "KD" in the poster face, "Training" light and spaced in Inter.
 * One component so the navbar, footer and auth screens cannot drift into three spellings of it.
 */
export function Brand({ size = 'md', className }: BrandProps) {
  return (
    <span className={clsx('inline-flex items-baseline gap-[0.45em] uppercase leading-none text-white', SIZES[size], className)}>
      <span className="font-display tracking-[0.08em] text-[1.25em]">KD</span>
      {/* A real space between the two words: the gap is drawn by flex, but the accessible name is read
          from the text, and without it the wordmark was announced as "KDTraining" — a name the
          home link's "KD Training — strona główna" label then failed to contain. */}
      {' '}
      <span className="font-light tracking-[0.32em] text-surface-300">Training</span>
    </span>
  )
}
