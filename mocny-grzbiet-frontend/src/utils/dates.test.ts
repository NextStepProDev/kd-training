import { describe, it, expect } from 'vitest'
import { formatDate, formatDateRange } from './dates'

describe('dates', () => {
  describe('formatDate', () => {
    it('should format ISO date to Polish format', () => {
      const result = formatDate('2026-05-30')
      expect(result).toBe('30 maj 2026')
    })

    it('should format another date', () => {
      const result = formatDate('2026-01-15')
      expect(result).toBe('15 sty 2026')
    })
  })

  describe('formatDateRange', () => {
    it('should format single date when no end date', () => {
      const result = formatDateRange('2026-07-15', null)
      expect(result).toBe('15 lip 2026')
    })

    it('should format same month range', () => {
      const result = formatDateRange('2026-07-10', '2026-07-15')
      expect(result).toBe('10 – 15 lip 2026')
    })

    it('should format cross-month range', () => {
      const result = formatDateRange('2026-06-28', '2026-07-05')
      expect(result).toBe('28 cze – 5 lip 2026')
    })
  })
})
