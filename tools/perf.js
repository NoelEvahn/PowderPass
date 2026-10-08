/* frame cost by graphics tier (headless software GL: only the RATIOS matter, absolute ms are far slower than a real GPU)
   node tools/perf.js  -> ms/frame for each of high / balanced / low at a few viewpoints */
const { boot } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 640, height: 360 } });
  const spots = [['base', '[-30, null, SK.RES_Z + 58, 0.15, 0.02, 600]'], ['hotel', '[SK.HOTEL.x + 3, null, SK.HOTEL.z + 17, 0.05, 0.12, 900]'], ['lobby', '[SK.HOTEL.x + 1.5, SK.HOTEL.gy + 0.23, SK.HOTEL.z + 1.5, 0.35, 0.05, 1290]']];
  for (const [name, src] of spots) for (const q of ['high', 'balanced', 'low']) {
    const ms = await page.evaluate(async ([src, q]) => {
      const [x, y, z, yaw, pitch, min] = (0, eval)(src), W = SK.W, S = SK.State; S.cam.walk = 'first'; S.settings.gfx = q; SK.P7.qualApply();
      SK.P6.setTime(min); W.x = x; W.z = z; W.y = y === null ? SK.groundY(x, z) : y; W.yaw = yaw; W.pitch = pitch; SK.play(6); SK.P6.lightsFor();
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))); const t0 = performance.now(); let n = 0;
      await new Promise(res => { const f = () => { n++; if (performance.now() - t0 > 2500) res(); else requestAnimationFrame(f); }; requestAnimationFrame(f); });
      const ri = SK.renderer.info; return { ms: (performance.now() - t0) / n, calls: ri.render.calls, tris: ri.render.triangles, post: SK.POST.mode, spots: SK.P6.SPS.filter(s => s.intensity > 0).length, shadows: SK.P6.SPS.filter(s => s.castShadow && s.intensity > 0).length, dpr: SK.renderer.getPixelRatio() }; }, [src, q]);
    console.log(name.padEnd(6), q.padEnd(9), ms.ms.toFixed(0).padStart(6) + ' ms/frame (software)', 'calls', String(ms.calls).padStart(5), 'tris', String(ms.tris).padStart(8), 'post', ms.post.padEnd(5), 'lit spots', ms.spots, 'shadowing', ms.shadows, 'dpr', ms.dpr);
  }
  console.log(errors.join('\n') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
