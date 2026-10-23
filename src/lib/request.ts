/**
 * Client IP for rate limiting. Uses X-Real-IP if the reverse proxy sets it,
 * otherwise the last X-Forwarded-For entry (the one appended by the nearest
 * proxy). The first entry is client-controlled and must not be trusted.
 */
export const clientIp = (headers: Headers): string => {
  const realIp = headers.get('x-real-ip')?.trim()
  if (realIp) return realIp
  const forwarded = headers.get('x-forwarded-for')?.split(',').map((s) => s.trim()).filter(Boolean)
  return forwarded?.at(-1) || 'unknown'
}
