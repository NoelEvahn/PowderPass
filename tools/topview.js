/* overhead screenshot: node tools/topview.js cx cz r [name] (resort-local x/z) */
const { boot, OUT } = require('./lib');
(async () => {
  const [cx, cz, r, name] = [+process.argv[2], +process.argv[3], +process.argv[4] || 60, process.argv[5] || 'top'];
  const { browser, page } = await boot();
  await page.evaluate(([cx, cz, r]) => { SK.W.on = false; const y = SK.groundY(cx, cz + SK.RES_Z); SK.snap({ tx: cx, ty: y, tz: cz + SK.RES_Z, r: r, theta: 0, phi: 0.02 }); }, [cx, cz, r]);
  await page.waitForTimeout(1500);
  await page.screenshot({ path: OUT + '/' + name + '.png' }); await browser.close();
})();
