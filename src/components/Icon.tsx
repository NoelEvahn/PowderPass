import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Camera,
  Check,
  Clock,
  CreditCard,
  ExternalLink,
  Info,
  Lock,
  Mail,
  MapPin,
  Menu,
  MessageCircle,
  MessageSquare,
  Phone,
  Play,
  Search,
  ShieldCheck,
  ShoppingBag,
  SlidersHorizontal,
  Store,
  Trash,
  TriangleAlert,
  Truck,
  User,
  Users,
  Video,
  X,
  type LucideIcon,
} from 'lucide-react'
import React from 'react'

/**
 * Site icons from Lucide (lucide.dev), imported one by one so only these ship.
 * Lucide has no brand logos, so social links use the closest generic icon; every
 * social link also shows its handle and names the platform for screen readers.
 */
const icons = {
  arrow: ArrowRight,
  'arrow-down': ArrowDown,
  'arrow-left': ArrowLeft,
  bag: ShoppingBag,
  user: User,
  menu: Menu,
  close: X,
  phone: Phone,
  mail: Mail,
  map: MapPin,
  clock: Clock,
  shield: ShieldCheck,
  check: Check,
  truck: Truck,
  store: Store,
  card: CreditCard,
  chat: MessageSquare,
  search: Search,
  info: Info,
  alert: TriangleAlert,
  external: ExternalLink,
  lock: Lock,
  filter: SlidersHorizontal,
  trash: Trash,
  // Social platforms (generic stand-ins; Lucide does not include brand marks)
  whatsapp: MessageCircle,
  instagram: Camera,
  tiktok: Video,
  facebook: Users,
  youtube: Play,
  xiaohongshu: BookOpen,
} satisfies Record<string, LucideIcon>

export type IconName = keyof typeof icons

export const Icon = ({
  name,
  size = 20,
  className,
  title,
}: {
  name: IconName | string
  size?: number
  className?: string
  title?: string
}) => {
  const Glyph = icons[name as IconName] ?? Info
  return (
    <Glyph
      size={size}
      strokeWidth={1.75}
      className={[name === 'arrow' ? 'icon-arrow' : '', className].filter(Boolean).join(' ') || undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      role={title ? 'img' : undefined}
      focusable="false"
    />
  )
}
