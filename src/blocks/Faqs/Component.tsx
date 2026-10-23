import type { Faq, FaqsBlock as Props } from '@/payload-types'
import { RichText } from '@/components/RichText'
import { Section } from '@/components/ui/Section'
import { SectionHeader } from '@/components/ui/SectionHeader'
import { getFaqs } from '@/lib/queries'
import { FaqList } from './FaqList'

export async function FaqsBlock({ eyebrow, heading, intro, source, categories, selected, searchable, anchorId, id }: Props) {
  const headingId = `faqs-${id}`
  const faqs: Faq[] =
    source === 'selected'
      ? (selected ?? []).filter((f): f is Faq => typeof f === 'object' && f !== null && f._status === 'published')
      : await getFaqs(categories)

  return (
    <Section id={anchorId} labelledBy={headingId}>
      <div className="container">
        <SectionHeader id={headingId} eyebrow={eyebrow} heading={heading} intro={intro} align="split" />
        <FaqList
          searchable={Boolean(searchable)}
          items={faqs.map((faq) => ({
            id: String(faq.id),
            question: faq.question,
            searchText: `${faq.question} ${faq.searchText ?? ''}`.toLowerCase(),
            answer: <RichText data={faq.answer} />,
          }))}
        />
      </div>
    </Section>
  )
}
