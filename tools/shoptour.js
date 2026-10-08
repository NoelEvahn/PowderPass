/* every shop / service sheet on a phone: walk to its counter, press F, screenshot (contact sheet shop-sheet.png) */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const fr = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  const shops = await page.evaluate(() => { SK.State.coins = 5000; SK.State.medals = 30; return Object.keys(SK.ECONX.SHOPS).map(k => [k, SK.ECONX.SHOPS[k].n, SK.ECONX.SHOPS[k].at]); });
  const files = [];
  for (const [id, name, at] of shops) {
    if (!at) continue;
    const r = await page.evaluate(([id, at]) => { const W = SK.W; SK.ECONX.closeSheet(); if (SK.PL.mode === 'ski') SK.PL.exitSki();
      /* find a standable spot within 3 m where the counter prompt shows */
      for (let rr = 0.5; rr < 14; rr += 0.5) for (let a = 0; a < 6.28; a += 0.3) { const x = at[0] + Math.cos(a) * rr, z = at[1] + Math.sin(a) * rr; const q = SK.ECON.nearby(x, z + SK.RES_Z); if (!q || !q.act) continue;
        W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); const fl = SK.FLOORS.find(f => W.x > f.x0 && W.x < f.x1 && W.z > f.z0 && W.z < f.z1); if (fl) W.y = fl.y0; W.yaw = Math.atan2(-(at[0] - x), -(at[1] - z)); W.vx = W.vz = 0; SK.play(2);
        const q2 = SK.ECON.nearby(W.x, W.z); if (q2 && q2.act) { SK.ECON.act(W.x, W.z); return q2.text; } }
      return null; }, [id, at]);
    await page.waitForTimeout(200); await fr();
    const sh = await page.evaluate(() => { const s = document.getElementById('sheet'); return s.classList.contains('off') ? '' : s.querySelector('.sh-h b').textContent; });
    console.log(id.padEnd(10), name.padEnd(22), '| prompt:', r, '| sheet:', sh);
    const f = `${OUT}/shop-${id}.png`; await page.screenshot({ path: f }); files.push([f, id]);
  }
  require('fs').writeFileSync(OUT + '/shops.json', JSON.stringify(files));
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
