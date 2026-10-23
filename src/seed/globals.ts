import type { Payload } from 'payload'
import type { ImageKey } from './images'

type Link = (label: string, target: string, appearance?: string) => Record<string, unknown>
const ctx = { disableRevalidate: true }

export async function seedGlobals(payload: Payload, { link, images }: { link: Link; images: Record<ImageKey, number> }) {
  const item = (label: string, target: string, description?: string) => ({ link: link(label, target), description })

  await payload.updateGlobal({
    slug: 'site-settings',
    context: ctx,
    data: {
      schoolName: 'The International School @ ParkCity',
      shortName: 'ISP',
      location: 'Desa ParkCity, Kuala Lumpur, Malaysia',
      generalEmail: 'info@isp.edu.my',
      admissionsEmail: 'admissions@isp.edu.my',
      defaultDescription:
        'The International School @ ParkCity (ISP), Kuala Lumpur: an international school for ages 3 to 18 in Desa ParkCity, from Early Years to A Levels.',
      defaultShareImage: images.community,
      campuses: [
        {
          name: 'Main Campus',
          key: 'main',
          ages: 'Ages 5–18',
          address: 'No. 1 Jalan Intisari,\nDesa ParkCity, 52200\nKuala Lumpur, Malaysia',
          phones: [
            { label: 'Main line', number: '+603 6280 8880', callsOnly: false },
            { label: 'Primary mobile', number: '+6017 572 0228', callsOnly: true },
            { label: 'Secondary mobile', number: '+6012 648 9880', callsOnly: true },
          ],
          email: 'info@isp.edu.my',
          mapUrl: 'https://www.google.com/maps/search/?api=1&query=The+International+School+%40+ParkCity+No.+1+Jalan+Intisari+Desa+ParkCity+Kuala+Lumpur',
          mapEmbedQuery: 'The International School @ ParkCity, No. 1 Jalan Intisari, Desa ParkCity, 52200 Kuala Lumpur',
          directions: 'Visits are by appointment. Please report to reception on arrival.',
          image: images.walkway,
          lastVerified: '2026-10-22',
        },
        {
          name: 'Early Years Centre',
          key: 'eyc',
          ages: 'Ages 3–5',
          address: 'No. 7 Persiaran Residen,\nDesa ParkCity, 52200\nKuala Lumpur, Malaysia',
          phones: [
            { label: 'EYC line', number: '+603 6263 8899', callsOnly: false },
            { label: 'EYC mobile', number: '+60112 877 8086', callsOnly: true },
          ],
          email: 'info@isp.edu.my',
          mapUrl: 'https://www.google.com/maps/search/?api=1&query=No.+7+Persiaran+Residen+Desa+ParkCity+52200+Kuala+Lumpur',
          mapEmbedQuery: 'No. 7 Persiaran Residen, Desa ParkCity, 52200 Kuala Lumpur',
          directions: 'Located next to The ParkCity Club. Visits are by appointment.',
          image: images.eyPlay,
          lastVerified: '2026-10-22',
        },
      ],
      social: [
        { platform: 'facebook', url: 'https://www.facebook.com/TheInternationalSchoolatParkCity/' },
        { platform: 'instagram', url: 'https://www.instagram.com/isp.kl/' },
        { platform: 'linkedin', url: 'https://my.linkedin.com/company/the-international-school-parkcity' },
      ],
      sisterSchool: { label: 'Visit our Hanoi sister school', url: 'https://isph.edu.vn/' },
      announcement: {
        enabled: true,
        text: 'EYC Open Morning · Saturday 14 November 2026, 9:00–11:00am',
        link: link('Find out more', '/admissions/visit'),
        expiresAt: '2026-11-14T03:00:00.000Z',
      },
    },
  })

  await payload.updateGlobal({
    slug: 'header',
    context: ctx,
    data: {
      navGroups: [
        {
          label: 'Discover ISP',
          summary: 'Our story, values and people, and the campuses where students learn.',
          overview: link('About ISP', '/discover'),
          items: [
            item('Vision, mission and values', '/discover/vision-values', 'What we believe and how we learn'),
            item('Leadership', '/discover/leadership', 'Welcome from the Principal and Group Director'),
            item('Campus and facilities', '/discover/campus', 'Main Campus and Early Years Centre'),
            item('Results and accreditation', '/discover/results-accreditation', '2025–2026 results and ISQM'),
          ],
        },
        {
          label: 'Learning',
          summary: 'One connected journey from age 3 to 18, rooted in the English National Curriculum.',
          overview: link('Curriculum overview', '/learning'),
          items: [
            item('Early Years', '/learning/early-years', 'Ages 3–5 · Nursery and Reception'),
            item('Primary', '/learning/primary', 'Ages 5–11 · Years 1–6'),
            item('Secondary', '/learning/secondary', 'Ages 11–16 · Years 7–11'),
            item('Sixth Form', '/learning/sixth-form', 'Ages 16–18 · Years 12–13'),
            item('Support and wellbeing', '/learning/support', 'Pastoral care, EAL and learning support'),
          ],
        },
        {
          label: 'Life at ISP',
          summary: 'Sport, the arts, over 100 activities and many ways to lead and serve.',
          overview: link('Life at ISP', '/life'),
          items: [
            item('PE and sport', '/life/sport', 'Facilities, teams and leagues'),
            item('Creative and performing arts', '/life/arts', 'Music, drama, dance and art'),
            item('Co-curricular activities', '/life/co-curricular', 'Clubs before and after school'),
            item('Service and leadership', '/life/service', 'Student roles and the International Award'),
          ],
        },
        {
          label: 'Admissions',
          summary: 'From first question to first day: visits, fees, applying and answers.',
          overview: link('Admissions overview', '/admissions'),
          items: [
            item('Visit & Enquire', '/admissions/visit', 'Tours and open days'),
            item('Fees and scholarships', '/admissions/fees', 'Academic year 2026/27'),
            item('How to apply', '/admissions/apply', 'Forms, assessment and offer'),
            item('Parent questions', '/admissions/faqs', 'Year groups, buses, visas and more'),
          ],
        },
        {
          label: 'Community',
          summary: 'Parents, students and staff working together.',
          overview: link('Our community', '/community'),
          items: [
            item('News and stories', '/community/stories', 'Announcements and articles'),
            item('Current families', '/families', 'Calendar, handbook and portals'),
            item('Careers', '/careers', 'Work at ISP'),
            item('Contact us', '/contact', 'Both campuses'),
          ],
        },
      ],
      cta: link('Visit & Enquire', '/admissions/visit'),
      utilityLinks: [{ link: link('Current families', '/families') }, { link: link('Careers', '/careers') }, { link: link('Contact', '/contact') }],
    },
  })

  await payload.updateGlobal({
    slug: 'footer',
    context: ctx,
    data: {
      columns: [
        { title: 'Admissions', links: [{ link: link('Visit & Enquire', '/admissions/visit') }, { link: link('Fees', '/admissions/fees') }, { link: link('How to apply', '/admissions/apply') }, { link: link('Parent questions', '/admissions/faqs') }] },
        { title: 'Learning', links: [{ link: link('Early Years', '/learning/early-years') }, { link: link('Primary', '/learning/primary') }, { link: link('Secondary', '/learning/secondary') }, { link: link('Sixth Form', '/learning/sixth-form') }] },
        { title: 'School', links: [{ link: link('Support and wellbeing', '/learning/support') }, { link: link('Safeguarding', '/safeguarding') }, { link: link('School calendar', 'https://isp.edu.my/school-calendar/') }, { link: link('Careers', '/careers') }] },
      ],
      legalLinks: [{ link: link('Privacy', '/privacy') }, { link: link('Cookies', '/cookies') }, { link: link('Accessibility', '/accessibility') }, { link: link('Staff sign-in', '/admin') }],
      memberships: [
        { link: link('Cambridge International', 'https://www.cambridgeinternational.org/') },
        { link: link('AQA', 'https://www.aqa.org.uk/') },
        { link: link('FOBISIA', 'https://www.fobisia.org/') },
        { link: link('The Duke of Edinburgh’s International Award', 'https://intaward.org/') },
      ],
    },
  })

  await payload.updateGlobal({
    slug: 'admissions',
    context: ctx,
    data: {
      academicYear: '2026/2027',
      feeScheduleUrl: 'https://drive.google.com/file/d/1zA5wuacCu5o0be2_2cM9Gzyfoozx4fIR/view',
      waiver:
        'ISP currently offers a 25% waiver on the Registration Fee for residents and property owners within Desa ParkCity. A copy of the tenancy agreement or proof of ownership is required at the time of application.\n\nThe waiver also applies to families moving to Desa ParkCity within the first year of their child’s enrolment, provided they inform Admissions and submit proof of the new address within the first academic year. In these cases the waiver is applied to an upcoming tuition fee invoice.',
      feesNote: 'Payment methods and terms are set out in the official documents linked below.',
      reviewedAt: '2026-10-22',
      owner: 'Admissions & Finance',
      applicationUrl: 'https://bit.ly/ISPStudentapplication',
      applicationNote:
        'The online application is hosted by the school’s application system. You will be asked to complete three forms; once all documentation is received, Admissions arranges an assessment.',
      requiredDocuments: [
        { item: 'Application for Admission form' },
        { item: 'Medical & Health Record' },
        { item: 'Home-School Agreement' },
        { item: 'Previous school reports (used for year-group placement)' },
        { item: 'Visa or passport copies for students without a Malaysian passport' },
      ],
      termsUrl: 'https://isp.edu.my/wp-content/uploads/2026/04/Terms-and-Conditions-Governing-Enrolment-and-Admissions.pdf',
      _status: 'published',
    },
  })
}
