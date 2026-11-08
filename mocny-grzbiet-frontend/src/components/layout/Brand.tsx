import clsx from 'clsx'

interface BrandProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const SIZES = { sm: 'text-base', md: 'text-lg', lg: 'text-3xl' } as const

/**
 * The client's round logo next to "Mocny Grzbiet" in the poster face. The logo carries the name too,
 * but at navbar size its lettering is too small to read, so the wordmark stays beside it.
 * One component so the navbar, footer and auth screens cannot drift into three versions of it.
 */
export function Brand({ size = 'md', className }: BrandProps) {
  return (
    <span className={clsx('inline-flex items-center gap-[0.55em] uppercase leading-none text-white', SIZES[size], className)}>
      {/* Decoration: the name is in the text next to it, so announcing the logo would say it twice.
          The size is in em, so the mark scales with the wordmark at every Brand size. */}
      <img src="/logo.svg" alt="" aria-hidden="true" width={40} height={40} className="h-[2.2em] w-[2.2em]" />
      {/* A real space between the two words: the accessible name is read from the text, and without it
          the wordmark would be announced as one word, a name the home link's label then fails to contain. */}
      <span className="font-display tracking-[0.08em] text-[1.15em]">
        Mocny{' '}<span className="font-light font-sans tracking-[0.3em] text-surface-300 text-[0.75em]">Grzbiet</span>
      </span>
    </span>
  )
}
