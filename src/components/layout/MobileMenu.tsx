'use client'
import { ChevronDown, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ResolvedLink } from '@/lib/links'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { SmartLink } from '../ui/SmartLink'
import styles from './MobileMenu.module.css'
import type { NavGroup } from './nav-types'

type Props = {
  open: boolean
  onClose: () => void
  onSearch: () => void
  groups: NavGroup[]
  utility: ResolvedLink[]
  cta: ResolvedLink | null
}

export function MobileMenu({ open, onClose, onSearch, groups, utility, cta }: Props) {
  const [expanded, setExpanded] = useState<string | null>(null)

  // Start each visit to the menu with every group collapsed.
  useEffect(() => {
    if (!open) setExpanded(null)
  }, [open])

  return (
    <Dialog open={open} onClose={onClose} title="Menu" hideTitle variant="fullscreen" className={styles.dialog}>
      <nav aria-label="Main" className={styles.nav}>
        <button type="button" className={styles.search} onClick={onSearch}>
          <Search size={20} aria-hidden="true" />
          Search the website
        </button>
        <ul className={styles.groups}>
          {groups.map((group) => {
            const isOpen = expanded === group.id
            const panelId = `mobile-panel-${group.id}`
            return (
              <li key={group.id} className={styles.group}>
                <button
                  type="button"
                  className={styles.groupButton}
                  aria-expanded={isOpen}
                  aria-controls={panelId}
                  onClick={() => setExpanded(isOpen ? null : group.id)}
                >
                  {group.label}
                  <ChevronDown size={22} aria-hidden="true" className={styles.chevron} />
                </button>
                <div id={panelId} hidden={!isOpen} className={styles.panel}>
                  <ul>
                    {group.overview && (
                      <li>
                        <SmartLink href={group.overview.href} newTab={group.overview.newTab} className={styles.overview} onClick={onClose}>
                          {group.overview.label}
                        </SmartLink>
                      </li>
                    )}
                    {group.items.map((item) => (
                      <li key={item.href}>
                        <SmartLink href={item.href} newTab={item.newTab} className={styles.item} onClick={onClose}>
                          {item.label}
                        </SmartLink>
                      </li>
                    ))}
                  </ul>
                </div>
              </li>
            )
          })}
        </ul>
        {utility.length > 0 && (
          <ul className={styles.utility} aria-label="More links">
            {utility.map((link) => (
              <li key={link.href}>
                <SmartLink href={link.href} newTab={link.newTab} onClick={onClose}>
                  {link.label}
                </SmartLink>
              </li>
            ))}
          </ul>
        )}
      </nav>
      {cta && (
        <div className={styles.footer}>
          <Button href={cta.href} className={styles.cta}>
            {cta.label}
          </Button>
        </div>
      )}
    </Dialog>
  )
}
