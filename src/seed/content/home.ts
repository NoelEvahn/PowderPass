import { rt } from '../lexical'
import type { PageDef } from './context'

export const homePage: PageDef = {
  path: '',
  title: 'Home',
  owner: 'Communications',
  description:
    'The International School @ ParkCity, Kuala Lumpur: a connected learning journey for ages 3 to 18, from Early Years to A Levels, in the green township of Desa ParkCity.',
  layout: (ctx) => [
    {
      blockType: 'hero',
      variant: 'home',
      eyebrow: 'The International School @ ParkCity · Kuala Lumpur',
      heading: 'A place to belong.\nA future to shape.',
      intro: 'Ages 3 to 18. A connected learning journey where curiosity, ambition and care help every child grow.',
      links: ctx.links(['Visit & Enquire', '/admissions/visit', 'primary'], ['Explore learning', '#learning-journey', 'arrow']),
      factLine: 'Early Years · Primary · Secondary · Sixth Form',
      slides: [
        { image: ctx.img('eyPlay'), caption: 'Early Years: learning through play at the Early Years Centre.' },
        { image: ctx.img('primaryHands'), caption: 'Primary: curiosity and confidence grow together.' },
        { image: ctx.img('scienceLab'), caption: 'Secondary: practical science in dedicated laboratories.' },
        { image: ctx.img('sixthOutdoors'), caption: 'Sixth Form: A Levels, enrichment and guidance for what comes next.' },
        { image: ctx.img('sprint'), caption: 'Sport: competitive fixtures and everyday physical activity.' },
        { image: ctx.img('primaryProduction'), caption: 'Performing arts: annual Primary and Secondary productions.' },
        { image: ctx.img('artTextile'), caption: 'Creative arts: making, designing and exhibiting.' },
      ],
    },
    {
      blockType: 'factStrip',
      heading: 'One school journey. Many ways to flourish.',
      facts: [
        { value: 'Ages 3–18', label: 'Nursery to Year 13' },
        { value: 'Desa ParkCity', label: 'Kuala Lumpur, Malaysia' },
        { value: 'EYFS to A Levels', label: 'Rooted in the English National Curriculum' },
        { value: 'Pastoral care throughout', label: 'Tutors, wellbeing advisors and learning support' },
      ],
    },
    {
      blockType: 'learningJourney',
      eyebrow: 'The learning journey',
      heading: 'Find their next chapter.',
      intro: 'Each stage builds on the last, from play-based Early Years to A Levels and university guidance.',
      programmes: ctx.programmes(),
      horizontal: true,
      anchorId: 'learning-journey',
    },
    {
      blockType: 'splitFeature',
      eyebrow: 'Belonging and care',
      heading: 'Known as an individual. Supported as a learner.',
      body: rt(
        'A strong school experience is built on relationships. Form tutors, Student Wellbeing Advisors, a Learning Support Department, English as an Additional Language specialists and an on-site medical team work together so that students can take their next steps with confidence.',
        'Support is shaped around each child. During admissions the team talks with families to confirm how the school can best meet their child’s needs.',
      ),
      media: ctx.img('smallGroup'),
      mediaPosition: 'left',
      background: 'plain',
      links: ctx.links(['Explore student support', '/learning/support', 'arrow']),
    },
    {
      blockType: 'featureMosaic',
      eyebrow: 'Life beyond lessons',
      heading: 'More room to discover.',
      intro: 'Over 100 teams, clubs and activities run each week across the year, alongside sport, the arts and service at every stage.',
      items: [
        { media: ctx.img('sprint'), category: 'Sport & movement', title: 'Train, compete and enjoy being active', text: 'Timetabled PE and swimming, on-campus pitches and pool, and fixtures through KLISS, ISAC and FOBISIA.', link: ctx.link('PE and sport', '/life/sport') },
        { media: ctx.img('drama'), category: 'Creative & performing arts', title: 'Find a stage, a studio or an instrument', text: 'Productions, orchestras, ensembles and an annual art exhibition of Secondary work.', link: ctx.link('The arts', '/life/arts') },
        { media: ctx.img('chess'), category: 'Co-curricular', title: 'Try something new every term', text: 'From photography and filmmaking to Taekwondo, robotics and creative cooking.', link: ctx.link('Co-curricular activities', '/life/co-curricular') },
        { media: ctx.img('trek'), category: 'Service & leadership', title: 'Lead, serve and take on a challenge', text: 'Student leadership roles, community outreach and the Duke of Edinburgh’s International Award from Year 10.', link: ctx.link('Service and leadership', '/life/service') },
      ],
    },
    {
      blockType: 'kineticStatement',
      eyebrow: 'Values we live',
      words: [{ word: 'Curiosity.' }, { word: 'Connection.' }, { word: 'Contribution.' }],
      supporting: 'Learning habits and core values shape everyday choices, relationships and opportunities at ISP.',
      link: ctx.link('Our values and learning habits', '/discover/vision-values'),
    },
    {
      blockType: 'latestStories',
      eyebrow: 'News and stories',
      heading: 'See learning in action.',
      category: 'all',
      limit: 3,
      showAllLink: true,
    },
    {
      blockType: 'campuses',
      eyebrow: 'Visit us',
      heading: 'Come and experience ISP.',
      intro: 'Students aged 5 to 18 learn at the Main Campus. Nursery and Reception children start at the purpose-built Early Years Centre beside The ParkCity Club. All tours are arranged in advance.',
      showMaps: false,
      links: ctx.links(['Plan your visit', '/admissions/visit', 'primary']),
    },
  ],
}
