import type { FormBlock as Props } from '@/payload-types'
import { RichText } from '@/components/RichText'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { intendedStartOptions } from '@/lib/enquiry/schema'
import { getSettings } from '@/lib/queries'
import { EnquiryForm } from './EnquiryForm'
import styles from './Form.module.css'

export async function FormBlock({ eyebrow, heading, intro, formType, aside, anchorId, id }: Props) {
  const headingId = `form-${id}`
  const settings = await getSettings()
  return (
    <Section id={anchorId ?? 'enquire'} labelledBy={headingId} tone="tint">
      <div className={`container ${styles.grid}`}>
        <div className={styles.aside}>
          <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} />
          <RichText data={aside} />
        </div>
        <EnquiryForm formType={formType} startOptions={intendedStartOptions()} fallbackEmail={settings.admissionsEmail || settings.generalEmail} />
      </div>
    </Section>
  )
}
