// Plain JavaScript so next.config.ts can import it without a TypeScript loader.
/**
 * Permanent redirects from the current isp.edu.my WordPress URLs to the new structure.
 * Source: route inventory in the research brief plus pages verified on isp.edu.my (Oct 2026).
 * Review with the digital owner before launch; unmapped URLs fall through to the 404 page
 * instead of being sent to the homepage.
 */
/** @type {Record<string, string>} */
const map = {
  '/about-us': '/discover',
  '/about-us/welcome': '/discover/leadership',
  '/about-us/vision-and-mission': '/discover/vision-values',
  '/about-us/core-values-and-learning-habits': '/discover/vision-values',
  '/about-us/our-staff': '/discover/leadership',
  '/about-us/board-of-governors': '/discover/leadership',
  '/about-us/facilities-and-services': '/discover/campus',
  '/about-us/isqm': '/discover/results-accreditation',
  '/about-us/results': '/discover/results-accreditation',
  '/about-us/contact-us': '/contact',
  '/about-us/careers': '/careers',
  '/about-us/the-international-school-parkcity-privacy-policy': '/privacy',
  '/learning': '/learning',
  '/learning/curriculum': '/learning',
  '/learning/early-years-centre': '/learning/early-years',
  '/learning/primary-school': '/learning/primary',
  '/learning/secondary-school': '/learning/secondary',
  '/learning/sixth-form': '/learning/sixth-form',
  '/learning/cca': '/life/co-curricular',
  '/learning/creative-arts': '/life/arts',
  '/learning/performing-arts': '/life/arts',
  '/learning/pe-and-sports': '/life/sport',
  '/learning/digital-learning': '/learning',
  '/admissions/admissions-guidelines': '/admissions/apply',
  '/admissions/fees': '/admissions/fees',
  '/admissions/faq': '/admissions/faqs',
  '/admissions/visit-us-open-days': '/admissions/visit',
  '/admissions/scholarships': '/admissions/fees',
  '/enquiry': '/admissions/visit',
  '/school-life': '/life',
  '/school-life/wellbeing': '/learning/support',
  '/school-life/international-award': '/life/service',
  '/school-life/news-and-updates': '/community/stories',
  '/school-calendar': '/families',
  '/community/parents': '/families',
  '/community/students': '/community',
  '/community/pta': '/community',
  '/community/alumni-2': '/community',
}

export const legacyRedirects = Object.entries(map)
  .filter(([source, destination]) => source !== destination)
  // Next.js strips trailing slashes before matching, so "/about-us/welcome/" is covered too.
  .map(([source, destination]) => ({ source, destination, permanent: true }))
