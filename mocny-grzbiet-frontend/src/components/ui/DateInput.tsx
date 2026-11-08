import { useRef, type InputHTMLAttributes } from 'react'

/**
 * The one date field in the app — a native `<input type="date">` that actually closes its picker.
 *
 * Safari on macOS leaves the calendar popover open after a day is clicked. The value IS committed
 * at that moment (the input event fires straight away — verified by hand in Safari, which is the
 * only way to check: a native popover is browser chrome, so no automation can click it), but the
 * calendar keeps hanging over the form, so the pick reads as "nothing happened" and you learn to
 * click somewhere else to make the date appear.
 *
 * Blurring the field on commit dismisses it, and focus goes straight back so nothing changes for
 * Chrome and Firefox, which close the popover themselves and would otherwise pay for this with
 * focus dropped on <body>: Enter would stop submitting and Tab would restart from the top of the
 * page. Restoring focus does not reopen the popover — a native picker opens on a click, not on
 * focus.
 *
 * This is a component rather than a remembered habit because a date field looks completely fine at
 * the call site, so nothing points at the missing two lines when a form is copied or extracted. A
 * gate (`__architecture__/dateInput.test.ts`) keeps raw date inputs out of the codebase.
 *
 * Two interactions are exempt, because for them dismissing early is itself the bug:
 *
 * - **Typing.** Editing a complete date by keyboard fires input on every keystroke, so blurring
 *   would rip focus away after the day segment and before the month.
 * - **Touch.** The iOS wheel fires input on every spin and is dismissed by its own Done button;
 *   blurring would snap it shut on the first nudge.
 */
interface DateInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> {
  /** `yyyy-MM-dd`, or '' for an empty field. */
  value: string
  /** Receives the new `yyyy-MM-dd`, or '' when the field is cleared. */
  onChange: (value: string) => void
}

export function DateInput({ value, onChange, onPointerDown, onKeyDown, ...rest }: DateInputProps) {
  // Defaults to true: should a browser ever commit a day without a pointerdown reaching the input,
  // the failure to fall back into is the stolen focus, not the popover that never closes.
  const dismissOnCommit = useRef(true)

  return (
    <input
      {...rest}
      type="date"
      value={value}
      onPointerDown={event => {
        dismissOnCommit.current = event.pointerType !== 'touch'
        onPointerDown?.(event)
      }}
      onKeyDown={event => {
        dismissOnCommit.current = false
        onKeyDown?.(event)
      }}
      onChange={event => {
        onChange(event.target.value)
        if (!dismissOnCommit.current) return
        event.target.blur()
        event.target.focus({ preventScroll: true })
      }}
    />
  )
}
