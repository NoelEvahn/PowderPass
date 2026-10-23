import type { DataTableBlock as Props } from '@/payload-types'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import styles from './DataTable.module.css'

export function DataTableBlock({ eyebrow, heading, intro, caption, columns, rows, note, anchorId, id }: Props) {
  const headingId = `table-${id}`
  const cols = columns ?? []
  return (
    <Section id={anchorId} labelledBy={heading ? headingId : undefined} spacing="tight">
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} />
        {/* The wrapper scrolls horizontally on narrow screens instead of the page. */}
        <div className={styles.scroller} role="region" aria-label={caption} tabIndex={0}>
          <table className={styles.table}>
            <caption className="visually-hidden">{caption}</caption>
            <thead>
              <tr>
                {cols.map((col, i) => (
                  <th key={col.id ?? i} scope="col">
                    {col.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(rows ?? []).map((row, r) => (
                <tr key={row.id ?? r}>
                  {cols.map((_, c) => {
                    const value = row.cells?.[c]?.value ?? ''
                    return c === 0 ? (
                      <th key={c} scope="row">
                        {value}
                      </th>
                    ) : (
                      <td key={c}>{value}</td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {note && <p className={styles.note}>{note}</p>}
      </div>
    </Section>
  )
}
