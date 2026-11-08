/**
 * Contact details shown on the site. One place, so the footer, the home page, the privacy policy and
 * the terms cannot drift apart. Translations take the address as {{email}} (site.test.ts rejects a literal);
 * the backend keeps its own copy in AdminUserDataExportService.readme().
 */
export const CONTACT_EMAIL = 'mocny.grzbiet@gmail.com'

/** As the client wrote it; `href` is the same number in international form for `tel:`. */
export const CONTACT_PHONE = { display: '730-348-440', href: 'tel:+48730348440' }

/** The client's sole proprietorship: data controller (privacy policy) and service provider (terms). */
export const BUSINESS = {
  name: 'Kacper Dąbrowski - Trener Personalny',
  nip: '7492112807',
  street: 'ul. Jana Matejki 15',
  city: '47-220 Kędzierzyn-Koźle',
}

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
