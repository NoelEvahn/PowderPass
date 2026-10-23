import type { StepsBlock as Props } from '@/payload-types'
import { HorizontalChapter } from '@/components/motion/HorizontalChapter'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { resolveLink } from '@/lib/links'
import styles from './Steps.module.css'

type Step = NonNullable<Props['steps']>[number]

function StepBody({ step, index }: { step: Step; index: number }) {
  const link = resolveLink(step.link)
  return (
    <>
      <span className={styles.number} aria-hidden="true">
        {index + 1}
      </span>
      <div className={styles.body}>
        <h3 className={styles.title}>
          <span className="visually-hidden">Step {index + 1}: </span>
          {step.title}
        </h3>
        <p className={styles.text}>{step.body}</p>
        {link && (
          <ArrowLink href={link.href} external={link.external} newTab={link.newTab}>
            {link.label}
          </ArrowLink>
        )}
      </div>
    </>
  )
}

export function StepsBlock({ eyebrow, heading, intro, layout, steps, anchorId, id }: Props) {
  const headingId = `steps-${id}`
  if (layout === 'horizontal') {
    return (
      <Section id={anchorId} labelledBy={headingId} spacing="none">
        <HorizontalChapter
          as="ol"
          headingId={headingId}
          skipLabel="Skip the steps"
          header={<SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />}
        >
          {(steps ?? []).map((step, index) => (
            <div key={step.id ?? index} className={`${styles.step} ${styles.card}`}>
              <StepBody step={step} index={index} />
            </div>
          ))}
        </HorizontalChapter>
      </Section>
    )
  }
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <ol className={styles.steps}>
          {(steps ?? []).map((step, index) => (
            <li key={step.id ?? index} className={styles.step} data-reveal style={{ '--reveal-index': index } as React.CSSProperties}>
              <StepBody step={step} index={index} />
            </li>
          ))}
        </ol>
      </div>
    </Section>
  )
}
