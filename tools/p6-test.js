/* clock + midnight, night-only sleep, hotel check-in (random free room, only that door opens), elevator, breakfast, cabin doors, ski payouts, lights at night, clear glass, save */
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  await ev(() => { window.T = { go(x, z, y) { const W = SK.W; W.x = x; W.z = z; W.y = y === undefined ? SK.groundY(x, z) : y; W.vx = W.vz = 0; SK.sim(3); }, op: s => { SK.ECONX.econOp(s.split(':')); SK.sim(2); } }; });
  let r = await ev(() => new Promise(res => { const S = SK.State, d0 = S.day; SK.P6.setTime(23 * 60 + 59.5); SK.sim(20); const a = { day: S.day - d0, time: +S.time.toFixed(1) };
    SK.P6.setTime(14 * 60); SK.ECONX.econOp(['sleep', '0']); setTimeout(() => { a.daySleepNoon = S.day - d0; SK.P6.setTime(21 * 60); SK.ECONX.econOp(['sleep', '0']); setTimeout(() => { a.dayNight = S.day - d0; a.wake = S.time; res(a); }, 1200); }, 900); }));
  check(r.day === 1 && r.time < 5, 'midnight rolls the day over (' + JSON.stringify(r) + ')', fails);
  check(r.daySleepNoon === 1 && r.dayNight === 2 && Math.abs(r.wake - 420) < 2, 'no sleeping at 14:00; sleeping at 21:00 wakes you next day at 07:00', fails);
  /* check-in */
  r = await ev(() => { const S = SK.State, H = SK.HOTEL, at = SK.ECONX.SHOPS.hotel.at; S.coins = 1000; SK.P6.setTime(15 * 60); T.go(at[0], at[1] + SK.RES_Z); const p = SK.ECON.nearby(SK.W.x, SK.W.z);
    SK.ECON.act(SK.W.x, SK.W.z); SK.sim(2); const html0 = document.querySelector('#sheet .sh-b').innerHTML; const free0 = SK.P6.freeRooms('normal').map(q => q.no);
    T.op('p6:pick:normal'); T.op('p6:in:normal'); const html1 = document.querySelector('#sheet .sh-b').innerHTML;
    return { prompt: p && p.text, hasChoices: /Normal room/.test(html0) && /Suite/.test(html0), free0: free0, room: S.room, coins: S.coins, key: /class="key"/.test(html1), again: (T.op('p6:pick:suite'), T.op('p6:in:suite'), S.room.no) }; });
  check(/reception/i.test(r.prompt) && r.hasChoices, 'reception desk offers normal rooms and suites (' + r.prompt + ')', fails);
  check(r.room && r.room.type === 'normal' && r.free0.indexOf(r.room.no) >= 0 && r.coins === 950 && r.key, 'paid 50, got a random free normal room ' + (r.room && r.room.no) + ' and a key card', fails);
  check(r.again === r.room.no, 'cannot check in twice while the key is valid', fails);
  await page.screenshot({ path: OUT + '/p6-checkin.png' });
  /* doors: mine opens, another stays shut */
  r = await ev(() => { const H = SK.HOTEL, S = SK.State; SK.ECONX.closeSheet(); const mine = H.doors.find(d => d.rm.no === S.room.no), other = H.doors.find(d => d.rm.no !== S.room.no && d.rm.floor === mine.rm.floor);
    T.go(mine.x, mine.z - 0.9 * mine.rm.sg, mine.y); SK.sim(30); const a = { mineOpen: mine.sol.off, o: +mine.o.toFixed(2) };
    T.go(other.x, other.z - 0.9 * other.rm.sg, other.y); SK.sim(30); a.otherOpen = other.sol.off; a.msg = document.getElementById('toast').textContent; return a; });
  check(r.mineOpen && !r.otherOpen && /locked/i.test(r.msg), 'your room door opens, another room stays locked with a message (' + JSON.stringify(r) + ')', fails);
  /* elevator */
  r = await ev(() => new Promise(res => { const H = SK.HOTEL; SK.P6.ride(2); setTimeout(() => { SK.sim(5); res({ y: +(SK.W.y - H.wy).toFixed(2), want: H.HX.walk[2] }); }, 800); }));
  check(Math.abs(r.y - r.want) < 0.05,   /* walk level = the visible floor */ 'elevator takes you to floor 2 (' + JSON.stringify(r) + ')', fails);
  await page.waitForTimeout(800); await page.screenshot({ path: OUT + '/p6-floor2.png' });
  /* breakfast */
  r = await ev(() => { const H = SK.HOTEL, S = SK.State, BF = SK.BF, sz = -10.25 + 0.85, fy = H.gy + 0.23; SK.P6.setTime(8 * 60); S.energy = 30; const at = x => { T.go(H.x + x, H.z + sz, fy); const p = SK.ECON.nearby(SK.W.x, SK.W.z); SK.ECON.act(SK.W.x, SK.W.z); return p && p.text; };
    const p = at(4.75); ['eggs', 'bacon', 'pancakes', 'coffee', 'juice'].forEach(k => at(BF.ST.find(s => s.k === k).x)); const plate = (BF.B.plate || []).slice();
    const seat = BF.SEATS.find(s => !s.taken); T.go(H.x + seat.x, H.z + seat.z + (seat.yaw ? -0.3 : 0.3), fy); const ps = SK.ECON.nearby(SK.W.x, SK.W.z); SK.ECON.act(SK.W.x, SK.W.z); const sat = !!SK.W.sit;
    SK.play(420); const e1 = S.energy, done = BF.B.eaten; SK.ECON.act(SK.W.x, SK.W.z); const stood = !SK.W.sit; const p2 = at(4.75); return { p: p, plate: plate, ps: ps && ps.text, sat: sat, e1: Math.round(e1), done: done, stood: stood, p2: p2 }; });
  check(/plate/i.test(r.p) && r.plate.length === 5 && r.sat && r.done === 5 && r.e1 >= 70 && r.stood && /already/.test(r.p2), 'breakfast: plate, fill 5 items, sit, eat them all, once a day (' + JSON.stringify(r) + ')', fails);
  await ev(() => { const H = SK.HOTEL; SK.W.yaw = Math.PI; SK.sim(2); }); await page.waitForTimeout(800); await page.screenshot({ path: OUT + '/p6-breakfast.png' });
  /* cabin doors */
  r = await ev(() => { const d = SK.CDOORS.find(q => q.id === 'logcab'), S = SK.State; T.go(d.x, d.z + 1.0); SK.sim(30); const a = { lockedSolid: !d.sol.off, msg: document.getElementById('toast').textContent }; S.cabins.push('logcab'); SK.sim(30); a.openSolid = !d.sol.off; return a; });
  check(r.lockedSolid && /for sale/.test(r.msg) && !r.openSolid, 'cabin door locked until you own it (' + JSON.stringify(r) + ')', fails);
  /* ski payouts */
  r = await ev(() => { const R = SK.RUN, t = SK.TRAILS.find(q => q.id === 'ribbon'), out = []; for (let i = 0; i < 3; i++) { R.start(t, { s: 0 }); R.dist = 230 * 0.9; R.sp = 80; R.tr = 657; R.trp = 100; out.push(R.finish().coins); }
    const g = SK.TRAILS.find(q => q.id === 'bunny'); R.start(g, { s: 0 }); R.dist = 23; const green = R.finish().coins; return { ribbon: out, green: green }; });
  check(r.ribbon[0] >= 140 && r.ribbon[1] < r.ribbon[0] && r.ribbon[2] < r.ribbon[1] && r.green < 10, 'ski payouts: worked example run ' + r.ribbon[0] + ' coins (was 63), repeats taper ' + r.ribbon + ', green beginner run ' + r.green, fails);
  /* night: lights on, sky dark; noon: street lights off, glass clear */
  r = await ev(() => { SK.P6.setTime(22 * 60); const W = SK.W; W.x = -4; W.z = 62 + SK.RES_Z; W.y = 0.1; SK.P6.lightsFor(); const n = { night: SK.P6.night(), pts: SK.P6.PTS.filter(p => p.intensity > 0).length, sps: SK.P6.SPS.filter(p => p.intensity > 0).length };
    SK.P6.setTime(12 * 60); n.dayNight = SK.P6.night(); n.daySps = SK.P6.SPS.filter(p => p.intensity > 0 && p.userData.k !== 'in').length;   /* indoor lamps stay on by day */ let maxO = 0; SK.scene.traverse(o => { if (o.isMesh && o.material && o.material.transparent && o.material.color && o.material.color.getHex() === 0xdff3ff) maxO = Math.max(maxO, o.material.opacity); }); n.glass = maxO; return n; });
  check(r.night > 0.9 && r.sps >= 3 && r.dayNight < 0.1 && r.daySps === 0 && r.glass <= 0.2, 'night lights on (spot cones ' + r.sps + '), off at noon; glass opacity ' + r.glass, fails);
  await ev(() => { SK.P6.setTime(21.5 * 60); T.go(-6, 63 + SK.RES_Z); SK.W.yaw = -Math.PI / 2; SK.W.pitch = 0; SK.sim(4); }); await page.waitForTimeout(1500); await page.screenshot({ path: OUT + '/p6-night-street.png' });
  /* save */
  await ev(() => SK.save()); await page.reload(); await page.waitForFunction(() => window.SK && SK.State && SK.State.q && !document.getElementById('load'), null, { timeout: 180000 });
  r = await ev(() => ({ room: SK.State.room, time: SK.State.time, bf: SK.State.bfDay }));
  check(r.room && r.room.no > 100 && r.time > 21 * 60 && r.bf > 0, 'room key, clock and breakfast survive a reload (' + JSON.stringify(r) + ')', fails);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
