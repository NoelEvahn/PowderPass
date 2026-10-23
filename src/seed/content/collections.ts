import { h3, p, rt, ul } from '../lexical'
import type { ImageKey } from '../images'

export type ProgrammeDef = {
  name: string
  ages: string
  order: number
  yearGroups: string
  tagline: string
  summary: string
  highlights: string[]
  campus: 'main' | 'eyc'
  image: ImageKey
  pagePath: string
  accent: string
}

export const PROGRAMMES: ProgrammeDef[] = [
  {
    name: 'Early Years',
    ages: '3–5',
    order: 1,
    yearGroups: 'Nursery and Reception',
    tagline: 'A confident beginning, through play.',
    summary:
      'Children learn through purposeful play at our purpose-built Early Years Centre beside The ParkCity Club. Following the EYFS framework for England, experienced practitioners guide each child at their own pace across bright indoor spaces and a safe, green outdoor environment.',
    highlights: ['EYFS framework for England', 'Separate Early Years Centre, opened 2016', 'Sandpit, bike track, garden and mini jungle trail'],
    campus: 'eyc',
    image: 'eyPlay',
    pagePath: 'learning/early-years',
    accent: 'gold',
  },
  {
    name: 'Primary',
    ages: '5–11',
    order: 2,
    yearGroups: 'Years 1–6',
    tagline: 'Curiosity becomes confidence.',
    summary:
      'Strong foundations in English and Mathematics through the National Curriculum for England, with the International Primary Curriculum bringing topics to life. Children grow more independent from Lower to Upper Primary, with specialist lessons and residential programmes along the way.',
    highlights: ['National Curriculum for England with the IPC', 'Specialist PE, swimming, Mandarin and Bahasa Malaysia', 'Upper Primary residential programmes'],
    campus: 'main',
    image: 'primaryHands',
    pagePath: 'learning/primary',
    accent: 'lime',
  },
  {
    name: 'Secondary',
    ages: '11–16',
    order: 3,
    yearGroups: 'Years 7–11',
    tagline: 'Discover strengths. Develop direction.',
    summary:
      'Key Stage 3 offers breadth across the English National Curriculum and two modern languages. In Years 10 and 11, students combine core IGCSEs with four option subjects, examined through Cambridge International and AQA, supported by specialist teachers and weekly COALS sessions.',
    highlights: ['Key Stage 3 breadth, two languages', 'IGCSE with Cambridge International and AQA', 'International Award from Year 10'],
    campus: 'main',
    image: 'scienceLab',
    pagePath: 'learning/secondary',
    accent: 'purple',
  },
  {
    name: 'Sixth Form',
    ages: '16–18',
    order: 4,
    yearGroups: 'Years 12–13',
    tagline: 'Prepare for what comes next.',
    summary:
      'AS and A Levels from Cambridge International and AQA in small classes, alongside the AQA Baccalaureate and Extended Project Qualification. Daily tutor time, academic monitoring and personalised careers and university guidance prepare students for life beyond school.',
    highlights: ['17 A-Level subjects currently offered', 'AQA Baccalaureate and EPQ', 'Dedicated Sixth Form Centre'],
    campus: 'main',
    image: 'sixthStudy',
    pagePath: 'learning/sixth-form',
    accent: 'violet',
  },
]

type FaqDef = { key: string; question: string; category: string; answer: ReturnType<typeof rt> }

/** Answers summarise the official FAQ page (isp.edu.my/admissions/faq, October 2026). */
export const FAQS: FaqDef[] = [
  {
    key: 'curriculum',
    category: 'curriculum',
    question: 'What curriculum does ISP offer?',
    answer: rt(
      'Early Years follows the Early Years Foundation Stage framework for England. Primary follows the National Curriculum for England in English and Mathematics (and Science in Upper Primary) alongside the International Primary Curriculum. Secondary builds on this through Key Stage 3 and leads to IGCSE examinations; the school is a Cambridge International examination centre and also uses AQA.',
      'In the Sixth Form, students study Cambridge International or AQA AS and A Levels, with the option of the AQA Baccalaureate. [Read more about learning at ISP](/learning).',
    ),
  },
  {
    key: 'age-groups',
    category: 'admissions',
    question: 'How do you decide my child’s year group?',
    answer: rt(
      'Students are placed according to their date of birth, based on their age on 1 September; our academic year starts in September. Children entering Nursery must be 3 years old and toilet independent. Placement also depends on previous school reports and a successful assessment during the application process. [See the year group table](/admissions).',
    ),
  },
  {
    key: 'class-size',
    category: 'daily-life',
    question: 'What is the class size at ISP?',
    answer: rt(ul(['Nursery: typically 20 per class', 'Reception: typically 22 per class', 'Year 1 onwards: typically 24 per class']), 'IGCSE and A-Level classes are often considerably smaller.'),
  },
  {
    key: 'ratio',
    category: 'daily-life',
    question: 'What is the staff-to-student ratio?',
    answer: rt('Throughout Early Years and Primary, each class has one fully qualified, experienced lead teacher and one full-time Classroom Assistant. In Secondary, additional support staff are shared within departments.'),
  },
  {
    key: 'languages',
    category: 'curriculum',
    question: 'Which languages are taught?',
    answer: rt('Students can learn Malay (Bahasa Malaysia), Mandarin and Spanish at advanced or beginner level. Primary children have specialist Mandarin and Bahasa Malaysia lessons, and Key Stage 3 students choose two languages.'),
  },
  {
    key: 'bus',
    category: 'daily-life',
    question: 'Is there a school bus service?',
    answer: rt(
      'Yes. ISP works with a local transport company, TNR Transport, to offer air-conditioned bus services across the Klang Valley. An attendant accompanies the driver to supervise students, who must wear seat belts and remain seated.',
      'For the latest routes, fees and times, contact the Admissions team at [admissions@isp.edu.my](mailto:admissions@isp.edu.my).',
    ),
  },
  {
    key: 'nationalities',
    category: 'community',
    question: 'What nationalities are ISP students?',
    answer: rt('As an international school, ISP educates children from a diverse range of nationalities. The school reports that its students currently come from around 50 different nationalities.'),
  },
  {
    key: 'school-day',
    category: 'daily-life',
    question: 'What time does school start and finish?',
    answer: rt(
      ul([
        'Nursery: 8.25am – 2.15pm',
        'Reception: 8.25am – 2.45pm (Early Years Centre drop-off from 8.00am)',
        'Year 1: 8.00am – 2.40pm',
        'Year 2 and above: 8.00am – 3.00pm (drop-off from 7.30am)',
        'After-school activities: usually 3.15pm – 4.15pm; some team sessions run from 6.30am – 7.30am',
      ]),
    ),
  },
  {
    key: 'eal',
    category: 'support',
    question: 'Is there support if my child is still learning English?',
    answer: rt('Yes. ISP offers a paid English as an Additional Language (EAL) programme to help students develop fluency and competency in English. Students are assessed during admissions to decide whether they would benefit from it.'),
  },
  {
    key: 'support',
    category: 'support',
    question: 'Does ISP support students with special educational needs?',
    answer: rt(
      'The school has a Learning Support Department whose staff provide specialist additional support for students who may benefit from it.',
      'On occasion the school may not be able to meet the needs of children with specific needs or learning disabilities. All applicants are assessed to make sure ISP can cater for their needs before they are accepted, and parents are asked to declare all relevant information during the application.',
    ),
  },
  {
    key: 'cca',
    category: 'daily-life',
    question: 'What co-curricular activities are available?',
    answer: rt('A wide range of after-school activities caters for many interests, with over 100 teams, clubs and activities across the year. Parents receive details of the activities offered each term. [Explore co-curricular activities](/life/co-curricular).'),
  },
  {
    key: 'food',
    category: 'daily-life',
    question: 'What food is available at school?',
    answer: rt('Petite Café offers Asian, Western and vegetarian meals, freshly cooked on site daily, with menus updated every month. It also runs the Parents’ Café. [See family information](/families).'),
  },
  {
    key: 'uniform',
    category: 'daily-life',
    question: 'Do students wear a uniform?',
    answer: rt('Yes. All students wear the school uniform, available from The Shop @ ISP. Sixth Form students wear their own smart business attire.'),
  },
  {
    key: 'unwell',
    category: 'support',
    question: 'What happens if my child is unwell at school?',
    answer: rt('Two fully qualified nurses are based in the School Medical Centre, with an additional full-time nurse at the Early Years Centre. Parents or guardians are contacted if a student is too unwell to stay in school or needs further medical treatment.'),
  },
  {
    key: 'security',
    category: 'support',
    question: 'What security measures are in place?',
    answer: rt('Security is constantly monitored and reviewed. The school has 24-hour CCTV surveillance and security personnel on site day and night. It sits within the secure environment of Desa ParkCity and works closely with the township’s security teams.'),
  },
  {
    key: 'communication',
    category: 'community',
    question: 'How does the school communicate with parents?',
    answer: rt('New parents receive an ISP parent email account just before the start of term; this is the only email account the school uses once a student has joined. The school also uses the SchoolsBuddy app to share information.'),
  },
  {
    key: 'volunteer',
    category: 'community',
    question: 'How can parents get involved?',
    answer: rt('Parents can play an active role through the Parent-Teacher Association (PTA) or by volunteering as a Parent Class Representative. [Learn about the community](/community).'),
  },
  {
    key: 'bullying',
    category: 'support',
    question: 'How does the school deal with bullying?',
    answer: rt('ISP does not tolerate bullying in any form. Every individual is entitled to learn and work in a safe and secure environment. The Code of Conduct for Students is in the Family Handbook.'),
  },
  {
    key: 'trips',
    category: 'curriculum',
    question: 'Do students go on field trips?',
    answer: rt('Field study trips are an integral part of the curriculum at every level, and residential trips are arranged with destinations depending on age and maturity. Parents are informed well in advance, and no student attends an excursion without the required parental documentation.'),
  },
  {
    key: 'availability',
    category: 'admissions',
    question: 'Are places currently available?',
    answer: rt('Parents are strongly advised to apply early. For current availability, contact the Admissions team with your child’s name and date of birth. [Send an enquiry](/admissions/visit).'),
  },
  {
    key: 'how-apply',
    category: 'admissions',
    question: 'How do I apply, and which documents are needed?',
    answer: rt('Complete the online Application for Admission, the Medical & Health Record and the Home-School Agreement. An assessment and a meeting with a Head of section follow once all documents are received. [How to apply](/admissions/apply).'),
  },
  {
    key: 'visas',
    category: 'visas',
    question: 'What are the Malaysian Government rules for enrolling international students?',
    answer: rt(
      'Students who do not hold a Malaysian passport must have a valid visa before starting school, with copies submitted to Admissions.',
      p('Students may hold a **Dependent Pass** under one of the following:'),
      ul(['Employment Pass (EP)', 'Malaysia My Second Home (MM2H or EMM2H)', 'Malaysia Premium Visa Program (PVIP)', 'Residence Pass Talent (RPT)', 'Digital DE Rantau Nomad Pass', 'Child to a Malaysian Citizen visa']),
      'Alternatively, students may study under a **Student Pass**, sometimes with a Guardian Pass. This involves a Ministry of Education process (which can take around three months) followed by the Immigration process; the school assists with the MOE application, and a recommended visa agent helps with Immigration. An Accompany Guardian Pass is only available for siblings under 3.',
      'ISP does not accept students whose parents hold an Employment Pass or SMM2H from Sarawak or Sabah. The Admissions team can guide you through every step.',
    ),
  },
]

export const EVENTS = [
  {
    title: 'EYC Open Morning',
    startsAt: '2026-11-14T01:00:00.000Z', // 9:00am MYT
    endsAt: '2026-11-14T03:00:00.000Z', // 11:00am MYT
    campus: 'eyc',
    audience: 'Nursery & Reception (ages 3–5)',
    location: 'ISP Early Years Centre, next to The ParkCity Club',
    summary:
      'Guided campus tours, classroom play provocations, garden music sessions and face painting, with the chance to meet the Early Years leadership and Admissions team.',
    // The booking link found on isp.edu.my contains older-year wording; Admissions must confirm it before publishing.
    registrationUrl: 'https://eventsize.com/event/1760516933833-the-international-school-parkcity-eyc-open-day-2025-1790837681',
    registrationStatus: 'open',
  },
]

export const STORIES = [
  {
    title: 'Celebrating the Class of 2026',
    category: 'achievement',
    publishedAt: '2026-10-22T00:00:00.000Z',
    image: 'sixthOutdoors' as ImageKey,
    excerpt: 'A-Level, AS-Level and IGCSE results for 2025–2026 reflect the hard work and resilience of our students, with A-Level grades averaging 0.53 above CAT4 predictions.',
    content: rt(
      'With A-Level results in, the school celebrated the achievements of the Class of 2026. Their hard work, determination and resilience led to strong outcomes across a wide range of subjects.',
      'On average, A-Level students achieved grades 0.53 higher than predicted by their CAT4 assessments. This value-added measure reflects the progress students make with the teaching, personalised support and encouragement they receive at ISP.',
      'Year 12 students also received their AS-Level results, and Year 11 students their IGCSE results, achieved across a broad range of subjects.',
      'Every member of staff who supported these students — whether they first taught them in the Early Years Centre or guided them through the Sixth Form — shares in this milestone. [See the 2025–2026 results](/discover/results-accreditation).',
    ),
  },
  {
    title: 'EYC Open Morning: Saturday 14 November 2026',
    category: 'announcement',
    publishedAt: '2026-10-22T00:00:00.000Z',
    image: 'eyPlay' as ImageKey,
    excerpt: 'Families of children aged 3 to 5 are invited to explore the Early Years Centre, meet the team and see learning through play in action.',
    content: rt(
      'Our first open morning of the academic year is the EYC Open Morning, on **Saturday 14 November 2026, from 9:00am to 11:00am**, at the ISP Early Years Centre next to The ParkCity Club.',
      'Designed for families interested in Nursery and Reception (ages 3–5), the morning includes guided campus tours, classroom play provocations, garden music sessions, face painting, and the chance to meet our leadership and Admissions team.',
      'Main Campus Open Days for Primary, Secondary and Sixth Form are typically held in Term 2. [Register your interest](/admissions/visit) to hear when dates are confirmed.',
    ),
  },
  {
    title: 'ISP at 15',
    category: 'news',
    publishedAt: '2026-10-21T00:00:00.000Z',
    image: 'community' as ImageKey,
    excerpt: 'From 131 students in September 2011 to a through-school for ages 3 to 18, ISP marks its 15th anniversary this year.',
    content: rt(
      'The International School @ ParkCity welcomed its first 131 students in September 2011. Fifteen years later, the school educates students from Nursery to Year 13 across its Main Campus and Early Years Centre in Desa ParkCity.',
      'In his anniversary welcome, Group Director – Education and founding Principal Andrew J Dalton reflected on the school’s journey and its commitment to “putting students at the heart of everything we do”.',
      '[Read the welcome messages from our leadership](/discover/leadership).',
    ),
  },
  {
    title: 'Play is the highest form of research',
    category: 'news',
    publishedAt: '2023-02-08T00:00:00.000Z',
    image: 'eyMusic' as ImageKey,
    authorRole: 'Head of The Early Years Centre (2023)',
    excerpt: 'Why play matters so much in the early years, how the EYC supports it, and simple ideas to encourage play at home.',
    content: rt(
      '“What are they learning when all they do is play?” When you first step into an early years setting where children are covered head to toe in paint or barefoot in the sand pit, this may be a question you ask yourself.',
      'The universal language of play is beautiful. The impulse to play is innate, and play includes children from all over the world regardless of language, culture, knowledge and experience. It provides opportunities for progression, extension and challenge, and it is a joy to watch children’s confidence grow through their natural curiosity.',
      h3('The right to play'),
      'Research shows that play is essential to every child’s life, vital for the enjoyment of childhood and for social, emotional, intellectual and physical development. When children can take the lead and become fully immersed in their play, we see:',
      ul([
        'increased self-awareness, self-esteem and self-respect',
        'the ability to make informed choices, make mistakes and learn from them',
        'improved physical development and emotional health',
        'successful interactions with other children and the development of social skills',
        'imagination, independence and creativity',
        'enhanced language and communication skills',
        'resilience through risk taking, challenge and problem solving',
      ]),
      h3('How we support play at the EYC'),
      'All children need to feel secure and have a sense of belonging before they can begin to learn. We consider every interaction a “teachable moment” — perhaps by providing a notepad to take orders in a pop-up restaurant, or by wondering aloud with children in the block area.',
      'Our learning spaces use open-ended, accessible resources. The garden encourages calculated risk taking through large-scale climbing; the sand kitchen prepares our engineers of the future; and in the “jungle” children explore plants and animals or create stories with friends.',
      h3('Supporting play at home'),
      ul([
        'Join in — be a customer in their pretend restaurant.',
        'Give your child time and space to solve problems independently.',
        'Use simple, open-ended resources such as large cardboard boxes.',
        'Ask open-ended questions: “How could we figure that out?”',
        'Celebrate the process, not just the final product.',
        'Get messy — children are 100% washable!',
      ]),
      'Originally published on the ISP website in February 2023.',
    ),
  },
  {
    title: 'The importance of a smooth transition',
    category: 'news',
    publishedAt: '2023-07-01T00:00:00.000Z',
    image: 'primaryReading' as ImageKey,
    authorRole: 'Head of Year 2 and Primary Teacher (2023)',
    excerpt: 'How teachers and parents can help children move confidently into a new year group, a new classroom and sometimes a new Key Stage.',
    content: rt(
      'The end of the school year brings an array of emotions. Alongside the excitement of holidays and end-of-year events, the thought of a new teacher, classroom and friends can feel daunting. A smooth transition, supported by teachers and parents, is critical to how a child feels about the year ahead — whether moving from Year 3 to Year 4 or across Key Stages from Reception, Year 2 or Year 6.',
      h3('The teacher’s role'),
      'Preparation can start from the beginning of Term 3. Teachers familiarise children with the expectations, routines and curriculum of their new year group, and encourage independence — such as carrying their own lunchbox or looking after an iPad. Colleagues share information about each child’s strengths, challenges and friendships before careful class placement decisions are made.',
      'Ending the year by meeting the new teacher and spending time in the new classroom helps children feel familiar with their setting before the long break. On “Meet the Teacher” day, children often create something for a shared display, such as:',
      ul(['“We All Fit Together” puzzle pieces', '“All About Me” bunting', 'Self-portraits', 'Predictions for the year', 'A letter to the new teacher']),
      h3('At home'),
      'Reading “The Kissing Hand” the night before school can help younger children with separation, while older children moving into Year 7 might enjoy “You Are Awesome” by Matthew Syed, which shows how hard work and determination lead to achievement.',
      'Originally published on the ISP website in July 2023.',
    ),
  },
]
