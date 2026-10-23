import type { Block } from 'payload'
import { AdmissionsInfoBlock } from './AdmissionsInfo/config'
import { CampusesBlock } from './Campuses/config'
import { CtaBandBlock } from './CtaBand/config'
import { DataTableBlock } from './DataTable/config'
import { DefinitionGridBlock } from './DefinitionGrid/config'
import { EventsListBlock } from './EventsList/config'
import { FactStripBlock } from './FactStrip/config'
import { FaqsBlock } from './Faqs/config'
import { FeatureMosaicBlock } from './FeatureMosaic/config'
import { FormBlock } from './Form/config'
import { GalleryBlock } from './Gallery/config'
import { HeroBlock } from './Hero/config'
import { KineticStatementBlock } from './KineticStatement/config'
import { LatestStoriesBlock } from './LatestStories/config'
import { LearningJourneyBlock } from './LearningJourney/config'
import { LinkCardsBlock } from './LinkCards/config'
import { ListColumnsBlock } from './ListColumns/config'
import { MarqueeBlock } from './Marquee/config'
import { NoticeBlock } from './Notice/config'
import { PeopleBlock } from './People/config'
import { ResourcesBlock } from './Resources/config'
import { ReusableSectionBlock } from './ReusableSection/config'
import { RichTextBlockConfig } from './RichTextBlock/config'
import { SplitFeatureBlock } from './SplitFeature/config'
import { StepsBlock } from './Steps/config'

/**
 * Deep-copies plain config objects while keeping functions (conditions, hooks,
 * validators) by reference. Payload sanitises block configs in place, so each
 * collection must receive its own copy rather than sharing one object.
 */
const cloneConfig = <T,>(value: T): T => {
  if (Array.isArray(value)) return value.map(cloneConfig) as T
  if (value && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype) {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, cloneConfig(v)])) as T
  }
  return value
}

/** Every section type, excluding the reusable reference (prevents recursion inside reusable sections). */
const baseContentBlocks: Block[] = [
  HeroBlock,
  RichTextBlockConfig,
  SplitFeatureBlock,
  FactStripBlock,
  LearningJourneyBlock,
  FeatureMosaicBlock,
  KineticStatementBlock,
  LinkCardsBlock,
  DefinitionGridBlock,
  ListColumnsBlock,
  DataTableBlock,
  StepsBlock,
  GalleryBlock,
  PeopleBlock,
  ResourcesBlock,
  FaqsBlock,
  LatestStoriesBlock,
  EventsListBlock,
  CampusesBlock,
  AdmissionsInfoBlock,
  FormBlock,
  NoticeBlock,
  CtaBandBlock,
  MarqueeBlock,
]

/** Section types available on pages, including the reusable-section reference. */
export const pageBlocks = (): Block[] => cloneConfig([...baseContentBlocks, ReusableSectionBlock])

/** Section types available inside a reusable section. */
export const contentBlocks = (): Block[] => cloneConfig(baseContentBlocks)
