/* F: sun 10 % down, a moon on its own arc (visible at night, gone at noon, night key light), clouds that follow the weather, nine kinds
   of day, fronts that ease in (no jumps within a day), blizzards that cut visibility but leave the game playable. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const P = SK.P7, S = SK.State, U = SK.SKY.uniforms, sunL = SK.scene.children.find(o => o.isDirectionalLight);
const dayOf = k => { let d = 1; while (P.forecast(d).k !== k && d < 600) d++; return d; };
const at = (k, h) => { S.day = dayOf(k); P.WX.t = null; SK.P6.setTime(h * 60); P.wxApply(); SK.play(2); };
/* 1. sun at clear noon = 0.9 x v30 (0.74) */
at('clear', 12); info.sunNoon = +sunL.intensity.toFixed(3); chk(Math.abs(sunL.intensity - 0.9 * 0.74) < 0.03, 'noon sun ' + info.sunNoon + ' (want ' + (0.9 * 0.74).toFixed(3) + ')');
/* 2. moon */
info.moonNoon = +U.moonV.value.toFixed(2); at('clear', 1); info.moonNight = +U.moonV.value.toFixed(2); info.keyIsMoon = U.sunD.value.distanceTo(U.moonD.value) < 0.01;
chk(info.moonNoon < 0.05 && info.moonNight > 0.8, 'moon visibility noon ' + info.moonNoon + ' / 01:00 ' + info.moonNight); chk(info.keyIsMoon, 'night key light does not come from the moon');
/* 3. nine kinds over 300 days, clouds by weather */
{ const ks = {}; for (let d = 1; d <= 300; d++) ks[P.forecast(d).k] = 1; info.kinds = Object.keys(ks); chk(info.kinds.length === 9, 'kinds of day: ' + info.kinds.join(','));
  at('clear', 12); const c0 = U.cCov.value; at('overcast', 13); const c1 = U.cCov.value; info.cov = [+c0.toFixed(2), +c1.toFixed(2)]; chk(c0 < 0.2 && c1 > 0.75, 'cloud cover clear / overcast ' + JSON.stringify(info.cov)); }
/* 4. easing: across a snowy day, minute steps never jump more than 0.06 in snowfall */
{ S.day = dayOf('heavy'); P.WX.t = null; SK.P6.setTime(6 * 60); P.wxApply(); let prev = P.WX.amt, worst = 0; for (let m = 6 * 60; m < 20 * 60; m += 2) { SK.P6.setTime(m); P.wxApply(); worst = Math.max(worst, Math.abs(P.WX.amt - prev)); prev = P.WX.amt; }
  info.maxStep = +worst.toFixed(3); chk(worst < 0.06, 'snowfall jumps by ' + info.maxStep + ' in 2 game minutes'); }
/* 5. blizzard: fog far plane well under clear, but still > 100 m */
{ at('clear', 12); const f0 = SK.scene.fog.far; const d = dayOf('storm'); S.day = d; P.WX.t = null; SK.P6.setTime(P.forecast(d).peak * 60); P.wxApply(); const f1 = SK.scene.fog.far; info.fog = [Math.round(f0), Math.round(f1)];
  chk(f1 < f0 * 0.4 && f1 > 100, 'blizzard visibility ' + JSON.stringify(info.fog)); }
/* 6. foggy morning lifts */
{ const d = dayOf('fog'); S.day = d; P.WX.t = null; SK.P6.setTime(7 * 60); P.wxApply(); const a = P.WX.fog; SK.P6.setTime(13 * 60); P.WX.t = null; P.wxApply(); const b = P.WX.fog; info.fogMorning = [+a.toFixed(2), +b.toFixed(2)]; chk(a > 0.6 && b < 0.2, 'fog does not lift: ' + JSON.stringify(info.fogMorning)); }
S.day = 1; P.WX.t = null; SK.P6.setTime(600); P.wxApply(); return { ok: !fails.length, fails, info };
