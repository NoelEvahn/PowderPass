import { rt, ul } from '../lexical'
import { cta, type PageDef } from './context'

const CCA_EXAMPLES = 'Animation\nArt & Craft\nAthletics\nBadminton\nBasketball\nCreative Cooking\nDance\nDodgeball\nDrama\nFootball\nLanguages\nMagic\nMulti-Sports\nOrigami\nPhotography & Filmmaking\nPickleball\nPrimary Choir\nPrimary & Secondary Orchestra\nRhythmic Gymnastics\nSwimming\nT-Ball\nTaekwondo\nTechnology\nYoga'

export const lifePages: PageDef[] = [
  {
    path: 'life',
    title: 'Life at ISP',
    owner: 'Head of Co-curricular',
    description: 'Sport, creative and performing arts, over 100 co-curricular activities, service and student leadership at The International School @ ParkCity.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Life at ISP',
        heading: 'More room\nto discover.',
        intro: 'Over 100 teams, clubs and activities each week, with sport, the arts and service running through every stage of school.',
        media: ctx.img('houseFriends'),
      },
      {
        blockType: 'featureMosaic',
        heading: 'Four ways to get involved',
        intro: 'Co-curricular life builds friendships across year groups and develops teamwork, leadership, communication and a real sense of responsibility.',
        items: [
          { media: ctx.img('sprint'), category: 'Sport & movement', title: 'PE and sport', text: 'Inclusive PE for every age, competitive teams and international fixtures.', link: ctx.link('PE and sport', '/life/sport') },
          { media: ctx.img('primaryProduction'), category: 'Creative & performing arts', title: 'The arts', text: 'Music, dance, drama and visual art, from weekly lessons to annual productions.', link: ctx.link('The arts', '/life/arts') },
          { media: ctx.img('chess'), category: 'Co-curricular', title: 'Clubs and activities', text: 'Teacher-led clubs and specialist activities before school, after school and at weekends.', link: ctx.link('Co-curricular activities', '/life/co-curricular') },
          { media: ctx.img('trek'), category: 'Service & leadership', title: 'Service and leadership', text: 'Student councils, prefects, ambassadors, COALS and the International Award.', link: ctx.link('Service and leadership', '/life/service') },
        ],
      },
      {
        blockType: 'splitFeature',
        eyebrow: 'Everyday life',
        heading: 'Lunch, uniform and the school day',
        media: ctx.img('canteen'),
        mediaPosition: 'right',
        background: 'tint',
        body: rt(
          'Petite Café offers Asian, Western and vegetarian meals, freshly cooked on site every day, with menus updated monthly. It also runs the Parents’ Café.',
          'All students wear the school uniform, available from The Shop @ ISP; Sixth Form students wear business attire.',
        ),
        links: ctx.links(['Practical information for families', '/families', 'arrow']),
      },
      { blockType: 'latestStories', eyebrow: 'Stories', heading: 'Recent highlights', limit: 3, category: 'all', showAllLink: true },
    ],
  },
  {
    path: 'life/sport',
    title: 'PE and sport',
    owner: 'Head of PE',
    description: 'PE and sport at ISP: inclusive Physical Education from Early Years to A Level, on-campus facilities, and competition through KLISS, ISAC and FOBISIA.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Life at ISP · Sport',
        heading: 'Physically confident,\nfor life.',
        intro: 'Our Physical Education programme promotes health, fitness and psychological wellbeing while developing physical and social skills.',
        media: ctx.img('sprint'),
      },
      {
        blockType: 'richText',
        eyebrow: 'Physical Education',
        heading: 'Inclusive, challenging and fun',
        layout: 'aside',
        content: rt(
          'English National Curriculum programmes of study are followed from Early Years through to A Level. The PE curriculum offers a broad range of activities, from team sports such as football and basketball to gymnastics, team building and sports leadership, with a focus on inclusive participation and enjoyment.',
          'Competition builds character and embeds values such as fairness and respect. Students take part in timetabled PE lessons and can enjoy competitive and non-competitive sport through the co-curricular and team training programme. The school reports over 40 sporting teams competing in more than 150 fixtures through the year.',
        ),
      },
      {
        blockType: 'definitionGrid',
        eyebrow: 'Competition',
        heading: 'Leagues and tours',
        columns: '3',
        accent: 'lime',
        background: 'tint',
        items: [
          { term: 'KLISS', tagline: 'Kuala Lumpur International School Sports', description: 'Regular competitive fixtures across Primary and Secondary.' },
          { term: 'ISAC', tagline: 'International Schools Athletics Conference', description: 'An established international league for competitive fixtures.' },
          { term: 'FOBISIA', tagline: 'Federation of British International Schools in Asia', description: 'ISP teams tour the region to compete against international schools from other countries.' },
        ],
      },
      {
        blockType: 'listColumns',
        heading: 'Where students train and play',
        background: 'plain',
        groups: [
          { title: 'On campus', items: 'Two multi-purpose halls\nTwo astroturf pitches\nTwo rooftop games areas\nAn additional sports hall\nA six-lane swimming pool' },
          { title: 'At The ParkCity Club, within walking distance', items: 'Pickleball, tennis and badminton courts\nA 50m lap pool and a recreational pool\nA FIFA-sized football field\nAthletics facilities\nBasketball courts' },
        ],
      },
      { blockType: 'gallery', heading: 'Sport at ISP', layout: 'mosaic', images: [{ image: ctx.img('sportsDay') }, { image: ctx.img('sprint') }, { image: ctx.img('houseFriends') }] },
      cta(ctx),
    ],
  },
  {
    path: 'life/arts',
    title: 'Creative and performing arts',
    navLabel: 'The arts',
    owner: 'Heads of Performing and Creative Arts',
    description: 'Music, dance, drama, art and design at ISP: weekly lessons, orchestras and ensembles, annual productions and an annual art exhibition.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Life at ISP · The arts',
        heading: 'A place for everyone\non stage and in the studio.',
        intro: 'Whether performing, composing, designing or working behind the scenes, students are encouraged to set goals, take risks and view mistakes as a way to learn.',
        media: ctx.img('drama'),
      },
      {
        blockType: 'richText',
        eyebrow: 'Performing arts',
        heading: 'Music, dance and drama',
        layout: 'aside',
        content: rt(
          'Students of all ages develop in dance, music and drama through compulsory lessons and many extra-curricular activities. All students from Nursery to Year 10 have weekly timetabled music lessons; group instrumental lessons are offered from Year 6 to Year 9, alongside private instrumental lessons.',
          'The year includes annual Primary and Secondary productions, smaller recitals, the annual “ISP Has Talent” competitions and weekly assemblies. Primary and Secondary orchestras involve students from 5 to 18 years old, with bands and student ensembles too.',
          ul(['Dance studios and a Black Box Theatre', 'Music and instrumental classrooms', 'Eight individual practice rooms', 'A renovated Multi-Purpose Hall for productions and concerts']),
        ),
      },
      {
        blockType: 'splitFeature',
        eyebrow: 'Creative arts',
        heading: 'Art & Design and Design & Technology',
        media: ctx.img('artTextile'),
        mediaPosition: 'right',
        background: 'tint',
        body: rt(
          'Specialist Creative Arts lessons run from Key Stage 3 to 5. Students work across 2D and 3D media, from acrylics, oils, pastel and printing to watercolour, charcoal, mixed media and digital manipulation, developing the formal elements of art: line, shape, form, tone, colour and pattern.',
          'Design & Technology links closely with Science, History, Maths and Art and builds practical project-management skills. Digital content creation, including 3D design and digital art, is woven through the curriculum. An annual exhibition showcases Secondary students’ work.',
        ),
      },
      { blockType: 'gallery', heading: 'The arts at ISP', layout: 'mosaic', images: [{ image: ctx.img('primaryProduction') }, { image: ctx.img('artTextile') }, { image: ctx.img('drama') }, { image: ctx.img('eyMusic') }, { image: ctx.img('dtWorkshop') }] },
      cta(ctx),
    ],
  },
  {
    path: 'life/co-curricular',
    title: 'Co-curricular activities',
    navLabel: 'Co-curricular',
    owner: 'Head of Co-curricular',
    description: 'Over 100 teams, clubs and activities at ISP, including teacher-led clubs at no charge and specialist activities led by external experts.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Life at ISP · Co-curricular',
        heading: 'Over 100 ways\nto find your thing.',
        intro: 'Teams, clubs and activities run across each week of the year — mostly after school, with some before school and at weekends.',
        media: ctx.img('chess'),
      },
      {
        blockType: 'richText',
        eyebrow: 'The CCA programme',
        heading: 'Enjoyment, challenge and friendship',
        layout: 'aside',
        content: rt(
          'CCAs promote enjoyment and fun, broaden students’ experiences and offer opportunities they might not otherwise have. They are a key way of building relationships with teachers and with students in other year groups who share the same interests.',
          '**Teacher-led clubs and teams** carry no charge. **Specialist CCAs** are paid activities led by external experts, currently including Fencing, Taekwondo, Robotics, Cheerleading and Cricket.',
          'Parents receive details of the CCAs offered each term. Afternoon activities usually run from 3.15pm to 4.15pm; some team sessions run before school from 6.30am to 7.30am, and some afternoon CCAs finish later.',
        ),
      },
      { blockType: 'listColumns', eyebrow: 'Examples', heading: 'Examples of CCAs we offer', background: 'tint', groups: [{ items: CCA_EXAMPLES, note: 'The programme changes each term.' }] },
      cta(ctx),
    ],
  },
  {
    path: 'life/service',
    title: 'Service and leadership',
    owner: 'Head of Secondary',
    description: 'Student leadership, community outreach through COALS, Sixth Form enrichment and the Duke of Edinburgh’s International Award at ISP.',
    layout: (ctx) => [
      {
        blockType: 'hero',
        variant: 'page',
        eyebrow: 'Life at ISP · Service',
        heading: 'Lead, serve and\nmake a difference.',
        intro: 'Students take on real responsibility, contribute to their community and challenge themselves beyond the classroom.',
        media: ctx.img('houseFriends'),
      },
      {
        blockType: 'listColumns',
        eyebrow: 'Student leadership',
        heading: 'Leadership roles',
        intro: 'There are many opportunities for students to lead across the school.',
        background: 'plain',
        groups: [
          { title: 'Primary', items: 'Primary Prefects\nPrimary Digital Leaders\nPrimary Sports Leaders\nPrimary Library Leaders' },
          { title: 'Secondary and whole school', items: 'Head Students\nSecondary Student Council\nSecondary Prefects\nSecondary Sports Council\nHouse Captains and Vice House Captains\nSustainability Ambassadors' },
        ],
      },
      {
        blockType: 'splitFeature',
        eyebrow: 'From Year 10',
        heading: 'The Duke of Edinburgh’s International Award',
        media: ctx.img('trek'),
        mediaPosition: 'right',
        background: 'tint',
        body: rt(
          'The world’s leading youth achievement award is open to ISP students from Year 10. Participants complete four sections — Physical, Skill, Service and an Adventurous Journey — at each level, with a Residential Project at Gold.',
          ul([
            '**Bronze** — offered at the start of Year 10, for students over 14.',
            '**Silver** — offered from the start of Term 3 in Year 10.',
            '**Gold** — for students over 16, including a four-day Adventurous Journey and a five-day Residential Project.',
          ]),
        ),
      },
      {
        blockType: 'definitionGrid',
        eyebrow: 'Community',
        heading: 'Service in the curriculum',
        columns: '2',
        accent: 'lime',
        background: 'plain',
        items: [
          { term: 'COALS', tagline: 'Community Outreach and Life Skills', description: 'Weekly sessions in Secondary and Sixth Form that develop character, Learning Habits and positive contributions to the local community.' },
          { term: 'Sixth Form enrichment', tagline: 'Three strands', description: 'Personal Growth and Development, Community Outreach and Work Experience.' },
        ],
      },
      cta(ctx),
    ],
  },
]
