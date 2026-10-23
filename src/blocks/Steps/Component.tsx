import type { StepsBlock as Props } from '@/payload-types'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { resolveLink } from '@/lib/links'
import styles from './Steps.module.css'

export function StepsBlock({ eyebrow, heading, intro, steps, anchorId, id }: Props) {
  const headingId = `steps-${id}`
  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <ol className={styles.steps}>
          {(steps ?? []).map((step, index) => {
            const link = resolveLink(step.link)
            return (
              <li key={step.id ?? index} className={styles.step} data-reveal style={{ '--reveal-index': index } as React.CSSProperties}>
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
              </li>
            )
          })}
        </ol>
      </div>
    </Section>
  )
}
