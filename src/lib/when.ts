/** Date and time wording for customers, always in Malaysia time (UTC+8, no daylight saving). */

const TZ = 'Asia/Kuala_Lumpur'

const parts = (date: Date, options: Intl.DateTimeFormatOptions) =>
  Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', { timeZone: TZ, ...options }).formatToParts(date).map((p) => [p.type, p.value]),
  ) as Record<string, string>

/** "Tuesday 27 October, 3:45pm" */
export const formatWhen = (value: string | Date | null | undefined): string => {
  if (!value) return ''
  const date = typeof value === 'string' ? new Date(value) : value
  if (Number.isNaN(date.getTime())) return ''
  const p = parts(date, { weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit', hour12: true })
  return `${p.weekday} ${p.day} ${p.month}, ${p.hour}:${p.minute}${(p.dayPeriod ?? '').toLowerCase()}`
}

/** "27 October 2026" */
export const formatDay = (value: string | Date | null | undefined): string => {
  if (!value) return ''
  const date = typeof value === 'string' ? new Date(value) : value
  if (Number.isNaN(date.getTime())) return ''
  const p = parts(date, { day: 'numeric', month: 'long', year: 'numeric' })
  return `${p.day} ${p.month} ${p.year}`
}

/** "23h 10m", "45m", or "" when the time has passed. */
export const formatRemaining = (value: string, now = Date.now()): string => {
  const ms = new Date(value).getTime() - now
  if (!Number.isFinite(ms) || ms <= 0) return ''
  const minutes = Math.max(1, Math.floor(ms / 60_000))
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h >= 48) return `${Math.floor(h / 24)} days`
  return h ? `${h}h ${m}m` : `${m}m`
}
