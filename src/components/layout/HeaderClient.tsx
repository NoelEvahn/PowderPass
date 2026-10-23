'use client'
import { ChevronDown, Menu, Search } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type { ResolvedLink } from '@/lib/links'
import { ArrowLink } from '../ui/ArrowLink'
import { Button } from '../ui/Button'
import { SmartLink } from '../ui/SmartLink'
import styles from './Header.module.css'
import { MobileMenu } from './MobileMenu'
import type { NavGroup } from './nav-types'
import { SearchDialog } from './SearchDialog'

type Props = { groups: NavGroup[]; utility: ResolvedLink[]; cta: ResolvedLink | null; schoolName: string }

const COMPACT_AFTER = 80

const isActive = (pathname: string, group: NavGroup) =>
  [group.overview, ...group.items].some((link) => link && !link.external && link.href !== '/' && pathname.startsWith(link.href))

export function HeaderClient({ groups, utility, cta, schoolName }: Props) {
  const pathname = usePathname()
  const [openId, setOpenId] = useState<string | null>(null)
  const [compact, setCompact] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const navRef = useRef<HTMLDivElement>(null)
  const baseId = useId()

  // Close panels on navigation (adjusting state during render, as React recommends).
  const [lastPath, setLastPath] = useState(pathname)
  if (pathname !== lastPath) {
    setLastPath(pathname)
    setOpenId(null)
    setMenuOpen(false)
  }

  useEffect(() => {
    const onScroll = () => setCompact(window.scrollY > COMPACT_AFTER)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // Escape and outside-click close an open menu panel; focus returns to its button.
  useEffect(() => {
    if (!openId) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        document.getElementById(`${baseId}-btn-${openId}`)?.focus()
        setOpenId(null)
      }
    }
    const onPointer = (event: PointerEvent) => {
      if (navRef.current && !navRef.current.contains(event.target as Node)) setOpenId(null)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [openId, baseId])

  // "/" or Ctrl/Cmd+K opens search, unless the visitor is typing in a field.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement
      const typing = target.closest('input, textarea, select, [contenteditable="true"]')
      if ((event.key === '/' && !typing) || ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k')) {
        event.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  const toggle = useCallback((id: string) => setOpenId((current) => (current === id ? null : id)), [])
  const openGroup = groups.find((g) => g.id === openId)

  return (
    <>
      <header className={styles.header} data-compact={compact || undefined} data-panel-open={openId ? '' : undefined}>
        <div className={styles.utility}>
          <div className={`container ${styles.utilityInner}`}>
            <ul className={styles.utilityList}>
              {utility.map((link) => (
                <li key={link.href}>
                  <SmartLink href={link.href} newTab={link.newTab} className={styles.utilityLink}>
                    {link.label}
                  </SmartLink>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className={styles.barWrap} ref={navRef}>
          <div className={`container ${styles.bar}`}>
            <Link href="/" className={styles.brand} aria-label={`${schoolName}, home`}>
              {/* eslint-disable-next-line @next/next/no-img-element -- official SVG lockup, kept intact */}
              <img src="/brand/isp-logo.svg" alt="" width={300} height={58} className={styles.logo} />
            </Link>

            <nav aria-label="Main" className={styles.nav}>
              <ul className={styles.navList}>
                {groups.map((group) => {
                  const expanded = openId === group.id
                  return (
                    <li key={group.id}>
                      <button
                        type="button"
                        id={`${baseId}-btn-${group.id}`}
                        className={styles.navButton}
                        aria-expanded={expanded}
                        aria-controls={`${baseId}-panel-${group.id}`}
                        data-active={isActive(pathname, group) || undefined}
                        onClick={() => toggle(group.id)}
                      >
                        {group.label}
                        <ChevronDown size={16} strokeWidth={2.25} aria-hidden="true" className={styles.chevron} />
                      </button>
                    </li>
                  )
                })}
              </ul>
            </nav>

            <div className={styles.actions}>
              <button type="button" className={styles.iconButton} onClick={() => setSearchOpen(true)} aria-label="Search the website">
                <Search size={20} strokeWidth={2} aria-hidden="true" />
              </button>
              {cta && (
                <Button href={cta.href} className={styles.cta}>
                  {cta.label}
                </Button>
              )}
              <button
                type="button"
                className={`${styles.iconButton} ${styles.menuButton}`}
                onClick={() => setMenuOpen(true)}
                aria-haspopup="dialog"
              >
                <Menu size={22} strokeWidth={2} aria-hidden="true" />
                <span>Menu</span>
              </button>
            </div>
          </div>

          {groups.map((group) => (
            <div
              key={group.id}
              id={`${baseId}-panel-${group.id}`}
              className={styles.panel}
              hidden={openId !== group.id}
              role="region"
              aria-labelledby={`${baseId}-btn-${group.id}`}
            >
              <div className={`container ${styles.panelInner}`}>
                <div className={styles.panelIntro}>
                  <p className={styles.panelTitle}>{group.label}</p>
                  {group.summary && <p className={styles.panelSummary}>{group.summary}</p>}
                  {group.overview && (
                    <ArrowLink href={group.overview.href} external={group.overview.external} newTab={group.overview.newTab}>
                      {group.overview.label}
                    </ArrowLink>
                  )}
                </div>
                <ul className={styles.panelList}>
                  {group.items.map((item) => (
                    <li key={item.href}>
                      <SmartLink
                        href={item.href}
                        newTab={item.newTab}
                        className={styles.panelLink}
                        aria-current={pathname === item.href ? 'page' : undefined}
                      >
                        <span className={styles.panelLinkLabel}>{item.label}</span>
                        {item.description && <span className={styles.panelLinkText}>{item.description}</span>}
                      </SmartLink>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </header>
      {openGroup && <div className={styles.scrim} aria-hidden="true" onClick={() => setOpenId(null)} />}

      <MobileMenu open={menuOpen} onClose={() => setMenuOpen(false)} groups={groups} utility={utility} cta={cta} onSearch={() => { setMenuOpen(false); setSearchOpen(true) }} />
      <SearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  )
}
