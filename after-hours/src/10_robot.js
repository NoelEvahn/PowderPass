// ───────────────────────── robot mascots ─────────────────────────
// Rig: root(=hip pivot, world) → torso → neck → head; root → hip → knee → ankle; torso → shoulder → elbow → wrist
// Characters face +z in local space. Left side = +x.

const POSE_KEYS = ['x', 'y', 'z', 'yaw', 'pitch', 'roll', 'spX', 'spY', 'spZ', 'nkX', 'nkY', 'nkZ',
  'lsF', 'lsO', 'lsT', 'leB', 'lwX', 'lwZ', 'lG', 'rsF', 'rsO', 'rsT', 'reB', 'rwX', 'rwZ', 'rG',
  'llF', 'llO', 'lkB', 'laX', 'rlF', 'rlO', 'rkB', 'raX', 'ground', 'fy', 'flat'];
const REST = {
  x: 0, y: 0, z: 0, yaw: 0, pitch: 0, roll: 0, spX: 0, spY: 0, spZ: 0, nkX: 0, nkY: 0, nkZ: 0,
  lsF: 0.05, lsO: 0.1, lsT: 0, leB: 0.25, lwX: 0, lwZ: 0, lG: 0.25,
  rsF: 0.05, rsO: 0.1, rsT: 0, reB: 0.25, rwX: 0, rwZ: 0, rG: 0.25,
  llF: 0, llO: 0.02, lkB: 0, laX: 0, rlF: 0, rlO: 0.02, rkB: 0, raX: 0, ground: 1, fy: 0, flat: 1,
};
function P(o) { return Object.assign({}, REST, o); }

function buildHand(parent, side, matA, matB, scale = 1) {
  const h = group(parent);
  h.rotation.y = side * Math.PI / 2 * -1; // palm faces inward
  const s = scale;
  const palm = mesh(rbox(0.07 * s, 0.07 * s, 0.045 * s, 0.018 * s), matA, 0, -0.035 * s, 0, h, { ol: 0.006 });
  const fingers = group(h, 0, -0.065 * s, 0.0);
  mesh(rbox(0.066 * s, 0.05 * s, 0.04 * s, 0.017 * s), matA, 0, -0.022 * s, 0.002 * s, fingers, { ol: 0.006 });
  // finger grooves
  for (let i = -1; i <= 1; i += 2) mesh(boxg(0.002, 0.03 * s, 0.042 * s), matB, i * 0.011 * s, -0.03 * s, 0.002 * s, fingers, { cast: false });
  const thumbJ = group(h, -0.03 * s * 0 + 0.0, -0.03 * s, 0.022 * s);
  const thumb = mesh(capg(0.013 * s, 0.026 * s), matB, 0, -0.012 * s, 0.006 * s, thumbJ, { ol: 0.005 });
  thumb.rotation.x = 0.5;
  const socket = group(h, 0, -0.07 * s, 0.035 * s);
  return { h, fingers, thumbJ, socket };
}

function buildRobot(kind) {
  const isCx = kind === 'codex';
  const R = { kind, j: {}, face: { open: 1, lookX: 0, lookY: 0, mood: 'neutral', talk: 0, boot: 1, blink: true, glow: 1, extra: null }, props: {} };
  const root = new THREE.Group(); root.rotation.order = 'YXZ'; R.root = root;
  const OL = 0.009;
  let A, B, C, D, E; // main, secondary, accent, dark, glow-accent
  if (isCx) {
    A = toon(PAL.cxWhite); B = toon(PAL.cxGraphite); C = toon(PAL.cxMint, { emissive: PAL.cxMint, ei: 0.35 });
    D = toon(PAL.cxDark); E = glow(PAL.cxMint, 1.8);
    R.dim = { H: 0.375, Lt: 0.15, Ls: 0.15, ankle: 0.075 };
  } else {
    A = toon(PAL.ccCream); B = toon(PAL.ccCopper); C = toon(PAL.ccAmber, { emissive: PAL.ccAmber, ei: 0.4 });
    D = toon(PAL.ccDeep); E = glow(PAL.ccAmber, 1.7);
    R.dim = { H: 0.335, Lt: 0.135, Ls: 0.135, ankle: 0.065 };
  }
  R.mats = { A, B, C, D, E };
  const { Lt, Ls, ankle } = R.dim;

  // ── torso ──
  const torso = group(root); torso.rotation.order = 'YXZ'; R.j.torso = torso;
  if (isCx) {
    mesh(rbox(0.25, 0.08, 0.19, 0.03), B, 0, 0.02, 0, torso, { ol: OL }); // pelvis
    const body = mesh(rbox(0.36, 0.27, 0.26, 0.07, 4), A, 0, 0.19, 0, torso, { ol: OL });
    // graphite side bands
    mesh(rbox(0.372, 0.04, 0.21, 0.018), B, 0, 0.11, 0, torso);
    // chest plate with >_ emblem
    const chestTex = canvasTex(128, 80, (c, w, h) => {
      rrect(c, 2, 2, w - 4, h - 4, 18); c.fillStyle = '#0c1417'; c.fill();
      c.strokeStyle = '#2b4a4c'; c.lineWidth = 4; c.stroke();
      c.fillStyle = '#5ff2cf'; c.shadowColor = '#3fe6c0'; c.shadowBlur = 10;
      c.font = `bold 46px ${FONT_MONO}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('>_', w / 2, h / 2 + 2);
    });
    R.chestTex = chestTex;
    const plate = mesh(rbox(0.17, 0.105, 0.02, 0.02), D, 0, 0.21, 0.125, torso);
    mesh(new THREE.PlaneGeometry(0.16, 0.098), new THREE.MeshBasicMaterial({ map: chestTex, toneMapped: false }), 0, 0.21, 0.1362, torso, { cast: false });
    // mint seam lines
    for (const sx of [-1, 1]) mesh(boxg(0.006, 0.12, 0.004), E, sx * 0.13, 0.22, 0.131, torso, { cast: false });
    // back battery pack w/ charge bars
    mesh(rbox(0.2, 0.17, 0.07, 0.03), B, 0, 0.2, -0.14, torso, { ol: OL });
    R.charge = [];
    for (let i = 0; i < 3; i++) R.charge.push(mesh(rbox(0.13, 0.024, 0.01, 0.006), glow(PAL.cxMint, 1.5).clone(), 0, 0.15 + i * 0.045, -0.178, torso, { cast: false }));
    void body; void plate;
  } else {
    mesh(sphg(0.13, 20, 12), B, 0, 0.035, 0, torso, { s: [1.1, 0.55, 0.95], ol: OL }); // copper pelvis
    mesh(sphg(0.2, 28, 20), A, 0, 0.185, 0, torso, { s: [1.0, 0.92, 0.85], ol: OL });
    // belly panel: rounded copper disc with glowing check
    const bellyTex = canvasTex(128, 128, (c, w, h) => {
      c.fillStyle = '#3a2015'; c.beginPath(); c.arc(64, 64, 60, 0, TAU); c.fill();
      c.strokeStyle = '#d98a54'; c.lineWidth = 6; c.stroke();
      c.strokeStyle = '#ffc07a'; c.shadowColor = '#ffa65a'; c.shadowBlur = 12; c.lineWidth = 11; c.lineCap = 'round'; c.lineJoin = 'round';
      c.beginPath(); c.moveTo(38, 66); c.lineTo(56, 84); c.lineTo(90, 46); c.stroke();
    });
    R.bellyTex = bellyTex;
    const belly = mesh(cylg(0.068, 0.068, 0.012, 32), B, 0, 0.17, 0.155, torso, { rx: Math.PI / 2 - 0.25 });
    mesh(new THREE.CircleGeometry(0.058, 32), new THREE.MeshBasicMaterial({ map: bellyTex, toneMapped: false }), 0, 0.172, 0.163, torso, { cast: false, rx: -0.25 });
    void belly;
    // copper back coil
    mesh(torg(0.05, 0.014, 8, 20), B, 0, 0.2, -0.165, torso, { ol: 0.006 });
    mesh(sphg(0.03), C, 0, 0.2, -0.17, torso);
  }

  // ── neck + head ──
  const neckY = isCx ? 0.335 : 0.33;
  const neck = group(torso, 0, neckY, 0); neck.rotation.order = 'YXZ'; R.j.neck = neck;
  mesh(cylg(0.045, 0.055, 0.05, 14), isCx ? B : B, 0, 0.0, 0, neck);
  const head = group(neck, 0, isCx ? 0.2 : 0.21, 0); R.j.head = head;
  if (isCx) {
    mesh(rbox(0.54, 0.4, 0.4, 0.1, 5), A, 0, 0, 0, head, { ol: OL });
    // graphite brow visor band
    mesh(rbox(0.5, 0.05, 0.08, 0.02), B, 0, 0.175, 0.155, head, { ol: 0.006 });
    // face bezel + screen
    mesh(rbox(0.46, 0.3, 0.04, 0.07), D, 0, -0.01, 0.19, head);
    const faceTex = canvasTex(320, 208); R.faceTex = faceTex;
    R.faceMesh = mesh(new THREE.PlaneGeometry(0.43, 0.28), new THREE.MeshBasicMaterial({ map: faceTex, toneMapped: false, transparent: true }), 0, -0.01, 0.2105, head, { cast: false });
    // ear modules: graphite discs + mint chevrons  < >
    for (const sx of [-1, 1]) {
      const ear = group(head, sx * 0.272, 0.0, 0.0);
      mesh(cylg(0.075, 0.075, 0.04, 6), B, 0, 0, 0, ear, { rz: Math.PI / 2, ol: 0.006 });
      mesh(torg(0.05, 0.008, 6, 6), E, sx * 0.022, 0, 0, ear, { ry: Math.PI / 2, cast: false });
      const shape = new THREE.Shape();
      shape.moveTo(0, 0.06); shape.lineTo(0.05, 0); shape.lineTo(0, -0.06); shape.lineTo(-0.02, -0.06); shape.lineTo(0.025, 0); shape.lineTo(-0.02, 0.06);
      const chev = mesh(G('chev', () => new THREE.ExtrudeGeometry(shape, { depth: 0.02, bevelEnabled: false })), C, sx * 0.03, 0, -0.01, ear, { ol: 0.004 });
      chev.rotation.y = sx > 0 ? 0 : Math.PI; chev.position.z = sx > 0 ? -0.01 : 0.01;
    }
    // antenna: rod with blinking "cursor" block
    const ant = group(head, 0.15, 0.19, -0.04); R.j.antenna = ant;
    mesh(cylg(0.008, 0.012, 0.13, 8), B, 0, 0.065, 0, ant);
    mesh(sphg(0.018, 10, 8), B, 0, 0.0, 0, ant);
    R.cursor = mesh(rbox(0.032, 0.055, 0.02, 0.006), glow(PAL.cxMint, 2.2).clone(), 0, 0.155, 0, ant, { cast: false });
    // little side vents
    for (const sx of [-1, 1]) for (let i = 0; i < 3; i++) mesh(boxg(0.003, 0.012, 0.09), B, sx * 0.271, -0.13 + i * 0.02, 0.06, head, { cast: false });
  } else {
    const headR = 0.25;
    const hs = [1.1, 0.9, 0.97];
    mesh(sphg(headR, 40, 28), A, 0, 0, 0, head, { s: hs, ol: OL });
    // warm face screen: spherical patch
    const pw = 1.55, ph = 1.0;
    const faceGeo = G('ccFace', () => new THREE.SphereGeometry(headR * 1.012, 40, 24, Math.PI / 2 - pw / 2, pw, Math.PI / 2 - ph / 2 + 0.06, ph));
    const bezelGeo = G('ccBezel', () => new THREE.SphereGeometry(headR * 1.006, 40, 24, Math.PI / 2 - pw / 2 - 0.06, pw + 0.12, Math.PI / 2 - ph / 2 + 0.0, ph + 0.12));
    const bez = mesh(bezelGeo, D, 0, 0, 0, head, { s: hs, cast: false });
    const faceTex = canvasTex(320, 208); R.faceTex = faceTex;
    R.faceMesh = mesh(faceGeo, new THREE.MeshBasicMaterial({ map: faceTex, toneMapped: false, transparent: true }), 0, 0, 0, head, { s: hs, cast: false });
    void bez;
    // headphone ears
    for (const sx of [-1, 1]) {
      const ear = group(head, sx * 0.268, -0.01, -0.01);
      mesh(cylg(0.072, 0.078, 0.06, 28), B, 0, 0, 0, ear, { rz: Math.PI / 2, ol: 0.006 });
      mesh(torg(0.048, 0.009, 8, 28), E, sx * 0.032, 0, 0, ear, { ry: Math.PI / 2, cast: false });
      mesh(cylg(0.04, 0.04, 0.01, 24), A, sx * 0.031, 0, 0, ear, { rz: Math.PI / 2, cast: false });
      if (sx > 0) R.j.earL = ear;
    }
    // headband arc
    mesh(torg(0.255, 0.014, 8, 40, Math.PI * 0.62), B, 0, 0.01, -0.03, head, { rz: Math.PI * 0.19, s: [1.08, 0.96, 1], ol: 0.005 });
    // starburst antenna
    const ant = group(head, 0, 0.225, -0.02); R.j.antenna = ant;
    mesh(cylg(0.01, 0.014, 0.07, 8), B, 0, 0.035, 0, ant);
    const star = group(ant, 0, 0.09, 0); R.star = star;
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * TAU;
      const ray = mesh(capg(0.011, 0.04 + (i % 2) * 0.012, 6), C, Math.sin(a) * 0.03, Math.cos(a) * 0.03, 0, star, { rz: -a, cast: false });
      void ray;
    }
    mesh(sphg(0.017, 12, 8), E, 0, 0, 0.004, star, { cast: false });
    // flip-down review loupe on left ear
    const lh = group(head, 0.282, -0.01, 0.0); R.j.loupe = lh;
    const arm = mesh(boxg(0.012, 0.012, 0.2), B, -0.0, 0, 0.1, lh, { cast: false });
    const arm2 = mesh(boxg(0.12, 0.012, 0.012), B, -0.06, 0, 0.2, lh, { cast: false });
    void arm; void arm2;
    const lens = group(lh, -0.12, 0, 0.215);
    mesh(torg(0.045, 0.009, 8, 28), B, 0, 0, 0, lens, { ol: 0.004 });
    mesh(new THREE.CircleGeometry(0.045, 24), new THREE.MeshBasicMaterial({ color: 0xffe2b8, transparent: true, opacity: 0.25, toneMapped: false }), 0, 0, 0, lens, { cast: false });
    // knitted scarf
    const scarfMat = toon(0xd9773f);
    const scarfTex = canvasTex(64, 16, (c) => { c.fillStyle = '#d9773f'; c.fillRect(0, 0, 64, 16); c.fillStyle = '#f2b06a'; for (let x = 0; x < 64; x += 16) c.fillRect(x, 0, 6, 16); }, { repeat: [8, 1] });
    const sm = new THREE.MeshToonMaterial({ map: scarfTex, gradientMap: GRAD3 });
    void scarfMat;
    mesh(torg(0.105, 0.034, 10, 28), sm, 0, 0.005, 0, neck, { rx: Math.PI / 2, s: [1.05, 1, 1], ol: 0.006 });
    const tail = group(neck, 0.06, -0.01, 0.09); R.j.scarf = tail;
    const t1 = mesh(rbox(0.06, 0.12, 0.02, 0.01), sm, 0, -0.06, 0, tail, { ol: 0.005 });
    const tail2 = group(tail, 0, -0.115, 0); R.j.scarf2 = tail2;
    mesh(rbox(0.06, 0.07, 0.02, 0.01), sm, 0, -0.03, 0, tail2, { ol: 0.005 });
    for (let i = 0; i < 4; i++) mesh(boxg(0.008, 0.022, 0.012), sm, -0.022 + i * 0.015, -0.072, 0, tail2, { cast: false });
    void t1;
  }

  // ── arms ──
  const shY = isCx ? 0.27 : 0.25, shX = isCx ? 0.205 : 0.195;
  const La = isCx ? 0.12 : 0.11, Lf = isCx ? 0.11 : 0.1;
  R.dim.La = La; R.dim.Lf = Lf;
  for (const side of [1, -1]) {
    const p = side > 0 ? 'l' : 'r';
    const sh = group(torso, side * shX, shY, 0); sh.rotation.order = 'ZYX'; R.j[p + 's'] = sh;
    if (isCx) {
      mesh(sphg(0.05, 16, 12), B, 0, 0, 0, sh, { ol: 0.006 });
      mesh(rbox(0.07, 0.035, 0.08, 0.015), A, side * 0.02, 0.03, 0, sh, { ol: 0.006 }); // shoulder pad
      mesh(cylg(0.032, 0.03, La, 12), B, 0, -La / 2, 0, sh);
    } else {
      mesh(sphg(0.048, 16, 12), A, 0, 0, 0, sh, { ol: 0.006 });
      mesh(capg(0.036, La - 0.04), A, 0, -La / 2, 0, sh, { ol: 0.006 });
    }
    const el = group(sh, 0, -La, 0); el.rotation.order = 'ZYX'; R.j[p + 'e'] = el;
    mesh(sphg(isCx ? 0.034 : 0.036, 12, 10), isCx ? C : B, 0, 0, 0, el, { ol: 0.005 });
    if (isCx) mesh(rbox(0.07, Lf, 0.07, 0.025), A, 0, -Lf / 2, 0, el, { ol: 0.006 });
    else mesh(capg(0.04, Lf - 0.04), A, 0, -Lf / 2, 0, el, { ol: 0.006 });
    if (isCx) mesh(boxg(0.072, 0.008, 0.072), E, 0, -Lf + 0.012, 0, el, { cast: false }); // mint cuff
    else mesh(torg(0.036, 0.008, 6, 20), B, 0, -Lf + 0.01, 0, el, { rx: Math.PI / 2, cast: false });
    const wr = group(el, 0, -Lf, 0); wr.rotation.order = 'ZYX'; R.j[p + 'w'] = wr;
    const hand = buildHand(wr, side, A, isCx ? B : B, isCx ? 1.05 : 1.08);
    R.j[p + 'hand'] = hand;
  }

  // ── legs ──
  const hipX = isCx ? 0.085 : 0.08;
  for (const side of [1, -1]) {
    const p = side > 0 ? 'l' : 'r';
    const hip = group(root, side * hipX, 0, 0); hip.rotation.order = 'ZYX'; R.j[p + 'h'] = hip;
    if (isCx) {
      mesh(sphg(0.042, 12, 10), B, 0, 0, 0, hip);
      mesh(cylg(0.036, 0.032, Lt, 12), B, 0, -Lt / 2, 0, hip, { ol: 0.006 });
    } else {
      mesh(capg(0.042, Lt - 0.03), A, 0, -Lt / 2 + 0.005, 0, hip, { ol: 0.006 });
    }
    const kn = group(hip, 0, -Lt, 0); R.j[p + 'k'] = kn;
    mesh(sphg(isCx ? 0.034 : 0.038, 12, 10), isCx ? C : B, 0, 0, 0, kn);
    if (isCx) mesh(rbox(0.075, Ls, 0.08, 0.025), A, 0, -Ls / 2, 0, kn, { ol: 0.006 });
    else mesh(capg(0.04, Ls - 0.035), A, 0, -Ls / 2, 0, kn, { ol: 0.006 });
    const an = group(kn, 0, -Ls, 0); R.j[p + 'a'] = an;
    if (isCx) {
      mesh(rbox(0.11, 0.065, 0.19, 0.03), A, 0, -ankle + 0.035, 0.025, an, { ol: 0.007 });
      mesh(rbox(0.112, 0.022, 0.192, 0.01), B, 0, -ankle + 0.011, 0.025, an);
      mesh(boxg(0.06, 0.006, 0.04), E, 0, -ankle + 0.068, 0.07, an, { cast: false });
    } else {
      mesh(sphg(0.07, 18, 12), A, 0, -ankle + 0.035, 0.03, an, { s: [0.85, 0.55, 1.35], ol: 0.007 });
      mesh(cylg(0.06, 0.062, 0.018, 18), B, 0, -ankle + 0.009, 0.03, an, { s: [0.85, 1, 1.4] });
    }
    an.userData.sole = [new THREE.Vector3(0, -ankle, -0.07), new THREE.Vector3(0, -ankle, 0.12)];
  }

  // shadows on, outlines off for shadow
  root.traverse((o) => { if (o.isMesh && o.userData.isOutline) o.castShadow = false; });
  R.root.userData.robot = R;
  R.sec = { ax: 0, av: 0, sx: 0, sv: 0, last: null };
  return R;
}

const _v1 = new THREE.Vector3(), _v2 = new THREE.Vector3();
function applyPose(R, p) {
  const j = R.j, H = R.dim.H;
  R.root.position.set(p.x, p.y, p.z);
  R.root.rotation.set(p.pitch, p.yaw, p.roll);
  j.torso.rotation.set(p.spX, p.spY, p.spZ);
  j.neck.rotation.set(p.nkX, p.nkY, p.nkZ);
  j.ls.rotation.set(-p.lsF, p.lsT, p.lsO);
  j.le.rotation.set(-p.leB, 0, 0);
  j.lw.rotation.set(-p.lwX, 0, p.lwZ);
  j.rs.rotation.set(-p.rsF, -p.rsT, -p.rsO);
  j.re.rotation.set(-p.reB, 0, 0);
  j.rw.rotation.set(-p.rwX, 0, -p.rwZ);
  j.lhand.fingers.rotation.x = p.lG * 1.5; j.lhand.thumbJ.rotation.x = p.lG * 0.6;
  j.rhand.fingers.rotation.x = p.rG * 1.5; j.rhand.thumbJ.rotation.x = p.rG * 0.6;
  j.lh.rotation.set(-p.llF, 0, p.llO);
  j.lk.rotation.set(p.lkB, 0, 0);
  j.la.rotation.set(-(p.laX + p.flat * (p.pitch - p.llF + p.lkB)), 0, -p.llO);
  j.rh.rotation.set(-p.rlF, 0, -p.rlO);
  j.rk.rotation.set(p.rkB, 0, 0);
  j.ra.rotation.set(-(p.raX + p.flat * (p.pitch - p.rlF + p.rkB)), 0, p.rlO);
  if (p.ground > 0) {
    // plant the lowest sole point on the floor (blend with explicit y for hops/landings)
    R.root.position.y = H;
    R.root.updateMatrixWorld(true);
    let minY = Infinity;
    for (const a of [j.la, j.ra]) for (const s of a.userData.sole) { _v1.copy(s); a.localToWorld(_v1); if (R.root.parent) R.root.parent.worldToLocal(_v1); minY = Math.min(minY, _v1.y); }
    const gy = H + (p.fy || 0) - minY;
    R.root.position.y = lerp(p.y, gy, clamp(p.ground));
  }
  R.root.updateMatrixWorld(true);
}

// procedural walk cycle layered on a pose. phase in radians, amt 0..1, style: {stride, arm, bounce}
function walkLayer(p, phase, amt, style = {}) {
  const sw = (style.leg ?? 0.42) * amt, arm = (style.arm ?? 0.45) * amt;
  const s = Math.sin(phase), c = Math.cos(phase);
  if (style.side) { p.llO += amt * 0.22 * Math.max(0, s); p.rlO += amt * 0.22 * Math.max(0, -s); p.lkB += amt * 0.5 * Math.max(0, s); p.rkB += amt * 0.5 * Math.max(0, -s); p.roll += 0.04 * amt * s; return p; }
  p.llF += sw * s; p.rlF -= sw * s;
  // knee bends in swing (when leg moves forward)
  p.lkB += amt * (0.15 + 0.7 * Math.max(0, c)) * (style.knee ?? 1);
  p.rkB += amt * (0.15 + 0.7 * Math.max(0, -c)) * (style.knee ?? 1);
  p.laX += -0.25 * amt * s; p.raX += 0.25 * amt * s;
  p.lsF -= arm * s; p.rsF += arm * s;
  p.leB += 0.2 * amt; p.reB += 0.2 * amt;
  p.spY += 0.08 * amt * s;
  p.spX += (style.lean ?? 0.06) * amt;
  p.roll += 0.03 * amt * c;
  p.nkY -= 0.05 * amt * s;
  return p;
}
function strideLen(R, style = {}) {
  const L = R.dim.Lt + R.dim.Ls;
  return 2 * L * Math.sin(style.leg ?? 0.42) * 0.92; // distance per half cycle (one step)
}

// Movement along a polyline over time with eased motion; returns {x,z,yaw,dist,speed}
function pathMover(points, t0, t1, easeFn = EASE.walk) {
  const segs = []; let total = 0;
  const mk = (fn) => { fn.t0 = t0; fn.t1 = t1; return fn; };
  for (let i = 0; i < points.length - 1; i++) {
    const a = points[i], b = points[i + 1];
    const l = Math.hypot(b[0] - a[0], b[1] - a[1]);
    segs.push({ a, b, l, s: total }); total += l;
  }
  return mk((t) => {
    const u = easeFn(seg(t, t0, t1));
    const d = u * total;
    let sg = segs[segs.length - 1];
    for (const s of segs) if (d <= s.s + s.l) { sg = s; break; }
    const f = sg.l > 0 ? (d - sg.s) / sg.l : 0;
    const dt = 0.02;
    const u2 = easeFn(seg(t + dt, t0, t1));
    const speed = ((u2 - u) * total) / dt;
    return { x: lerp(sg.a[0], sg.b[0], f), z: lerp(sg.a[1], sg.b[1], f), yaw: Math.atan2(sg.b[0] - sg.a[0], sg.b[1] - sg.a[1]), dist: d, speed, total };
  });
}

// helper: walk pose at time t along mover; style to tune; returns pose
function walkPose(R, mover, t, base, style = {}) {
  const m = mover(t);
  const p = Object.assign({}, base);
  p.x = m.x; p.z = m.z;
  if (style.keepYaw === undefined) p.yaw = m.yaw;
  const step = strideLen(R, style);
  const nominal = style.speed ?? 0.9;
  const amt = clamp(m.speed / nominal, 0, 1);
  const phase = (m.dist / step) * Math.PI;
  walkLayer(p, phase + (style.phase0 || 0), smooth(amt), style);
  return { p, m, phase, amt };
}

// ── secondary motion (antenna, scarf) — visual only, resets on discontinuity ──
function updateSecondary(R, t, dt) {
  const s = R.sec;
  const pos = _v2.setFromMatrixPosition(R.j.head.matrixWorld);
  if (!s.last || dt <= 0 || dt > 0.1) { s.last = pos.clone(); s.vel = new THREE.Vector3(); s.ax = s.av = s.sx = s.sv = 0; }
  const vel = pos.clone().sub(s.last).divideScalar(Math.max(dt, 1e-3));
  const acc = vel.clone().sub(s.vel).divideScalar(Math.max(dt, 1e-3));
  s.vel.copy(vel); s.last.copy(pos);
  // transform acceleration into head local axes (approx using yaw)
  const yaw = R.root.rotation.y;
  const fwd = acc.x * Math.sin(yaw) + acc.z * Math.cos(yaw);
  const side = acc.x * Math.cos(yaw) - acc.z * Math.sin(yaw);
  const step = (x, v, target, k, d) => { const a = -k * (x - target) - d * v; v += a * dt; x += v * dt; return [x, v]; };
  const drive = clamp(-fwd * 0.02 + acc.y * 0.004, -0.6, 0.6);
  [s.ax, s.av] = step(s.ax, s.av, drive, 90, 7);
  [s.sx, s.sv] = step(s.sx, s.sv, clamp(-fwd * 0.03 - side * 0.01, -0.9, 0.9), 40, 4);
  const idle = Math.sin(t * 1.7 + (R.kind === 'codex' ? 0 : 2)) * 0.04;
  if (R.j.antenna) R.j.antenna.rotation.x = clamp(s.ax, -0.7, 0.7) + idle;
  if (R.j.antenna) R.j.antenna.rotation.z = (R.kind === 'codex' ? -0.12 : 0) + Math.sin(t * 1.3) * 0.03;
  if (R.j.scarf) { R.j.scarf.rotation.x = clamp(s.sx, -1.2, 0.9) - 0.1; R.j.scarf.rotation.z = 0.15; R.j.scarf2.rotation.x = clamp(s.sx * 0.6, -0.8, 0.8); }
  if (R.star) R.star.rotation.z = t * 0.6;
}

// ───────────────────────── faces ─────────────────────────
function blinkAmount(t, seed) {
  const period = 3.1 + seed;
  const k = Math.floor(t / period);
  const tt = t - k * period - hash1(k + seed * 17) * 1.2;
  if (tt > 0 && tt < 0.16) return Math.sin((tt / 0.16) * Math.PI);
  return 0;
}

function drawCodexFace(R, t) {
  const tex = R.faceTex, c = tex.userData.ctx, W = 320, H = 208, f = R.face;
  c.clearRect(0, 0, W, H);
  rrect(c, 2, 2, W - 4, H - 4, 46); c.fillStyle = '#081013'; c.fill();
  c.save(); c.clip();
  const g = c.createRadialGradient(W / 2, H / 2, 10, W / 2, H / 2, W * 0.7);
  g.addColorStop(0, 'rgba(40,120,110,0.25)'); g.addColorStop(1, 'rgba(0,0,0,0)');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  c.fillStyle = 'rgba(80,255,220,0.035)';
  for (let y = 0; y < H; y += 4) c.fillRect(0, y, W, 1.5);
  const boot = f.boot;
  const col = '#6bf7d6';
  if (boot < 1) {
    // boot sequence: scanline + log lines
    c.font = `600 15px ${FONT_MONO}`; c.fillStyle = col; c.shadowColor = '#3fe6c0'; c.shadowBlur = 8;
    const lines = ['> wake --agent codex', '  loading weights....ok', '  mounting /workspace..ok', '  tests: 0 warnings', '> ready_'];
    const n = Math.floor(boot * 6);
    if (boot > 0.02) for (let i = 0; i < Math.min(n, lines.length); i++) c.fillText(lines[i], 26, 42 + i * 26);
    if (boot > 0 && boot < 0.12) { c.fillStyle = 'rgba(110,255,220,0.8)'; c.fillRect(0, H * (boot / 0.12), W, 3); }
    c.restore(); tex.needsUpdate = true; return;
  }
  const open = clamp(f.open * (f.blink ? 1 - blinkAmount(t, 0.3) : 1), 0, 1);
  const lx = f.lookX * 22, ly = -f.lookY * 14;
  const cx1 = 108 + lx, cx2 = 212 + lx, cy = 96 + ly;
  c.shadowColor = '#3fe6c0'; c.shadowBlur = 16 * f.glow;
  c.fillStyle = col; c.strokeStyle = col; c.lineCap = 'round'; c.lineJoin = 'round';
  const mood = f.mood;
  const eye = (x, y, w, h, topTilt = 0, botCut = 0) => {
    // pill eye, with optional slanted lid (topTilt: +down toward centre)
    c.save();
    rrect(c, x - w / 2, y - h / 2, w, h, Math.min(w, h) / 2.2); c.clip();
    c.fillRect(x - w / 2, y - h / 2, w, h);
    c.globalCompositeOperation = 'destination-out';
    if (topTilt) { c.beginPath(); c.moveTo(x - w, y - h / 2 - 2 + (topTilt < 0 ? -topTilt : 0)); c.lineTo(x + w, y - h / 2 - 2 + (topTilt > 0 ? topTilt : 0)); c.lineTo(x + w, y - h); c.lineTo(x - w, y - h); c.closePath(); c.fill(); }
    if (botCut) c.fillRect(x - w, y + h / 2 - botCut, w * 2, h);
    c.restore();
  };
  const eh = 64 * open;
  if (mood === 'happy') {
    c.lineWidth = 13;
    for (const x of [cx1, cx2]) { c.beginPath(); c.moveTo(x - 22, cy + 8); c.quadraticCurveTo(x, cy - 30, x + 22, cy + 8); c.stroke(); }
  } else if (mood === 'sleepy' || open < 0.12) {
    c.lineWidth = 9;
    for (const x of [cx1, cx2]) { c.beginPath(); c.moveTo(x - 20, cy + 4); c.lineTo(x + 20, cy + 4); c.stroke(); }
  } else if (mood === 'smug') {
    eye(cx1, cy + 6, 36, eh * 0.55);
    eye(cx2, cy, 36, eh * 0.85, 0, eh * 0.15);
  } else if (mood === 'annoyed') {
    eye(cx1, cy + 8, 38, eh * 0.5, 10); eye(cx2, cy + 8, 38, eh * 0.5, -10);
  } else if (mood === 'determined' || mood === 'angry') {
    const tilt = mood === 'angry' ? 26 : 18;
    eye(cx1, cy + 2, 40, eh * 0.9, tilt); eye(cx2, cy + 2, 40, eh * 0.9, -tilt);
  } else if (mood === 'worried') {
    eye(cx1, cy, 34, eh * 0.85, -14); eye(cx2, cy, 34, eh * 0.85, 14);
  } else if (mood === 'surprised') {
    eye(cx1, cy - 4, 46, Math.max(10, eh * 1.15)); eye(cx2, cy - 4, 46, Math.max(10, eh * 1.15));
  } else if (mood === 'focus') {
    eye(cx1, cy + 4, 40, eh * 0.62); eye(cx2, cy + 4, 40, eh * 0.62);
  } else if (mood === 'error') {
    c.lineWidth = 11; c.strokeStyle = '#ff6b6b'; c.shadowColor = '#ff4040';
    for (const x of [cx1, cx2]) { c.beginPath(); c.moveTo(x - 18, cy - 18); c.lineTo(x + 18, cy + 18); c.moveTo(x + 18, cy - 18); c.lineTo(x - 18, cy + 18); c.stroke(); }
    c.strokeStyle = col; c.shadowColor = '#3fe6c0';
  } else {
    eye(cx1, cy, 36, eh); eye(cx2, cy, 36, eh);
  }
  // mouth
  const my = 160 + ly * 0.4, mx = 160 + lx * 0.5;
  c.lineWidth = 7;
  if (f.talk > 0.02) {
    const o = f.talk;
    rrect(c, mx - 18, my - 4 - o * 9, 36, 8 + o * 18, 8); c.fill();
  } else if (mood === 'smug') { c.beginPath(); c.moveTo(mx - 20, my + 2); c.quadraticCurveTo(mx + 6, my + 6, mx + 22, my - 8); c.stroke(); }
  else if (mood === 'happy') { c.beginPath(); c.moveTo(mx - 22, my - 6); c.quadraticCurveTo(mx, my + 16, mx + 22, my - 6); c.stroke(); }
  else if (mood === 'annoyed' || mood === 'determined' || mood === 'angry') { c.beginPath(); c.moveTo(mx - 16, my + 2); c.lineTo(mx + 16, my + 2); c.stroke(); }
  else if (mood === 'surprised') { c.beginPath(); c.arc(mx, my, 9, 0, TAU); c.stroke(); }
  else if (mood === 'worried' || mood === 'error') { c.beginPath(); c.moveTo(mx - 16, my + 4); c.lineTo(mx - 6, my - 3); c.lineTo(mx + 4, my + 4); c.lineTo(mx + 14, my - 3); c.stroke(); }
  else if (mood !== 'sleepy') { c.beginPath(); c.moveTo(mx - 12, my); c.lineTo(mx + 12, my); c.stroke(); }
  // blinking cursor motif
  if (Math.floor(t * 2) % 2 === 0) { c.shadowBlur = 6; c.fillRect(W - 46, H - 40, 12, 18); }
  if (f.extra) { c.font = `700 22px ${FONT_MONO}`; c.textAlign = 'left'; c.fillText(f.extra, 24, 40); }
  c.restore();
  tex.needsUpdate = true;
}

function drawClaudeFace(R, t) {
  const tex = R.faceTex, c = tex.userData.ctx, W = 320, H = 208, f = R.face;
  c.clearRect(0, 0, W, H);
  rrect(c, 0, 0, W, H, 60); c.fillStyle = '#1e110b'; c.fill();
  c.save(); c.clip();
  const g = c.createRadialGradient(W / 2, H * 0.55, 10, W / 2, H / 2, W * 0.65);
  g.addColorStop(0, `rgba(255,150,80,${0.22 * f.boot})`); g.addColorStop(1, 'rgba(0,0,0,0)');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  const col = '#ffb877';
  c.fillStyle = col; c.strokeStyle = col; c.lineCap = 'round'; c.lineJoin = 'round';
  c.shadowColor = '#ff9a4a'; c.shadowBlur = 18 * f.glow;
  if (f.boot < 1) {
    // gentle warm fade-in: soft dots breathing to life
    const b = f.boot;
    c.globalAlpha = clamp(b * 1.4);
    for (let i = 0; i < 3; i++) { const a = clamp(b * 3 - i); c.beginPath(); c.arc(W / 2 - 40 + i * 40, H / 2, 7 + 4 * a, 0, TAU); c.globalAlpha = a; c.fill(); }
    c.restore(); tex.needsUpdate = true; return;
  }
  const open = clamp(f.open * (f.blink ? 1 - blinkAmount(t, 1.1) : 1), 0, 1);
  const lx = f.lookX * 20, ly = -f.lookY * 13;
  const cx1 = 112 + lx, cx2 = 208 + lx, cy = 92 + ly, r = 24;
  const mood = f.mood;
  const roundEye = (x, y, rr, sq = 1, lid = 0) => {
    c.save(); c.beginPath(); c.ellipse(x, y, rr, rr * sq, 0, 0, TAU); c.clip();
    c.fillRect(x - rr, y - rr, rr * 2, rr * 2);
    c.globalCompositeOperation = 'destination-out';
    if (lid) { c.beginPath(); c.moveTo(x - rr * 2, y - rr * 1.1 + (lid < 0 ? -lid : 0)); c.lineTo(x + rr * 2, y - rr * 1.1 + (lid > 0 ? lid : 0)); c.lineTo(x + rr * 2, y - rr * 3); c.lineTo(x - rr * 2, y - rr * 3); c.fill(); }
    c.restore();
    // highlight
    if (sq > 0.4) { c.save(); c.shadowBlur = 0; c.fillStyle = 'rgba(255,245,225,0.9)'; c.beginPath(); c.arc(x - rr * 0.32, y - rr * 0.35 * sq, rr * 0.22, 0, TAU); c.fill(); c.restore(); }
  };
  if (mood === 'happy') {
    c.lineWidth = 12;
    for (const x of [cx1, cx2]) { c.beginPath(); c.arc(x, cy + 10, 20, Math.PI * 1.1, Math.PI * 1.9); c.stroke(); }
  } else if (mood === 'sleepy' || open < 0.12) {
    c.lineWidth = 9;
    for (const x of [cx1, cx2]) { c.beginPath(); c.arc(x, cy - 4, 18, Math.PI * 0.15, Math.PI * 0.85); c.stroke(); }
  } else if (mood === 'thinking') {
    roundEye(cx1 + 8, cy - 6, r * 0.9, open); roundEye(cx2 + 8, cy - 6, r * 0.9, open);
    c.shadowBlur = 8; for (let i = 0; i < 3; i++) { c.globalAlpha = (Math.floor(t * 3) % 4) > i ? 1 : 0.25; c.beginPath(); c.arc(250 + i * 16, 40, 5, 0, TAU); c.fill(); }
    c.globalAlpha = 1;
  } else if (mood === 'annoyed') {
    roundEye(cx1, cy + 6, r, open * 0.75, 18); roundEye(cx2, cy + 6, r, open * 0.75, -18);
  } else if (mood === 'determined' || mood === 'angry') {
    const tilt = mood === 'angry' ? 30 : 22;
    roundEye(cx1, cy + 4, r, open * 0.85, tilt); roundEye(cx2, cy + 4, r, open * 0.85, -tilt);
  } else if (mood === 'worried') {
    roundEye(cx1, cy, r * 0.95, open, -16); roundEye(cx2, cy, r * 0.95, open, 16);
  } else if (mood === 'surprised') {
    roundEye(cx1, cy - 4, r * 1.25, open); roundEye(cx2, cy - 4, r * 1.25, open);
  } else if (mood === 'focus') {
    roundEye(cx1, cy + 4, r * 0.95, open * 0.7); roundEye(cx2, cy + 4, r * 0.95, open * 0.7);
  } else if (mood === 'error') {
    c.lineWidth = 10;
    for (const x of [cx1, cx2]) { c.beginPath(); c.moveTo(x - 16, cy - 16); c.lineTo(x + 16, cy + 16); c.moveTo(x + 16, cy - 16); c.lineTo(x - 16, cy + 16); c.stroke(); }
  } else {
    roundEye(cx1, cy, r, open); roundEye(cx2, cy, r, open);
  }
  // blush
  if (mood === 'happy' || f.blush) { c.save(); c.shadowBlur = 0; c.fillStyle = 'rgba(255,120,90,0.35)'; for (const x of [cx1 - 18, cx2 + 18]) { c.beginPath(); c.ellipse(x, cy + 36, 18, 9, 0, 0, TAU); c.fill(); } c.restore(); }
  const my = 152 + ly * 0.4, mx = 160 + lx * 0.5;
  c.lineWidth = 7;
  if (f.talk > 0.02) {
    const o = f.talk; c.beginPath(); c.ellipse(mx, my, 14 + o * 3, 4 + o * 11, 0, 0, TAU); c.fill();
  } else if (f.yawn) { c.beginPath(); c.ellipse(mx, my + 2, 16, 8 + f.yawn * 14, 0, 0, TAU); c.fill(); }
  else if (mood === 'happy' || mood === 'neutral' || mood === 'thinking') { c.beginPath(); c.arc(mx, my - 10, 14, Math.PI * 0.2, Math.PI * 0.8); c.stroke(); }
  else if (mood === 'surprised') { c.beginPath(); c.arc(mx, my, 8, 0, TAU); c.stroke(); }
  else if (mood === 'worried' || mood === 'error') { c.beginPath(); c.arc(mx, my + 10, 13, Math.PI * 1.2, Math.PI * 1.8); c.stroke(); }
  else if (mood !== 'sleepy') { c.beginPath(); c.moveTo(mx - 12, my); c.lineTo(mx + 12, my); c.stroke(); }
  if (f.extra) { c.font = `700 24px ${FONT_UI}`; c.textAlign = 'center'; c.shadowBlur = 10; c.fillText(f.extra, W / 2, 34); }
  c.restore();
  tex.needsUpdate = true;
}

function drawFace(R, t) { if (R.kind === 'codex') drawCodexFace(R, t); else drawClaudeFace(R, t); }

// ───────────────────────── ChatGPT (floating dispatcher) ─────────────────────────
function buildGPT() {
  const R = { kind: 'gpt', j: {}, face: { mood: 'cheer', talk: 0, open: 1, lookX: 0, lookY: 0 } };
  const root = new THREE.Group(); R.root = root;
  const body = group(root); R.j.body = body;
  const W = toon(PAL.gpt), Dk = toon(0x22272b), Gr = toon(PAL.gptGreen, { emissive: PAL.gptGreen, ei: 0.5 });
  mesh(sphg(0.2, 36, 24), W, 0, 0, 0, body, { s: [1.12, 0.95, 1.0], ol: 0.009 });
  // face band
  mesh(G('gptBand', () => new THREE.SphereGeometry(0.203, 36, 16, Math.PI / 2 - 0.85, 1.7, Math.PI / 2 - 0.42, 0.84)), Dk, 0, 0, 0, body, { s: [1.12, 0.95, 1.0], cast: false });
  const faceTex = canvasTex(256, 128); R.faceTex = faceTex;
  R.faceMesh = mesh(G('gptFace', () => new THREE.SphereGeometry(0.206, 36, 16, Math.PI / 2 - 0.8, 1.6, Math.PI / 2 - 0.38, 0.76)), new THREE.MeshBasicMaterial({ map: faceTex, transparent: true, toneMapped: false }), 0, 0, 0, body, { s: [1.12, 0.95, 1.0], cast: false });
  // knot halo: three interlocking rings
  const halo = group(body, 0, 0, -0.03); R.j.halo = halo;
  for (let i = 0; i < 3; i++) {
    const ring = mesh(torg(0.27, 0.014, 8, 48), i === 1 ? Gr : W, 0, 0, 0, halo, { ol: 0.004 });
    ring.rotation.set(Math.PI / 2 + 0.35, 0, (i / 3) * Math.PI);
    ring.rotation.order = 'ZXY';
  }
  // hover glow ring underneath
  R.hover = mesh(torg(0.09, 0.012, 6, 32), glow(PAL.gptGreen, 2), 0, -0.21, 0, body, { rx: Math.PI / 2, cast: false });
  // floating mitten hands
  for (const side of [1, -1]) {
    const h = group(root, side * 0.32, -0.05, 0.05); R.j[side > 0 ? 'lh' : 'rh'] = h;
    mesh(sphg(0.055, 16, 12), W, 0, 0, 0, h, { s: [1, 1.1, 0.7], ol: 0.006 });
    mesh(capg(0.017, 0.03), W, -side * 0.04, 0.02, 0.01, h, { rz: side * 0.8, ol: 0.004 });
  }
  return R;
}
function drawGPTFace(R, t) {
  const tex = R.faceTex, c = tex.userData.ctx, W = 256, H = 128, f = R.face;
  c.clearRect(0, 0, W, H);
  c.fillStyle = '#1a1f22'; rrect(c, 0, 0, W, H, 50); c.fill();
  c.fillStyle = '#ffffff'; c.strokeStyle = '#ffffff'; c.shadowColor = '#7dffd9'; c.shadowBlur = 10; c.lineCap = 'round';
  const lx = f.lookX * 14, cy = 54;
  const open = 1 - blinkAmount(t, 2.2);
  if (f.mood === 'stern') {
    c.lineWidth = 9;
    for (const [x, d] of [[88 + lx, 1], [168 + lx, -1]]) { c.beginPath(); c.moveTo(x - 16, cy - 10 * d * -1 - 6); c.lineTo(x + 16, cy + 10 * d * -1 - 6); c.stroke(); c.beginPath(); c.arc(x, cy + 6, 10, 0, TAU); c.fill(); }
  } else if (f.mood === 'cheer') {
    c.lineWidth = 10;
    for (const x of [88 + lx, 168 + lx]) { c.beginPath(); c.arc(x, cy + 8, 15, Math.PI * 1.15, Math.PI * 1.85); c.stroke(); }
  } else {
    for (const x of [88 + lx, 168 + lx]) { c.beginPath(); c.ellipse(x, cy, 12, 16 * open + 1, 0, 0, TAU); c.fill(); }
  }
  c.lineWidth = 7;
  if (f.talk > 0.02) { c.beginPath(); c.ellipse(128 + lx * 0.5, 96, 14, 3 + f.talk * 10, 0, 0, TAU); c.fill(); }
  else if (f.mood === 'stern') { c.beginPath(); c.moveTo(112, 98); c.lineTo(144, 98); c.stroke(); }
  else { c.beginPath(); c.arc(128 + lx * 0.5, 84, 18, Math.PI * 0.2, Math.PI * 0.8); c.stroke(); }
  tex.needsUpdate = true;
}
function poseGPT(R, t, o) {
  R.root.position.set(o.x, o.y + Math.sin(t * 2.2) * 0.025, o.z);
  R.root.rotation.y = o.yaw || 0;
  R.j.body.rotation.z = Math.sin(t * 1.6) * 0.06 + (o.tilt || 0);
  R.j.halo.rotation.z = t * 0.5;
  const s = o.scale ?? 1; R.root.scale.setScalar(s);
  const g = o.gesture || 0; // 0 rest, 1 wave, 2 stop (both hands up), 3 shrug
  const wave = Math.sin(t * 9) * 0.5;
  R.j.lh.position.set(0.32, -0.05 + (g === 2 ? 0.2 : g === 3 ? 0.08 : 0) + Math.sin(t * 2.4) * 0.015, 0.05 + (g === 2 ? 0.15 : 0));
  R.j.rh.position.set(-0.32 + (g === 1 ? 0 : 0), -0.05 + (g === 1 ? 0.22 : g === 2 ? 0.2 : g === 3 ? 0.08 : 0) + Math.sin(t * 2.4 + 1) * 0.015, 0.05 + (g === 2 ? 0.15 : 0));
  R.j.rh.rotation.z = g === 1 ? wave : 0;
  R.j.lh.rotation.z = 0;
  R.root.updateMatrixWorld(true);
}

// ───────────────────────── IK helpers ─────────────────────────
const _ikA = new THREE.Vector3(), _ikB = new THREE.Vector3(), _ikC = new THREE.Vector3(), _ikD = new THREE.Vector3(), _ikM = new THREE.Matrix4(), _ikQ = new THREE.Quaternion(), _ikQ2 = new THREE.Quaternion();
// target / pole given in the coordinates of R.root.parent (env-local). blend 0..1
function armIK(R, side, target, blend = 1, pole) {
  if (blend <= 0) return;
  const p = side === 'l' ? 'l' : 'r', sgn = side === 'l' ? 1 : -1;
  const sh = R.j[p + 's'], el = R.j[p + 'e'], torso = R.j.torso;
  R.root.updateMatrixWorld(true);
  const T = _ikA.set(target[0], target[1], target[2]);
  if (R.root.parent) R.root.parent.localToWorld(T);
  torso.worldToLocal(T); // target in torso space
  const d = T.sub(sh.position); // shoulder → target, torso space
  const a = R.dim.La, b = R.dim.Lf + 0.075;
  let dist = d.length();
  dist = clamp(dist, Math.abs(a - b) + 1e-3, (a + b) * 0.999);
  const dh = d.clone().normalize();
  const cosA = clamp((a * a + dist * dist - b * b) / (2 * a * dist), -1, 1), alpha = Math.acos(cosA);
  const cosI = clamp((a * a + b * b - dist * dist) / (2 * a * b), -1, 1), bend = Math.PI - Math.acos(cosI);
  const P = _ikB.set(...(pole || [sgn * 0.6, -0.5, -0.8])).normalize();
  const pp = P.sub(dh.clone().multiplyScalar(P.dot(dh)));
  if (pp.lengthSq() < 1e-6) pp.set(0, 0, -1);
  pp.normalize();
  const u = dh.clone().multiplyScalar(Math.cos(alpha)).add(pp.clone().multiplyScalar(Math.sin(alpha))).normalize();
  const E = u.clone().multiplyScalar(a);
  const fdir = dh.clone().multiplyScalar(dist).sub(E);
  const f = fdir.sub(u.clone().multiplyScalar(fdir.dot(u))).normalize();
  const Y = u.clone().negate(), Z = f, X = new THREE.Vector3().crossVectors(Y, Z).normalize();
  _ikM.makeBasis(X, Y, Z);
  _ikQ.setFromRotationMatrix(_ikM);
  sh.quaternion.slerp(_ikQ, blend);
  el.rotation.x = lerp(el.rotation.x, -bend, blend);
  R.root.updateMatrixWorld(true);
}
// turn the head toward a point (env-local)
function headLook(R, target, blend = 1, maxYaw = 1.1) {
  if (blend <= 0) return;
  R.root.updateMatrixWorld(true);
  const T = _ikC.set(target[0], target[1], target[2]);
  if (R.root.parent) R.root.parent.localToWorld(T);
  R.j.torso.worldToLocal(T);
  T.sub(R.j.neck.position); T.y -= 0.2;
  const yaw = clamp(Math.atan2(T.x, T.z), -maxYaw, maxYaw);
  const pitch = clamp(-Math.atan2(T.y, Math.hypot(T.x, T.z)), -0.6, 0.6);
  R.j.neck.rotation.y = lerp(R.j.neck.rotation.y, yaw, blend);
  R.j.neck.rotation.x = lerp(R.j.neck.rotation.x, pitch, blend);
  R.root.updateMatrixWorld(true);
}
// hold a prop in a hand with a WORLD-space orientation (yaw/pitch/roll in env frame) and an offset along the socket
function holdUpright(prop, R, side, env, euler = [0, 0, 0], off = [0, 0, 0], woff = [0, 0, 0]) {
  const sock = R.j[side === 'l' ? 'lhand' : 'rhand'].socket;
  if (prop.parent !== env.group) env.group.add(prop);
  sock.updateMatrixWorld(true);
  const wp = _ikD.set(off[0], off[1], off[2]); sock.localToWorld(wp); env.group.worldToLocal(wp);
  prop.position.copy(wp).add(_ikB.set(woff[0], woff[1], woff[2]));
  prop.rotation.set(euler[0], euler[1], euler[2], 'YXZ');
  prop.visible = true;
}
function sockPos(R, side, env) { const s = R.j[side === 'l' ? 'lhand' : 'rhand'].socket; const v = new THREE.Vector3(); s.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z]; }
// point in front of the face (for drinking), env-local
function mouthPos(R, env, fwd = 0.22, down = -0.09) {
  const v = new THREE.Vector3(0, down, fwd); R.j.head.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z];
}
