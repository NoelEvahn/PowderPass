import type { LatestStoriesBlock as Props } from '@/payload-types'
import { StoryCard } from '@/components/stories/StoryCard'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { getStories } from '@/lib/queries'
import { STORIES_BASE_PATH } from '@/lib/routes'
import styles from './LatestStories.module.css'

export async function LatestStoriesBlock({ eyebrow, heading, intro, category, limit, showAllLink, anchorId, id }: Props) {
  const headingId = `stories-${id}`
  const stories = await getStories({ category: category ?? 'all', limit: limit ?? 3 })
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split">
          {showAllLink && <ArrowLink href={`/${STORIES_BASE_PATH}`}>All news and stories</ArrowLink>}
        </SectionHeader>
        {stories.length === 0 ? (
          <p className={styles.empty}>No stories have been published yet.</p>
        ) : (
          <ul className={styles.grid} data-count={stories.length}>
            {stories.map((story, index) => (
              <li key={story.id} data-reveal style={{ '--reveal-index': index % 3 } as React.CSSProperties}>
                <StoryCard story={story} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Section>
  )
}
