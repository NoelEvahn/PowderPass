import { rt, ul } from '../lexical'
import { cta, type PageDef } from './context'

export const communityPages: PageDef[] = [
  {
    path: 'community',
    title: 'Community',
    owner: 'Communications',
    description: 'The ISP community: parents as partners, the Parent-Teacher Association, student voice, alumni, news and stories.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Community',
        heading: 'A community that\npartners in learning.',
        intro: 'Teachers, students and families work together. Parents are welcomed as partners in their children’s education and in the life of the school.',
        media: ctx.img('pta'),
      },
      {
        blockType: 'splitFeature',
        eyebrow: 'Parent-Teacher Association',
        heading: 'Everyone is part of the PTA',
        media: ctx.img('parentWorkshop'),
        mediaPosition: 'right',
        background: 'plain',
        body: rt(
          'All parents, guardians and teachers are automatically members of the PTA, which fosters contact and cooperation between parents and staff and supports fundraising and co-curricular activities.',
          'Fundraising and social events bring families together — from the Family Picnic and Car Boot Sale to the annual end-of-year celebration at the Desa ParkCity Club. Funds have supported charities as well as extras for students, such as a new adventure playground designed by the Student Council.',
          'Parents can also volunteer as Parent Class Representatives.',
        ),
      },
      {
        blockType: 'linkCards',
        heading: 'Around the community',
        style: 'image',
        cards: [
          { title: 'News and stories', text: 'Announcements, achievements and articles from across the school.', media: ctx.img('primaryProduction'), accent: 'purple', link: ctx.link('Read the latest', '/community/stories') },
          { title: 'Current families', text: 'Calendar, handbook, parent portal and everyday information.', media: ctx.img('canteen'), accent: 'lime', link: ctx.link('Family information', '/families') },
          { title: 'Alumni', text: 'Stay connected with ISP after graduation.', media: ctx.img('sixthOutdoors'), accent: 'gold', link: ctx.link('Alumni (current site)', 'https://isp.edu.my/community/alumni-2/') },
        ],
      },
      {
        blockType: 'richText',
        eyebrow: 'Communication',
        heading: 'How school and home stay in touch',
        layout: 'aside',
        content: rt(
          'New parents receive an ISP parent email account just before the start of term; this is the account the school uses to communicate once a student has joined. The school also uses the SchoolsBuddy app to share day-to-day information.',
          ul(['Termly reports and attendance are available in the Parent Portal.', 'Calendars can be subscribed to on your own devices.', 'The Family Handbook covers every aspect of school life and should be reviewed each year.']),
        ),
      },
      cta(ctx),
    ],
  },
  {
    path: 'community/stories',
    title: 'News and stories',
    owner: 'Communications',
    description: 'News, announcements and stories from The International School @ ParkCity.',
    layout: () => [
      { blockType: 'hero', variant: 'compact', eyebrow: 'Community', heading: 'News and stories.', intro: 'Announcements, achievements and articles from across the school.' },
      { blockType: 'latestStories', heading: 'Latest from ISP', category: 'all', limit: 12, showAllLink: false },
    ],
  },
  {
    path: 'families',
    title: 'Current families',
    owner: 'Communications',
    description: 'Quick links for current ISP families: school calendar, Family Handbook, Parent Portal, SchoolsBuddy, canteen menu and attendance.',
    layout: () => [
      {
        blockType: 'hero',
        variant: 'compact',
        eyebrow: 'Current families',
        heading: 'Everything you need,\nin one place.',
        intro: 'Links to the school’s official systems and documents. Student records stay in the school’s secure systems — this website never asks you to sign in.',
      },
      {
        blockType: 'resources',
        heading: 'Calendars and handbooks',
        items: [
          { title: 'School calendar', description: 'Term dates and events', kind: 'calendar', url: 'https://isp.edu.my/school-calendar/' },
          { title: 'Subscribing to the ISP calendar', description: 'Add calendars to your devices', kind: 'document', url: 'https://docs.google.com/a/isp.edu.my/document/d/1Nd5nXhjnxLLSXfvZCNzsH3nJX3FFsTUC8BF3Df_duK0/edit' },
          { title: 'Family Handbook', description: 'Comprehensive parent information', kind: 'document', url: 'https://drive.google.com/file/d/1YJ4a4MmVorgFhUhPabno5VIZ09q-Qbep/view' },
          { title: 'Canteen menu', description: 'Updated monthly by Petite Café', kind: 'document', url: 'https://docs.google.com/document/d/1tDCwRaDhljzkv71WXWkynu1juA6aptYxx-dQt1mSPJ4/edit' },
        ],
      },
      {
        blockType: 'resources',
        heading: 'Systems and guides',
        items: [
          { title: 'Using the Parent Portal', description: 'Termly reports and attendance', kind: 'portal', url: 'https://docs.google.com/document/d/13295T4vd85FcYkNAlbosb3C8BLKa7O9vpwQHkwJS6QU/edit' },
          { title: 'Parent’s guide to SchoolsBuddy', description: 'Day-to-day communication app', kind: 'portal', url: 'https://isp.edu.my/wp-content/uploads/2023/09/SchoolBuddy-signup-guide.pdf' },
          { title: 'ISP parent email account', kind: 'document', url: 'https://docs.google.com/document/d/1So9E2wohUgKdSo8yBtHgMOL27s6bCj3TgLyaGOgMHM4/edit' },
          { title: 'Technology for Learning — a parent’s guide', kind: 'document', url: 'https://docs.google.com/document/d/1hGllTWZ_Qr_ZcMKx16dfQ-16-OCUNvkloREUPKQDr5s/edit' },
          { title: 'Technology support', description: 'techsupport@isp.edu.my', kind: 'email', url: 'mailto:techsupport@isp.edu.my' },
          { title: 'Parents page (current website)', kind: 'website', url: 'https://isp.edu.my/community/parents/' },
        ],
      },
      {
        blockType: 'richText',
        eyebrow: 'Attendance',
        heading: 'If your child is unwell',
        layout: 'aside',
        content: rt(
          'If your child is sick and unable to attend school, please inform their Class Teacher or Tutor directly as soon as possible, by email or through the absentee form in the SchoolsBuddy app. If you know of an absence in advance, please also let the teacher know.',
          'If a child becomes unwell during the school day, the School Medical Centre will contact parents or guardians when needed.',
        ),
      },
    ],
  },
  {
    path: 'careers',
    title: 'Careers at ISP',
    navLabel: 'Careers',
    owner: 'Human Resources',
    description: 'Work at The International School @ ParkCity, Kuala Lumpur: current vacancies, how to apply, and our commitment to safer recruitment.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Careers',
        heading: 'Do you want to be part\nof something special?',
        intro: 'The calibre of the staff we appoint is paramount to the learning environment at ISP. We look for people who will complement our existing team so that we continue to learn from each other.',
        media: ctx.img('secondaryClass'),
        links: ctx.links(['Current vacancies', 'https://isp.edu.my/about-us/careers/', 'primary']),
      },
      {
        blockType: 'definitionGrid',
        heading: 'What we look for',
        intro: 'A blend of youth and experience — and, above all, a genuine love of working with children.',
        columns: '4',
        accent: 'purple',
        background: 'tint',
        items: ['Creative', 'Committed', 'Flexible', 'Friendly', 'Passionate', 'Respectful', 'Tolerant', 'Enthusiastic', 'Proactive', 'Sense of humour'].map((term) => ({ term })),
      },
      {
        blockType: 'richText',
        eyebrow: 'Applying',
        heading: 'How to apply',
        layout: 'aside',
        content: rt(
          ul([
            'Clearly state the position you are applying for.',
            'Include a letter of application (maximum two sides of A4).',
            'Include your CV with a recent photograph.',
            'Include email contact details for three referees, one of whom must be from your current school.',
          ]),
          'Email your application to [careers@isp.edu.my](mailto:careers@isp.edu.my). Applications are considered until the post is filled.',
        ),
      },
      {
        blockType: 'richText',
        eyebrow: 'Safer recruitment',
        heading: 'Child protection and safeguarding',
        layout: 'aside',
        content: rt(
          'ISP is committed to ensuring that all students are kept safe and free from harm. Child protection is the responsibility of every member of the school community, and we expect all employees and volunteers to share this commitment.',
          'By applying, you declare that you are able to work with students in a way that promotes and safeguards their safety and welfare, that there is no reason that would prevent you from working with children, and that you support appropriate background checks.',
        ),
      },
      {
        blockType: 'resources',
        heading: 'Policies',
        items: [
          { title: 'Safeguarding & Child Protection Policy', kind: 'document', url: 'https://docs.google.com/document/d/1KE1zInPLAlSeNEldZ3eYSUcovKeTZefbPGSvms6zqr4/edit' },
          { title: 'Safer Recruitment Policy', kind: 'document', url: 'https://docs.google.com/document/d/1q_y8VQCj2ZfULuw-_rsEjANFoKs3ddGCCTCimb0Nt9M/edit' },
        ],
      },
    ],
  },
  {
    path: 'contact',
    title: 'Contact us',
    navLabel: 'Contact',
    owner: 'Admissions',
    description: 'Contact The International School @ ParkCity: Main Campus and Early Years Centre addresses, telephone numbers, email and directions in Desa ParkCity, Kuala Lumpur.',
    layout: () => [
      {
        blockType: 'hero',
        variant: 'compact',
        eyebrow: 'Contact',
        heading: 'Get in touch.',
        intro: 'Call or email either campus, or send us a message below. For admissions questions, the Visit & Enquire form is the quickest route.',
      },
      { blockType: 'campuses', heading: 'Campuses', showMaps: true },
      {
        blockType: 'formBlock',
        eyebrow: 'Message',
        heading: 'Send us a message',
        intro: 'For general questions. We use your details only to reply.',
        formType: 'contact',
        anchorId: 'message',
      },
    ],
  },
]
