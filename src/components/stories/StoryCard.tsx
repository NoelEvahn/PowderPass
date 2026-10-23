import Link from 'next/link'
import type { Story } from '@/payload-types'
import { STORY_CATEGORIES } from '@/collections/Stories'
import { formatDate } from '@/lib/format'
import { STORIES_BASE_PATH } from '@/lib/routes'
import { Media } from '../ui/Media'
import styles from './StoryCard.module.css'

const categoryLabel = (value: string) => STORY_CATEGORIES.find((c) => c.value === value)?.label ?? value

export function StoryCard({ story, headingLevel = 3, featured }: { story: Story; headingLevel?: 2 | 3; featured?: boolean }) {
  const Heading = `h${headingLevel}` as 'h2' | 'h3'
  return (
    <article className={styles.card} data-featured={featured || undefined}>
      <div className={styles.media}>
        <Media media={story.heroImage} maxSize="feature" sizes={featured ? '(min-width: 1024px) 60vw, 100vw' : '(min-width: 1024px) 30vw, 100vw'} className={styles.image} />
      </div>
      <div className={styles.body}>
        <p className={styles.meta}>
          <span className={styles.category}>{categoryLabel(story.category)}</span>
          <time dateTime={story.publishedAt}>{formatDate(story.publishedAt)}</time>
        </p>
        <Heading className={styles.title}>
          {/* The stretched link makes the whole card clickable without nesting controls. */}
          <Link href={`/${STORIES_BASE_PATH}/${story.slug}`} className={styles.link}>
            {story.title}
          </Link>
        </Heading>
        <p className={styles.excerpt}>{story.excerpt}</p>
      </div>
    </article>
  )
}
