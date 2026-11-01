import type { ReactNode } from 'react'
import clsx from 'clsx'

interface BadgeProps {
  children: ReactNode
  /** `solid` for the one state that matters on a screen; `outline` for everything else. */
  tone?: 'outline' | 'solid' | 'muted'
  className?: string
}

const TONES = {
  outline: 'border border-white/15 text-surface-200',
  solid: 'bg-white text-surface-950',
  muted: 'bg-white/[0.06] text-surface-400',
} as const

/** Small status pill. Monochrome on purpose: state reads from fill and weight, not colour. */
export function Badge({ children, tone = 'outline', className }: BadgeProps) {
  return (
    <span className={clsx('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium', TONES[tone], className)}>
      {children}
    </span>
  )
}
