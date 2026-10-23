/** Best-effort client IP from proxy headers. Only trust these headers behind a proxy you control. */
export const clientIp = (headers: Headers): string =>
  headers.get('x-forwarded-for')?.split(',')[0]?.trim() || headers.get('x-real-ip') || 'unknown'
