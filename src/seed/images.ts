/**
 * Image register for the seed. Every photograph is published by the school on
 * isp.edu.my (October 2026). They are marked "pending" because publication rights
 * and student releases must be confirmed by the school before launch.
 * Alt text describes the activity and never identifies children.
 */
export type SeedImage = { url: string; alt: string; caption?: string }

const U = 'https://isp.edu.my/wp-content/uploads'

export const IMAGES = {
  eyMusic: { url: `${U}/2026/10/Website-Picture-update.png`, alt: 'Young children sit on a classroom carpet playing glockenspiels with their teacher, an interactive screen behind them.', caption: 'Music in the early years' },
  primaryIpad: { url: `${U}/2026/10/Website-Picture-update-1.png`, alt: 'Two Primary pupils share ideas at a classroom table with an iPad and exercise book.', caption: 'Primary classroom' },
  primaryReading: { url: `${U}/2026/10/Website-Picture-update-2.png`, alt: 'A teacher reads with a small group of Primary pupils around a table.', caption: 'Small-group reading' },
  sportsDay: { url: `${U}/2026/10/Website-Picture-update-3.png`, alt: 'Students in house colours race along a grass track while classmates watch from the side.', caption: 'Sports day' },
  drama: { url: `${U}/2026/10/Website-Picture-update-4.png`, alt: 'A student performs on stage in costume during a school production, with the cast behind.', caption: 'A school production' },
  dtWorkshop: { url: `${U}/2026/10/Website-Picture-update-5.png`, alt: 'Secondary students build wooden projects at workbenches in the design and technology workshop.', caption: 'Design & Technology workshop' },
  scienceLab: { url: `${U}/2026/10/Website-Picture-update-6.png`, alt: 'Students use microscopes in a science laboratory while their teacher looks on.', caption: 'Science laboratory' },
  sixthOutdoors: { url: `${U}/2026/10/Website-Picture-update-7.png`, alt: 'A group of Sixth Form students in business attire sit together on the artificial-grass pitch.', caption: 'Sixth Form students' },
  primaryHands: { url: `${U}/2026/10/Website-Picture-update-8.png`, alt: 'Primary pupils sitting on the classroom floor eagerly raise their hands.', caption: 'Primary learning' },
  eyPlay: { url: `${U}/2026/10/Website-Picture-update-9.png`, alt: 'An Early Years teacher laughs with young children during a pretend-play activity with cupcakes.', caption: 'Learning through play' },
  primaryWriting: { url: `${U}/2026/10/Website-Picture-update-10.png`, alt: 'A teacher supports a young pupil with her writing at a classroom table.', caption: 'One-to-one support' },
  classCarpet: { url: `${U}/2026/10/Website-Picture-update-11.png`, alt: 'A class of young pupils sits on the carpet facing their teacher and the interactive whiteboard.', caption: 'Whole-class teaching' },
  secondaryCorridor: { url: `${U}/2026/10/Website-Picture-update-12.png`, alt: 'A Secondary student walks and talks with a teacher along a school corridor.', caption: 'Secondary School' },
  secondaryClass: { url: `${U}/2026/10/Website-Picture-update-13.png`, alt: 'A teacher leads a discussion with a Secondary class working at their desks.', caption: 'Secondary classroom' },
  sixthStudy: { url: `${U}/2026/10/Website-Picture-update-14.png`, alt: 'A Sixth Form student smiles while studying at a desk.', caption: 'Independent study in the Sixth Form' },
  artTextile: { url: `${U}/2026/10/Website-Picture-update-15.png`, alt: 'Students paint a large, colourful textile artwork laid across tables in the art room.', caption: 'Creative Arts' },
  primaryProduction: { url: `${U}/2026/10/Website-Picture-update-16.png`, alt: 'Primary pupils in costume perform together on stage.', caption: 'Primary production' },
  sprint: { url: `${U}/2026/10/Website-Picture-update-17.png`, alt: 'Secondary students sprint on the school field during an athletics event.', caption: 'PE & sport' },
  blocksPlay: { url: `${U}/2026/10/Website-Picture-update-18.png`, alt: 'Two pupils explore wooden shapes together at a table.', caption: 'Hands-on learning' },
  houseFriends: { url: `${U}/2026/10/Website-Picture-update-19.png`, alt: 'Smiling Secondary students in different house-colour shirts stand together outdoors.', caption: 'Friendship and belonging' },
  globe: { url: `${U}/2026/10/Website-Picture-update-20.png`, alt: 'A teacher points to a globe as two pupils discuss it at their desk.', caption: 'Curiosity in the classroom' },
  parentWorkshop: { url: `${U}/2026/10/Website-Picture-update-21.png`, alt: 'Parents attend a presentation at round tables in a school hall.', caption: 'A parent workshop' },
  smallGroup: { url: `${U}/2026/10/Website-Picture-update-22.png`, alt: 'A teacher works closely with a small group of pupils at a classroom table.', caption: 'Small-group support' },
  staffCare: { url: `${U}/2026/10/Website-Picture-update-23.png`, alt: 'A member of staff talks kindly with two young pupils in a school office.', caption: 'Care across the school' },
  canteen: { url: `${U}/2026/10/Website-Picture-update-24.png`, alt: 'Pupils collect freshly cooked lunch at the school canteen counter.', caption: 'Lunch at school' },
  trek: { url: `${U}/2026/10/Website-Picture-update-26-e1791961571599.png`, alt: 'Students trek along a forest trail carrying backpacks.', caption: 'An expedition' },
  chess: { url: `${U}/2026/10/Website-Picture-update-27.png`, alt: 'Four students laugh while playing chess on a low table in a lounge.', caption: 'Time together' },
  pta: { url: `${U}/2026/10/Website-Picture-update-28-e1791962101131.png`, alt: 'Parents, staff and children gather around a fundraising stall selling popcorn.', caption: 'A PTA fundraising stall' },
  ipads: { url: `${U}/2026/10/Website-Picture-update-29.png`, alt: 'Pupils work on iPads together in a Primary classroom.', caption: 'Digital learning' },
  community: { url: `${U}/2026/07/ISP_All-years_banner_2026_-scaled.png`, alt: 'Students of all ages walk together along a covered school corridor with school leaders and staff.', caption: 'The ISP community' },
  walkway: { url: `${U}/2016/07/PD7_7491-e1494553009524.jpg`, alt: 'Pupils walk along a covered walkway beside the green artificial-grass field, with national flags hanging overhead.', caption: 'Main Campus' },
  sixthCommon: { url: `${U}/2016/07/DSC_4517-min.jpg`, alt: 'Sixth Form students talk with teachers in a common room.', caption: 'Sixth Form common room' },
  keenan: { url: `${U}/2017/05/Keenan-Tom.jpg`, alt: 'Portrait of Tom Keenan, Principal.' },
  dalton: { url: `${U}/2026/07/Andrew-Dalton.png`, alt: 'Portrait of Andrew J Dalton, Group Director – Education.' },
  resultsALevel: { url: `${U}/2026/08/01.png`, alt: 'A-Level results 2025–2026, school-published graphic: average value added 0.53; grades A* 36%, A*–A 60%, A*–B 82%.', caption: 'A-Level results 2025–2026' },
  resultsAS: { url: `${U}/2026/08/01-1.png`, alt: 'AS-Level results 2025–2026, school-published graphic: average value added 0.73; grades A 46%, A–B 71%, A–C 86%.', caption: 'AS-Level results 2025–2026' },
  resultsIGCSE: { url: `${U}/2026/08/01-2.png`, alt: 'IGCSE results 2025–2026, school-published graphic: average value added 0.51; grades A*–A 57%, A*–B 83%, A*–C 96%.', caption: 'IGCSE results 2025–2026' },
} satisfies Record<string, SeedImage>

export type ImageKey = keyof typeof IMAGES
