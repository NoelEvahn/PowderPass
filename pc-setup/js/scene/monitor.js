// Xiaomi Mini LED Gaming Monitor G Pro 27i — 27" 2560x1440 180 Hz.
// Panel 613 x 364.5 mm, overall height on stand ≈ 526 mm, base depth ≈ 169 mm.
import * as THREE from 'three';
import { M, TEX } from '../core/materials.js';
import { desktopTexture, labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, extrude } from '../core/geo.js';
import { DESK } from './desk.js';

export const MONITOR = {
	x: - 0.18, zFront: - 1.80,
	panelW: 0.6133, panelH: 0.3645, screenW: 0.5967, screenH: 0.3356,
	topY: DESK.top + 0.5265,
};
MONITOR.cy = MONITOR.topY - MONITOR.panelH / 2;

export function buildMonitor() {

	const g = group( 'monitor' );
	const { panelW, panelH, screenW, screenH } = MONITOR;
	g.position.set( MONITOR.x, 0, MONITOR.zFront );

	const housing = new THREE.MeshStandardMaterial( { name: 'monitorHousing', color: 0x222326, roughness: 0.55, metalness: 0.1, normalMap: TEX.grain, normalScale: new THREE.Vector2( 0.05, 0.05 ) } );
	const bezelMat = new THREE.MeshStandardMaterial( { name: 'bezel', color: 0x0d0d0e, roughness: 0.4 } );

	const panel = group( 'panel', g, [ 0, MONITOR.cy, 0 ] );
	// thin front frame
	mesh( rbox( panelW, panelH, 0.011, 0.003 ), bezelMat, { parent: panel, p: [ 0, 0, - 0.0055 ] } );
	// rear housing: stepped bulge with soft edges
	mesh( rbox( panelW - 0.01, panelH - 0.01, 0.014, 0.006 ), housing, { parent: panel, p: [ 0, 0, - 0.017 ] } );
	mesh( rbox( 0.44, 0.27, 0.03, 0.02, 4 ), housing, { parent: panel, p: [ 0, - 0.02, - 0.035 ] } );
	mesh( rbox( 0.3, 0.19, 0.022, 0.02, 4 ), housing, { parent: panel, p: [ 0, - 0.03, - 0.055 ] } );
	// rear vent slots
	for ( let i = 0; i < 18; i ++ ) mesh( box( 0.012, 0.0025, 0.003 ), M.blackMatte, { parent: panel, p: [ - 0.15 + i * 0.0175, 0.098, - 0.0505 ], cast: false } );
	// port recess (bottom of the rear bulge)
	mesh( box( 0.18, 0.004, 0.025 ), M.blackMatte, { parent: panel, p: [ 0.0, - 0.125, - 0.05 ], cast: false } );

	// Screen: emissive Windows 11 desktop, matte anti-glare surface
	const tex = desktopTexture();
	const screenMat = new THREE.MeshPhysicalMaterial( {
		name: 'screen', color: 0x030304, roughness: 0.32, metalness: 0,
		emissive: 0xffffff, emissiveMap: tex, emissiveIntensity: 1.0,
		clearcoat: 0.25, clearcoatRoughness: 0.35,
	} );
	const screen = mesh( new THREE.PlaneGeometry( screenW, screenH ), screenMat, { parent: panel, p: [ 0, ( panelH - screenH ) / 2 - 0.0085 + 0.0005, 0.00015 ], cast: false } );
	screen.name = 'screen';
	screen.userData.noAO = true;

	// chin logo
	const logo = labelTexture( 256, 32, ( ctx, w, h ) => {

		ctx.fillStyle = '#5c5d61'; ctx.font = 'bold 20px Arial'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
		ctx.fillText( 'Xiaomi', w / 2, h / 2 );

	} );
	const lm = mesh( new THREE.PlaneGeometry( 0.032, 0.004 ), new THREE.MeshStandardMaterial( { map: logo, transparent: true } ), { parent: panel, p: [ 0, - panelH / 2 + 0.0055, 0.0002 ], cast: false } );
	lm.userData.noAO = true;
	// power LED (small, white, on the underside of the chin)
	const ledMat = new THREE.MeshStandardMaterial( { color: 0x111111, emissive: 0xffffff, emissiveIntensity: 0.6 } );
	const led = mesh( box( 0.003, 0.0008, 0.002 ), ledMat, { parent: panel, p: [ 0.25, - panelH / 2 - 0.0002, - 0.004 ], cast: false } );

	// Stand: flat rectangular metal base + upright column + hinge block
	const standMat = new THREE.MeshStandardMaterial( { name: 'stand', color: 0x2b2c2f, roughness: 0.35, metalness: 0.8, normalMap: M.brushedAlu.normalMap, normalScale: new THREE.Vector2( 0.15, 0.15 ) } );
	const baseShape = roundedRectShape( 0.27, 0.169, 0.02 );
	const base = mesh( extrude( baseShape, 0.01, { bevel: 0.002, curveSegments: 12 } ), standMat, { parent: g } );
	base.rotation.x = - Math.PI / 2;
	base.position.set( 0, DESK.top + 0.005, - 0.06 );
	mesh( rbox( 0.27, 0.0015, 0.169, 0.018 ), M.rubber, { parent: g, p: [ 0, DESK.top + 0.0008, - 0.06 ], cast: false } );
	const colH = MONITOR.cy - DESK.top - 0.02;
	mesh( rbox( 0.06, colH, 0.026, 0.008 ), standMat, { parent: g, p: [ 0, DESK.top + 0.01 + colH / 2, - 0.105 ] } );
	mesh( rbox( 0.075, 0.09, 0.03, 0.01 ), standMat, { parent: g, p: [ 0, MONITOR.cy - 0.01, - 0.082 ] } );
	// cable management cut-out in the column
	mesh( rbox( 0.03, 0.05, 0.002, 0.008 ), M.blackMatte, { parent: g, p: [ 0, DESK.top + 0.13, - 0.1185 ], cast: false } );

	return {
		root: g, screen, screenMat, ledMat,
		desktop: tex,
		back: new THREE.Vector3( MONITOR.x, MONITOR.cy - 0.12, MONITOR.zFront - 0.06 ),
	};

}
