import { describe, it, expect } from 'vitest'
import { heatLevel, HEAT_CLASSES, HEAT_LEGEND } from './heatmapScale'

describe('heatmapScale', () => {
  it('has exactly three levels, never five', () => {
    // The five-step ramp spends four shades on days that hardly ever happen and reads as noise.
    expect(Object.keys(HEAT_CLASSES)).toHaveLength(3)
    expect(HEAT_LEGEND).toHaveLength(3)
  })

  it('maps any busy day to the same top level', () => {
    expect(heatLevel(0)).toBe(0)
    expect(heatLevel(1)).toBe(1)
    expect(heatLevel(2)).toBe(2)
    // Five activities in a day is not five times as dark as one — it is the same "2+".
    expect(heatLevel(5)).toBe(2)
    expect(heatLevel(50)).toBe(2)
  })

  it('treats missing or negative counts as empty', () => {
    expect(heatLevel(-1)).toBe(0)
  })

  it('labels the legend with counts rather than "less/more"', () => {
    expect(HEAT_LEGEND.map(l => l.label)).toEqual(['0', '1', '2+'])
  })
})
