'use client'
import { RefreshRouteOnSave } from '@payloadcms/live-preview-react'
import { Eye } from 'lucide-react'
import { usePathname, useRouter } from 'next/navigation'
import styles from './LivePreviewListener.module.css'

/** In draft mode: refreshes the page whenever the editor autosaves, and shows a clear preview badge. */
export function LivePreviewListener() {
  const router = useRouter()
  const pathname = usePathname()
  return (
    <>
      <RefreshRouteOnSave refresh={() => router.refresh()} serverURL={process.env.NEXT_PUBLIC_SERVER_URL || ''} />
      <a className={styles.badge} href={`/next/exit-preview?path=${encodeURIComponent(pathname)}`}>
        <Eye size={16} aria-hidden="true" /> Previewing draft — exit preview
      </a>
    </>
  )
}
