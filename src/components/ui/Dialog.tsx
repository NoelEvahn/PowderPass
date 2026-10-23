'use client'
import { X } from 'lucide-react'
import { type ReactNode, useEffect, useRef } from 'react'
import styles from './Dialog.module.css'

type Props = {
  open: boolean
  onClose: () => void
  /** Accessible name. Shown as a heading unless `hideTitle`. */
  title: string
  hideTitle?: boolean
  children: ReactNode
  variant?: 'panel' | 'sheet' | 'fullscreen' | 'media'
  className?: string
  initialFocus?: React.RefObject<HTMLElement | null>
}

/**
 * Modal dialog on the native <dialog> element: the browser makes the rest of
 * the page inert, contains focus and handles Escape. We restore focus to the
 * element that opened it and lock page scroll while open.
 */
export function Dialog({ open, onClose, title, hideTitle, children, variant = 'panel', className, initialFocus }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const opener = useRef<Element | null>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      opener.current = document.activeElement
      dialog.showModal()
      document.documentElement.dataset.dialogOpen = ''
      initialFocus?.current?.focus()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open, initialFocus])

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    const handleClose = () => {
      delete document.documentElement.dataset.dialogOpen
      if (opener.current instanceof HTMLElement && opener.current.isConnected) opener.current.focus()
      onCloseRef.current()
    }
    const handleCancel = (event: Event) => {
      event.preventDefault()
      dialog.close()
    }
    dialog.addEventListener('close', handleClose)
    dialog.addEventListener('cancel', handleCancel)
    return () => {
      dialog.removeEventListener('close', handleClose)
      dialog.removeEventListener('cancel', handleCancel)
      if (dialog.open) {
        dialog.close()
        delete document.documentElement.dataset.dialogOpen
      }
    }
  }, [])

  const titleId = `dialog-title-${title.replace(/\W+/g, '-').toLowerCase()}`

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      className={[styles.dialog, styles[variant], className].filter(Boolean).join(' ')}
      onClick={(event) => {
        // Click on the backdrop (the dialog element itself, outside the inner box) closes.
        if (event.target === ref.current) ref.current?.close()
      }}
    >
      <div className={styles.inner}>
        <div className={styles.head}>
          <h2 id={titleId} className={hideTitle ? 'visually-hidden' : styles.title}>
            {title}
          </h2>
          <button type="button" className={styles.close} onClick={() => ref.current?.close()} aria-label={`Close ${title.toLowerCase()}`}>
            <X size={22} strokeWidth={2} aria-hidden="true" />
          </button>
        </div>
        {open && children}
      </div>
    </dialog>
  )
}
