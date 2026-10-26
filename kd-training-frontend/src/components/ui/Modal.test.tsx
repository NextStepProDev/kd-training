import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { Modal } from './Modal'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

/**
 * Nesting is the normal case here — a delete confirmation over a training's details, the video
 * picker over the training form — and both of these went wrong before the modals shared a stack.
 */
describe('Modal nesting', () => {
  it('shouldKeepPageLockedWhenInnerModalCloses', () => {
    const { rerender } = render(
      <>
        <Modal isOpen onClose={() => {}} title="Szczegóły">outer</Modal>
        <Modal isOpen onClose={() => {}} title="Na pewno?">inner</Modal>
      </>,
    )
    expect(document.body.style.overflow).toBe('hidden')

    // The confirmation goes away; the details behind it are still covering the page.
    rerender(
      <>
        <Modal isOpen onClose={() => {}} title="Szczegóły">outer</Modal>
        <Modal isOpen={false} onClose={() => {}} title="Na pewno?">inner</Modal>
      </>,
    )
    expect(document.body.style.overflow).toBe('hidden')

    rerender(
      <>
        <Modal isOpen={false} onClose={() => {}} title="Szczegóły">outer</Modal>
        <Modal isOpen={false} onClose={() => {}} title="Na pewno?">inner</Modal>
      </>,
    )
    expect(document.body.style.overflow).toBe('')
  })

  it('shouldCloseOnlyTheTopmostModalOnEscape', () => {
    const closeOuter = vi.fn()
    const closeInner = vi.fn()
    render(
      <>
        <Modal isOpen onClose={closeOuter} title="Szczegóły">outer</Modal>
        <Modal isOpen onClose={closeInner} title="Na pewno?">inner</Modal>
      </>,
    )

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(closeInner).toHaveBeenCalledTimes(1)
    expect(closeOuter).not.toHaveBeenCalled()
  })

  it('shouldCloseASingleModalOnEscape', () => {
    const onClose = vi.fn()
    render(<Modal isOpen onClose={onClose} title="Szczegóły">outer</Modal>)

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})
