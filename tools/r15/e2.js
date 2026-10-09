/* E2: the Lodge Café works through Mia and the real food on the counter. Buying off the counter (no shop list) pays, fills the bag and
   sends Mia to make it; talking to her opens quests / crafting / fondue only; after hours she is gone and the counter says closed; she
   never leaves her aisle; the walls are solid. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const W = SK.W, S = SK.State, st = SK.STORES.find(s => s.id === 'cafe'), M = SK.MIA;
chk(st && st.disp.length === 4, 'café counter displays: ' + (st && st.disp.length)); chk(!!M, 'no Mia');
SK.P6.setTime(600); SK.play(3);
/* 1. buy a cocoa off the counter */
{ S.coins = 200; S.inv.cocoa = 0; const c0 = S.coins; const r = SK.SHOPX.actKey('f:cafe:cocoa'); SK.play(2);
  info.buy = { paid: c0 - S.coins, cocoa: S.inv.cocoa, miaQ: M.M.q.length + (M.M.task ? 1 : 0), line: r.line };
  chk(info.buy.paid === 12 && info.buy.cocoa === 1, 'counter cocoa did not sell: ' + JSON.stringify(info.buy)); chk(info.buy.miaQ >= 1, 'Mia did not start making it');
  let served = false; const t0 = SK.clock; const tw = SK.toast; SK.toast = (m, k) => { if (/Mia hands you/.test(m)) served = true; tw(m, k); };
  for (let i = 0; i < 30 * 12 && !served; i++) SK.play(1); SK.toast = tw; info.buy.servedAfter = +(SK.clock - t0).toFixed(1); chk(served, 'Mia never handed the cocoa over'); }
/* 2. talk to Mia: quests, craft, fondue; no generic food list */
{ const tp = SK.MIA.talkAt; W.on = true; W.x = tp[0]; W.z = tp[1] + 0.3; W.y = st.y + 0.22; SK.play(3); const q = SK.ECON.nearby(W.x, W.z);
  info.talk = q && q.text; chk(q && /Mia/.test(q.text), 'no "Talk to Chef Mia" at the counter: ' + (q && q.text));
  SK.ECON.act(W.x, W.z); SK.play(2); const h = document.querySelector('#sheet .sh-b').innerHTML;
  info.sheet = { fondue: /Fondue/.test(h), craft: /Craft/.test(h), granolaRow: /Granola/.test(h) };
  chk(info.sheet.fondue && info.sheet.craft && !info.sheet.granolaRow, 'Mia\'s sheet: ' + JSON.stringify(info.sheet)); document.querySelector('#sheet .sh-x').click(); }
/* 3. her aisle, over a minute of work */
{ let out = 0; for (let i = 0; i < 60 * 10; i++) { SK.play(1, 0.1); const m = M.M; if (m.x < -0.55 || m.x > 3.45 || m.z < -2.7 || m.z > -2.0) out++; } info.aisleOut = out; chk(!out, 'Mia left her aisle ' + out + ' times'); }
/* 4. closed at night */
{ SK.P6.setTime(1320); SK.play(3); const r = SK.SHOPX.actKey('f:cafe:soup'); info.night = { visible: M.g.visible, line: r.line }; chk(!M.g.visible && /closed/i.test(r.line), 'after hours: ' + JSON.stringify(info.night)); SK.P6.setTime(600); SK.play(2); }
/* 5. solid walls: walking at the back wall from outside stops outside */
{ W.x = st.x + 1.5; W.z = SK.RES_Z + st.z - 5.0; W.y = SK.groundY(W.x, W.z); W.yaw = Math.PI; W.vx = W.vz = 0; SK.keys.w = 1; SK.play(90); SK.keys.w = 0;
  const lz = W.z - SK.RES_Z - st.z; info.wallStop = +lz.toFixed(2); chk(lz < -3.5, 'walked through the café back wall (local z ' + lz.toFixed(2) + ')'); }
return { ok: !fails.length, fails, info };
