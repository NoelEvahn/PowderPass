import { SCHOOL_TIMEZONE } from './routes'

const dateFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: SCHOOL_TIMEZONE })
const weekdayFmt = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: SCHOOL_TIMEZONE })
const timeFmt = new Intl.DateTimeFormat('en-GB', { hour: 'numeric', minute: '2-digit', hour12: true, timeZone: SCHOOL_TIMEZONE })

const dayFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', timeZone: SCHOOL_TIMEZONE })
const monthFmt = new Intl.DateTimeFormat('en-GB', { month: 'short', timeZone: SCHOOL_TIMEZONE })

export const formatDay = (iso: string) => dayFmt.format(new Date(iso))
export const formatMonthShort = (iso: string) => monthFmt.format(new Date(iso))
export const formatDate = (iso: string) => dateFmt.format(new Date(iso))
export const formatWeekday = (iso: string) => weekdayFmt.format(new Date(iso))
export const formatTime = (iso: string) => timeFmt.format(new Date(iso)).replace(' ', '').toLowerCase()
export const formatTimeRange = (start: string, end: string) => `${formatTime(start)} – ${formatTime(end)} MYT`

/** Approximate reading time for a block of text. */
export const readingMinutes = (text: string) => Math.max(1, Math.round(text.split(/\s+/).filter(Boolean).length / 220))
