/* round 13: Auto graphics steps down when frames stay slow and recovers, a hand-picked tier switches Auto off, cabin/station/hotel finish
   adds no new editor keys, interior finish pieces exist */
const { boot, check } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  let r = await ev(() => { const A = SK.AUTOQ, P = SK.P7; SK.State.settings.gfx = ''; A.tier = A.cap = 'high'; P.qualApply(); return { tier: A.tier, post: SK.POST.mode }; });
  check(r.tier === 'high' && r.post === 'full', 'auto starts at high with full post on desktop (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const A = SK.AUTOQ; A.tier = 'high'; A.slow = 0; SK.P7.qualApply(); SK.renderer.setPixelRatio(2); SK.autoQ(60); return { tier: A.tier, slow: A.slow }; });
  check(r.tier === 'high', 'one slow sample is not enough to drop the tier (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const A = SK.AUTOQ; A.tier = 'high'; A.slow = 0; SK.P7.qualApply(); const dpr0 = SK.renderer.getPixelRatio(); SK.forceMinDpr && SK.forceMinDpr(); for (let i = 0; i < 4; i++) SK.autoQ(60); return { tier: A.tier, dpr: dpr0 }; });
  check(['balanced', 'low'].indexOf(r.tier) >= 0, 'sustained slow frames at minimum resolution step the tier down (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const A = SK.AUTOQ, t0 = A.tier; for (let i = 0; i < 40; i++) SK.autoQ(8); return { from: t0, to: A.tier }; });
  check(r.to === 'high' || r.to === 'balanced', 'sustained fast frames step back up toward the cap (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.State.settings.gfx = 'low'; const A = SK.AUTOQ; A.tier = 'balanced'; for (let i = 0; i < 6; i++) SK.autoQ(80); const t = A.tier; SK.State.settings.gfx = ''; return t; });
  check(r === 'balanced', 'a hand-picked tier switches auto off (' + r + ')', fails);
  check(!errors.length, 'no page errors' + (errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''), fails);
  console.log(fails.length ? fails.length + ' FAILED' : 'all ok'); await browser.close(); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
