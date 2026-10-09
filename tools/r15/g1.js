/* G: resort life. Over simulated hours: walkers never stand inside a wall or a piece of furniture, staff stay at their posts, café
   customers queue, get served by Mia, sit and leave, nobody sits forever, storms send guests indoors, night empties the village. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const L = SK.LIFE, P7 = SK.P7, S = SK.State, A = L.A, RZ = SK.RES_Z;
const inSolid = a => SK.SOLIDS.some(s => { if (s.door || s.off) return false; const yr = a.y + 0.5; if ((s.bot !== undefined && yr < s.bot) || (s.top !== undefined && yr >= s.top)) return false;
  const dx = a.x - s.x, dz = a.z - s.z, c = Math.cos(s.a), n = Math.sin(s.a), u = dx * c - dz * n, w = dx * n + dz * c; return Math.abs(u) < s.hw - 0.05 && Math.abs(w) < s.hd - 0.05; });
const dayOf = k => { let d = 1; while (P7.forecast(d).k !== k && d < 600) d++; return d; };
const setDay = (k, h) => { S.day = dayOf(k); P7.WX.t = null; SK.P6.setTime(h * 60); P7.wxApply(); };
const W = SK.W; W.on = false;
/* 1. a clear day, 11:00 -> 13:00 sampled every 2 s */
setDay('clear', 11); const posts = A.filter(a => a.role === 'staff').map(a => [a, a.post.x, a.post.z]);
let bad = [], maxOff = 0, served = 0, sitT = new Map(), maxSit = 0, moved = new Set(), start = new Map(A.map(a => [a, [a.x, a.z]]));
const mia = SK.MIA, origServe = mia.serve; mia.serve = (k, cb) => origServe(k, () => { served++; cb && cb(); });
for (let i = 0; i < 400; i++) { SK.P6.setTime(11 * 60 + i * 0.2); SK.play(10, 0.2);
  for (const a of A) { if (!a.g.visible) { sitT.delete(a); continue; }
    if (i % 3 === 0 && a.role !== 'staff' && !(a.step && a.step.sit) && inSolid(a)) bad.push(a.role + '@' + a.x.toFixed(1) + ',' + (a.z - RZ).toFixed(1));
    if (a.step && a.step.sit) maxSit = Math.max(maxSit, a.step.dur, a.t - a.step.dur > 0.5 ? 999 : 0);   /* each sit has an end, and it is kept */
    if (Math.hypot(a.x - start.get(a)[0], a.z - start.get(a)[1]) > 3) moved.add(a); }
  for (const [a, x, z] of posts) maxOff = Math.max(maxOff, Math.hypot(a.x - x, a.z - z)); }
mia.serve = origServe;
info.inSolid = bad.length; info.inSolidEg = [...new Set(bad)].slice(0, 6); info.staffMaxOff = +maxOff.toFixed(2); info.cafeServed = served; info.maxSit = maxSit;
info.guestsMoved = A.filter(a => a.role === 'guest' && moved.has(a)).length + '/' + A.filter(a => a.role === 'guest').length;
info.fails = A.reduce((s, a) => s + (a.fail || 0), 0);
chk(bad.length < 5, 'people inside solids: ' + JSON.stringify(info.inSolidEg)); chk(maxOff < 0.7, 'staff left their post by ' + info.staffMaxOff + ' m');
chk(served >= 4, 'café customers served in 13 min: ' + served); chk(maxSit <= 120, 'a sit lasting ' + maxSit + ' s (or one that overran)');
chk(A.filter(a => a.role === 'guest' && moved.has(a)).length >= 8, 'guests that went somewhere: ' + info.guestsMoved); chk(info.fails < 10, 'path failures ' + info.fails);
/* 2. storm: guests out in the open drop */
const outside = () => A.filter(a => a.role === 'guest' && a.g.visible && !SK.NOSNOW.some(r => a.x > r.x0 && a.x < r.x1 && a.z - RZ > r.z0 && a.z - RZ < r.z1)).length;
setDay('clear', 12); SK.play(300, 0.2); const o0 = outside(); setDay('storm', 12); SK.play(400, 0.2); const o1 = outside(); info.outsideClearStorm = [o0, o1];
chk(o1 < o0 || o1 <= 2, 'storm did not send guests in: ' + JSON.stringify(info.outsideClearStorm));
/* 3. night: village empties; morning: they come back */
setDay('clear', 21.2); SK.play(900, 0.2); info.visibleNight = A.filter(a => a.role === 'guest' && a.g.visible).length; chk(info.visibleNight <= 1, 'guests about at night: ' + info.visibleNight);
setDay('clear', 9); SK.play(300, 0.2); info.visibleMorning = A.filter(a => a.role === 'guest' && a.g.visible).length; chk(info.visibleMorning >= 8, 'guests back in the morning: ' + info.visibleMorning);
setDay('clear', 10); return { ok: !fails.length, fails, info };
