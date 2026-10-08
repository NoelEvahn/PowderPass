const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors, bootMs } = await boot();
  console.log('boot ms', bootMs);
  await page.waitForTimeout(1500);
  const st = await page.evaluate(() => ({ coins: SK.State.coins, day: SK.State.day, mode: SK.PL.mode, saved: SK.save() }));
  check(st.coins >= 0 && st.day >= 1, 'state ok ' + JSON.stringify(st), fails);
  await page.screenshot({ path: OUT + '/smoke.png' });
  /* save round trip */
  await page.evaluate(() => { SK.State.coins = 4321; SK.save(); });
  await page.reload(); await page.waitForFunction(() => window.SK && SK.State && !document.getElementById('load'), null, { timeout: 180000 });
  const c2 = await page.evaluate(() => SK.State.coins);
  check(c2 === 4321, 'save round trip (coins ' + c2 + ')', fails);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
