// AABB vs voxel collision and fluid queries shared by the player, mobs and item entities.
import { BLOCKS } from './blocks.js';

const SOLID = new Uint8Array(256);
for (const b of BLOCKS) if (b) SOLID[b.id] = b.solid ? 1 : 0;

export function isSolid(world, x, y, z) {
  return SOLID[world.getBlock(x, y, z)] === 1;
}

function overlapsSolid(world, minX, minY, minZ, maxX, maxY, maxZ) {
  const x0 = Math.floor(minX), x1 = Math.floor(maxX - 1e-7);
  const y0 = Math.floor(minY), y1 = Math.floor(maxY - 1e-7);
  const z0 = Math.floor(minZ), z1 = Math.floor(maxZ - 1e-7);
  for (let y = y0; y <= y1; y++)
    for (let z = z0; z <= z1; z++)
      for (let x = x0; x <= x1; x++) {
        if (SOLID[world.getBlock(x, y, z)]) return true;
        // unloaded chunks act solid so nothing falls out of the world
        if (y >= 0 && !world.isLoaded(x, z)) return true;
      }
  return false;
}

export class Body {
  constructor(w, h) {
    this.pos = { x: 0, y: 0, z: 0 }; // feet center
    this.vel = { x: 0, y: 0, z: 0 };
    this.w = w;
    this.h = h;
    this.onGround = false;
    this.hitWall = false;
    this.inWater = false;
    this.inLava = false;
    this.headInWater = false;
    this.fallStart = null;
  }
  box() {
    const r = this.w / 2;
    return [this.pos.x - r, this.pos.y, this.pos.z - r, this.pos.x + r, this.pos.y + this.h, this.pos.z + r];
  }
}

// Move a body by its velocity * dt with per-axis collision. Returns fall distance on landing.
export function moveBody(world, b, dt, opts = {}) {
  let dx = b.vel.x * dt, dy = b.vel.y * dt, dz = b.vel.z * dt;
  const r = b.w / 2;
  const steps = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz)) / 0.3));
  dx /= steps; dy /= steps; dz /= steps;
  const wasOnGround = b.onGround;
  b.onGround = false;
  b.hitWall = false;
  let landed = false;
  for (let s = 0; s < steps; s++) {
    // Y
    if (dy !== 0) {
      const ny = b.pos.y + dy;
      if (overlapsSolid(world, b.pos.x - r, ny, b.pos.z - r, b.pos.x + r, ny + b.h, b.pos.z + r)) {
        if (dy < 0) {
          b.pos.y = Math.floor(ny) + 1;
          // in case we're deeper than one block (shouldn't happen) step back up
          let guard = 0;
          while (overlapsSolid(world, b.pos.x - r, b.pos.y, b.pos.z - r, b.pos.x + r, b.pos.y + b.h, b.pos.z + r) && guard++ < 3) b.pos.y += 1;
          b.onGround = true;
          landed = true;
        } else {
          b.pos.y = Math.min(b.pos.y, Math.floor(ny + b.h) - b.h - 1e-4);
        }
        b.vel.y = 0;
        dy = 0;
      } else b.pos.y = ny;
    }
    // X
    if (dx !== 0) {
      const nx = b.pos.x + dx;
      if (overlapsSolid(world, nx - r, b.pos.y, b.pos.z - r, nx + r, b.pos.y + b.h, b.pos.z + r)) {
        if (!tryStep(world, b, nx, b.pos.z, opts.stepHeight)) {
          b.pos.x = dx > 0 ? Math.floor(nx + r) - r - 1e-4 : Math.floor(nx - r) + 1 + r + 1e-4;
          b.vel.x = 0; dx = 0; b.hitWall = true;
        }
      } else if (opts.sneakEdge && wasOnGround && !overlapsSolid(world, nx - r + 0.05, b.pos.y - 0.6, b.pos.z - r + 0.05, nx + r - 0.05, b.pos.y - 0.01, b.pos.z + r - 0.05)) {
        b.vel.x = 0; dx = 0;
      } else b.pos.x = nx;
    }
    // Z
    if (dz !== 0) {
      const nz = b.pos.z + dz;
      if (overlapsSolid(world, b.pos.x - r, b.pos.y, nz - r, b.pos.x + r, b.pos.y + b.h, nz + r)) {
        if (!tryStep(world, b, b.pos.x, nz, opts.stepHeight)) {
          b.pos.z = dz > 0 ? Math.floor(nz + r) - r - 1e-4 : Math.floor(nz - r) + 1 + r + 1e-4;
          b.vel.z = 0; dz = 0; b.hitWall = true;
        }
      } else if (opts.sneakEdge && wasOnGround && !overlapsSolid(world, b.pos.x - r + 0.05, b.pos.y - 0.6, nz - r + 0.05, b.pos.x + r - 0.05, b.pos.y - 0.01, nz + r - 0.05)) {
        b.vel.z = 0; dz = 0;
      } else b.pos.z = nz;
    }
  }
  if (!b.onGround && b.vel.y <= 0) {
    // ground probe for standing still
    if (overlapsSolid(world, b.pos.x - r, b.pos.y - 0.01, b.pos.z - r, b.pos.x + r, b.pos.y, b.pos.z + r)) b.onGround = true;
  }
  updateFluids(world, b);
  // fall distance tracking
  let fall = 0;
  if (b.onGround || b.inWater) {
    if (b.fallStart !== null && landed && !b.inWater) fall = b.fallStart - b.pos.y;
    b.fallStart = null;
  } else if (b.vel.y < 0) {
    if (b.fallStart === null) b.fallStart = b.pos.y;
  } else if (b.fallStart !== null && b.pos.y > b.fallStart) b.fallStart = b.pos.y;
  return fall;
}

function tryStep(world, b, nx, nz, stepHeight) {
  if (!stepHeight || !b.onGround) return false;
  const r = b.w / 2;
  const ty = Math.floor(b.pos.y + 1e-3) + 1;
  if (ty - b.pos.y > stepHeight + 1e-3) return false;
  if (overlapsSolid(world, nx - r, ty, nz - r, nx + r, ty + b.h, nz + r)) return false;
  b.pos.y = ty; b.pos.x = nx; b.pos.z = nz;
  return true;
}

export function updateFluids(world, b) {
  const r = b.w / 2;
  b.inWater = false; b.inLava = false; b.headInWater = false;
  b.flow = b.flow || { x: 0, z: 0 };
  b.flow.x = 0; b.flow.z = 0;
  const x0 = Math.floor(b.pos.x - r), x1 = Math.floor(b.pos.x + r);
  const z0 = Math.floor(b.pos.z - r), z1 = Math.floor(b.pos.z + r);
  const y0 = Math.floor(b.pos.y), y1 = Math.floor(b.pos.y + b.h);
  const eyeY = b.pos.y + b.h * 0.9;
  for (let y = y0; y <= y1; y++)
    for (let z = z0; z <= z1; z++)
      for (let x = x0; x <= x1; x++) {
        const id = world.getBlock(x, y, z);
        const L = BLOCKS[id].liquid;
        if (!L) continue;
        const h = world.fluidHeight(x, y, z, L);
        const top = y + h;
        if (b.pos.y < top - 0.05) {
          if (L === 'water') b.inWater = true;
          else b.inLava = true;
          // flow push: compare neighbour heights
          if (L === 'water') {
            const hs = (dx, dz) => {
              const hh = world.fluidHeight(x + dx, y, z + dz, L);
              return hh < 0 ? (SOLID[world.getBlock(x + dx, y, z + dz)] ? h : h - 0.2) : hh;
            };
            const meta = world.getMeta(x, y, z);
            if (meta !== 0) {
              b.flow.x += (h - hs(1, 0)) - (h - hs(-1, 0));
              b.flow.z += (h - hs(0, 1)) - (h - hs(0, -1));
            }
          }
        }
        if (L === 'water' && eyeY >= y && eyeY < top) b.headInWater = true;
      }
  const fl = Math.hypot(b.flow.x, b.flow.z);
  if (fl > 0) { b.flow.x /= fl; b.flow.z /= fl; }
}

export function boxIntersectsBlock(box, x, y, z) {
  return box[0] < x + 1 && box[3] > x && box[1] < y + 1 && box[4] > y && box[2] < z + 1 && box[5] > z;
}

// Voxel ray cast (Amanatides & Woo). Returns {x,y,z, face:[nx,ny,nz], dist, id} or null.
export function raycast(world, ox, oy, oz, dx, dy, dz, maxDist, filter) {
  let x = Math.floor(ox), y = Math.floor(oy), z = Math.floor(oz);
  const sx = Math.sign(dx), sy = Math.sign(dy), sz = Math.sign(dz);
  const tdx = dx !== 0 ? Math.abs(1 / dx) : Infinity;
  const tdy = dy !== 0 ? Math.abs(1 / dy) : Infinity;
  const tdz = dz !== 0 ? Math.abs(1 / dz) : Infinity;
  let tmx = dx > 0 ? (x + 1 - ox) * tdx : dx < 0 ? (ox - x) * tdx : Infinity;
  let tmy = dy > 0 ? (y + 1 - oy) * tdy : dy < 0 ? (oy - y) * tdy : Infinity;
  let tmz = dz > 0 ? (z + 1 - oz) * tdz : dz < 0 ? (oz - z) * tdz : Infinity;
  let face = [0, 0, 0];
  let t = 0;
  while (t <= maxDist) {
    const id = world.getBlock(x, y, z);
    if (id && filter(id, x, y, z)) return { x, y, z, face, dist: t, id };
    if (tmx < tmy && tmx < tmz) { x += sx; t = tmx; tmx += tdx; face = [-sx, 0, 0]; }
    else if (tmy < tmz) { y += sy; t = tmy; tmy += tdy; face = [0, -sy, 0]; }
    else { z += sz; t = tmz; tmz += tdz; face = [0, 0, -sz]; }
  }
  return null;
}

// Ray vs AABB slab test, returns distance or -1.
export function rayBox(ox, oy, oz, dx, dy, dz, box) {
  let tmin = -Infinity, tmax = Infinity;
  const o = [ox, oy, oz], d = [dx, dy, dz];
  for (let i = 0; i < 3; i++) {
    if (Math.abs(d[i]) < 1e-9) {
      if (o[i] < box[i] || o[i] > box[i + 3]) return -1;
    } else {
      let t1 = (box[i] - o[i]) / d[i], t2 = (box[i + 3] - o[i]) / d[i];
      if (t1 > t2) [t1, t2] = [t2, t1];
      tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
      if (tmin > tmax) return -1;
    }
  }
  return tmax < 0 ? -1 : Math.max(0, tmin);
}
