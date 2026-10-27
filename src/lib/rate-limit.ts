/**
 * Small in-memory sliding-window limiter for form submissions. It protects a
 * single server instance; behind several instances each keeps its own window,
 * which still bounds abuse per instance.
 */
const buckets = new Map<string, number[]>()
let lastSweep = Date.now()

export const rateLimit = (key: string, limit: number, windowMs: number): { ok: boolean; retryAfterMs: number } => {
  const now = Date.now()
  if (now - lastSweep > 60_000) {
    for (const [k, hits] of buckets) {
      if (!hits.length || now - hits[hits.length - 1] > windowMs * 4) buckets.delete(k)
    }
    lastSweep = now
  }
  const hits = (buckets.get(key) ?? []).filter((t) => now - t < windowMs)
  if (hits.length >= limit) {
    buckets.set(key, hits)
    return { ok: false, retryAfterMs: windowMs - (now - hits[0]) }
  }
  hits.push(now)
  buckets.set(key, hits)
  return { ok: true, retryAfterMs: 0 }
}

export const resetRateLimits = () => buckets.clear()
