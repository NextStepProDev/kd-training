import { describe, it, expect } from 'vitest'
import { CONTACT_EMAIL, CONTACT_PHONE, HOME_PHOTOS } from './site'
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

describe('CONTACT_PHONE', () => {
  it('shouldDialTheSameNumberItShows', () => {
    // The two strings are typed separately; a fix to one alone would show one number and dial another.
    const shown = CONTACT_PHONE.display.replace(/\D/g, '')
    expect(CONTACT_PHONE.href).toBe(`tel:+48${shown}`)
  })
})

describe('CONTACT_EMAIL', () => {
  // Translations get the address through {{email}}. The guard looks for ANY address, not for the
  // current one: after a change of mailbox a forgotten copy is the OLD address, which no longer
  // equals CONTACT_EMAIL. The only address allowed is the example in the e-mail inputs.
  const locales = import.meta.glob('/src/locales/pl/*.json', { eager: true, import: 'default' })
  const EMAIL = /[\w.%+-]+@[\w-]+\.[\w.]+/g
  const INPUT_PLACEHOLDER = 'twoj@email.com'

  it('shouldSeeTheLocaleFiles', () => {
    expect(Object.keys(locales).length).toBeGreaterThan(0)
  })

  it('shouldRecognizeARealAddress', () => {
    // A pattern that matches nothing would let every translation through.
    expect(CONTACT_EMAIL.match(EMAIL)).toEqual([CONTACT_EMAIL])
  })

  it('shouldNotHaveAnAddressTypedIntoTranslations', () => {
    for (const [path, json] of Object.entries(locales)) {
      const found = (JSON.stringify(json).match(EMAIL) ?? []).filter((a) => a !== INPUT_PLACEHOLDER)
      expect(found, path).toEqual([])
    }
  })
})
