import type { ReactNode } from 'react'
import clsx from 'clsx'

interface SectionTitleProps {
  children: ReactNode
  /** Numbered or labelled kicker, e.g. "01" or "Usługi". */
  kicker?: ReactNode
  as?: 'h2' | 'h3'
  className?: string
}

/** A heading inside a page — same face as the page title, one size down. */
export function SectionTitle({ children, kicker, as: Tag = 'h2', className }: SectionTitleProps) {
  return (
    <div className={className}>
      {kicker && <p className="kicker mb-3">{kicker}</p>}
      <Tag className={clsx('text-display text-white', Tag === 'h2' ? 'text-3xl sm:text-4xl' : 'text-2xl')}>
        {children}
      </Tag>
    </div>
  )
}
