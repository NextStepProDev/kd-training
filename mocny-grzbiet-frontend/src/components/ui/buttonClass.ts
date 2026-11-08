import clsx from 'clsx'

/**
 * Class strings shared with links that look like buttons (`<Link className={buttonClass(...)}>`):
 * a CTA is a link as often as it is a button, and two copies of the look drift apart.
 */
const BASE =
  'inline-flex items-center justify-center gap-2 font-semibold rounded-full transition-[background-color,color,border-color,transform] duration-200 ease-out focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-40 disabled:cursor-not-allowed'

const VARIANTS = {
  // White on black is the accent: the one thing on a screen that asks to be pressed.
  primary: 'bg-white text-surface-950 hover:bg-surface-200 focus-visible:outline-white',
  secondary: 'border border-surface-600 text-surface-100 hover:border-surface-300 hover:bg-white/5 focus-visible:outline-surface-300',
  ghost: 'bg-transparent text-surface-300 hover:bg-white/5 hover:text-surface-100 focus-visible:outline-surface-400',
  danger: 'bg-rose-500/12 text-rose-300 hover:bg-rose-500/20 border border-rose-500/25 focus-visible:outline-rose-400',
} as const

const SIZES = {
  sm: 'px-3.5 py-1.5 text-sm',
  md: 'px-5 py-2.5 text-sm',
  lg: 'px-7 py-3.5 text-base',
} as const

export type ButtonVariant = keyof typeof VARIANTS
export type ButtonSize = keyof typeof SIZES

export function buttonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md') {
  return clsx(BASE, VARIANTS[variant], SIZES[size])
}
