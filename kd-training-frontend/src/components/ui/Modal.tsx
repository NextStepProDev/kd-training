import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

type ModalSize = 'md' | 'lg' | 'xl' | '2xl'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  /** Max width on desktop; mobile always stays full-width (minus container padding). Defaults to 'md'. */
  size?: ModalSize
}

// Mobile keeps full width via `w-full`; these caps only kick in on wider viewports.
const SIZE_CLASS: Record<ModalSize, string> = {
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-3xl',
  '2xl': 'max-w-5xl',
}

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'

/**
 * Every modal currently on screen, innermost last.
 *
 * Modals nest here as a matter of course — a delete confirmation over a training's details, the
 * video picker over the training form — and each one used to keep its own document-level key
 * handler and clear `body.overflow` on the way out, with no idea the others existed. Two things
 * followed: closing the inner one handed the page back to the scroll wheel while the outer still
 * covered it, and a single Escape reached every handler at once, so dismissing a confirmation tore
 * down the dialog behind it too.
 *
 * With a shared stack only the topmost modal answers the keyboard, and scrolling returns when the
 * last one leaves. Module-level rather than context: a modal must behave the same wherever it is
 * rendered, including from a portal or a page that never thought about nesting.
 */
const modalStack: symbol[] = []

export function Modal({ isOpen, onClose, title, children, size = 'md' }: ModalProps) {
  const { t } = useTranslation('common')
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  // Identity for the stack, stable for this modal's lifetime.
  const [modalId] = useState(() => Symbol('modal'))

  // onClose in a ref so the effect doesn't restart on every new callback reference
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!isOpen) return

    modalStack.push(modalId)
    document.body.style.overflow = 'hidden'
    const previouslyFocused = document.activeElement as HTMLElement | null

    // Move focus into the modal after opening
    const panel = panelRef.current
    const first = panel?.querySelector<HTMLElement>(FOCUSABLE)
    ;(first ?? panel)?.focus()

    // Only the modal on top of the stack reacts. Escape belongs to whatever is in front of the
    // user, and so does the focus trap — a ConfirmDialog is a SIBLING of the modal that opened it,
    // so the one underneath would otherwise pull focus back out of the dialog on the next Tab.
    const isTopmost = () => modalStack[modalStack.length - 1] === modalId

    const handleKeyDown = (e: KeyboardEvent) => {
      if (!isTopmost()) return
      if (e.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (e.key !== 'Tab' || !panel) return

      const focusable = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE))
      if (focusable.length === 0) {
        e.preventDefault()
        panel.focus()
        return
      }
      const firstEl = focusable[0]
      const lastEl = focusable[focusable.length - 1]
      const active = document.activeElement

      if (e.shiftKey && (active === firstEl || active === panel)) {
        e.preventDefault()
        lastEl.focus()
      } else if (!e.shiftKey && active === lastEl) {
        e.preventDefault()
        firstEl.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      const index = modalStack.indexOf(modalId)
      if (index !== -1) modalStack.splice(index, 1)
      // Scrolling comes back only once the LAST modal is gone. An inner dialog closing must not
      // release the page while its parent is still covering it.
      if (modalStack.length === 0) {
        document.body.style.overflow = ''
      }
      // Restore focus to the triggering element
      previouslyFocused?.focus?.()
    }
  }, [isOpen, modalId])

  if (!isOpen) return null

  // A bottom sheet on a phone (within thumb reach, no floating box between 16px gutters), a centred
  // dialog from `sm` up.
  return (
    <div className="fixed inset-0 z-[100] flex items-end justify-center sm:items-center sm:p-4">
      <div className="fixed inset-0 bg-black/80 backdrop-blur-md" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`relative w-full ${SIZE_CLASS[size]} max-h-[92dvh] sm:max-h-[90vh] overflow-y-auto rounded-t-3xl sm:rounded-2xl border border-white/10 bg-surface-900 shadow-glow animate-rise focus:outline-none`}
      >
        <div className="sticky top-0 z-10 flex items-center justify-between gap-4 px-5 sm:px-6 py-4 border-b border-white/[0.07] bg-surface-900/95 backdrop-blur">
          <h2 id={titleId} className="font-display text-lg font-semibold tracking-tight text-white">{title}</h2>
          <button
            onClick={onClose}
            aria-label={t('actions.close')}
            className="-mr-2 rounded-full p-2 text-surface-400 transition-colors hover:bg-white/5 hover:text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="p-5 sm:p-6">{children}</div>
      </div>
    </div>
  )
}
