import type { Page } from '@/payload-types'
import { AdmissionsInfoBlock } from './AdmissionsInfo/Component'
import { CampusesBlock } from './Campuses/Component'
import { CtaBandBlock } from './CtaBand/Component'
import { DataTableBlock } from './DataTable/Component'
import { DefinitionGridBlock } from './DefinitionGrid/Component'
import { EventsListBlock } from './EventsList/Component'
import { FactStripBlock } from './FactStrip/Component'
import { FaqsBlock } from './Faqs/Component'
import { FeatureMosaicBlock } from './FeatureMosaic/Component'
import { FormBlock } from './Form/Component'
import { GalleryBlock } from './Gallery/Component'
import { HeroBlock } from './Hero/Component'
import { KineticStatementBlock } from './KineticStatement/Component'
import { LatestStoriesBlock } from './LatestStories/Component'
import { LearningJourneyBlock } from './LearningJourney/Component'
import { LinkCardsBlock } from './LinkCards/Component'
import { ListColumnsBlock } from './ListColumns/Component'
import { MarqueeBlock } from './Marquee/Component'
import { NoticeBlock } from './Notice/Component'
import { PeopleBlock } from './People/Component'
import { ResourcesBlock } from './Resources/Component'
import { ReusableSectionBlock } from './ReusableSection/Component'
import { RichTextBlock } from './RichTextBlock/Component'
import { SplitFeatureBlock } from './SplitFeature/Component'
import { StepsBlock } from './Steps/Component'

type LayoutBlock = NonNullable<Page['layout']>[number]

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- each component receives its own block type
const components: Record<LayoutBlock['blockType'], React.ComponentType<any>> = {
  hero: HeroBlock,
  richText: RichTextBlock,
  splitFeature: SplitFeatureBlock,
  factStrip: FactStripBlock,
  learningJourney: LearningJourneyBlock,
  featureMosaic: FeatureMosaicBlock,
  kineticStatement: KineticStatementBlock,
  linkCards: LinkCardsBlock,
  definitionGrid: DefinitionGridBlock,
  listColumns: ListColumnsBlock,
  dataTable: DataTableBlock,
  steps: StepsBlock,
  gallery: GalleryBlock,
  people: PeopleBlock,
  resources: ResourcesBlock,
  faqs: FaqsBlock,
  latestStories: LatestStoriesBlock,
  eventsList: EventsListBlock,
  campuses: CampusesBlock,
  admissionsInfo: AdmissionsInfoBlock,
  formBlock: FormBlock,
  notice: NoticeBlock,
  ctaBand: CtaBandBlock,
  marquee: MarqueeBlock,
  reusableSection: ReusableSectionBlock,
}

/** Renders a page's sections in order. Unknown block types are skipped rather than breaking the page. */
export function RenderBlocks({ blocks, nested }: { blocks: LayoutBlock[]; nested?: boolean }) {
  return (
    <>
      {blocks.map((block, index) => {
        const Component = components[block.blockType]
        if (!Component) return null
        return <Component key={block.id ?? index} {...block} isFirst={!nested && index === 0} />
      })}
    </>
  )
}
