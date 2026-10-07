/* editor round 2: parts (individual pieces via box select / Parts taps), start & finish gates driving their trail, terrain shaping (brush, undo, trees, save -> reload) */
const fs = require('fs'), path = require('path');
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = [], VP = { width: 420, height: 820 };
  const { browser, page, errors } = await boot({ viewport: VP });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const ev = (f, a) => page.evaluate(f, a);
  await page.click('#bEd');

  /* ---------- gates drive trails ---------- */
  const G0 = await ev(() => {
    const E = SK.ED, mine = [...E.UNITS.values()].filter(o => o.userData.gt && !o.userData.edGone);
    return { n: mine.length, tags: mine.map(o => o.userData.gt.slice(0, 2).join(':')), user: [...E.ADDED.values()].filter(o => E.nameOf(o) === 'Start gate').length };
  });
  check(G0.n >= 10, 'start/finish gates are tagged with their trail (' + G0.n + ': ' + G0.tags.slice(0, 5).join(' ') + '…)', fails);
  check(G0.user >= 1, 'the gate you added in the editor is loaded from the saved edits', fails);

  const G = await ev(() => {
    const E = SK.ED, t = SK.TRAILS.find(x => x.id === 'skyline'), g = [...E.UNITS.values()].find(o => o.userData.gt && o.userData.gt[0] === 'skyline' && o.userData.gt[1] === 'start' && !o.userData.edGone);
    if (!g) return { err: 'gate missing' };
    const s0 = t.pts[0].slice(), len0 = t.len, gx = g.position.x, gz = g.position.z; E.select(g); E.S.snap = false; E.moveBy(0, 12, 0);
    // moveBy takes world dx,dz: 12 m in +z
    const s1 = t.pts[0].slice(), len1 = t.len, p0 = SK.P8.pointAt(t, 0), inB = t.bz1 > s1[1];
    const q = E.S.sel[0].position; const dz = q.z - gz;
    E.undo(); const s2 = t.pts[0].slice(), len2 = t.len;
    return { s0, s1, s2, len0, len1, len2, p0: [p0.x, p0.z], dz: dz, finS: t.finS };
  });
  check(!G.err && Math.abs(G.s1[1] - (G.s0[1] + G.dz)) < 0.05 && Math.abs(G.dz - 12) < 0.3, 'moving a trail start gate moves the trail start by the same amount (' + (G.s1 && (G.s1[1] - G.s0[1]).toFixed(2)) + ' m)', fails);
  check(!G.err && Math.abs(G.p0[1] - G.s1[1]) < 0.05 && Math.abs(G.len1 - G.len0) > 1, 'trail skiers / run detection use the new start (length ' + (G.len0 && G.len0.toFixed(0)) + ' → ' + (G.len1 && G.len1.toFixed(0)) + ')', fails);
  check(!G.err && Math.abs(G.s2[1] - G.s0[1]) < 0.01 && Math.abs(G.len2 - G.len0) < 0.01, 'undo puts the trail back', fails);

  const F = await ev(() => {   /* finish gate, plus Pine Ribbon start -> Summit Express skiers' route */
    const E = SK.ED, t = SK.TRAILS.find(x => x.id === 'ribbon'), g = [...E.UNITS.values()].find(o => o.userData.gt && o.userData.gt[0] === 'ribbon' && o.userData.gt[1] === 'start');
    const cx0 = SK.CROWDX.pts[2].slice(); E.select(g); E.S.snap = false; E.moveBy(6, 0, 0);
    const cx1 = SK.CROWDX.pts[2].slice(), s1 = t.pts[0].slice(); SK.sim(40);
    const walkers = SK.CROWD.filter(n => n.kind === 'ski').every(n => isFinite(n.g.position.x) && isFinite(n.g.position.z));
    E.undo(); return { cx0, cx1, cx2: SK.CROWDX.pts[2].slice(), s1, ok: walkers };
  });
  check(Math.hypot(F.cx1[0] - F.cx0[0], F.cx1[1] - F.cx0[1]) > 3 && Math.hypot(F.cx2[0] - F.cx0[0], F.cx2[1] - F.cx0[1]) < 0.01 && F.ok, 'Summit Express skiers route follows the Pine Ribbon start gate (and back on undo)', fails);

  const L = await ev(() => {   /* link an added gate to the nearest trail end */
    const E = SK.ED, t = SK.TRAILS.find(x => x.id === 'timber'), e = t.pts0[t.pts0.length - 1];
    SK.W.x = 0; E.addAsset('Start gate'); const o = E.S.sel[0]; o.position.set(e[0] + 2, o.position.y, e[1] - 3); o.updateMatrixWorld(true); E.S.snap = false;
    E.linkGate(); const lk = o.userData.gt && o.userData.gt.slice(0, 2).join(':'), a0 = t.pts[t.pts.length - 1].slice(); E.moveBy(0, 0, 0); E.moveBy(-5, 0, 0);
    const a1 = t.pts[t.pts.length - 1].slice(); return { lk, a0, a1, key: o.userData.ek, entry: o.userData.edAdd.lk };
  });
  check(L.lk === 'timber:finish' && Math.abs((L.a1[0] - L.a0[0]) + 5) < 0.05, 'a gate added in the editor links to the nearest trail end and drags it (' + L.lk + ')', fails);

  /* ---------- parts ---------- */
  const P = await ev(() => {
    const E = SK.ED; let comp = null, best = 0; E.UNITS.forEach(u => { if (u.userData.edGone) return; const n = E.leavesOf(u).length, c = E.center(u); if (n >= 5 && n < 40 && n > best && Math.abs(c.x) < 120 && c.z > SK.RES_Z - 360) { best = n; comp = u; } });
    if (!comp) return { err: 'no composite asset' };
    const c = E.center(comp); E.select(null); SK.snap({ tx: c.x, ty: c.y, tz: c.z, r: 30, theta: 0.4, phi: 1.0 }); SK.sim(1);
    return { key: comp.userData.ek, name: E.nameOf(comp), leaves: E.leavesOf(comp).length };
  });
  await ev(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  const BX = await ev(() => { SK.ED.boxSelect(0, 0, innerWidth, innerHeight); const S = SK.ED.S, parts = S.sel.filter(o => (o.userData.ek || '').indexOf('/') > 0); return { n: S.sel.length, parts: parts.length, units: SK.ED.UNITS.size }; });
  check(!P.err && BX.parts >= 3, 'box select picks individual parts of multi-piece assets (' + BX.parts + ' parts of ' + BX.n + ' selected; ' + P.name + ' has ' + P.leaves + ' pieces)', fails);
  const PM = await ev(() => {
    const E = SK.ED, o = E.S.sel.find(o => (o.userData.ek || '').indexOf('/') > 0), k = o.userData.ek, par = o.parent, sib = par.children.filter(c => c !== o && c.userData.ek && c.userData.ek.indexOf('/') < 0).length;
    const p0 = o.position.clone(), unit = E.byKey(k.slice(0, k.indexOf('/'))), u0 = unit.position.clone(); E.S.snap = false; E.S.sel = [o]; E.moveBy(4, 0, 0);
    const moved = o.position.distanceTo(p0), unitSame = unit.position.distanceTo(u0) < 1e-6, saved = !!E.E.obj[k], found = E.byKey(k) === o, nm = E.nameOf(o);
    return { k, moved, unitSame, saved, found, nm };
  });
  check(PM.moved > 3 && PM.unitSame && PM.saved && PM.found, 'a single part moves on its own and saves under its path key (' + PM.k + ', ' + PM.nm + ')', fails);

  /* ---------- terrain ---------- */
  const T = await ev(() => {
    const E = SK.ED, tr = SK.TREES.find(t => t[1] < -40 && t[1] > -300 && Math.abs(t[0]) < 100), wx = tr[0], lz = tr[1], wz = lz + SK.RES_Z, y0 = SK.groundY(wx, wz), tree = tr;
    const im = E.S.tb && null; let iy0 = null, ii = -1, imesh = null;
    SK.world.res.traverse(c => { if (c.isInstancedMesh && c.name === 'forest' && !imesh && tree) { const a = c.instanceMatrix.array; for (let i = 0; i < c.count; i++) if (Math.hypot(a[i * 16 + 12] - tree[0], a[i * 16 + 14] - tree[1]) < 0.01) { imesh = c; ii = i; iy0 = a[i * 16 + 13]; } } });
    E.S_.tb.mode = 'raise'; E.S_.tb.r = 8; for (let i = 0; i < 30; i++) E.dab(wx, wz, 0.05); E.strokeEnd();
    const y1 = SK.groundY(wx, wz), ty = imesh ? imesh.instanceMatrix.array[ii * 16 + 13] : null;
    const chunkOk = (() => { let bad = 0; SK.HG.chunks.forEach(c => { const P = c.m.geometry.attributes.position; for (let j = 0; j < c.hh; j += 8) for (let i = 0; i < c.w; i += 8) { if (Math.abs(P.array[(j * c.w + i) * 3 + 1] - SK.HG.h[(c.cj + j) * SK.HG.W1 + c.ci + i]) > 1e-4) bad++; } }); return bad === 0; })();
    E.undo(); const y2 = SK.groundY(wx, wz), ty2 = imesh ? imesh.instanceMatrix.array[ii * 16 + 13] : null;
    return { y0, y1, y2, hasTree: !!imesh, iy0, ty, ty2, chunkOk, rows: !!E.terrainDelta() };
  });
  check(T.y1 - T.y0 > 1.2, 'raise brush builds up the ground the game uses (' + T.y0.toFixed(2) + ' → ' + T.y1.toFixed(2) + ' m)', fails);
  check(T.chunkOk, 'terrain mesh matches the height grid', fails);
  check(!T.hasTree || (T.ty - T.iy0 > 1 && Math.abs(T.ty2 - T.iy0) < 0.01), 'trees rise with the ground and drop back on undo' + (T.hasTree ? '' : ' (no tree in range, skipped)'), fails);
  check(Math.abs(T.y2 - T.y0) < 0.01 && !T.rows, 'undo restores the original ground and leaves nothing to save', fails);

  const T2 = await ev(() => {
    const E = SK.ED, wx = -10, wz = -60 + SK.RES_Z, a = SK.groundY(wx, wz);
    E.S_.tb.mode = 'lower'; E.S_.tb.r = 6; for (let i = 0; i < 20; i++) E.dab(wx, wz, 0.05); E.strokeEnd();
    const b = SK.groundY(wx, wz); E.S_.tb.mode = 'smooth'; for (let i = 0; i < 10; i++) E.dab(wx, wz, 0.05); E.strokeEnd();
    E.S_.tb.mode = 'level'; for (let i = 0; i < 10; i++) E.dab(wx + 12, wz, 0.05); E.strokeEnd();
    const d = E.terrainDelta(); E.S_.tool = 'terrain'; E.act('tlook'); E.act('tlook');
    SK.snap({ tx: wx, ty: SK.groundY(wx, wz), tz: wz, r: 30, theta: 0.3, phi: 1.0 }); SK.sim(1);
    return { a, b, rows: d ? d.rows.length : 0, ctrl: document.getElementById('edB').innerText.indexOf('Smooth') >= 0 };
  });
  check(T2.b < T2.a - 1 && T2.rows > 10, 'lower / smooth / level brushes dig and edit (' + T2.a.toFixed(2) + ' → ' + T2.b.toFixed(2) + ' m, ' + T2.rows + ' rows to save)', fails);
  await page.click('[data-t="terrain"]'); await page.waitForTimeout(500);
  check(await ev(() => document.getElementById('edB').innerText.indexOf('Raise') >= 0 && document.getElementById('edB').innerText.indexOf('Brush') >= 0), 'terrain tool shows the brush bar', fails);
  /* a real drag-paint with the mouse */
  await ev(() => { SK.ED.S_.tb.mode = 'raise'; SK.ED.S_.tb.r = 5; SK.ED.S_.tb.look = false; SK.snap({ tx: 30, ty: SK.groundY(30, -150 + SK.RES_Z), tz: -150 + SK.RES_Z, r: 28, theta: 0.2, phi: 1.05 }); });
  await ev(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));   /* let the camera settle on a rendered frame */
  const cp = await ev(() => { SK.cam.updateMatrixWorld(); const v = new THREE.Vector3(30, SK.groundY(30, -150 + SK.RES_Z), -150 + SK.RES_Z).project(SK.cam); return { x: (v.x + 1) / 2 * innerWidth, y: (1 - v.y) / 2 * innerHeight, h: SK.groundY(30, -150 + SK.RES_Z) }; });
  await page.mouse.move(cp.x, cp.y); await page.mouse.down(); for (let i = 0; i < 8; i++) { await page.mouse.move(cp.x + i * 6, cp.y); await page.waitForTimeout(120); await ev(() => SK.sim(4, 0.05)); } await page.mouse.up();
  const h2 = await ev(() => SK.groundY(30, -150 + SK.RES_Z));
  check(h2 - cp.h > 0.2, 'mouse drag paints terrain (' + cp.h.toFixed(2) + ' → ' + h2.toFixed(2) + ')', fails);
  await page.screenshot({ path: OUT + '/editor-terrain.png' });

  /* ---------- save -> reload ---------- */
  const html = await ev(() => SK.ED.pageHTML());
  const exp = await ev(() => { const t = SK.TRAILS.find(x => x.id === 'timber'); return { end: t.pts[t.pts.length - 1].slice(), h: SK.groundY(-10, -60 + SK.RES_Z), h2: SK.groundY(30, -150 + SK.RES_Z), partKey: Object.keys(SK.ED.E.obj).find(k => k.indexOf('/') > 0), partPos: SK.ED.E.obj[Object.keys(SK.ED.E.obj).find(k => k.indexOf('/') > 0)].p }; });
  check(/"terrain":\{"v":1,"rows":/.test(html) && html.length < 4e6, 'saved page carries terrain rows (' + (html.length / 1e6).toFixed(2) + ' MB)', fails);
  const f = path.join(OUT, 'edited2.html'); fs.writeFileSync(f, html);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close();

  const b2 = await boot({ file: f, viewport: VP });
  const R = await b2.page.evaluate(([pk]) => { const t = SK.TRAILS.find(x => x.id === 'timber'), o = SK.ED.byKey(pk);
    return { end: t.pts[t.pts.length - 1].slice(), h: SK.groundY(-10, -60 + SK.RES_Z), h2: SK.groundY(30, -150 + SK.RES_Z), part: o ? o.position.toArray().map(v => +v.toFixed(3)) : null, gates: SK.ED.TR.length }; }, [exp.partKey]);
  check(Math.hypot(R.end[0] - exp.end[0], R.end[1] - exp.end[1]) < 0.02, 'reload: linked gate still drives the Timberline finish', fails);
  check(Math.abs(R.h - exp.h) < 0.02 && Math.abs(R.h2 - exp.h2) < 0.02, 'reload: sculpted terrain is back (' + R.h.toFixed(2) + ', ' + R.h2.toFixed(2) + ')', fails);
  check(R.part && Math.abs(R.part[0] - exp.partPos[0]) < 0.01, 'reload: moved part is where it was left', fails);
  const html2 = await b2.page.evaluate(() => SK.ED.pageHTML());
  check(html2 === html, 'saving again without changes gives the identical page', fails);
  await b2.page.screenshot({ path: OUT + '/editor2-reload.png' });
  check(b2.errors.length === 0, 'reloaded page: no errors ' + b2.errors.join(' | '), fails);
  await b2.browser.close();
  console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
