/* E4 audit: placed assets floating over or sunk into the ground. For every editor unit (and each direct part of building groups)
   the lowest point of the object is compared with the surface under its centre: terrain (+ snow) outdoors, the real floor (SURF)
   inside buildings. Lift carriers, people, labels and things meant to hang (signs, lamps on walls, roofs) are skipped. */
const out = [], V = () => new THREE.Vector3(), RZ = SK.RES_Z;
const SKIP = /Label|Lift chair|Gondola|cable|Cable|Cloud|Sun|Mountain|Range|Sky|Bird|Flag|Bunting|String lights|Sign|sign|Lamp|lamp|Lantern|Pendant|Icicle|Moves|FX|person/;
const surf = (x, z, top) => { const g = SK.groundY(x, z), f = SK.SURF.at(x, z, g - 0.3, top); return f !== null && f > g ? f : g; };
SK.ED.UNITS.forEach((u, k) => {
  if (!u || !u.visible) return; const nm = SK.ED.nameOf(u) || ''; if (SKIP.test(nm)) return;
  let skip = false; u.traverse(o => { if (o.userData && (o.userData.person || o.userData.seats)) skip = true; }); if (skip) return;
  const bb = new THREE.Box3().setFromObject(u); if (bb.isEmpty()) return; const sz = bb.getSize(V()); if (sz.x > 40 || sz.z > 40) return;
  const c = bb.getCenter(V()), g = surf(c.x, c.z, bb.min.y + 0.5), gap = bb.min.y - g;
  /* corners: a prop half off a ledge shows as one corner floating high */
  const corners = [[bb.min.x, bb.min.z], [bb.max.x, bb.min.z], [bb.min.x, bb.max.z], [bb.max.x, bb.max.z]].map(p => bb.min.y - surf(p[0], p[1], bb.min.y + 0.5));
  const worst = Math.min(...corners);
  if (gap > 0.12 && worst > 0.08) out.push({ k, nm, kind: 'floating', gap: +gap.toFixed(2), at: [+c.x.toFixed(1), +(c.z - RZ).toFixed(1)], size: [+sz.x.toFixed(1), +sz.y.toFixed(1), +sz.z.toFixed(1)] });
  else if (gap < -Math.max(0.6, sz.y * 0.35)) out.push({ k, nm, kind: 'sunk', gap: +gap.toFixed(2), at: [+c.x.toFixed(1), +(c.z - RZ).toFixed(1)], size: [+sz.x.toFixed(1), +sz.y.toFixed(1), +sz.z.toFixed(1)] });
});
return { n: SK.ED.UNITS.size || SK.ED.UNITS.length, issues: out.length, out };
