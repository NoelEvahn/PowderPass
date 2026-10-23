type Bucket = { count: number; resetAt: number }

const buckets = new Map<string, Bucket>()
const MAX_KEYS = 10_000

/**
 * Fixed-window, in-memory rate limiter. Adequate for a single server instance.
 * For several instances behind a load balancer, replace with a shared store (e.g. Redis)
 * behind the same function signature.
 */
export function rateLimit(key: string, { limit, windowMs }: { limit: number; windowMs: number }, now = Date.now()) {
  if (buckets.size > MAX_KEYS) {
    for (const [k, b] of buckets) if (b.resetAt <= now) buckets.delete(k)
  }
  const bucket = buckets.get(key)
  if (!bucket || bucket.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs })
    return { ok: true, remaining: limit - 1 }
  }
  bucket.count += 1
  return { ok: bucket.count <= limit, remaining: Math.max(0, limit - bucket.count) }
}

export const resetRateLimits = () => buckets.clear()
