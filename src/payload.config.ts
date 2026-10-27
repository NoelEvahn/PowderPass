import { postgresAdapter } from '@payloadcms/db-postgres'
import { nodemailerAdapter } from '@payloadcms/email-nodemailer'
import { lexicalEditor } from '@payloadcms/richtext-lexical'
import { s3Storage } from '@payloadcms/storage-s3'
import path from 'path'
import { buildConfig, type Plugin } from 'payload'
import sharp from 'sharp'
import { fileURLToPath } from 'url'

import { Brands } from './collections/Brands'
import { Categories } from './collections/Categories'
import { Customers } from './collections/Customers'
import { Enquiries } from './collections/Enquiries'
import { Media } from './collections/Media'
import { Orders } from './collections/Orders'
import { Pages } from './collections/Pages'
import { Products } from './collections/Products'
import { Reservations } from './collections/Reservations'
import { Users } from './collections/Users'
import { HomePage } from './globals/HomePage'
import { ShopSettings } from './globals/ShopSettings'
import { SiteSettings } from './globals/SiteSettings'
import { getServerURL } from './lib/env'
import { migrations } from './migrations'

const filename = fileURLToPath(import.meta.url)
const dirname = path.dirname(filename)

if (process.env.NODE_ENV === 'production' && !process.env.PAYLOAD_SECRET && process.env.NEXT_PHASE !== 'phase-production-build') {
  throw new Error('PAYLOAD_SECRET must be set in production.')
}

const plugins: Plugin[] = []

// Images go to S3-compatible storage (AWS S3, Cloudflare R2, MinIO…) when configured,
// otherwise to the local `media` folder.
if (process.env.S3_BUCKET) {
  plugins.push(
    s3Storage({
      collections: { media: true },
      bucket: process.env.S3_BUCKET,
      config: {
        region: process.env.S3_REGION || 'auto',
        endpoint: process.env.S3_ENDPOINT || undefined,
        forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
        credentials: {
          accessKeyId: process.env.S3_ACCESS_KEY_ID || '',
          secretAccessKey: process.env.S3_SECRET_ACCESS_KEY || '',
        },
      },
    }),
  )
}

const serverURL = getServerURL()

export default buildConfig({
  serverURL,
  admin: {
    user: Users.slug,
    // Built-in avatar: no Gravatar request (keeps the content security policy tight).
    avatar: 'default',
    importMap: { baseDir: path.resolve(dirname) },
    meta: {
      titleSuffix: ' · Label Trunc editor',
      icons: [{ rel: 'icon', type: 'image/svg+xml', url: '/labeltrunc-icon.svg' }],
    },
    components: {
      graphics: {
        Logo: '@/components/admin/Logo#Logo',
        Icon: '@/components/admin/Logo#Icon',
      },
      beforeDashboard: ['@/components/admin/Welcome#Welcome'],
    },
  },
  collections: [Products, Brands, Categories, Media, Orders, Reservations, Enquiries, Customers, Pages, Users],
  globals: [HomePage, SiteSettings, ShopSettings],
  editor: lexicalEditor(),
  secret: process.env.PAYLOAD_SECRET || 'build-time-placeholder-secret-not-for-use',
  cors: [serverURL],
  csrf: [serverURL],
  typescript: { outputFile: path.resolve(dirname, 'payload-types.ts') },
  graphQL: { disable: true },
  db: postgresAdapter({
    pool: { connectionString: process.env.DATABASE_URL || '' },
    migrationDir: path.resolve(dirname, 'migrations'),
    push: process.env.PAYLOAD_DB_PUSH === 'true',
    // Applied automatically when the production server starts.
    prodMigrations: migrations,
  }),
  email: process.env.SMTP_HOST
    ? nodemailerAdapter({
        defaultFromAddress: process.env.EMAIL_FROM_ADDRESS || 'no-reply@localhost',
        defaultFromName: process.env.EMAIL_FROM_NAME || 'Label Trunc',
        transportOptions: {
          host: process.env.SMTP_HOST,
          port: Number(process.env.SMTP_PORT || 587),
          secure: process.env.SMTP_SECURE === 'true',
          auth: process.env.SMTP_USER ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS } : undefined,
        },
      })
    : undefined,
  sharp,
  plugins,
  telemetry: false,
})
