/* cut-away floor plans: everything above (floor + cut) is clipped, camera looks straight down.
   node tools/plan.js <name> <xLocal> <zLocal> <radius> <floorY(world)> [cut=2.1] [tiltDeg=0] -> tools/out/plan-<name>.png */
const { boot, OUT } = require('./lib');
(async () => {
  const [name, x, z, r, fy, cut, tilt] = [process.argv[2], +process.argv[3], +process.argv[4], +process.argv[5], +process.argv[6], +(process.argv[7] || 2.1), +(process.argv[8] || 0)];
  const { browser, page, errors } = await boot({ viewport: { width: 1100, height: 760 } });
  await page.evaluate(([x, z, r, fy, cut, tilt]) => { SK.W.on = false; document.body.classList.add('ed'); SK.P6.setTime(13 * 60);
    SK.renderer.clippingPlanes = [new THREE.Plane(new THREE.Vector3(0, -1, 0), fy + cut)];
    SK.scene.traverse(o => { if (o.isMesh && o.material && o.material.isShaderMaterial && o.material.side === THREE.BackSide && o.geometry && o.geometry.attributes.sn) o.visible = false; });   /* outline shells ignore clipping */
    SK.snap({ tx: x, ty: fy, tz: z + SK.RES_Z, r: r, theta: 0, phi: Math.max(0.001, tilt * Math.PI / 180) }); }, [x, z, r, fy, cut, tilt]);
  await page.waitForTimeout(1200); await page.screenshot({ path: `${OUT}/plan-${name}.png` });
  console.log(errors.join('\n') || 'ok'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
