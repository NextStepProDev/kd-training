/**
 * The dark-surface form field style, in one place.
 *
 * The same ~90-character class string was pasted into 27 fields across 16 files, in six variants that
 * differed only by padding scale and whether the field resizes. Six near-identical strings is not a
 * design decision, it is a copy that drifted: retuning the focus ring or the border meant finding every
 * one of them, and a missed paste shows up as a single field that looks subtly wrong.
 *
 * The variants below are the ones actually in use. `inputClassMuted` is a real difference (the auth
 * forms tone their placeholders down), not an oversight to be unified away here.
 */
// Border colours are kept out of BASE so a field is either OK or INVALID, never both: two border
// colour classes on one element resolve by stylesheet order, not by the order they are written in.
const BASE = 'bg-surface-950/60 border rounded-xl text-surface-100 transition-colors focus:outline-none focus:ring-2'
const OK = 'border-white/10 hover:border-white/20 focus:border-white/50 focus:ring-white/10'
const INVALID = 'border-rose-500/70 focus:border-rose-400 focus:ring-rose-500/20'
const SURFACE = `${BASE} ${OK}`

/** Default field padding — admin panels, modals, settings. */
export const inputClass = `w-full px-3 py-2 ${SURFACE}`

/** Roomier padding, used by the standalone auth/consent forms. */
export const inputClassLg = `w-full px-4 py-2.5 ${SURFACE}`

/** Auth forms: muted placeholder. */
export const inputClassMuted = `${inputClass} placeholder-surface-500`

/** A field the form has rejected — same shape as `inputClassMuted`, red border instead of grey. */
export const inputClassInvalid = `w-full px-3 py-2 ${BASE} ${INVALID} placeholder-surface-500`

/** Multi-line, user-resizable. */
export const textareaClass = `${inputClass} resize-y`

/** Multi-line, user-resizable, roomier padding. */
export const textareaClassLg = `${inputClassLg} resize-y`

/** Multi-line at a fixed height — used where the surrounding layout cannot reflow. */
export const textareaClassFixed = `${inputClass} resize-none`
