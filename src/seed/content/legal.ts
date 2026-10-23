import { h3, rt, ul } from '../lexical'
import type { PageDef } from './context'

const reviewNotice = (owner: string) => ({
  blockType: 'notice',
  tone: 'pending',
  title: 'Draft for school review',
  body: `This page was prepared with the new website and must be reviewed and approved by the school’s ${owner} before launch.`,
})

export const legalPages: PageDef[] = [
  {
    path: 'privacy',
    title: 'Website privacy notice',
    navLabel: 'Privacy',
    owner: 'Privacy owner',
    description: 'How this website handles the personal information you send through its forms, and how to contact the school about your data.',
    layout: () => [
      { blockType: 'hero', variant: 'compact', eyebrow: 'Legal', heading: 'Website privacy notice.', intro: 'How this website handles information you send us. The school’s full privacy policy is linked below.' },
      reviewNotice('privacy owner'),
      {
        blockType: 'richText',
        layout: 'narrow',
        content: rt(
          h3('Who is responsible'),
          'This website is operated by The International School @ ParkCity, No. 1 Jalan Intisari, Desa ParkCity, 52200 Kuala Lumpur, Malaysia. The school’s [full privacy policy](https://isp.edu.my/about-us/the-international-school-parkcity-privacy-policy/) explains how it handles personal data more generally.',
          h3('What we collect through this website'),
          ul([
            '**Enquiry and contact forms:** your name, email address, optional telephone number, the stage and start date you are interested in, your message, the page you sent it from, and whether you asked to receive updates.',
            '**Cookies:** only what is described in our [cookie policy](/cookies). Optional analytics and embedded maps are off unless you choose to allow them.',
          ]),
          'The forms do not ask for, and you should not send, medical information, school reports, passport details or a child’s full date of birth. These are collected later through the school’s secure application system.',
          h3('How we use it'),
          'Enquiry details are used only to reply to you and to help with your admissions questions. They are stored in the school’s website management system, which only authorised admissions staff can access, and may be forwarded to the school’s admissions email or admissions system. We will only send you updates about events if you tick the separate optional box.',
          h3('Your rights'),
          'You may ask to access or correct the personal data the school holds about you, or ask questions about how it is used, by emailing [info@isp.edu.my](mailto:info@isp.edu.my).',
        ),
      },
      {
        blockType: 'resources',
        heading: 'Related policies',
        items: [
          { title: 'ISP privacy policy', description: 'Full school policy', kind: 'website', url: 'https://isp.edu.my/about-us/the-international-school-parkcity-privacy-policy/' },
          { title: 'Cookie policy', kind: 'website', url: '/cookies' },
        ],
      },
    ],
  },
  {
    path: 'cookies',
    title: 'Cookie policy',
    owner: 'Privacy owner',
    description: 'The cookies this website uses, why, and how to change your choices.',
    layout: () => [
      { blockType: 'hero', variant: 'compact', eyebrow: 'Legal', heading: 'Cookie policy.', intro: 'Essential cookies keep the site working. Everything else is off until you allow it, and you can change your mind at any time using “Cookie settings” in the footer.' },
      {
        blockType: 'dataTable',
        heading: 'Cookies we use',
        caption: 'Cookies used by this website',
        columns: [{ label: 'Cookie' }, { label: 'Category' }, { label: 'Purpose' }, { label: 'Duration' }],
        rows: [
          { cells: [{ value: 'isp_consent' }, { value: 'Strictly necessary' }, { value: 'Remembers your cookie choices' }, { value: '180 days' }] },
          { cells: [{ value: 'payload-token' }, { value: 'Strictly necessary' }, { value: 'Keeps school staff signed in to the website editor (staff only)' }, { value: 'Up to 8 hours' }] },
          { cells: [{ value: '_ga, _ga_*' }, { value: 'Analytics (optional)' }, { value: 'Google Analytics: anonymous statistics on how pages are used. Only set if you allow analytics and the school has enabled it.' }, { value: 'Up to 2 years' }] },
          { cells: [{ value: 'Google Maps cookies' }, { value: 'Maps and media (optional)' }, { value: 'Set by Google when you choose to load an embedded map.' }, { value: 'Set by Google' }] },
        ],
        note: 'If you withdraw analytics consent, the site removes its analytics cookies and reloads.',
      },
    ],
  },
  {
    path: 'accessibility',
    title: 'Accessibility statement',
    navLabel: 'Accessibility',
    owner: 'Digital owner',
    description: 'Our commitment to an accessible website, what we have done, known limitations and how to report a problem.',
    layout: () => [
      { blockType: 'hero', variant: 'compact', eyebrow: 'Legal', heading: 'Accessibility statement.', intro: 'We want everyone to be able to use this website, including people who use a keyboard, a screen reader, magnification or reduced motion.' },
      reviewNotice('digital owner'),
      {
        blockType: 'richText',
        layout: 'narrow',
        content: rt(
          h3('Our aim'),
          'The website aims to meet the Web Content Accessibility Guidelines (WCAG) 2.2 at level AA.',
          h3('What we have done'),
          ul([
            'Every page can be used with a keyboard, with a visible focus indicator and a “skip to main content” link.',
            'Menus, dialogs, image galleries and questions open and close with the keyboard, and close with Escape.',
            'If your device asks for reduced motion, scroll animations, moving bands and pinned sections are replaced with ordinary, still layouts.',
            'Images have text alternatives, and results graphics are also provided as a table.',
            'Forms have visible labels, clear error messages and an error summary.',
          ]),
          h3('Known limitations'),
          ul([
            'Some linked documents (for example curriculum guides and the fee schedule) are hosted on Google Drive and may not be fully accessible.',
            'This statement has not yet been confirmed by an independent accessibility audit.',
          ]),
          h3('Report a problem'),
          'If you find something difficult to use, please email [info@isp.edu.my](mailto:info@isp.edu.my) with the page address and a description of the problem.',
        ),
      },
    ],
  },
  {
    path: 'safeguarding',
    title: 'Safeguarding',
    owner: 'Designated Safeguarding Lead',
    description: 'Safeguarding and child protection at The International School @ ParkCity: our whole-school approach, policy and how to raise a concern.',
    layout: () => [
      { blockType: 'hero', variant: 'compact', eyebrow: 'Safeguarding', heading: 'Keeping every\nchild safe.', intro: 'Safeguarding and child protection are the responsibility of all members of the school community. We take a whole-school approach.' },
      {
        blockType: 'notice',
        tone: 'important',
        title: 'If a child is in immediate danger',
        body: 'Call the police in Malaysia on 999. For any other safeguarding concern about an ISP student, contact the school straight away on +603 6280 8880 or info@isp.edu.my and ask for the Safeguarding team.',
      },
      {
        blockType: 'richText',
        layout: 'aside',
        heading: 'Our approach',
        content: rt(
          'ISP has a dedicated Safeguarding team responsible for leading safeguarding and child protection across the school, all trained at an advanced level. All other staff undergo annual safeguarding training to support our practices and procedures.',
          'Safer recruitment practices apply to everyone who works or volunteers with our students, including appropriate background checks.',
        ),
      },
      {
        blockType: 'resources',
        heading: 'Policies',
        items: [
          { title: 'Safeguarding & Child Protection Policy', kind: 'document', url: 'https://docs.google.com/document/d/1KE1zInPLAlSeNEldZ3eYSUcovKeTZefbPGSvms6zqr4/edit' },
          { title: 'Safer Recruitment Policy', kind: 'document', url: 'https://docs.google.com/document/d/1q_y8VQCj2ZfULuw-_rsEjANFoKs3ddGCCTCimb0Nt9M/edit' },
          { title: 'Safeguarding & Wellbeing (current website)', kind: 'website', url: 'https://isp.edu.my/school-life/wellbeing/' },
        ],
      },
      reviewNotice('Designated Safeguarding Lead (named contacts should be added here)'),
    ],
  },
]
