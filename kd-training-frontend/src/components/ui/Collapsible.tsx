import { useId, useState, type ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import clsx from 'clsx'

interface CollapsibleProps {
  title: ReactNode
  /** Small count/summary shown as a pill next to the title. */
  badge?: ReactNode
  defaultOpen?: boolean
  children: ReactNode
  className?: string
}

/** A self-contained expand/collapse section used on the account sub-pages. */
export function Collapsible({ title, badge, defaultOpen = false, children, className }: CollapsibleProps) {
  const [open, setOpen] = useState(defaultOpen)
  const panelId = useId()

  return (
    <div className={clsx('rounded-xl border border-surface-800 bg-surface-900 overflow-hidden', className)}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(o => !o)}
        className="flex w-full items-center justify-between gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-800/60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary-400"
      >
        <span className="flex items-center gap-2.5 text-lg font-semibold text-surface-100">
          {title}
          {badge != null && (
            <span className="inline-flex items-center justify-center rounded-full bg-primary-600/15 px-2 py-0.5 text-xs font-medium text-primary-300">
              {badge}
            </span>
          )}
        </span>
        <ChevronDown
          className={clsx('h-5 w-5 shrink-0 text-surface-400 transition-transform duration-200', open && 'rotate-180')}
        />
      </button>
      {open && (
        <div id={panelId} className="px-5 pb-5 pt-1">
          {children}
        </div>
      )}
    </div>
  )
}
