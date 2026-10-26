import { describe, it, expect } from 'vitest'
import { keepWithinEntity, sameEntity } from './queryEntity'

describe('sameEntity', () => {
  it('ignores the trailing parameters', () => {
    expect(sameEntity(['admin', 'weights', 'a1', 'QUARTER'], ['admin', 'weights', 'a1', 'YEAR'], 1)).toBe(true)
  })

  it('separates two people asked the same question', () => {
    expect(sameEntity(['admin', 'weights', 'a1', 'YEAR'], ['admin', 'weights', 'a2', 'YEAR'], 1)).toBe(false)
  })

  it('separates two roles reading the same calendar', () => {
    // The coach's and the client's views of one plan are different keys on purpose.
    expect(sameEntity(
      ['admin', 'training-calendar', 'a1', '2027-03-01', '2027-03-07'],
      ['user', 'my-training', 'calendar', '2027-03-01', '2027-03-07'],
      2,
    )).toBe(false)
  })

  it('treats keys of different lengths as different entities', () => {
    expect(sameEntity(['admin', 'weights', 'a1'], ['admin', 'weights', 'a1', 'YEAR'], 1)).toBe(false)
  })

  it('compares the whole key when there are no parameters', () => {
    expect(sameEntity(['admin', 'goals', 'a1'], ['admin', 'goals', 'a1'], 0)).toBe(true)
    expect(sameEntity(['admin', 'goals', 'a1'], ['admin', 'goals', 'a2'], 0)).toBe(false)
  })
})

describe('keepWithinEntity', () => {
  const key = ['admin', 'weights', 'a1', 'YEAR']

  it('hands back the previous page of the same entity', () => {
    expect(keepWithinEntity('quarter data', { queryKey: ['admin', 'weights', 'a1', 'QUARTER'] }, key, 1))
      .toBe('quarter data')
  })

  it('drops data belonging to someone else', () => {
    // A spinner is the right answer across the boundary: another client's readings standing under
    // this client's name is a worse failure than a moment of nothing.
    expect(keepWithinEntity('a2 data', { queryKey: ['admin', 'weights', 'a2', 'YEAR'] }, key, 1))
      .toBeUndefined()
  })

  it('has nothing to offer on a first load', () => {
    expect(keepWithinEntity(undefined, undefined, key, 1)).toBeUndefined()
  })
})
