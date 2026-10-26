/**
 * Three levels, not a five-step ramp.
 *
 * People almost never log more than one training a day, so a five-shade scale spends four of its
 * shades on cases that hardly occur — the result reads as random noise rather than as information.
 * The reference implementation shipped the five-step version and had to walk it back.
 */
export type HeatLevel = 0 | 1 | 2

export function heatLevel(count: number): HeatLevel {
  if (count <= 0) return 0
  return count === 1 ? 1 : 2
}

export const HEAT_CLASSES: Record<HeatLevel, string> = {
  0: 'bg-surface-800',
  1: 'bg-primary-900',
  2: 'bg-primary-500',
}

/** Labelled swatches beat "less → more", which says nothing about what a shade means. */
export const HEAT_LEGEND: { level: HeatLevel; label: string }[] = [
  { level: 0, label: '0' },
  { level: 1, label: '1' },
  { level: 2, label: '2+' },
]
