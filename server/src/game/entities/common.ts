import type { Vec3 } from '@doors/shared';

/** Moves along a polyline at a given speed. */
export class PathFollower {
  i = 0;
  pos: Vec3;
  yaw = 0;
  constructor(public pts: Vec3[]) {
    this.pos = { ...pts[0] };
  }
  get done() { return this.i >= this.pts.length - 1 && this.remaining() < 0.01; }
  remaining() {
    const t = this.pts[Math.min(this.i + 1, this.pts.length - 1)];
    return Math.hypot(t.x - this.pos.x, t.y - this.pos.y, t.z - this.pos.z);
  }
  advance(dist: number) {
    while (dist > 0 && this.i < this.pts.length - 1) {
      const t = this.pts[this.i + 1];
      const dx = t.x - this.pos.x, dy = t.y - this.pos.y, dz = t.z - this.pos.z;
      const d = Math.hypot(dx, dy, dz);
      if (d < 1e-6) { this.i++; continue; }
      if (Math.abs(dx) + Math.abs(dz) > 1e-4) this.yaw = Math.atan2(-dx, -dz);
      if (d <= dist) { this.pos = { ...t }; this.i++; dist -= d; }
      else { const k = dist / d; this.pos = { x: this.pos.x + dx * k, y: this.pos.y + dy * k, z: this.pos.z + dz * k }; dist = 0; }
    }
  }
  reverse() {
    this.pts = [...this.pts].reverse();
    this.i = 0;
    this.pos = { ...this.pts[0] };
  }
  /** Index of the nearest path point at or after the current position. */
}

export function moveToward(pos: Vec3, target: Vec3, dist: number): { pos: Vec3; yaw: number | null; arrived: boolean } {
  const dx = target.x - pos.x, dy = target.y - pos.y, dz = target.z - pos.z;
  const d = Math.hypot(dx, dz);
  if (d <= dist || d < 1e-5) return { pos: { ...target }, yaw: d > 1e-5 ? Math.atan2(-dx, -dz) : null, arrived: true };
  const k = dist / d;
  return { pos: { x: pos.x + dx * k, y: pos.y + dy * Math.min(1, k * 2), z: pos.z + dz * k }, yaw: Math.atan2(-dx, -dz), arrived: false };
}

export const r2 = (v: number) => Math.round(v * 100) / 100;
