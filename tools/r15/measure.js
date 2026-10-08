/* body measurements of both avatars (standing, on flat ground at the plaza) -> metres */
const W = SK.W; W.x = -44; W.z = -55; W.y = SK.groundY(W.x, W.z); W.yaw = 0;
const out = {};
for (const [k, A] of [['walker', SK.AVW], ['skier', SK.AVS]]) {
  const p = A.p, d = p.userData.dbg; A.wrap.position.set(W.x, W.y, W.z); A.wrap.rotation.set(0, 0, 0); A.wrap.visible = true;
  p.userData.play('Standing', true); SK.sim(20, 0.05); A.wrap.updateMatrixWorld(true);
  const bb = new THREE.Box3().setFromObject(p), v = new THREE.Vector3(), hd = d.head.getWorldPosition(new THREE.Vector3());
  const nk = d.head.parent.getWorldPosition(new THREE.Vector3()), sh = d.arms[0].sh.getWorldPosition(new THREE.Vector3());
  out[k] = { height: +(bb.max.y - W.y).toFixed(3), headBase: +(hd.y - W.y).toFixed(3), neckBase: +(nk.y - W.y).toFixed(3), neckLen: +(hd.y - nk.y).toFixed(3), shoulder: +(sh.y - W.y).toFixed(3), soleMin: +(bb.min.y - W.y).toFixed(3) };
  A.wrap.visible = false;
}
out.fpWalkEye = (SK.EYE || null); return out;
