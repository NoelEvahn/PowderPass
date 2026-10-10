// Bedroom shell: warm off-white walls, light oak floor, window wall behind the
// desk covered by a white pleated curtain. Unseen parts (bed, door, ceiling
// light) are plausible completions kept deliberately simple and neutral.
import * as THREE from 'three';
import { M } from '../core/materials.js';
import { mesh, group, rbox, box, cyl } from '../core/geo.js';

export const ROOM = { x0: - 2.0, x1: 2.0, z0: - 2.15, z1: 2.0, h: 2.6 };
export const WINDOW = { x0: - 1.05, x1: 1.05, y0: 0.85, y1: 2.25 };

function curtainGeometry( width, height, seed = 0 ) {

	const sx = 220, sy = 60;
	const g = new THREE.PlaneGeometry( width, height, sx, sy );
	const p = g.attributes.position;
	for ( let i = 0; i < p.count; i ++ ) {

		const x = p.getX( i ), y = p.getY( i );
		const t = 1 - ( y / height + 0.5 ); // 0 top → 1 bottom
		// pinch pleats at the top, relaxing into broader folds lower down
		const fine = Math.sin( x * 46 + seed ) * 0.022 * ( 1 - t * 0.5 );
		const broad = Math.sin( x * 13 + 1.7 + seed ) * 0.018 * ( 0.4 + t ) + Math.sin( x * 5.3 + seed * 2 ) * 0.012 * t;
		const sway = Math.sin( y * 3.1 + x * 2 ) * 0.004 * t;
		p.setZ( i, fine + broad + sway );
		// subtle hem lift at the bottom edge
		if ( t > 0.985 ) p.setY( i, y + ( t - 0.985 ) * 0.4 * Math.sin( x * 13 ) * 0.1 );

	}

	g.computeVertexNormals();
	return g;

}

export function buildRoom() {

	const root = group( 'room' );
	const { x0, x1, z0, z1, h } = ROOM;
	const W = x1 - x0, D = z1 - z0;

	// Floor
	const floor = mesh( new THREE.PlaneGeometry( W, D ), M.floor, { parent: root, r: [ - Math.PI / 2, 0, 0 ], p: [ ( x0 + x1 ) / 2, 0, ( z0 + z1 ) / 2 ], cast: false } );
	floor.material.map.repeat.set( W / 2.2, D / 2.2 );
	floor.material.roughnessMap.repeat.set( W / 2.2, D / 2.2 );
	floor.name = 'floor';

	// Ceiling
	mesh( new THREE.PlaneGeometry( W, D ), M.ceiling, { parent: root, r: [ Math.PI / 2, 0, 0 ], p: [ ( x0 + x1 ) / 2, h, ( z0 + z1 ) / 2 ], cast: false } );

	// Side and front walls
	const wall = ( w, p, ry ) => mesh( new THREE.PlaneGeometry( w, h ), M.wall, { parent: root, p, r: [ 0, ry, 0 ], cast: false } );
	wall( D, [ x0, h / 2, ( z0 + z1 ) / 2 ], Math.PI / 2 );
	wall( D, [ x1, h / 2, ( z0 + z1 ) / 2 ], - Math.PI / 2 );
	wall( W, [ ( x0 + x1 ) / 2, h / 2, z1 ], Math.PI );

	// Back wall with window opening (built from 4 slabs)
	const t = 0.12, bz = z0 - t / 2;
	const slab = ( sx, sy, cx, cy ) => mesh( box( sx, sy, t ), M.wall, { parent: root, p: [ cx, cy, bz ], cast: true } );
	slab( W, WINDOW.y0, 0, WINDOW.y0 / 2 );
	slab( W, h - WINDOW.y1, 0, ( h + WINDOW.y1 ) / 2 );
	slab( WINDOW.x0 - x0, WINDOW.y1 - WINDOW.y0, ( x0 + WINDOW.x0 ) / 2, ( WINDOW.y0 + WINDOW.y1 ) / 2 );
	slab( x1 - WINDOW.x1, WINDOW.y1 - WINDOW.y0, ( x1 + WINDOW.x1 ) / 2, ( WINDOW.y0 + WINDOW.y1 ) / 2 );

	// Window frame (white uPVC) + glass + sky backdrop
	const win = group( 'window', root );
	const fw = 0.06, wz = z0 - t * 0.6;
	const ww = WINDOW.x1 - WINDOW.x0, wh = WINDOW.y1 - WINDOW.y0, wcx = 0, wcy = ( WINDOW.y0 + WINDOW.y1 ) / 2;
	mesh( box( ww, fw, 0.07 ), M.skirting, { parent: win, p: [ wcx, WINDOW.y0 + fw / 2, wz ] } );
	mesh( box( ww, fw, 0.07 ), M.skirting, { parent: win, p: [ wcx, WINDOW.y1 - fw / 2, wz ] } );
	for ( const x of [ WINDOW.x0 + fw / 2, 0, WINDOW.x1 - fw / 2 ] ) mesh( box( fw, wh, 0.07 ), M.skirting, { parent: win, p: [ x, wcy, wz ] } );
	// window sill (inside)
	mesh( rbox( ww + 0.1, 0.025, 0.1, 0.004 ), M.skirting, { parent: win, p: [ 0, WINDOW.y0 - 0.0125, z0 - 0.015 ] } );
	const skyMat = new THREE.MeshStandardMaterial( { color: 0x000000, emissive: 0xdfe9f2, emissiveIntensity: 1.5, roughness: 1, name: 'sky' } );
	const sky = mesh( new THREE.PlaneGeometry( ww + 0.4, wh + 0.4 ), skyMat, { parent: win, p: [ 0, wcy, z0 - t - 0.3 ], cast: false, receive: false } );
	sky.userData.noAO = true;
	sky.userData.noPick = true;

	// Curtain rod + curtain
	const rodY = h - 0.12;
	mesh( cyl( 0.011, 0.011, 2.9, 16 ), M.brushedAlu, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ 0, rodY, z0 + 0.09 ] } );
	for ( const x of [ - 1.48, 1.48 ] ) {

		mesh( cyl( 0.018, 0.018, 0.02, 16 ), M.brushedAlu, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ x, rodY, z0 + 0.09 ] } );
		mesh( box( 0.02, 0.02, 0.09 ), M.brushedAlu, { parent: root, p: [ x - Math.sign( x ) * 0.06, rodY, z0 + 0.045 ] } );

	}

	const curtainH = rodY - 0.03;
	const curtains = group( 'curtain', root );
	for ( const [ cx, cw, seed ] of [ [ - 0.725, 1.43, 0 ], [ 0.725, 1.43, 2.1 ] ] ) {

		const c = mesh( curtainGeometry( cw, curtainH, seed ), M.curtain, { parent: curtains, p: [ cx, curtainH / 2 + 0.02, z0 + 0.075 ] } );
		c.castShadow = false; // sheer fabric: diffuses daylight rather than blocking it
		c.receiveShadow = true;
		// rings along the rod
		for ( let i = 0; i < 14; i ++ ) {

			const ring = mesh( new THREE.TorusGeometry( 0.016, 0.0025, 6, 16 ), M.brushedAlu, { parent: curtains, p: [ cx - cw / 2 + 0.05 + i * ( cw - 0.1 ) / 13, rodY, z0 + 0.09 ], cast: false } );
			ring.rotation.y = Math.PI / 2;

		}

	}

	// Skirting boards
	const sk = 0.08;
	mesh( box( W, sk, 0.012 ), M.skirting, { parent: root, p: [ 0, sk / 2, z0 + 0.006 ], cast: false } );
	mesh( box( W, sk, 0.012 ), M.skirting, { parent: root, p: [ 0, sk / 2, z1 - 0.006 ], cast: false } );
	mesh( box( 0.012, sk, D ), M.skirting, { parent: root, p: [ x0 + 0.006, sk / 2, ( z0 + z1 ) / 2 ], cast: false } );
	mesh( box( 0.012, sk, D ), M.skirting, { parent: root, p: [ x1 - 0.006, sk / 2, ( z0 + z1 ) / 2 ], cast: false } );

	// Wall socket behind the desk (right)
	mesh( rbox( 0.085, 0.085, 0.012, 0.006 ), M.whitePlastic, { parent: root, p: [ 0.95, 0.3, z0 + 0.006 ] } );
	for ( const x of [ - 0.01, 0.01 ] ) mesh( cyl( 0.0025, 0.0025, 0.004, 10 ), M.blackMatte, { parent: root, r: [ Math.PI / 2, 0, 0 ], p: [ 0.95 + x, 0.3, z0 + 0.013 ], cast: false } );

	// Ceiling light fixture (flush, opal diffuser)
	const lampMat = new THREE.MeshStandardMaterial( { name: 'ceilingLamp', color: 0xffffff, emissive: 0xfff1dc, emissiveIntensity: 0, roughness: 0.6 } );
	const lamp = mesh( new THREE.CylinderGeometry( 0.2, 0.22, 0.06, 48 ), lampMat, { parent: root, p: [ 0, h - 0.03, - 0.2 ], cast: false } );
	lamp.userData.noPick = true;

	// Door (front wall, right)
	const door = group( 'door', root, [ 1.35, 0, z1 ] );
	mesh( box( 0.82, 2.04, 0.04 ), M.skirting, { parent: door, p: [ 0, 1.02, - 0.025 ] } );
	for ( const [ sx, sy, px, py ] of [ [ 0.06, 2.1, - 0.44, 1.05 ], [ 0.06, 2.1, 0.44, 1.05 ], [ 0.94, 0.06, 0, 2.07 ] ] ) mesh( box( sx, sy, 0.02 ), M.skirting, { parent: door, p: [ px, py, - 0.012 ] } );
	mesh( cyl( 0.01, 0.01, 0.12, 12 ), M.brushedAlu, { parent: door, r: [ 0, 0, Math.PI / 2 ], p: [ 0.3, 1.0, - 0.07 ] } );
	mesh( cyl( 0.03, 0.03, 0.01, 20 ), M.brushedAlu, { parent: door, r: [ Math.PI / 2, 0, 0 ], p: [ 0.34, 1.0, - 0.05 ] } );

	// Bed along the left wall (outside the reference photo; neutral bedding)
	const bed = group( 'bed', root, [ - 1.27, 0, 0.75 ] );
	const bedMat = new THREE.MeshStandardMaterial( { color: 0xd9d4cc, roughness: 0.7, name: 'bedFrame' } );
	const duvetMat = new THREE.MeshPhysicalMaterial( { color: 0xe9e6e0, roughness: 0.95, sheen: 0.5, sheenRoughness: 0.7, sheenColor: new THREE.Color( 0xffffff ), normalMap: M.curtain.normalMap, normalScale: new THREE.Vector2( 0.3, 0.3 ), name: 'duvet' } );
	const throwMat = new THREE.MeshPhysicalMaterial( { color: 0x9a9fa6, roughness: 0.95, sheen: 0.6, sheenRoughness: 0.7, sheenColor: new THREE.Color( 0xdddddd ), name: 'throw' } );
	mesh( rbox( 1.46, 0.3, 2.06, 0.02 ), bedMat, { parent: bed, p: [ 0, 0.15 + 0.02, 0 ] } );
	for ( const [ x, z ] of [ [ - 0.68, - 0.98 ], [ 0.68, - 0.98 ], [ - 0.68, 0.98 ], [ 0.68, 0.98 ] ] ) mesh( box( 0.05, 0.03, 0.05 ), M.wood, { parent: bed, p: [ x, 0.015, z ] } );
	mesh( rbox( 1.46, 0.75, 0.06, 0.02 ), bedMat, { parent: bed, p: [ 0, 0.375, - 1.06 ] } );
	mesh( rbox( 1.4, 0.2, 2.0, 0.05, 3 ), M.whitePlastic, { parent: bed, p: [ 0, 0.42, 0 ] } );
	const duvet = mesh( rbox( 1.48, 0.1, 1.5, 0.045, 3 ), duvetMat, { parent: bed, p: [ 0, 0.55, 0.26 ] } );
	duvet.scale.set( 1, 1, 1 );
	mesh( rbox( 1.5, 0.04, 0.5, 0.018, 3 ), throwMat, { parent: bed, p: [ 0, 0.615, 0.68 ] } );
	for ( const x of [ - 0.34, 0.34 ] ) {

		const pillow = mesh( rbox( 0.62, 0.14, 0.4, 0.065, 4 ), duvetMat, { parent: bed, p: [ x, 0.6, - 0.78 ] } );
		pillow.rotation.x = - 0.25;

	}

	// Rug under the chair area (neutral wool)
	const rugMat = new THREE.MeshStandardMaterial( { color: 0xd8d2c8, roughness: 1, name: 'rug', normalMap: M.curtain.normalMap, normalScale: new THREE.Vector2( 0.6, 0.6 ) } );
	mesh( rbox( 1.6, 0.012, 1.1, 0.005 ), rugMat, { parent: root, p: [ 0.0, 0.006, - 0.75 ], cast: false } );

	return { root, curtainMat: M.curtain, skyMat, lampMat, curtains };

}
