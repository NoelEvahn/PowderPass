import { CheckCircle2, FileText } from 'lucide-react'
import type { AdmissionsInfoBlock as Props } from '@/payload-types'
import { Button } from '@/components/ui/Button'
import { ArrowLink } from '@/components/ui/ArrowLink'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { formatDate } from '@/lib/format'
import { getAdmissions } from '@/lib/queries'
import styles from './AdmissionsInfo.module.css'

/** Renders fees or application details from the single Admissions global so they are never out of sync. */
export async function AdmissionsInfoBlock({ show, eyebrow, heading, intro, anchorId, id }: Props) {
  const headingId = `admissions-${id}`
  const info = await getAdmissions()

  return (
    <Section id={anchorId} labelledBy={headingId} tone="tint">
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />

        {show === 'fees' ? (
          <div className={styles.grid}>
            <div className={styles.card}>
              <p className={styles.kicker}>Academic year {info.academicYear}</p>
              <h3 className={styles.title}>Fee schedule {info.academicYear}</h3>
              {info.feeTable && info.feeTable.length > 0 ? (
                <table className={styles.table}>
                  <caption className="visually-hidden">Fees for {info.academicYear}</caption>
                  <tbody>
                    {info.feeTable.map((row) => (
                      <tr key={row.id ?? row.item}>
                        <th scope="row">{row.item}</th>
                        <td>
                          {row.amount}
                          {row.note && <span className={styles.rowNote}>{row.note}</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p>
                  Tuition, registration and other fees for {info.academicYear} are set out in the school’s official fee schedule, which
                  also explains what is included and how payments are made.
                </p>
              )}
              <Button href={info.feeScheduleUrl} newTab icon={<FileText size={20} />}>
                View the {info.academicYear} fee schedule
              </Button>
              {info.feesNote && <p className={styles.small}>{info.feesNote}</p>}
              {info.reviewedAt && (
                <p className={styles.small}>
                  Last reviewed {formatDate(info.reviewedAt)}
                  {info.owner && ` by ${info.owner}`}
                </p>
              )}
            </div>
            {info.waiver && (
              <div className={`${styles.card} ${styles.accent}`}>
                <p className={styles.kicker}>Desa ParkCity families</p>
                <h3 className={styles.title}>25% registration fee waiver</h3>
                <p className={styles.preline}>{info.waiver}</p>
                <p className={styles.small}>This waiver applies to the registration fee only. It is not a tuition discount.</p>
              </div>
            )}
          </div>
        ) : (
          <div className={styles.grid}>
            <div className={styles.card}>
              <h3 className={styles.title}>Begin the official application</h3>
              {info.applicationNote && <p className={styles.preline}>{info.applicationNote}</p>}
              <Button href={info.applicationUrl} newTab>
                Start the online application
              </Button>
              {info.termsUrl && (
                <ArrowLink href={info.termsUrl} external newTab>
                  Terms & conditions of enrolment
                </ArrowLink>
              )}
            </div>
            {info.requiredDocuments && info.requiredDocuments.length > 0 && (
              <div className={styles.card}>
                <h3 className={styles.title}>What you will be asked to complete</h3>
                <ul className={styles.checklist}>
                  {info.requiredDocuments.map((doc) => (
                    <li key={doc.id ?? doc.item}>
                      <CheckCircle2 size={20} aria-hidden="true" />
                      {doc.item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </Section>
  )
}
