import type { ReactNode } from 'react'
import clsx from 'clsx'

interface PageHeaderProps {
  title: ReactNode
  /** Small spaced-out label above the title. */
  kicker?: ReactNode
  description?: ReactNode
  /** Buttons or links on the right (below the title on a phone). */
  actions?: ReactNode
  className?: string
}

/** Top of every inner page: one h1 in the display face, so each screen opens the same way. */
export function PageHeader({ title, kicker, description, actions, className }: PageHeaderProps) {
  return (
    <header className={clsx('flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div className="min-w-0 animate-rise">
        {kicker && <p className="kicker mb-3">{kicker}</p>}
        <h1 className="text-display text-4xl sm:text-5xl text-white break-words">{title}</h1>
        {description && <p className="mt-4 max-w-2xl text-surface-400 leading-relaxed">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2 shrink-0">{actions}</div>}
    </header>
  )
}
