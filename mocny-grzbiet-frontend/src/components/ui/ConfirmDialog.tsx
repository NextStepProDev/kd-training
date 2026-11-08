import type { ReactNode } from 'react'
import { Modal } from './Modal'
import { Button } from './Button'

interface ConfirmDialogProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  loading?: boolean
  children?: ReactNode
}

export function ConfirmDialog({ isOpen, onClose, onConfirm, title, message, confirmLabel = 'Potwierdź', danger, loading, children }: ConfirmDialogProps) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <p className="text-surface-300 mb-4">{message}</p>
      {children && <div className="mb-6">{children}</div>}
      <div className="flex justify-end gap-3">
        <Button variant="ghost" size="sm" onClick={onClose}>Anuluj</Button>
        <Button variant={danger ? 'danger' : 'primary'} size="sm" onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
