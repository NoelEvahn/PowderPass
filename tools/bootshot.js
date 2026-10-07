/* boots + footprints close-up: a walker and a sprinter cross fresh snow; prints must land under the feet. tools/out/boots-*.png */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important} #gh,#ctl,#qt,#toast,#hint{display:none!important}' });
  const fr = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  const r = await page.evaluate(() => {
    const X = -8, Z = -40, W = SK.W, out = {};
    const mk = (b, o, x) => { const g = SK.PEOPLE[b](Object.assign({ npc: 1 }, o)); g.scale.setScalar(0.5); g.position.set(x, SK.groundY(x, Z + SK.RES_Z), Z + SK.RES_Z); SK.scene.add(g); SK.SNOWS.push(g); return g; };
    const walker = mk('guy', {}, X), runner = mk('lady', {}, X + 3), skier = mk('man', {}, X + 6);
    walker.userData.play('Walking'); runner.userData.play('Sprinting'); skier.userData.play('Standing');
    const feet = []; const t0 = SK.clock;
    for (let i = 0; i < 30 * 5; i++) {   /* walk 1.1 m/s, sprint 3.2 m/s toward -z (uphill) */
      [[walker, 1.1], [runner, 3.2]].forEach(([g, v]) => { g.position.z -= v / 30; g.position.y = SK.groundY(g.position.x, g.position.z); g.rotation.y = Math.PI; });
      SK.play(1, 1 / 30);
    }
    /* compare: the snow is pressed under the last planted feet */
    const sink = (x, z) => SK.SNOW.sinkAt(x, z - SK.RES_Z);
    const along = g => { const a = []; for (let d = 0; d < 6; d += 0.1) a.push(+(sink(g.position.x, g.position.z + d) * 100).toFixed(1)); return a; };
    out.walkProfile = along(walker).join(' '); out.runProfile = along(runner).join(' ');
    W.x = X + 3; W.z = Z + SK.RES_Z - 9; W.y = SK.groundY(W.x, W.z); W.yaw = 0; W.pitch = -0.55; SK.play(1);
    return out;
  });
  console.log(JSON.stringify(r));
  await fr(); await page.screenshot({ path: OUT + '/boots-tracks.png' });
  await page.evaluate(() => { const W = SK.W; W.x -= 1.5; W.z -= 2.2; W.y = SK.groundY(W.x, W.z) - 1.0; W.yaw = Math.PI * 0.85; W.pitch = -0.2; SK.play(1); });
  await fr(); await page.screenshot({ path: OUT + '/boots-close.png' });
  console.log(errors.join('|') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
