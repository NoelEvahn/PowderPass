// ───────────────────────── offices (OpenAI + Anthropic) ─────────────────────────
// Room x ∈ [-5, 5], z ∈ [-4, 3]; window wall at z = -4. Hero desk centred at (0, -1.6); chair at (0, -0.95).
const DESK_H = 0.5;
function buildOffice(name, origin, o) {
  const env = makeEnv(name, origin);
  const g = env.group; env.props = {};
  const floorTex = o.wood ? plankTex(o.floor, o.floorDark, 512, 512, 7, 61) : noiseTex(o.floor, 0.04, 256, 256, 61);
  floorTex.wrapS = floorTex.wrapT = THREE.RepeatWrapping; floorTex.repeat.set(4, 3);
  mesh(boxg(10.4, 0.1, 7.4), texMat(floorTex), 0, -0.05, -0.5, g, { cast: false });
  const wm = texMat(noiseTex(o.wall, 0.03, 256, 256, 62, [6, 3]));
  mesh(boxg(0.2, 2.8, 7.4), wm, -5.1, 1.4, -0.5, g);
  mesh(boxg(0.2, 2.8, 7.4), wm, 5.1, 1.4, -0.5, g);
  mesh(boxg(10.4, 0.1, 7.4), toon(o.ceiling), 0, 2.85, -0.5, g, { cast: false });
  // window wall: mullions + sky/city view
  const frame = toon(o.frame);
  for (let i = 0; i <= 6; i++) mesh(boxg(0.06, 2.8, 0.1), frame, -5 + i * (10 / 6), 1.4, -4.0, g, { ol: 0.004 });
  mesh(boxg(10.2, 0.08, 0.12), frame, 0, 0.4, -4.0, g); mesh(boxg(10.2, 0.4, 0.2), wm, 0, 0.2, -4.05, g);
  env.viewTex = canvasTex(1024, 384); env.viewKey = '';
  mesh(new THREE.PlaneGeometry(14, 5.2), new THREE.MeshBasicMaterial({ map: env.viewTex, toneMapped: false, fog: false }), 0, 1.8, -6.0, g, { cast: false, recv: false });
  // hero desk
  const desk = group(g, 0, 0, -1.6); env.desk = desk;
  const dtop = toon(o.desk), leg = toon(o.deskLeg);
  mesh(rbox(1.6, 0.04, 0.7, 0.015), dtop, 0, DESK_H - 0.02, 0, desk, { ol: 0.006 });
  for (const x of [-0.75, 0.75]) mesh(boxg(0.05, DESK_H - 0.04, 0.62), leg, x, (DESK_H - 0.04) / 2, 0, desk);
  // monitors
  env.screen = makeScreenTex(1024, 600);
  const mon = group(desk, 0, DESK_H, -0.18); env.monitor = mon;
  mesh(cylg(0.08, 0.1, 0.015, 18), leg, 0, 0.008, 0, mon); mesh(boxg(0.03, 0.22, 0.03), leg, 0, 0.11, -0.02, mon);
  mesh(rbox(0.86, 0.52, 0.03, 0.015), toon(0x1b1e22), 0, 0.42, 0, mon, { ol: 0.006 });
  env.screenMesh = mesh(new THREE.PlaneGeometry(0.82, 0.48), new THREE.MeshBasicMaterial({ map: env.screen, toneMapped: false }), 0, 0.42, 0.0165, mon, { cast: false });
  const mon2 = group(desk, 0.62, DESK_H, -0.12, -0.45);
  mesh(rbox(0.45, 0.32, 0.03, 0.012), toon(0x1b1e22), 0, 0.33, 0, mon2, { ol: 0.005 });
  mesh(boxg(0.03, 0.2, 0.03), leg, 0, 0.1, -0.02, mon2);
  env.screen2 = canvasTex(512, 360);
  mesh(new THREE.PlaneGeometry(0.42, 0.29), new THREE.MeshBasicMaterial({ map: env.screen2, toneMapped: false }), 0, 0.33, 0.0165, mon2, { cast: false });
  // keyboard + mouse + mug + plant + sticky notes
  mesh(rbox(0.4, 0.02, 0.13, 0.008), toon(o.kbd), 0, DESK_H + 0.01, 0.24, desk, { ol: 0.004 });
  for (let r = 0; r < 4; r++) mesh(boxg(0.36, 0.006, 0.022), toon(shadeHex(o.kbd, 0.85)), 0, DESK_H + 0.022, 0.19 + r * 0.03, desk, { cast: false });
  mesh(sphg(0.03, 10, 8), toon(o.kbd), 0.3, DESK_H + 0.01, 0.24, desk, { s: [0.8, 0.5, 1.2] });
  addPlant(desk, -0.62, DESK_H, -0.15, 0.55, o.pot, 0x4f9a6a);
  for (let i = 0; i < 3; i++) mesh(boxg(0.07, 0.07, 0.002), toon([0xffe27a, 0x9ff2d6, 0xffb3a0][i]), -0.32 + i * 0.09, DESK_H + 0.35 + (i % 2) * 0.05, -0.16, desk, { cast: false, rz: (i - 1) * 0.12 });
  // chair (seat top 0.32)
  const ch = group(g, 0, 0, -1.02); env.chair = ch;
  mesh(rbox(0.42, 0.06, 0.4, 0.03), toon(o.chair), 0, 0.29, 0, ch, { ol: 0.006 });
  mesh(rbox(0.42, 0.42, 0.06, 0.03), toon(o.chair), 0, 0.58, 0.2, ch, { ol: 0.006 });
  mesh(cylg(0.025, 0.025, 0.24, 8), leg, 0, 0.14, 0, ch);
  for (let i = 0; i < 5; i++) { const a = (i / 5) * TAU; mesh(boxg(0.03, 0.025, 0.22), leg, Math.sin(a) * 0.1, 0.02, Math.cos(a) * 0.1, ch, { ry: a }); }
  // background desks with glowing screens
  env.bgScreens = [];
  for (const [x, z] of [[-3.2, -2.2], [3.2, -2.2], [-3.2, 0.6], [3.2, 0.6]]) {
    const d = group(g, x, 0, z);
    mesh(rbox(1.4, 0.04, 0.6, 0.015), dtop, 0, DESK_H - 0.02, 0, d, { ol: 0.005 });
    for (const lx of [-0.65, 0.65]) mesh(boxg(0.04, DESK_H - 0.04, 0.55), leg, lx, (DESK_H - 0.04) / 2, 0, d);
    mesh(rbox(0.6, 0.38, 0.03, 0.012), toon(0x1b1e22), 0, DESK_H + 0.3, -0.15, d);
    const sc = mesh(new THREE.PlaneGeometry(0.56, 0.34), new THREE.MeshBasicMaterial({ map: bgScreenTex(o.theme, x + z), toneMapped: false }), 0, DESK_H + 0.3, -0.134, d, { cast: false });
    env.bgScreens.push(sc);
    mesh(rbox(0.38, 0.05, 0.36, 0.02), toon(o.chair), 0, 0.29, 0.5, d, { ol: 0.004 });
  }
  // ceiling light panels
  for (const x of [-2.5, 0, 2.5]) for (const z of [-2.4, 0.4]) mesh(boxg(1.2, 0.03, 0.5), glow(0xfff8ec, 1.1), x, 2.79, z, g, { cast: false });
  // whiteboard on left wall
  const wb = canvasTex(512, 300, (c, w, h) => {
    c.fillStyle = '#fbfbf8'; c.fillRect(0, 0, w, h);
    c.strokeStyle = o.theme === 'oa' ? '#1f8f7a' : '#c8693a'; c.lineWidth = 5; c.lineCap = 'round';
    c.font = `700 30px ${FONT_UI}`; c.fillStyle = '#2c3238';
    c.fillText(o.theme === 'oa' ? 'SHIP FAST ✦' : 'THINK → TEST → SHIP', 30, 50);
    for (let i = 0; i < 4; i++) { c.strokeRect(40 + i * 115, 110, 80, 50); if (i < 3) { c.beginPath(); c.moveTo(120 + i * 115, 135); c.lineTo(155 + i * 115, 135); c.stroke(); } }
    c.beginPath(); c.moveTo(40, 250); for (let x = 0; x < 420; x += 20) c.lineTo(40 + x, 250 - x * 0.18 - Math.sin(x / 30) * 10); c.stroke();
  });
  const wbg = group(g, -4.97, 1.4, -1.2, Math.PI / 2);
  mesh(boxg(1.9, 1.1, 0.04), toon(0xd8dde0), 0, 0, 0, wbg, { ol: 0.005 });
  mesh(new THREE.PlaneGeometry(1.8, 1.0), new THREE.MeshToonMaterial({ map: wb, gradientMap: GRAD4 }), 0, 0, 0.021, wbg, { cast: false });
  // big plants
  addPlant(g, -4.5, 0, -3.5, 2.0, o.pot, 0x3d8f62); addPlant(g, 4.5, 0, -3.5, 2.0, o.pot, 0x4f9a6a); addPlant(g, 4.6, 0, 2.2, 1.6, o.pot, 0x3d8f62);
  // signature piece: server rack (OpenAI) / bookshelf + couch (Anthropic)
  env.leds = [];
  if (o.theme === 'oa') {
    const rack = group(g, 4.6, 0, -0.9);
    mesh(rbox(0.6, 1.9, 0.7, 0.03), toon(0x22272b), 0, 0.95, 0, rack, { ol: 0.008 });
    for (let i = 0; i < 9; i++) { mesh(boxg(0.5, 0.14, 0.01), toon(0x2f363c), -0.0, 0.3 + i * 0.18, 0.352, rack, { ry: 0, cast: false }); const led = mesh(boxg(0.03, 0.02, 0.01), glow(PAL.cxMint, 1.8).clone(), -0.18, 0.3 + i * 0.18, 0.36, rack, { cast: false }); env.leds.push(led); }
    rack.rotation.y = -Math.PI / 2;
    const logo = canvasTex(512, 128, (c, w, h) => { c.fillStyle = '#f6f7f7'; c.fillRect(0, 0, w, h); c.fillStyle = '#111416'; c.font = `700 70px ${FONT_UI}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('OpenAI', w / 2, h / 2 + 4); });
    mesh(new THREE.PlaneGeometry(1.6, 0.4), new THREE.MeshToonMaterial({ map: logo, gradientMap: GRAD4 }), 4.99, 2.0, 1.0, g, { ry: -Math.PI / 2, cast: false });
  } else {
    const bs = group(g, 4.85, 0, -0.5, -Math.PI / 2);
    mesh(boxg(1.6, 1.8, 0.3), toon(0x9a6440), 0, 0.9, 0, bs, { ol: 0.008 });
    for (let i = 0; i < 4; i++) { mesh(boxg(1.52, 0.03, 0.28), toon(0xc99668), 0, 0.12 + i * 0.44, 0.02, bs); addBooks(bs, 0, 0.135 + i * 0.44, 0.04, 1.4, 90 + i); }
    const couch = group(g, 3.4, 0, 2.1, Math.PI);
    mesh(rbox(1.5, 0.3, 0.6, 0.1), toon(0xc87a4a), 0, 0.2, 0, couch, { ol: 0.008 });
    mesh(rbox(1.5, 0.45, 0.16, 0.08), toon(0xc87a4a), 0, 0.45, -0.25, couch, { ol: 0.008 });
    const logo = canvasTex(768, 128, (c, w, h) => { c.fillStyle = '#f1e1c8'; c.fillRect(0, 0, w, h); c.fillStyle = '#191512'; c.font = `700 62px ${FONT_UI}`; c.letterSpacing = '12px'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('ANTHROPIC', w / 2, h / 2 + 4); });
    mesh(new THREE.PlaneGeometry(2.0, 0.33), new THREE.MeshToonMaterial({ map: logo, gradientMap: GRAD4 }), -4.99, 2.2, 1.2, g, { ry: Math.PI / 2, cast: false });
  }
  // desk mug prop
  const mug = group(desk, -0.4, DESK_H, 0.2);
  mesh(cylg(0.04, 0.034, 0.08, 14), toon(o.theme === 'oa' ? 0xffffff : 0xf6e8d2), 0, 0.04, 0, mug, { ol: 0.004 });
  mesh(torg(0.022, 0.006, 6, 12), toon(o.theme === 'oa' ? 0xffffff : 0xf6e8d2), 0.045, 0.04, 0, mug, { cast: false });
  // lights
  env.hemi = new THREE.HemisphereLight(o.hemiSky, o.hemiGround, 1.0); g.add(env.hemi);
  env.amb = new THREE.AmbientLight(0xffffff, 0.35); g.add(env.amb);
  const key = new THREE.DirectionalLight(0xfff4e6, 0.7); key.position.set(-2, 5, 5); g.add(key); g.add(key.target); env.key = key;
  makeSun(env, o.sunC, 1.6, [0.3, 0.9, -0.5], 5);
  env.deskLamp = new THREE.PointLight(o.theme === 'oa' ? 0x9ff2e0 : 0xffc890, 0, 3, 2); env.deskLamp.position.set(0, 1.0, -1.3); g.add(env.deskLamp);
  env.bg = new THREE.Color(0xdde4e6);
  env.theme = o.theme;
  env.updaters.push((t) => updateOffice(env, t));
  return env;
}
function bgScreenTex(theme, seed) {
  return canvasTex(256, 160, (c, w, h) => {
    const r = rng(Math.floor(seed * 10 + 50));
    c.fillStyle = theme === 'oa' ? '#0f1619' : '#1c130e'; c.fillRect(0, 0, w, h);
    for (let i = 0; i < 12; i++) { c.fillStyle = theme === 'oa' ? ['#5ff2cf', '#8ab4ff', '#e6edf0', '#ffcf6b'][Math.floor(r() * 4)] : ['#ffb877', '#e6c79a', '#9fd39a', '#f2e6d8'][Math.floor(r() * 4)]; c.globalAlpha = 0.85; c.fillRect(12 + r() * 30, 14 + i * 11, 40 + r() * 150, 5); }
  });
}
// day → sunset → night through the office window, by film time
function officeDayness(t) { return 1 - sseg(t, 104, 117); }
function updateOffice(env, t) {
  const d = officeDayness(t);
  const key = Math.round(d * 20);
  if (env.viewKey !== key) {
    env.viewKey = key;
    const c = env.viewTex.userData.ctx, w = 1024, h = 384;
    const top = new THREE.Color(0x0b1430).lerp(new THREE.Color(0x7fb6e6), d);
    const bot = new THREE.Color(0xff9a5a).lerp(new THREE.Color(0xf4e6d0), clamp(d * 1.6 - 0.3));
    if (d < 0.25) bot.lerp(new THREE.Color(0x1a2448), 1 - d * 4);
    const gr = c.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#' + top.getHexString()); gr.addColorStop(1, '#' + bot.getHexString());
    c.fillStyle = gr; c.fillRect(0, 0, w, h);
    const r = rng(env.theme === 'oa' ? 12 : 34);
    for (let layer = 0; layer < 2; layer++) {
      let x = 0; const col = new THREE.Color(layer ? 0x5a6a80 : 0x8a9ab0).lerp(new THREE.Color(layer ? 0x0e1324 : 0x182038), 1 - d);
      while (x < w) {
        const bw = 30 + r() * 60, bh = (layer ? 120 : 180) + r() * 140;
        c.fillStyle = '#' + col.getHexString(); c.fillRect(x, h - bh, bw, bh);
        for (let wy = h - bh + 10; wy < h - 6; wy += 14) for (let wx = x + 6; wx < x + bw - 6; wx += 11) if (r() > 0.55) { c.fillStyle = `rgba(255,${200 + Math.floor(r() * 40)},140,${0.15 + (1 - d) * 0.8})`; c.fillRect(wx, wy, 5, 7); }
        x += bw + 3;
      }
    }
    env.viewTex.needsUpdate = true;
  }
  env.sun.intensity = 1.6 * d; env.sun.color.set(0xfff0d8).lerp(new THREE.Color(0xff9a5a), 1 - d);
  env.hemi.intensity = lerp(0.55, 1.0, d);
  env.hemi.color.set(0x7080b0).lerp(new THREE.Color(0xe0ecff), d);
  env.key.intensity = lerp(0.35, 0.7, d);
  env.deskLamp.intensity = lerp(0.5, 0, d);
  env.bg.set(0x1a2034).lerp(new THREE.Color(0xdde4e6), d);
  env.leds.forEach((l, i) => { l.material.color.set(Math.floor(t * 3 + i * 1.7) % 3 ? PAL.cxMint : 0x1a3a34).multiplyScalar(1.6); });
}

BUILDERS.push(() => buildOffice('oaOffice', new THREE.Vector3(0, 0, -60), {
  theme: 'oa', floor: '#d6d9d8', floorDark: '#b8bcbb', wall: '#eef1f1', ceiling: 0xf6f7f7, frame: 0x2c3238, desk: 0xf3f4f4, deskLeg: 0x2c3238,
  kbd: 0x2c3238, pot: 0xf3f3f3, chair: 0x2c3238, hemiSky: 0xe6f2ff, hemiGround: 0xb8bcbb, sunC: 0xfff0d8,
}));
BUILDERS.push(() => buildOffice('anOffice', new THREE.Vector3(40, 0, -60), {
  theme: 'an', wood: true, floor: '#b0805a', floorDark: '#7a5236', wall: '#f2e2cb', ceiling: 0xf6ecdd, frame: 0x7a4a30, desk: 0xd9b48a, deskLeg: 0x4a2e20,
  kbd: 0xf3e6d3, pot: 0xc8693a, chair: 0xc87a4a, hemiSky: 0xfff0dc, hemiGround: 0xa27a5a, sunC: 0xffe6c4,
}));
