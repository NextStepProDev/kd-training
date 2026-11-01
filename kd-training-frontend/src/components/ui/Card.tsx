import type { HTMLAttributes } from 'react'
import clsx from 'clsx'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** `raised` lifts the card off a section that is itself a surface (a card inside a card). */
  tone?: 'default' | 'raised'
  padding?: 'none' | 'sm' | 'md' | 'lg'
}

const PADDING = { none: '', sm: 'p-4', md: 'p-5 sm:p-6', lg: 'p-6 sm:p-8' } as const

/**
 * The one surface container. `bg-surface-900 rounded-xl border border-surface-800` used to be pasted
 * into every page, each copy a little different; a card is now a decision made here.
 */
export function Card({ tone = 'default', padding = 'md', className, ...rest }: CardProps) {
  return (
    <div
      className={clsx(
        'rounded-2xl border',
        tone === 'default' ? 'bg-surface-900 border-white/[0.07]' : 'bg-surface-850 border-white/10',
        PADDING[padding],
        className,
      )}
      {...rest}
    />
  )
}
