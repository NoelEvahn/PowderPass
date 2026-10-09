/* H: the ski-school lesson runs its full sequence (assemble → … → disperse), riders go up the Magic Carpet in single file, students descend
   Snowdrop one by one, weak skiers fall and get up, skills grow, no lesson after hours or in a blizzard; birds take off when you walk up and
   land on another perch, deer stay on the ground, out of trees and walls, and bolt from you. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const X = SK.SCHOOLX, WD = SK.WILD, RZ = SK.RES_Z, C = SK.CARPET, W = SK.W, P7 = SK.P7, S = SK.State;
const dayOf = k => { let d = 1; while (P7.forecast(d).k !== k && d < 600) d++; return d; };
const setDay = (k, h) => { S.day = dayOf(k); P7.WX.t = null; SK.P6.setTime(h * 60); P7.wxApply(); };
const inSolid = (x, zw) => SK.SOLIDS.some(s => { if (s.off || s.door) return false; const dx = x - s.x, dz = zw - s.z, c = Math.cos(s.a), n = Math.sin(s.a), u = dx * c - dz * n, w = dx * n + dz * c; return Math.abs(u) < s.hw - 0.05 && Math.abs(w) < s.hd - 0.05; });
W.on = false; if (SK.LIFE) SK.LIFE.hold(true);
/* ---- 1. two lessons on a clear morning ---- */
setDay('clear', 10); X.P.slice(0, 2).forEach(p => { p.skill = 0; }); const sk0 = X.P.map(p => p.skill), f0 = X.S.falls, n0 = X.S.n; X.S.log.length = 0;
let rideBad = 0, rideN = 0, starts = [], bad = [], last = '';
for (let i = 0; i < 9000 && X.S.n < n0 + 2; i++) { SK.P6.setTime(600); SK.play(2, 1 / 15);
  if (X.S.st === 'ride') X.ALL.forEach(p => { if (p.belt) { rideN++; if (Math.abs(p.x - C.x) > 0.2) rideBad++; } });
  if (X.S.st === 'descend' && X.S.st !== last) starts = []; if (X.S.st === 'descend') X.P.forEach((p, k) => { if (p.ski && starts.indexOf(k) < 0) starts.push(k); });
  if (i % 10 === 0) X.ALL.forEach(p => { if (p.path && !p.belt && !p.ski && inSolid(p.x, p.z + RZ)) bad.push(p.x.toFixed(1) + ',' + p.z.toFixed(1)); });
  last = X.S.st; }
const want = ['assemble', 'listen', 'stance', 'pushoff', 'wedge', 'turns', 'queue', 'ride', 'regroup', 'descend', 'feedback', 'disperse', 'off'];
const seq = X.S.log.slice(0, want.length); info.lessons = X.S.n - n0; info.seq = seq.join('>'); info.ride = { samples: rideN, offBelt: rideBad }; info.descentOrder = starts; info.falls = X.S.falls - f0; info.inSolid = [...new Set(bad)].slice(0, 5);
info.skill = X.P.map((p, i) => +(p.skill - sk0[i]).toFixed(2));
chk(info.lessons >= 2, 'lessons finished: ' + info.lessons); chk(JSON.stringify(seq) === JSON.stringify(want), 'lesson sequence: ' + info.seq);
chk(rideN > 50 && rideBad === 0, 'carpet riders off the belt line: ' + JSON.stringify(info.ride)); chk(JSON.stringify(starts) === '[0,1,2,3]', 'students did not go down one by one in order: ' + JSON.stringify(starts));
chk(info.falls >= 1, 'no falls from two zero-skill students in two lessons'); chk(info.skill.every(v => v >= 0.079), 'skills did not grow: ' + JSON.stringify(info.skill)); chk(!bad.length, 'lesson walkers inside solids: ' + JSON.stringify(info.inSolid));
/* ---- 2. no lessons after hours or in a blizzard ---- */
setDay('clear', 17); for (let i = 0; i < 300; i++) { SK.P6.setTime(17 * 60); SK.play(2, 1 / 15); } info.after = X.S.st; chk(X.S.st === 'off', 'lesson running at 17:00: ' + X.S.st);
{ const d = dayOf('storm'); S.day = d; P7.WX.t = null; SK.P6.setTime(P7.forecast(d).peak * 60); P7.wxApply(); X.S.st = 'off'; X.S.t = 5; const pk = S.time; for (let i = 0; i < 300; i++) { SK.P6.setTime(pk); SK.play(2, 1 / 15); } info.storm = X.S.st; chk(X.S.st === 'off', 'lesson in a blizzard: ' + X.S.st); }
/* ---- 3. birds ---- */
setDay('clear', 11); const f = WD.FL[0]; if (f.st !== 'perch') SK.play(400, 1 / 15); const p0 = f.p, gy = p0.kind === 'ground' ? SK.groundY(p0.x, p0.z + RZ) : p0.y;
W.on = true; W.x = p0.x + 3; W.z = p0.z + RZ; W.y = SK.groundY(W.x, W.z); if (p0.kind === 'tree') W.y = gy - 6; SK.play(15, 1 / 15); info.birdsUp = f.st;
chk(f.st === 'fly', 'birds did not take off when you walked up: ' + f.st); W.on = false; W.x = 4000; W.z = 4000; SK.play(900, 1 / 15);
info.birdsLanded = { st: f.st, moved: +Math.hypot(f.p.x - p0.x, f.p.z - p0.z).toFixed(1) }; chk(f.st === 'perch' && info.birdsLanded.moved > 25, 'birds did not land on another perch: ' + JSON.stringify(info.birdsLanded));
{ const ys = f.B.map((b, i) => b.y); info.perchY = f.p.kind; const ok = f.B.every(b => f.p.kind === 'ground' ? Math.abs(b.y - SK.groundY(b.x, b.z + RZ) - 0.05) < 0.1 : Math.abs(b.y - f.p.y) < 0.6); chk(ok, 'perched birds not on their perch'); }
/* ---- 4. deer: grounded, never inside solids, bolt from you ---- */
let air = 0, inS = 0; for (let i = 0; i < 600; i++) { SK.play(2, 1 / 15); WD.DEER.forEach(d => { SK.cam.position.set(d.x, d.y + 20, d.z + RZ); if (Math.abs(d.y - SK.groundY(d.x, d.z + RZ)) > 0.3) air++; if (WD.blockedAt(d.x, d.z + RZ, -0.05)) inS++; }); }
info.deer = { n: WD.DEER.length, air, inSolid: inS }; chk(WD.DEER.length >= 6 && !air && !inS, 'deer off the ground or inside solids: ' + JSON.stringify(info.deer));
{ info.deerFlee = []; for (let k = 0; k < WD.DEER.length; k += 3) { const d = WD.DEER[k]; PQ.stand(d.x + 14, null, d.z + RZ, -Math.PI / 2, 0, null); W.x = d.x + 14; W.z = d.z + RZ; SK.play(60, 1 / 15);
    info.deerFlee.push(+Math.hypot(W.x - d.x, W.z - RZ - d.z).toFixed(1)); W.on = false; SK.play(30, 1 / 15); }
  chk(info.deerFlee.every(v => v > 24), 'deer did not bolt from 14 m: ' + JSON.stringify(info.deerFlee)); }
/* ---- 5. far deer hidden ---- */
PQ.orbit(4000, 4000, 10, 0, 60, null); SK.play(2, 1 / 15); info.farHidden = WD.DEER.every(d => !d.g.visible); chk(info.farHidden, 'deer 4 km away still drawn');
document.body.classList.remove('ed'); if (SK.LIFE) SK.LIFE.hold(false); setDay('clear', 10); return { ok: !fails.length, fails, info };
