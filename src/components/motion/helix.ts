/**
 * Shared helix geometry for the spiral scenes (hero gallery, values spiral).
 * `d` is a continuous offset from the front of the spiral: 0 is the focused
 * item, ±1 its neighbours. Cards orbit a vertical axis and rise along it, so the
 * set reads as a shallow spiral rather than a flat carousel.
 */
export type HelixOptions = {
  /** Orbit radius in px. */
  radius: number
  /** Angle between neighbouring items, in degrees. */
  step: number
  /** Vertical rise per item, in px (negative rises upward to the right). */
  rise: number
  /** Items further than this from the front are hidden. */
  visible: number
}

export function helixStyle(d: number, { radius, step, rise, visible }: HelixOptions) {
  const angle = (d * step * Math.PI) / 180
  const x = Math.sin(angle) * radius
  const z = (Math.cos(angle) - 1) * radius
  const y = d * rise
  const abs = Math.abs(d)
  const opacity = abs >= visible ? 0 : abs <= 1 ? 1 - abs * 0.25 : 0.75 * (1 - (abs - 1) / (visible - 1))
  return {
    transform: `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, ${z.toFixed(1)}px) rotateY(${(d * step).toFixed(2)}deg)`,
    opacity: Math.max(0, opacity),
    zIndex: String(100 - Math.round(abs * 10)),
    dim: Math.min(1, abs),
  }
}

/** Shortest signed continuous distance from `pos` to `index` around a ring of `count`. */
export function ringDistance(index: number, pos: number, count: number) {
  let d = (index - pos) % count
  if (d > count / 2) d -= count
  if (d < -count / 2) d += count
  return d
}

/** Frame-rate independent damping toward a target (time constant in ms). */
export const damp = (current: number, target: number, dt: number, tau = 80) =>
  current + (target - current) * (1 - Math.exp(-dt / tau))
