/* E4 z-fighting audit: inside every building footprint, flat up-facing triangles of different colours that overlap and lie within
   4 mm of each other (they flicker as the camera moves: the Pro Shop rug was one). Reports one line per cluster: where, which colours. */
const RZ = SK.RES_Z, R = SK.NOSNOW, va = new THREE.Vector3(), vb = new THREE.Vector3(), vc = new THREE.Vector3(), found = {};
const skip = o => { for (let p = o; p; p = p.parent) { const u = p.userData; if (!p.visible || (u && (u.person || u.seats))) return true; } return false; };
const tris = []; SK.scene.updateMatrixWorld(true);
SK.scene.traverse(o => { if (!o.isMesh || o.isInstancedMesh || skip(o)) return; const m = o.material; if (!m || m.type === 'ShaderMaterial' || (m.transparent && m.opacity < 0.99) || !m.color) return;
  const P = o.geometry.attributes.position, I = o.geometry.index, n = I ? I.count : P.count; if (n > 200000) return; const col = m.color.getHexString();
  for (let t = 0; t < n; t += 3) { const ia = I ? I.getX(t) : t, ib = I ? I.getX(t + 1) : t + 1, ic = I ? I.getX(t + 2) : t + 2;
    va.fromBufferAttribute(P, ia).applyMatrix4(o.matrixWorld); vb.fromBufferAttribute(P, ib).applyMatrix4(o.matrixWorld); vc.fromBufferAttribute(P, ic).applyMatrix4(o.matrixWorld);
    const ux = vb.x - va.x, uy = vb.y - va.y, uz = vb.z - va.z, wx = vc.x - va.x, wy = vc.y - va.y, wz = vc.z - va.z, nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, nl = Math.hypot(nx, ny, nz);
    if (nl < 1e-8 || ny / nl < 0.99) continue; const cx = (va.x + vb.x + vc.x) / 3, cz = (va.z + vb.z + vc.z) / 3, lz = cz - RZ;
    const ri = R.findIndex(r => cx > r.x0 && cx < r.x1 && lz > r.z0 && lz < r.z1); if (ri < 0) continue;
    tris.push({ ri, col, o: o.id, y: (va.y + vb.y + vc.y) / 3, cx, cz, a: [va.x, va.z, vb.x, vb.z, vc.x, vc.z], area: nl / 2 }); } });
const inTri = (t, x, z) => { const a = t.a, d = (a[3] - a[5]) * (a[0] - a[4]) + (a[4] - a[2]) * (a[1] - a[5]); if (Math.abs(d) < 1e-12) return false; const l1 = ((a[3] - a[5]) * (x - a[4]) + (a[4] - a[2]) * (z - a[5])) / d, l2 = ((a[5] - a[1]) * (x - a[4]) + (a[0] - a[4]) * (z - a[5])) / d; return l1 > 0.02 && l2 > 0.02 && 1 - l1 - l2 > 0.02; };
const bins = new Map(); tris.forEach(t => { const k = Math.floor(t.cx) + ',' + Math.floor(t.cz) + ',' + Math.round(t.y * 50); (bins.get(k) || bins.set(k, []).get(k)).push(t); });
tris.forEach(t => { if (t.area < 0.05) return; for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (let dy = -1; dy <= 1; dy++) { const L = bins.get((Math.floor(t.cx) + dx) + ',' + (Math.floor(t.cz) + dz) + ',' + (Math.round(t.y * 50) + dy)); if (!L) continue;
  for (const s of L) { if (s === t || s.col === t.col || s.area < 0.05 || Math.abs(s.y - t.y) > 0.004 || s.y > t.y) continue; if (!inTri(s, t.cx, t.cz)) continue;
    const key = t.ri + ':' + [t.col, s.col].sort().join('/') + ':' + Math.round(t.cx) + ',' + Math.round(t.cz - RZ); found[key] = (found[key] || 0) + 1; } } });
const byRect = {}; Object.keys(found).forEach(k => { const [ri, cols, at] = k.split(':'); (byRect[ri] = byRect[ri] || []).push(cols + ' @' + at + ' x' + found[k]); });
return { tris: tris.length, clusters: Object.keys(found).length, byRect: Object.fromEntries(Object.entries(byRect).map(([ri, v]) => [ri + ' ' + JSON.stringify(R[ri]).replace(/(\.\d)\d+/g, '$1'), v.slice(0, 12)])) };
