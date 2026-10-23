import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { LivePreviewListener } from '@/components/LivePreviewListener'
import { RichText } from '@/components/RichText'
import { Breadcrumbs } from '@/components/layout/Breadcrumbs'
import { StoryCard } from '@/components/stories/StoryCard'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Media } from '@/components/ui/Media'
import { STORY_CATEGORIES } from '@/collections/Stories'
import { formatDate, readingMinutes } from '@/lib/format'
import { getBreadcrumbs, getStories, getStory, isPreview } from '@/lib/queries'
import { STORIES_BASE_PATH } from '@/lib/routes'
import { buildMetadata, jsonLdString, siteUrl } from '@/lib/seo'
import styles from './story.module.css'

type Props = { params: Promise<{ slug: string }> }

export const revalidate = 3600

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const story = await getStory((await params).slug)
  if (!story) return { title: 'Story not found' }
  return buildMetadata({
    title: story.meta?.title || story.title,
    description: story.meta?.description || story.excerpt,
    path: `/${STORIES_BASE_PATH}/${story.slug}`,
    image: story.meta?.image || story.heroImage,
    noIndex: story.meta?.noIndex,
    type: 'article',
    publishedTime: story.publishedAt,
  })
}

export default async function StoryPage({ params }: Props) {
  const { slug } = await params
  const [story, preview] = await Promise.all([getStory(slug), isPreview()])
  if (!story) notFound()

  const crumbs = await getBreadcrumbs(`${STORIES_BASE_PATH}/${story.slug}`)
  const related = (await getStories({ limit: 4 })).filter((s) => s.id !== story.id).slice(0, 3)
  const category = STORY_CATEGORIES.find((c) => c.value === story.category)?.label ?? story.category
  const minutes = readingMinutes(story.searchText ?? story.excerpt)
  const image = typeof story.heroImage === 'object' ? story.heroImage : null

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'NewsArticle',
    headline: story.title,
    datePublished: story.publishedAt,
    dateModified: story.updatedAt,
    description: story.excerpt,
    ...(image?.url ? { image: [`${siteUrl()}${image.sizes?.og?.url ?? image.url}`] } : {}),
    publisher: { '@type': 'School', name: 'The International School @ ParkCity', logo: `${siteUrl()}/brand/isp-logo.svg` },
    mainEntityOfPage: `${siteUrl()}/${STORIES_BASE_PATH}/${story.slug}`,
  }

  return (
    <>
      {preview && <LivePreviewListener />}
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLdString(jsonLd) }} />
      <Breadcrumbs items={crumbs} current={story.title} />
      <article className={styles.article} aria-labelledby="page-title">
        <header className={`container ${styles.header}`}>
          <p className={styles.meta}>
            <span className={styles.category}>{category}</span>
            <time dateTime={story.publishedAt}>{formatDate(story.publishedAt)}</time>
            <span>{minutes} min read</span>
          </p>
          <h1 id="page-title" className={styles.title}>
            {story.title}
          </h1>
          <p className="lead">{story.excerpt}</p>
        </header>
        {image && (
          <figure className={`container ${styles.figure}`}>
            <Media media={image} priority maxSize="hero" sizes="(min-width: 1440px) 1312px, 100vw" className={styles.image} />
            {image.caption && <figcaption className={styles.caption}>{image.caption}</figcaption>}
          </figure>
        )}
        <div className={`container ${styles.body}`}>
          <RichText data={story.content} />
          {story.authorRole && <p className={styles.author}>{story.authorRole}</p>}
          <ArrowLink href={`/${STORIES_BASE_PATH}`}>Back to all news and stories</ArrowLink>
        </div>
      </article>

      {related.length > 0 && (
        <section className={styles.related} aria-labelledby="related-title">
          <div className="container">
            <h2 id="related-title" className={styles.relatedTitle}>
              More from ISP
            </h2>
            <ul className={styles.relatedGrid}>
              {related.map((item) => (
                <li key={item.id}>
                  <StoryCard story={item} />
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}
    </>
  )
}
