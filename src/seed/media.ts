import fs from 'fs/promises'
import path from 'path'
import type { Payload } from 'payload'
import sharp from 'sharp'
import { IMAGES, type ImageKey } from './images'

const CACHE_DIR = path.resolve(process.cwd(), '.seed-cache')

/** Clearly labelled stand-in used when an official image cannot be downloaded. */
async function placeholder(label: string): Promise<Buffer> {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1067" viewBox="0 0 1600 1067">
    <rect width="1600" height="1067" fill="#ebe7f2"/>
    <text x="800" y="510" text-anchor="middle" font-family="sans-serif" font-size="56" font-weight="700" fill="#211646">Image awaiting school approval</text>
    <text x="800" y="590" text-anchor="middle" font-family="sans-serif" font-size="34" fill="#5d5968">${label.replace(/[<&>]/g, '')}</text>
  </svg>`
  return sharp(Buffer.from(svg)).png().toBuffer()
}

async function download(url: string): Promise<Buffer | null> {
  const file = path.join(CACHE_DIR, path.basename(new URL(url).pathname))
  try {
    return await fs.readFile(file)
  } catch {
    // not cached yet
  }
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(60_000) })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const buffer = Buffer.from(await res.arrayBuffer())
    await fs.mkdir(CACHE_DIR, { recursive: true })
    await fs.writeFile(file, buffer)
    return buffer
  } catch (error) {
    console.warn(`  ! Could not download ${url}: ${(error as Error).message}`)
    return null
  }
}

/** Uploads every registered image once and returns a key → media id map. */
export async function seedMedia(payload: Payload): Promise<Record<ImageKey, number>> {
  const ids = {} as Record<ImageKey, number>
  for (const [key, image] of Object.entries(IMAGES) as [ImageKey, (typeof IMAGES)[ImageKey]][]) {
    const existing = await payload.find({ collection: 'media', where: { sourceUrl: { equals: image.url } }, limit: 1, depth: 0 })
    if (existing.docs[0]) {
      ids[key] = existing.docs[0].id
      continue
    }
    const downloaded = await download(image.url)
    const data = downloaded ?? (await placeholder((image as { caption?: string }).caption ?? key))
    const ext = downloaded ? path.extname(new URL(image.url).pathname) : '.png'
    const doc = await payload.create({
      collection: 'media',
      data: {
        alt: image.alt,
        caption: (image as { caption?: string }).caption,
        approval: 'pending',
        credit: 'The International School @ ParkCity',
        sourceUrl: image.url,
      },
      file: {
        data,
        name: `${key}${ext}`,
        mimetype: ext === '.jpg' ? 'image/jpeg' : 'image/png',
        size: data.length,
      },
    })
    ids[key] = doc.id
    console.log(`  ✓ ${key}${downloaded ? '' : ' (placeholder)'}`)
  }
  return ids
}
