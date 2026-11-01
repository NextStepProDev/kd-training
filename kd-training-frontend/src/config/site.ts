/**
 * Contact details shown on the site. One place, so the footer, the home page and the privacy
 * policy cannot drift apart. Placeholder until the client confirms the real address.
 */
export const CONTACT_EMAIL = 'kontakt@kdtraining.example'

/**
 * Photos for the home page, under public/images/. Empty until the client sends them: every slot then
 * shows the designed placeholder and the browser makes no request for a file that is not there.
 * Adding a photo is dropping the file in public/images/ and naming it here.
 */
export const HOME_PHOTOS: { hero?: string; work: string[] } = {
  hero: undefined,
  work: [],
}

/** How many tiles the "Z pracy" gallery shows; placeholders fill whatever the photos do not. */
export const HOME_WORK_SLOTS = 4
