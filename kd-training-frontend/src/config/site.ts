/**
 * Contact details shown on the site. One place, so the footer, the home page and the privacy
 * policy cannot drift apart. Placeholder until the client confirms the real address.
 */
export const CONTACT_EMAIL = 'kontakt@kdtraining.example'

/**
 * Photos for the home page, under public/images/: the client's own, resized and stripped of camera
 * metadata before they went in (the repository is public). A slot without a file shows the designed
 * placeholder and the browser makes no request for it. The alt text of each "work" photo sits at the
 * same index in home.work.photos (common.json), so the two lists change together.
 */
export const HOME_PHOTOS: { hero?: string; work: string[] } = {
  hero: '/images/hero.jpg',
  work: ['/images/work-1.jpg', '/images/work-2.jpg', '/images/work-3.jpg', '/images/work-4.jpg'],
}

/** How many tiles the "Z pracy" gallery shows; placeholders fill whatever the photos do not. */
export const HOME_WORK_SLOTS = 4
