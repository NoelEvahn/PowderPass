/* developer editor: select / move / rotate / scale / delete / copy / paste / add / group, label rename, NPC path, then Save -> reload the saved HTML and check every edit came back */
const fs = require('fs'), path = require('path');
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = [];
  const { browser, page, errors } = await boot(Object.assign({ query: '#dev' }, { viewport: { width: 420, height: 820 } }));
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const ev = (f, a) => page.evaluate(f, a);
  await page.click('#bEd');
  const st = await ev(() => ({ on: SK.ED.S.on, units: SK.ED.UNITS.size, cont: SK.ED.CONT.size, npcs: SK.ED.NPCS.length, labels: SK.LABELS.length, walk: SK.W.on, bar: !document.getElementById('ed').hidden }));
  check(st.on && !st.walk && st.bar, 'editor opens (orbit camera, editor bars) ' + JSON.stringify(st), fails);
  check(st.units > 100 && st.npcs > 10 && st.labels > 5, 'assets, NPCs and labels are registered', fails);

  /* pick a lamp post (has a collision box + a real light) and a bench-like asset near the village */
  const T = await ev(() => {
    const E = SK.ED, lamps = [...E.UNITS.values()].filter(o => E.nameOf(o) === 'Lamp post'); const lamp = lamps[0];
    const c = E.center(lamp).clone(); E.select(lamp); const r = SK.ED.S.sel.length;
    const p0 = lamp.getWorldPosition(new THREE.Vector3()), near = p => SK.SOLIDS.filter(k => !k.off && Math.hypot(k.x - p.x, k.z - p.z) < 0.5).length, sol0 = near(p0);
    E.moveBy(3, 0, 0); const c2 = E.center(lamp).clone(); E.rotate(Math.PI / 12); E.scale(1.1); const p1 = lamp.getWorldPosition(new THREE.Vector3());
    const solMoved = near(p1), solOld = near(p0);
    const L = SK.LIGHTS.find(l => Math.hypot(l.x - c2.x, l.z - c2.z) < 2.5);
    return { n: lamps.length, key: lamp.userData.ek, r, sol0, solMoved, solOld, light: !!L, dx: +(c2.x - c.x).toFixed(2), entry: SK.ED.E.obj[lamp.userData.ek] };
  });
  check(T.n > 3 && T.r === 1, 'select a lamp post (' + T.n + ' lamp posts)', fails);
  check(Math.abs(T.dx - 3) < 0.05 && T.entry && T.entry.p, 'move + rotate + scale write an edit ' + JSON.stringify(T.entry), fails);
  check(T.sol0 >= 1 && T.solMoved >= 1 && T.solOld === 0, 'its collision box moved with it (' + T.sol0 + ' -> ' + T.solMoved + ')', fails);
  check(T.light, 'its street light moved with it', fails);

  const D = await ev(() => { const E = SK.ED, o = [...E.UNITS.values()].filter(o => E.nameOf(o) === 'Lamp post')[1]; E.select(o); const c = E.center(o).clone(); E.del();
    return { key: o.userData.ek, vis: o.visible, off: SK.SOLIDS.filter(k => Math.hypot(k.x - c.x, k.z - c.z) < 0.6).every(k => k.off), lightOff: SK.LIGHTS.filter(l => Math.hypot(l.x - c.x, l.z - c.z) < 2.5).every(l => l.off), e: E.E.obj[o.userData.ek] }; });
  check(!D.vis && D.off && D.lightOff && D.e && D.e.del, 'delete hides it, its collision and light', fails);
  const U = await ev(() => { SK.ED.undo(); const o = SK.ED.byKey(SK.ED.E && Object.keys(SK.ED.E.obj).find(k => !SK.ED.E.obj[k].del) ? '' : ''); return true; });
  const un = await ev(k => { const o = SK.ED.byKey(k); return { vis: o.visible, e: SK.ED.E.obj[k] || null }; }, D.key);
  check(un.vis && !un.e, 'undo brings it back', fails);

  /* copy / paste / duplicate / group */
  const base = await ev(() => SK.ED.E.add.length);   /* the shipped file already carries saved edits */
  const C = await ev(() => { const E = SK.ED, o = [...E.UNITS.values()].filter(o => E.nameOf(o) === 'Lamp post')[2]; E.select(o); E.copy(); E.paste(); const a = E.S.sel[0]; E.dup(); const b = E.S.sel[0];
    E.select(a); E.select(b, true); E.group(); E.select(null); E.select(a); const grp = E.S.sel.length; const sol = SK.SOLIDS.length;
    return { adds: E.E.add.length, grp, groups: E.E.groups.length, aKey: a.userData.ek, bKey: b.userData.ek, name: E.nameOf(a) }; });
  check(C.adds - base === 2 && C.name === 'Lamp post', 'copy/paste + duplicate create 2 lamp posts', fails);
  check(C.grp === 2 && C.groups === 1, 'grouped objects select together', fails);
  const G = await ev(() => { SK.ED.ungroup(); SK.ED.group(); return SK.ED.E.groups.length; });
  check(G === 1, 'ungroup / regroup', fails);

  /* add from the gallery + search panel */
  const A = await ev(() => { const E = SK.ED; E.openPanel('add'); document.getElementById('edQ').value = 'snowman'; document.getElementById('edQ').dispatchEvent(new Event('input')); const rows = document.querySelectorAll('#edL [data-i]').length; E.rowTap(1); return { rows, sel: E.S.sel.length && E.nameOf(E.S.sel[0]), adds: E.E.add.length, last: E.E.add[E.E.add.length - 1] }; });
  check(A.rows >= 2 && A.sel === 'Snowman' && A.last.g === 'Snowman', 'add panel search + place a Snowman', fails);
  const LS = await ev(() => { const E = SK.ED; E.openPanel('list'); document.getElementById('edQ').value = 'lamp'; document.getElementById('edQ').dispatchEvent(new Event('input')); const n = document.querySelectorAll('#edL [data-i]').length; E.closePanel(); return n; });
  check(LS > 3, 'object list search finds lamp posts (' + LS + ')', fails);

  /* label rename */
  const LB = await ev(() => { const E = SK.ED, s = SK.LABELS[0]; E.select(s); document.getElementById('edN').value = 'Renamed Place'; E.act('rename'); return { t: s.userData.ltxt, e: E.E.labels[s.userData.lk], k: s.userData.lk }; });
  check(LB.t === 'Renamed Place' && LB.e && LB.e.t === 'Renamed Place', 'label renamed', fails);

  /* NPC path */
  const N = await ev(() => { const E = SK.ED, N = E.NPCS.find(n => n.kind === 'life'); E.selectNpc(N); const r0 = E.routeOf(N).what; N.r.g.getWorldPosition(window.__v = new THREE.Vector3());
    E.pathStart(); const v = window.__v; E.S.path.wp = [[v.x + 4, v.z], [v.x + 4, v.z + 4], [v.x, v.z + 4]]; E.pathDone(); SK.sim(40); const p = N.r.g.getWorldPosition(new THREE.Vector3());
    return { r0, key: N.key, moved: Math.hypot(p.x - v.x, p.z - v.z), e: E.E.npc[N.key], line: !!E.S.line }; });
  check(/guest/.test(N.r0) && N.e && N.e.wp.length === 3 && N.moved > 1 && N.line, 'NPC route shown, new path set and walked (' + N.moved.toFixed(1) + ' m)', fails);

  /* tap-select through the real canvas: snap the camera onto a lamp and tap the screen centre */
  const TP = await ev(() => { const E = SK.ED, o = [...E.ADDED.values()].find(o => E.nameOf(o) === 'Snowman'), c = E.center(o).clone(); E.select(null); SK.snap({ tx: c.x, ty: c.y, tz: c.z, r: 9, theta: 0.3, phi: 1.2 }); return { k: o.userData.ek }; });
  await page.waitForTimeout(800); await page.screenshot({ path: OUT + '/editor-pre.png' }); await page.waitForTimeout(300);
  Object.assign(TP, await ev(() => { const E = SK.ED, o = [...E.ADDED.values()].find(o => E.nameOf(o) === 'Snowman'), c = E.center(o).clone(); SK.cam.updateMatrixWorld(); const v = c.clone().project(SK.cam), r = SK.renderer.domElement.getBoundingClientRect(); return { x: r.left + (v.x + 1) / 2 * r.width, y: r.top + (1 - v.y) / 2 * r.height }; }));
  await page.waitForTimeout(600); await page.mouse.click(TP.x, TP.y); await page.waitForTimeout(300);
  const tapped = await ev(() => SK.ED.S.sel.map(o => o.userData.ek));
  check(tapped.length > 0, 'tapping an object on screen selects it (' + tapped.join(',') + ' vs ' + TP.k + ')', fails);
  await page.screenshot({ path: OUT + '/editor-select.png' });

  /* save -> the regenerated page carries the edits; boot it fresh and compare */
  const html = await ev(() => SK.ED.pageHTML());
  const saved = { obj: await ev(() => JSON.parse(JSON.stringify(SK.ED.E.obj))), adds: C.adds + 1 };
  check(html.indexOf('<script id="pp-game">') > 0 && html.indexOf('id="pp-edits">{"v":1') > 0 && /<\/body><\/html>$/.test(html), 'save builds a full page (' + (html.length / 1e6).toFixed(2) + ' MB)', fails);
  const f = path.join(OUT, 'edited.html'); fs.writeFileSync(f, html);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close();

  const b2 = await boot({ file: f, viewport: { width: 420, height: 820 } });
  const R = await b2.page.evaluate(([lk, dk, nk, lab]) => { const E = SK.ED, o = E.byKey(lk), d = E.byKey(dk), N = E.NPCS.find(n => n.key === nk), s = SK.LABELS.find(x => x.userData.lk === lab);
    return { lampP: [o.position.x, o.position.z].map(v => +v.toFixed(2)), e: E.E.obj[lk] && E.E.obj[lk].p, adds: E.ADDED.size, snow: [...E.ADDED.values()].some(a => E.nameOf(a) === 'Snowman'), label: s.userData.ltxt, npc: !!(N.r.custom && N.r.custom.wp.length === 3), groups: E.E.groups.length, pre: !!window.PP_PRE }; }, [T.key, D.key, N.key, LB.k]);
  check(R.e && Math.abs(R.lampP[0] - R.e[0]) < 0.01 && Math.abs(R.lampP[1] - R.e[2]) < 0.01, 'reloaded page: moved lamp is where it was left', fails);
  check(R.adds === saved.adds && R.snow, 'reloaded page: pasted + added assets are back (' + R.adds + ')', fails);
  check(R.label === 'Renamed Place' && R.npc && R.groups === 1, 'reloaded page: label name, NPC path and group are back', fails);
  /* a second save from the reloaded page is byte-stable apart from the edits */
  const html2 = await b2.page.evaluate(() => SK.ED.pageHTML());
  check(html2 === html, 'saving again without changes gives the identical page', fails);
  check(b2.errors.length === 0, 'reloaded page: no errors ' + b2.errors.join(' | '), fails);
  await b2.browser.close();
  console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK');
  process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
