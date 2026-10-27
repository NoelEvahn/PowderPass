/**
 * Seed content for the editable pages (About, Buying info and the policy pages).
 * Policy wording is adapted from the approved wording of the previous one-page site.
 * Anything in [square brackets] is a visible placeholder awaiting confirmation by Label Trunc.
 * Rule for this file: never put a [placeholder] before a [link](/url) in the same string.
 */
import { doc, h3, p, ul } from './lexical'

type Seed = Record<string, unknown> & { slug: string }

const LAST_UPDATED = '2026-10-27'

/** A text section for reading, narrow by default. */
const text = (heading: string | undefined, ...body: ReturnType<typeof p>[]) => ({
  blockType: 'content' as const,
  ...(heading ? { heading } : {}),
  body: doc(...body),
  width: 'narrow' as const,
})

const faq = (question: string, ...answer: ReturnType<typeof p>[]) => ({ question, answer: doc(...answer) })

export const pages: Seed[] = [
  /* ------------------------------------------------------------------ About */
  {
    title: 'About Label Trunc',
    slug: 'about',
    navLabel: 'About',
    intro: 'Curated · Collected · Consigned. A boutique for new and pre-loved designer bags in Ampang, Selangor.',
    seo: {
      description:
        'Label Trunc is an independent boutique in Ampang for new and pre-loved designer bags. Every piece is guaranteed authentic. Shop online or visit us.',
    },
    layout: [
      text(
        'Luxury deserves to be loved again.',
        p(
          'Label Trunc is a boutique for new and pre-loved designer bags at Ukay Boulevard, Ampang. Each piece is chosen with care, whether it arrives brand new or has already had a first life with someone else.',
        ),
        p(
          'Do you have a piece that deserves another owner? We also take consignments. [Tell us about it](/contact) and we will explain how it works before anything is agreed.',
        ),
        p(
          'Label Trunc is an independent business. We are not affiliated with, endorsed by or an authorised retailer of any designer house whose pieces we sell.',
        ),
      ),
      {
        blockType: 'terms',
        heading: 'What we promise',
        items: [
          {
            icon: 'shield',
            title: '100% authentic, money-back guaranteed',
            text: 'Every piece we sell is guaranteed authentic. Our refund policy explains what happens if one is not.',
          },
          {
            icon: 'bag',
            title: 'Condition described before you buy',
            text: 'We describe the condition of every pre-loved piece up front, so you know what you are getting.',
          },
          {
            icon: 'clock',
            title: 'Time to decide',
            text: 'Add a piece to your cart and we hold it for you for up to 24 hours.',
          },
          {
            icon: 'store',
            title: 'See it in person',
            text: 'Collect at the boutique, or visit us first. We are at Ukay Boulevard, Ampang.',
          },
        ],
      },
      text(undefined, p('Collected with passion · Carried with love.')),
      {
        blockType: 'cta',
        heading: 'Find your next piece',
        text: 'Browse what is in stock now, or come and meet the pieces in person.',
        links: [
          { label: 'Shop now', url: '/shop' },
          { label: 'Visit the boutique', url: '/visit' },
        ],
      },
    ],
  },

  /* ------------------------------------------------------------------- Info */
  {
    title: 'How buying works',
    slug: 'info',
    navLabel: 'Buying info',
    intro:
      'Add a piece to your cart and we hold it for you while you decide. Pay online, or reserve it and pay at the boutique.',
    seo: {
      description:
        'How buying from Label Trunc works: 24-hour holds, paying online with FPX, cards or Touch n Go eWallet, paying in store, collection and condition grades.',
    },
    layout: [
      {
        blockType: 'terms',
        heading: 'In short',
        items: [
          {
            icon: 'clock',
            title: 'Held for up to 24 hours',
            text: 'Add a piece to your cart and it is held for you, so nobody else can buy it while you decide.',
          },
          {
            icon: 'card',
            title: 'Pay online with Billplz',
            text: "FPX online banking (Maybank2u, HSBC, Public Bank PBe and more), debit and credit cards, Touch 'n Go eWallet and other e-wallets.",
          },
          {
            icon: 'store',
            title: 'Or reserve and pay at the boutique',
            text: 'We hold the piece for you, and you pay and collect in store at Ukay Boulevard, Ampang.',
          },
          {
            icon: 'truck',
            title: 'Collection, or delivery when offered',
            text: 'Collection at the boutique is always available. Delivery is offered only when we have switched it on.',
          },
          {
            icon: 'shield',
            title: '100% authentic, money-back guaranteed',
            text: 'Every piece is guaranteed authentic. No account is needed to buy.',
          },
        ],
      },
      text(
        'Buying tips',
        h3('Bring your day'),
        p('If your phone will not lie flat inside, the bag stays home. Bring what you carry every day and try it.'),
        h3('Read the corners'),
        p('Wear shows first at corners, handles and hardware. Ask for daylight photos or a video before you buy.'),
        h3('Ask what is included'),
        p('Dust bag, box, receipt, cards. Each piece lists what comes with it; ask before you decide.'),
        h3('Getting it home'),
        p('Collect in store, or ask about same-day delivery in the Klang Valley.'),
      ),
      {
        blockType: 'faq',
        heading: 'Questions and answers',
        items: [
          faq(
            'How does a hold work?',
            p(
              'When you add a piece to your cart, we hold it for you for up to 24 hours. Nobody else can buy it during that time. The boutique sets the exact hold time and it is never more than 24 hours.',
            ),
            p('If you choose to reserve and pay at the boutique, the piece is held in the same way until you come in.'),
          ),
          faq(
            "What happens if I don't pay in time?",
            p(
              'The hold ends and the piece goes back on the shop, where someone else may buy it. You are not charged anything.',
            ),
            p(
              'If you think you paid but the hold had already ended, [message us on WhatsApp](https://wa.me/60124211205) straight away and we will help.',
            ),
          ),
          faq(
            'Which payment methods can I use?',
            p(
              "Online, you can pay with FPX online banking (including Maybank2u, HSBC and Public Bank PBe, plus other FPX banks), debit and credit cards, Touch 'n Go eWallet and other e-wallets.",
            ),
            p(
              'You can also reserve a piece and pay at the boutique. [Label Trunc to confirm accepted payment methods in store]',
            ),
          ),
          faq(
            'Is paying online safe?',
            p(
              'Online payments are processed by Billplz on its own pages. We never see or store your card or bank login details. Billplz gives us only the result of the payment, so we can confirm your order.',
            ),
          ),
          faq(
            'Can I see a piece before I buy it?',
            p(
              'Yes. Visit the boutique by appointment, or reserve the piece online and pay when you collect it. See [how to visit](/visit), or [ask us](/contact) to arrange a time.',
            ),
          ),
          faq(
            'What do the condition grades mean?',
            ul([
              '**Brand new:** a new piece that has not been used.',
              '**Unused:** a piece that has not been carried.',
              '**Pre-loved, excellent:** carried, with very light signs of use.',
              '**Pre-loved, very good:** light, even wear.',
              '**Pre-loved, good:** visible signs of regular use.',
              '**Pre-loved, fair:** clear wear, described in detail.',
            ]),
            p('Each piece lists its own condition notes. [Label Trunc to confirm the wording of each condition grade]'),
          ),
          faq(
            'What comes with a piece?',
            p(
              'Anything included, such as a dust bag, box, cards or paperwork, is listed on that piece. If something is not listed, it is not included.',
            ),
          ),
          faq(
            'Can I return something?',
            p(
              'Our [refunds and returns policy](/refunds) is being finalised. What we can already say is that every piece is guaranteed authentic and the condition is described before you buy.',
            ),
          ),
          faq(
            'Can I consign a piece with you?',
            p(
              'Yes. [Contact us](/contact) with photos of your piece. We will explain how consignment works before anything is agreed.',
            ),
          ),
          faq(
            'Do I need an account to buy?',
            p(
              'No. You can check out as a guest. An account lets you see your orders in one place. You only need your name, email, phone number and a password.',
            ),
          ),
        ],
      },
      {
        blockType: 'notice',
        tone: 'pending',
        text: 'Delivery fees and areas, and some policy details, are still being confirmed with the boutique. Where something is not final, you will see it marked on the relevant page.',
      },
      {
        blockType: 'cta',
        heading: 'Still have a question?',
        text: 'Ask us anything before you buy. We are happy to help.',
        links: [
          { label: 'Contact us', url: '/contact' },
          { label: 'Browse the shop', url: '/shop' },
        ],
      },
    ],
  },

  /* ------------------------------------------------------- Privacy (English) */
  {
    title: 'Privacy notice',
    slug: 'privacy',
    navLabel: 'Privacy',
    intro: 'How we look after your personal data when you browse, buy or contact Label Trunc.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc privacy notice: what personal data we receive when you create an account, order or contact us, why we use it, who we share it with, and your rights.',
    },
    layout: [
      text(
        undefined,
        p(
          '**In short:** this website has no analytics, no advertising trackers and no third-party embeds. It uses only two strictly necessary cookies. We receive personal data when you create an account, place an order or contact us.',
        ),
        p('[Baca dalam Bahasa Malaysia](/privasi)'),
      ),
      text(
        '1. Who we are',
        p(
          'Label Trunc (“we”, “us”) is a boutique for new and pre-loved designer pieces at 11-2A, Level 2, Ukay Boulevard, Jln Lingkaran Tengah 2, 68000 Ampang, Selangor, Malaysia. We are the data user responsible for personal data described in this notice under the Personal Data Protection Act 2010 (PDPA).',
        ),
        p('Registered business name and SSM number: [to be added by Label Trunc]'),
        p('Email: labeltrunc@gmail.com · Phone and WhatsApp: +60 12-421 1205'),
      ),
      text(
        '2. What we receive and how',
        p('**When you create an account:** your name, email address, phone number and password. Your password is stored in scrambled (hashed) form, not as readable text.'),
        p('**When you place an order**, as a guest or with an account: your name and contact details, the pieces you chose, whether you will collect or want delivery, your delivery address if delivery applies, and the status of your payment.'),
        p('**When you pay online:** payment is made on Billplz’s own pages. Billplz collects your bank, card or e-wallet details, not us. We receive only the result and a payment reference, so we can confirm your order. We do not see or store your card or bank login details.'),
        p('**When you use the contact form on this site:** the name, contact details and message you enter are saved in our shop system and emailed to our staff.'),
        p('**When you contact us by WhatsApp, phone, email or Instagram:** your name and number, email address or handle as shown by that service, and anything you choose to include, such as a message or photos.'),
        p('**When you consign with us:** the details needed to complete it, such as your name, contact details and payment records. [Label Trunc to confirm any other details collected, for example identification for consignment]'),
        p('**Emails we send you:** we email you order confirmations and password reset links, and nothing else unless you ask us to.'),
        p('**On your device:** this site sets two cookies that keep you signed in and keep your cart. See the [cookie and storage policy](/cookies).'),
      ),
      text(
        '3. Why we use it',
        ul([
          'To run your account and show you your orders.',
          'To hold pieces for you, take and confirm your order, and arrange collection or delivery.',
          'To reply to your enquiry and arrange viewings.',
          'To complete a sale or consignment, and provide after-sales help.',
          'To send the emails described above, such as order confirmations and password resets.',
          'To keep the shop secure and prevent misuse.',
          'To meet legal, tax and accounting obligations.',
          'To tell you about new arrivals, only if you ask us to. You can stop this at any time.',
        ]),
      ),
      text(
        '4. Is it compulsory?',
        p(
          'No. Everything you send us is your choice. You do not need an account to buy, and guest checkout is available. To complete an order we do need your name and contact details. If you do not give us enough to reply to or to complete an order, we may not be able to help with your enquiry or complete a purchase.',
        ),
      ),
      text(
        '5. Who we share it with',
        p('We do not sell personal data. We share it only as needed with:'),
        ul([
          'Billplz, which processes online payments, under its own privacy policy. [Label Trunc to confirm the link to Billplz’s privacy policy]',
          'The messaging and email services you choose to contact us through or that we use to email you, such as WhatsApp (Meta) and Gmail (Google). [Label Trunc to name the email service used for order and password emails]',
          'Our hosting provider, which runs this website and its database.',
          'Delivery partners and banks, to complete a purchase, and only where delivery is offered.',
          'Professional advisers, and authorities where the law requires it.',
        ]),
        p('Some of these services may store and process data outside Malaysia under their own terms.'),
      ),
      text(
        '6. How long we keep it',
        p(
          'Only as long as needed for the purposes above and to meet legal requirements. [Label Trunc to confirm retention periods, for example how long accounts and unused carts are kept, how long enquiries from the contact form and chats are kept when no purchase follows, and how long sales records are kept]',
        ),
      ),
      text(
        '7. Keeping it safe',
        p(
          'We take reasonable steps to protect personal data from loss, misuse and unauthorised access, including keeping accounts and devices used for customer messages locked and limited to our team. Our sign-in and cart cookies cannot be read by scripts on the page. If a data breach is likely to cause significant harm, we will notify the Personal Data Protection Commissioner and affected people as required by the PDPA.',
        ),
      ),
      text(
        '8. Your rights',
        p(
          'You can ask to see the personal data we hold about you, correct it, withdraw your consent, limit how we use it, stop direct marketing, close your account, or receive a copy to transfer elsewhere. Contact us by email or WhatsApp. We will respond within 21 days.',
        ),
        p(
          'If you are not satisfied with our response, you can contact the Personal Data Protection Department (Jabatan Perlindungan Data Peribadi) at [pdp.gov.my](https://www.pdp.gov.my).',
        ),
      ),
      text(
        '9. This website’s hosting',
        p(
          'This website and its shop are delivered by a hosting provider, which may automatically record technical data such as IP address, browser type and time of visit to serve and secure the page, under its own privacy policy. [Label Trunc to name the hosting provider once the site is published, and to confirm where the shop’s database is hosted]',
        ),
        p(
          'The fonts used on this site are built into the page, so viewing it does not contact any font service. This site has no embedded videos, maps or social media widgets.',
        ),
      ),
      text(
        '10. Other websites',
        p(
          'When you pay online you are taken to Billplz’s own pages, where Billplz’s privacy policy applies. Links to Instagram, TikTok, Facebook, WhatsApp and Google Maps open those services, whose own privacy policies apply.',
        ),
      ),
      text(
        '11. Children',
        p('This website is intended for adults. We do not knowingly collect personal data from anyone under 18.'),
      ),
      text(
        '12. Language and changes',
        p(
          'This notice is available in English and Bahasa Malaysia. [Label Trunc to confirm which version prevails if they differ]',
        ),
        p('We may update this notice and will change the date above when we do.'),
      ),
    ],
  },

  /* ------------------------------------------------------- Privacy (Malay) */
  {
    title: 'Notis privasi',
    slug: 'privasi',
    navLabel: 'Privasi',
    intro: 'Cara kami menjaga data peribadi anda apabila anda melayari, membeli atau menghubungi Label Trunc.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Notis privasi Label Trunc dalam Bahasa Malaysia: data yang kami terima apabila anda membuat akaun, memesan atau menghubungi kami, tujuan, perkongsian dan hak anda.',
    },
    layout: [
      text(
        undefined,
        p(
          '**Ringkasnya:** laman web ini tidak menggunakan analitik, penjejak pengiklanan atau kandungan pihak ketiga yang dibenamkan. Ia hanya menggunakan dua kuki yang benar-benar diperlukan. Kami menerima data peribadi apabila anda membuat akaun, membuat pesanan atau menghubungi kami.',
        ),
        p('[Read in English](/privacy)'),
      ),
      text(
        '1. Siapa kami',
        p(
          'Label Trunc (“kami”) ialah butik barangan berjenama baharu dan terpakai di 11-2A, Tingkat 2, Ukay Boulevard, Jln Lingkaran Tengah 2, 68000 Ampang, Selangor, Malaysia. Kami ialah pengguna data yang bertanggungjawab ke atas data peribadi dalam notis ini di bawah Akta Perlindungan Data Peribadi 2010 (APDP).',
        ),
        p('Nama perniagaan berdaftar dan nombor SSM: [akan ditambah oleh Label Trunc]'),
        p('E-mel: labeltrunc@gmail.com · Telefon dan WhatsApp: +60 12-421 1205'),
      ),
      text(
        '2. Data yang kami terima',
        p('**Apabila anda membuat akaun:** nama, alamat e-mel, nombor telefon dan kata laluan anda. Kata laluan disimpan dalam bentuk terkod (hash), bukan teks yang boleh dibaca.'),
        p('**Apabila anda membuat pesanan**, sebagai tetamu atau dengan akaun: nama dan butiran hubungan, barangan yang dipilih, sama ada anda akan mengambilnya di butik atau mahu penghantaran, alamat penghantaran jika penghantaran terpakai, dan status pembayaran.'),
        p('**Apabila anda membayar dalam talian:** pembayaran dibuat di laman Billplz sendiri. Billplz mengumpul butiran bank, kad atau e-dompet anda, bukan kami. Kami hanya menerima keputusan pembayaran dan rujukan pembayaran untuk mengesahkan pesanan anda. Kami tidak melihat atau menyimpan butiran kad atau log masuk bank anda.'),
        p('**Apabila anda menggunakan borang hubungi di laman ini:** nama, butiran hubungan dan mesej yang anda masukkan disimpan dalam sistem kedai kami dan dihantar melalui e-mel kepada staf kami.'),
        p('**Apabila anda menghubungi kami melalui WhatsApp, telefon, e-mel atau Instagram:** nama dan nombor, alamat e-mel atau nama pengguna seperti yang dipaparkan oleh perkhidmatan tersebut, serta apa sahaja yang anda pilih untuk sertakan.'),
        p('**Apabila anda mengkonsainkan barangan:** butiran yang diperlukan, seperti nama, butiran hubungan dan rekod pembayaran. [Label Trunc akan mengesahkan butiran lain yang dikumpul]'),
        p('**E-mel yang kami hantar:** kami menghantar e-mel pengesahan pesanan dan pautan tetapan semula kata laluan, dan tiada yang lain melainkan anda memintanya.'),
        p('**Pada peranti anda:** laman ini menetapkan dua kuki untuk memastikan anda kekal log masuk dan troli anda disimpan. Lihat [dasar kuki dan storan](/cookies).'),
      ),
      text(
        '3. Tujuan',
        ul([
          'Untuk menguruskan akaun anda dan memaparkan pesanan anda.',
          'Untuk menahan barangan untuk anda, menerima dan mengesahkan pesanan, serta mengatur pengambilan atau penghantaran.',
          'Untuk membalas pertanyaan anda dan mengatur temu janji.',
          'Untuk melengkapkan jualan atau konsainan, dan memberikan bantuan selepas jualan.',
          'Untuk menghantar e-mel yang diterangkan di atas, seperti pengesahan pesanan dan tetapan semula kata laluan.',
          'Untuk memastikan kedai ini selamat dan mencegah penyalahgunaan.',
          'Untuk mematuhi kewajipan undang-undang, cukai dan perakaunan.',
          'Untuk memaklumkan ketibaan baharu, hanya jika anda memintanya. Anda boleh berhenti pada bila-bila masa.',
        ]),
      ),
      text(
        '4. Adakah ia wajib?',
        p(
          'Tidak. Semua maklumat adalah pilihan anda. Anda tidak perlu membuat akaun untuk membeli, dan pembelian sebagai tetamu boleh dilakukan. Untuk melengkapkan pesanan, kami memerlukan nama dan butiran hubungan anda. Jika maklumat tidak mencukupi, kami mungkin tidak dapat membalas pertanyaan anda atau melengkapkan pembelian.',
        ),
      ),
      text(
        '5. Pihak yang menerima data',
        p('Kami tidak menjual data peribadi. Kami hanya berkongsi apabila perlu dengan:'),
        ul([
          'Billplz, yang memproses pembayaran dalam talian, di bawah dasar privasinya sendiri. [Label Trunc akan mengesahkan pautan kepada dasar privasi Billplz]',
          'Perkhidmatan pemesejan dan e-mel yang anda gunakan untuk menghubungi kami atau yang kami gunakan untuk menghantar e-mel kepada anda, seperti WhatsApp dan Gmail. [Label Trunc akan menamakan perkhidmatan e-mel yang digunakan untuk e-mel pesanan dan kata laluan]',
          'Penyedia pengehosan kami, yang menjalankan laman web ini dan pangkalan datanya.',
          'Rakan penghantaran dan bank, untuk melengkapkan pembelian, dan hanya apabila penghantaran ditawarkan.',
          'Penasihat profesional, dan pihak berkuasa jika dikehendaki oleh undang-undang.',
        ]),
        p('Sesetengah perkhidmatan ini mungkin memproses data di luar Malaysia di bawah terma mereka sendiri.'),
      ),
      text(
        '6. Tempoh simpanan',
        p(
          'Hanya selama yang diperlukan untuk tujuan di atas dan untuk mematuhi undang-undang. [Label Trunc akan mengesahkan tempoh simpanan, termasuk akaun, troli yang tidak digunakan, pertanyaan dan rekod jualan]',
        ),
      ),
      text(
        '7. Keselamatan',
        p(
          'Kami mengambil langkah munasabah untuk melindungi data peribadi. Kuki log masuk dan troli kami tidak boleh dibaca oleh skrip pada halaman. Jika berlaku pelanggaran data yang mungkin menyebabkan kemudaratan ketara, kami akan memaklumkan Pesuruhjaya Perlindungan Data Peribadi dan individu yang terjejas seperti yang dikehendaki oleh APDP.',
        ),
      ),
      text(
        '8. Hak anda',
        p(
          'Anda boleh meminta untuk mengakses, membetulkan, menarik balik persetujuan, mengehadkan penggunaan, menghentikan pemasaran langsung, menutup akaun anda, atau menerima salinan data peribadi anda. Hubungi kami melalui e-mel atau WhatsApp. Kami akan membalas dalam tempoh 21 hari.',
        ),
        p(
          'Anda juga boleh menghubungi Jabatan Perlindungan Data Peribadi di [pdp.gov.my](https://www.pdp.gov.my).',
        ),
      ),
      text(
        '9. Pengehosan dan laman lain',
        p(
          'Penyedia pengehosan laman dan kedai ini mungkin merekodkan data teknikal seperti alamat IP untuk memaparkan dan melindungi laman, di bawah dasar privasinya sendiri. [nama penyedia pengehosan dan lokasi pangkalan data kedai]',
        ),
        p(
          'Apabila anda membayar dalam talian, anda dibawa ke laman Billplz sendiri, dan dasar privasi Billplz terpakai. Pautan ke Instagram, TikTok, Facebook, WhatsApp dan Google Maps membuka perkhidmatan tersebut, dan dasar privasi mereka terpakai. Fon laman ini dibina dalam halaman, dan laman ini tidak mempunyai video, peta atau widget media sosial yang dibenamkan.',
        ),
      ),
      text(
        '10. Kanak-kanak, bahasa dan perubahan',
        p(
          'Laman ini ditujukan untuk orang dewasa. Kami tidak sengaja mengumpul data peribadi daripada sesiapa yang berumur di bawah 18 tahun.',
        ),
        p('Notis ini disediakan dalam Bahasa Malaysia dan Bahasa Inggeris. [versi yang terpakai jika terdapat percanggahan]'),
        p('Kami akan mengemas kini tarikh di atas jika notis ini berubah.'),
      ),
    ],
  },

  /* ---------------------------------------------------------------- Cookies */
  {
    title: 'Cookies and storage',
    slug: 'cookies',
    navLabel: 'Cookies',
    intro: 'Only two cookies, both strictly necessary. No analytics, no advertising, no tracking.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc cookie and storage policy: two strictly necessary cookies keep you signed in and keep your cart. No analytics, advertising or tracking.',
    },
    layout: [
      text(
        undefined,
        p(
          'This website does not use analytics, advertising or tracking tools. It sets two cookies, and both are strictly necessary for features you choose to use: signing in and holding pieces in your cart.',
        ),
      ),
      text(
        'Cookies this site sets on your device',
        h3('payload-token'),
        ul([
          '**Purpose:** keeps you signed in to your account.',
          '**When:** only when you are signed in. It is httpOnly, so scripts on the page cannot read it.',
          '**How long:** expires after 14 days, or when you sign out.',
        ]),
        h3('lt_cart'),
        ul([
          '**Purpose:** holds a random identifier that links your browser to the pieces held in your cart.',
          '**When:** used for your cart. It is httpOnly, so scripts on the page cannot read it.',
          '**How long:** expires after 1 day.',
        ]),
        p('We do not set any other cookies.'),
      ),
      text(
        'Do I need to agree?',
        p(
          'No. These cookies only support features you choose to use, so the site does not show a consent banner. If we ever add analytics or advertising tools, we will ask for your consent first.',
        ),
      ),
      text(
        'Remove them',
        p(
          'Signing out removes your sign-in cookie. You can also delete cookies for this site in your browser settings at any time. If you delete lt_cart, you may lose access to your cart on that device. Pieces already held are released when their hold ends.',
        ),
      ),
      text(
        'Other services',
        p(
          'When you pay online you are taken to Billplz’s own pages. Billplz may set its own cookies there, under its own policies.',
        ),
        p(
          'When you follow a link to Instagram, TikTok, Facebook, WhatsApp or Google Maps, those services may set their own cookies under their own policies. The hosting provider that delivers this page may also use technical cookies. [Label Trunc to confirm once the hosting provider is chosen]',
        ),
        p('For how we handle personal data, see our [privacy notice](/privacy).'),
      ),
    ],
  },

  /* ------------------------------------------------------------------ Terms */
  {
    title: 'Terms & conditions',
    slug: 'terms',
    navLabel: 'Terms',
    intro: 'The terms that apply when you use this website and buy from Label Trunc.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc terms and conditions for using this website and buying online or in store: orders, holds, prices in RM, payment, authenticity, accounts and liability.',
    },
    layout: [
      text(
        '1. About these terms',
        p(
          'These terms apply to your use of this website and to anything you buy through it. By using it, you agree to them. Business details are on our [business details](/business-details) page.',
        ),
      ),
      text(
        '2. What this website is',
        p(
          'This website is the online shop of Label Trunc. It shows pieces we have in stock, with their condition and price. We work to keep descriptions and photographs accurate, but they may contain errors. If you think something is wrong, please tell us.',
        ),
      ),
      text(
        '3. Orders and holds',
        p(
          'When you add a piece to your cart, we hold it for you for up to 24 hours. The boutique sets the exact hold time. A hold is not a purchase. When it ends, the piece is released back to the shop and may be sold to someone else.',
        ),
        p(
          'If you choose to reserve and pay at the boutique, we hold the piece for the time stated when you reserve it. If you have not paid and collected it by then, the hold ends in the same way.',
        ),
        p(
          '[Label Trunc to confirm what happens if a payment arrives after a hold has ended, and whether a reserved piece may be released earlier]',
        ),
      ),
      text(
        '4. Prices and payment',
        p(
          'Prices are shown in Malaysian ringgit (RM). [Label Trunc to confirm whether prices include any tax]',
        ),
        p(
          'You can pay online through Billplz, using FPX online banking, debit or credit cards, Touch ’n Go eWallet and other e-wallets, or reserve a piece and pay at the boutique. Online payments are processed by Billplz, whose terms apply to the payment. [Label Trunc to confirm any fees and the payment methods accepted in store]',
        ),
      ),
      text(
        '5. When a contract is formed',
        p(
          'For online payments, a contract is formed when your payment is confirmed and we confirm your order. For reserved pieces, a contract is formed when you pay for the piece in store. If a payment fails or is not completed, no contract is formed and the hold ends when its time runs out.',
        ),
      ),
      text(
        '6. Collection and delivery',
        p(
          'Collection at the boutique is always available. Delivery is offered only when we have switched it on. [Label Trunc to confirm delivery areas, fees and timing]',
        ),
      ),
      text(
        '7. Authenticity',
        p(
          'We guarantee that every item we sell is authentic. What happens if an item is shown not to be authentic is set out in our [refund policy](/refunds).',
        ),
      ),
      text(
        '8. Brands, trademarks and copyright',
        p(
          'Label Trunc is an independent business and is not affiliated with, endorsed by or an authorised retailer of any designer house named on this website. Brand names, design names and logos belong to their owners and are used only to identify designs. Product photographs remain the copyright of their owners unless we say otherwise. The website text, layout and LT monogram belong to Label Trunc.',
        ),
      ),
      text(
        '9. Your account',
        p(
          'You can buy as a guest or create an account. Please give us accurate details, and keep your password secret. You are responsible for what happens under your account, so tell us at once if you think someone else has used it. We may close or restrict an account that misuses the website or breaks these terms.',
        ),
      ),
      text(
        '10. Consignment',
        p('Consignment is not arranged through this website. It follows the consignment agreement signed with each consignor.'),
      ),
      text(
        '11. Using this website',
        p('Please do not misuse the website, copy it for commercial use, or attempt to interfere with how it works.'),
      ),
      text(
        '12. Links',
        p('Links to other websites are provided for convenience. We are not responsible for their content.'),
      ),
      text(
        '13. Liability',
        p(
          'To the extent permitted by law, we provide this website without warranties about its availability or completeness. Nothing in these terms limits your rights under the Consumer Protection Act 1999, the Sale of Goods Act 1957 or any liability that cannot be limited by law.',
        ),
      ),
      text(
        '14. Governing law',
        p('These terms are governed by the laws of Malaysia, and the courts of Malaysia have jurisdiction.'),
      ),
      text(
        '15. Changes',
        p(
          'We may update these terms and will change the date above when we do. For how we handle personal data, see our [privacy notice](/privacy) and [cookie policy](/cookies).',
        ),
      ),
    ],
  },

  /* ---------------------------------------------------------------- Refunds */
  {
    title: 'Refunds & returns',
    slug: 'refunds',
    navLabel: 'Refunds',
    intro: 'What happens if a piece is not authentic, not as described, or if you change your mind.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc refunds and returns: our authenticity guarantee, items not as described, cancelling an unpaid order, how online payments are refunded and your rights.',
    },
    layout: [
      {
        blockType: 'notice',
        tone: 'pending',
        text: 'Draft for Label Trunc to confirm. Items in [square brackets] are proposed defaults and must be confirmed or changed by Label Trunc before this policy is relied on.',
      },
      text(
        '1. Authenticity guarantee',
        p(
          'Every item we sell is guaranteed authentic. If an item bought from us is shown not to be authentic, we will refund the full purchase price when the item is returned in the condition it was sold. [confirm how authenticity is assessed, for example an independent authentication service agreed by both sides, and any time limit for claims]',
        ),
      ),
      text(
        '2. Item not as described, or damaged in delivery',
        p(
          'Tell us within [48 hours] of delivery, with photos. If the item does not match what we agreed or was damaged in delivery, we will offer a refund or exchange and cover the return delivery cost.',
        ),
      ),
      text(
        '3. Change of mind',
        p(
          '[Label Trunc to confirm whether change-of-mind returns are accepted. If yes: time limit, condition required (unused, with all accessories and tags), and who pays return delivery. If no, state: “We do not accept returns for change of mind. Please view items in the boutique or ask for photos and video before buying.”]',
        ),
      ),
      text(
        '4. Pre-loved condition',
        p(
          'We describe the condition of pre-loved items before you buy. Wear that matches the agreed description is not a fault.',
        ),
      ),
      text(
        '5. Cancelling before you pay',
        p(
          'If you have not paid, you can cancel your order or simply let the hold run out. The hold is released at no cost to you, and the piece goes back on the shop.',
        ),
      ),
      text(
        '6. How refunds are paid',
        p('To the original payment method within [14 days] of receiving the returned item.'),
        p(
          'Online payments are refunded through Billplz to the method you paid with. How long the money takes to reach your account depends on your bank or e-wallet. [Label Trunc to confirm the time for online refunds to reach you]',
        ),
        p(
          'For pieces paid for in store, [Label Trunc to confirm how in-store payments are refunded]. [Label Trunc to confirm whether deposits, if any are taken, are refundable]',
        ),
      ),
      text(
        '7. Consignment',
        p('Payouts and the return of consigned items follow the consignment agreement signed with each consignor.'),
      ),
      text(
        '8. Your statutory rights',
        p('This policy does not affect your rights under the Consumer Protection Act 1999 or the Sale of Goods Act 1957.'),
        p(
          'To start a return, contact us on WhatsApp at +60 12-421 1205 or email labeltrunc@gmail.com, or use our [contact page](/contact).',
        ),
      ),
    ],
  },

  /* ---------------------------------------------------------- Accessibility */
  {
    title: 'Accessibility statement',
    slug: 'accessibility',
    navLabel: 'Accessibility',
    intro: 'We want everyone to be able to browse, shop and contact us easily.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc accessibility statement: we aim for WCAG 2.2 AA, with keyboard access, visible focus, a skip link and photo descriptions. Tell us about any problem.',
    },
    layout: [
      text(
        undefined,
        p(
          'We want everyone to be able to browse this website, buy from us and contact us easily. We aim to meet the Web Content Accessibility Guidelines (WCAG) 2.2 at level AA.',
        ),
      ),
      text(
        'What we have done',
        ul([
          'Text and essential controls meet WCAG AA colour contrast against their backgrounds.',
          'Every control can be reached and used with a keyboard, with a visible focus outline, and a “Skip to content” link at the top of the page.',
          'Every photograph needs a text description before it can be saved in our editor. Decorative images are hidden from screen readers.',
          'Buttons and form fields have clear labels, and form text is at least 16px so phones do not zoom unexpectedly.',
          'Animations are reduced when your device asks for reduced motion.',
          'The layout adapts from small phones to large screens without sideways scrolling.',
        ]),
      ),
      text(
        'Known limitations',
        ul([
          'The site has not yet been audited by an accessibility specialist or fully tested with screen readers such as VoiceOver and TalkBack.',
          'Online payment happens on Billplz’s own pages, which we do not control. If you cannot complete a payment there, you can reserve the piece and pay at the boutique, or contact us.',
        ]),
      ),
      text(
        'Tell us about a problem',
        p(
          'If anything on this website is hard to use, please tell us and we will help you another way. WhatsApp +60 12-421 1205 or email labeltrunc@gmail.com. We aim to reply within [2 working days].',
        ),
      ),
    ],
  },

  /* -------------------------------------------------------- Business details */
  {
    title: 'Business details',
    slug: 'business-details',
    navLabel: 'Business details',
    intro: 'Who we are and how to reach us.',
    lastUpdated: LAST_UPDATED,
    needsConfirmation: true,
    seo: {
      description:
        'Label Trunc business details: trading name, registered name and SSM number, boutique address in Ampang, Selangor, phone and WhatsApp, and email.',
    },
    layout: [
      text(
        undefined,
        h3('Trading name'),
        p('Label Trunc'),
        h3('Registered name'),
        p('[registered business or company name]'),
        h3('SSM registration'),
        p('[SSM registration number]'),
        h3('Address'),
        p('11-2A, Level 2, Ukay Boulevard, Jln Lingkaran Tengah 2, 68000 Ampang, Selangor, Malaysia'),
        h3('Phone and WhatsApp'),
        p('+60 12-421 1205'),
        h3('Email'),
        p('labeltrunc@gmail.com'),
        h3('Instagram'),
        p('@labeltrunc_luxury'),
      ),
    ],
  },
]
