import type { ResolvedLink } from '@/lib/links'

export type NavItem = ResolvedLink & { description?: string | null }
export type NavGroup = { id: string; label: string; summary?: string | null; overview: ResolvedLink | null; items: NavItem[] }
