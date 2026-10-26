/**
 * The dark-surface form field style, in one place.
 *
 * The same ~90-character class string was pasted into 27 fields across 16 files, in six variants that
 * differed only by padding scale and whether the field resizes. Six near-identical strings is not a
 * design decision, it is a copy that drifted: retuning the focus ring or the border meant finding every
 * one of them, and a missed paste shows up as a single field that looks subtly wrong.
 *
 * The variants below are the ones actually in use, and render identically to the strings they replaced.
 * `inputClassMuted` in particular is a real difference (the auth forms tone their placeholders down and
 * drop the border on focus), not an oversight to be unified away here.
 */
const SURFACE =
  'bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

/** Default field padding — admin panels, modals, settings. */
export const inputClass = `w-full px-3 py-2 ${SURFACE}`

/** Roomier padding, used by the standalone auth/consent forms. */
export const inputClassLg = `w-full px-4 py-2.5 ${SURFACE}`

/** Auth forms: muted placeholder, and the border gives way to the ring on focus. */
export const inputClassMuted = `${inputClass} placeholder-surface-500 focus:border-transparent`

/** Multi-line, user-resizable. */
export const textareaClass = `${inputClass} resize-y`

/** Multi-line, user-resizable, roomier padding. */
export const textareaClassLg = `${inputClassLg} resize-y`

/** Multi-line at a fixed height — used where the surrounding layout cannot reflow. */
export const textareaClassFixed = `${inputClass} resize-none`
