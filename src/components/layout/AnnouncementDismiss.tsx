'use client'
import { X } from 'lucide-react'
import { useEffect } from 'react'

/** Hides the announcement for the rest of the browsing session. Storage failures simply keep it visible. */
export function DismissButton({ storageKey, className }: { storageKey: string; className?: string }) {
  useEffect(() => {
    try {
      if (sessionStorage.getItem(storageKey)) document.querySelector<HTMLElement>(`[data-announcement="${CSS.escape(storageKey)}"]`)?.setAttribute('hidden', '')
    } catch {
      // storage unavailable
    }
  }, [storageKey])

  return (
    <button
      type="button"
      className={className}
      aria-label="Dismiss announcement"
      onClick={(event) => {
        try {
          sessionStorage.setItem(storageKey, '1')
        } catch {
          // storage unavailable; hide for this page view only
        }
        event.currentTarget.closest('aside')?.setAttribute('hidden', '')
      }}
    >
      <X size={18} aria-hidden="true" />
    </button>
  )
}
