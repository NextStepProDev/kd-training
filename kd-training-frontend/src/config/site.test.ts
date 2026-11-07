import { describe, it, expect } from 'vitest'
import { HOME_PHOTOS } from './site'
import common from '../locales/pl/common.json'

/**
 * HOME_PHOTOS names files under public/, and the alt text of each "work" photo sits at the same index
 * in common.json. Neither link fails loudly: a missing file quietly falls back to the placeholder and a
 * missing alt renders an unlabelled photo, so both are checked here.
 */
// Keys only: the loaders are never called, so no image is pulled into the test run.
const publicImages = new Set(Object.keys(import.meta.glob('/public/images/*')).map((p) => p.replace('/public', '')))

describe('HOME_PHOTOS', () => {
  it('shouldSeeThePublicImagesFolder', () => {
    // An empty glob would make the next test pass for any path.
    expect(publicImages.size).toBeGreaterThan(0)
  })

  it('shouldPointOnlyAtFilesThatExist', () => {
    const paths = [HOME_PHOTOS.hero, ...HOME_PHOTOS.work].filter((p): p is string => Boolean(p))
    for (const path of paths) {
      expect(publicImages, path).toContain(path)
    }
  })

  it('shouldGiveEveryWorkPhotoItsOwnAltText', () => {
    const alts = common.home.work.photos
    HOME_PHOTOS.work.forEach((path, i) => {
      expect(alts[i]?.trim(), path).toBeTruthy()
    })
    expect(new Set(alts).size, 'two photos share one description').toBe(alts.length)
  })
})
