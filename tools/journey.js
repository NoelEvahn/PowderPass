/* first-day journey: follow the quest tracker like a new player (walk there, press F), screenshot each step: tools/out/jr-*.png */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const frame = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  await page.screenshot({ path: OUT + '/jr-00-start.png' });
  for (let i = 1; i <= 10; i++) {
    const t = await page.evaluate(() => { const T = SK.tracked(); return T ? { title: T.title, txt: T.txt, at: T.at } : null; });
    if (!t) { console.log('no target'); break; }
    console.log(i, t.title, '|', t.txt, t.at ? t.at.map(v => v.toFixed(0)).join(',') : '-');
    if (!t.at) break;
    /* walk the last stretch for real: start 6 m away, face it, hold W */
    await page.evaluate(at => { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); if (SK.ECONX) SK.ECONX.closeSheet(); const x = at[0], z = at[1] + SK.RES_Z, a = Math.random() * 6.28;
      W.x = x + Math.cos(a) * 6; W.z = z + Math.sin(a) * 6; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(x - W.x), -(z - W.z)); W.pitch = -0.1; W.vx = W.vz = 0; SK.keys.w = 1;
      for (let k = 0; k < 90; k++) { SK.play(1); if (Math.hypot(W.x - x, W.z - z) < 2.2) break; } SK.keys.w = 0; SK.play(2); }, t.at);
    await frame(); await page.screenshot({ path: `${OUT}/jr-${String(i).padStart(2, '0')}a.png` });
    const act = await page.evaluate(() => { const a = document.getElementById('act'); return a.style.display !== 'none' ? a.textContent : ''; });
    await page.keyboard.press('f'); await page.waitForTimeout(300); await frame();
    const sh = await page.evaluate(() => { const s = document.getElementById('sheet'); return s.classList.contains('off') ? '' : s.innerText.replace(/\s+/g, ' ').slice(0, 260); });
    console.log('   prompt:', act, '| sheet:', sh);
    await page.screenshot({ path: `${OUT}/jr-${String(i).padStart(2, '0')}b.png` });
    /* press the first enabled sheet button (accept / talk / buy), like a curious player */
    const clicked = await page.evaluate(() => { const b = [...document.querySelectorAll('#sheet:not(.off) .sh-b button')].find(x => !x.disabled); if (!b) return ''; const t = b.textContent; b.click(); return t; });
    if (clicked) { console.log('   clicked:', clicked); await page.waitForTimeout(300); await frame(); await page.screenshot({ path: `${OUT}/jr-${String(i).padStart(2, '0')}c.png` }); }
    await page.evaluate(() => { if (SK.ECONX) SK.ECONX.closeSheet(); });
  }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
