// White desk + accessories: laptop, headset on stand, plant, storage shelf,
// toy van, cables.
import * as THREE from 'three';
import { M } from '../core/materials.js';
import { labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, extrude, mergeStatic } from '../core/geo.js';
import { ROOM } from './room.js';

export const DESK = { w: 1.6, d: 0.7, top: 0.75, t: 0.025, x: 0, zBack: - 2.0 };
DESK.zFront = DESK.zBack + DESK.d;
DESK.zC = ( DESK.zBack + DESK.zFront ) / 2;

export function buildDesk() {

	const g = group( 'desk' );
	const { w, d, top, t, zC } = DESK;
	const slab = mesh( rbox( w, t, d, 0.004, 3 ), M.desk, { parent: g, p: [ 0, top - t / 2, zC ] } );
	slab.name = 'deskTop';
	slab.material.roughnessMap.repeat.set( 2, 1 );

	// steel frame: four square legs + side rails + back stretcher
	const legH = top - t;
	for ( const [ x, z ] of [ [ - w / 2 + 0.05, DESK.zFront - 0.05 ], [ w / 2 - 0.05, DESK.zFront - 0.05 ], [ - w / 2 + 0.05, DESK.zBack + 0.05 ], [ w / 2 - 0.05, DESK.zBack + 0.05 ] ] ) {

		mesh( rbox( 0.04, legH, 0.04, 0.003 ), M.whitePowder, { parent: g, p: [ x, legH / 2, z ] } );
		mesh( cyl( 0.018, 0.018, 0.008, 16 ), M.whitePlastic, { parent: g, p: [ x, 0.004, z ] } );

	}

	for ( const x of [ - w / 2 + 0.05, w / 2 - 0.05 ] ) mesh( rbox( 0.025, 0.05, d - 0.1, 0.003 ), M.whitePowder, { parent: g, p: [ x, top - t - 0.025, zC ] } );
	mesh( rbox( w - 0.14, 0.05, 0.025, 0.003 ), M.whitePowder, { parent: g, p: [ 0, top - t - 0.025, DESK.zBack + 0.05 ] } );
	mesh( rbox( w - 0.14, 0.03, 0.025, 0.003 ), M.whitePowder, { parent: g, p: [ 0, 0.16, DESK.zBack + 0.05 ] } );
	return g;

}

export function buildLaptop() {

	// Closed ultrabook (silver aluminium) — model unknown, generic proportions
	const g = group( 'laptop' );
	const silver = new THREE.MeshStandardMaterial( { name: 'laptopAlu', color: 0xc5c7ca, metalness: 1, roughness: 0.38, normalMap: M.brushedAlu.normalMap, normalScale: new THREE.Vector2( 0.08, 0.08 ) } );
	mesh( rbox( 0.312, 0.009, 0.221, 0.004, 3 ), silver, { parent: g, p: [ 0, 0.0055, 0 ] } );
	mesh( rbox( 0.312, 0.0065, 0.221, 0.004, 3 ), silver, { parent: g, p: [ 0, 0.0135, 0 ] } );
	mesh( box( 0.3, 0.001, 0.209 ), M.blackMatte, { parent: g, p: [ 0, 0.0102, 0 ], cast: false } ); // lid seam
	mesh( rbox( 0.25, 0.004, 0.006, 0.002 ), M.gunmetal, { parent: g, p: [ 0, 0.01, - 0.108 ], cast: false } ); // hinge
	for ( const [ x, z ] of [ [ - 0.13, 0.09 ], [ 0.13, 0.09 ], [ - 0.13, - 0.09 ], [ 0.13, - 0.09 ] ] ) mesh( cyl( 0.005, 0.005, 0.0012, 12 ), M.rubber, { parent: g, p: [ x, 0.0006, z ] } );
	g.position.set( - 0.6, DESK.top, - 1.52 );
	g.rotation.y = 0.07;
	return g;

}

function bandGeometry( radius, width, thick, a0 = 0.15, a1 = Math.PI - 0.15 ) {

	const curve = new THREE.EllipseCurve( 0, 0, radius, radius * 1.08, a0, a1 );
	const pts = curve.getPoints( 48 ).map( ( p ) => new THREE.Vector3( p.x, p.y, 0 ) );
	const path = new THREE.CatmullRomCurve3( pts );
	const shape = roundedRectShape( thick, width, Math.min( width, thick ) * 0.45 ); // x → curve normal, y → binormal (band width)
	return new THREE.ExtrudeGeometry( shape, { extrudePath: path, steps: 64, bevelEnabled: false } );

}

export function buildHeadset() {

	// White & black over-ear headset hanging on a stand
	const g = group( 'headset' );
	const standMat = M.blackMetal;
	mesh( cyl( 0.065, 0.068, 0.012, 48 ), standMat, { parent: g, p: [ 0, 0.006, 0 ] } );
	mesh( cyl( 0.058, 0.058, 0.002, 48 ), M.rubber, { parent: g, p: [ 0, 0.0005, 0 ], cast: false } );
	mesh( cyl( 0.0075, 0.0075, 0.336, 20 ), standMat, { parent: g, p: [ 0, 0.012 + 0.168, 0 ] } );
	// curved cradle following the headband's inner radius
	mesh( bandGeometry( 0.068, 0.006, 0.03, 0.85, Math.PI - 0.85 ), standMat, { parent: g, p: [ 0, 0.27, 0 ] } );

	const hs = group( 'headsetBody', g, [ 0, 0.27, 0 ] );
	const white = new THREE.MeshStandardMaterial( { name: 'headsetWhite', color: 0xf1f0ec, roughness: 0.42 } );
	const blackSoft = new THREE.MeshStandardMaterial( { name: 'headsetCushion', color: 0x1a1a1b, roughness: 0.85, normalMap: M.curtain.normalMap, normalScale: new THREE.Vector2( 0.4, 0.4 ) } );
	// headband: white outer, black padded inner
	mesh( bandGeometry( 0.088, 0.034, 0.008 ), white, { parent: hs } );
	mesh( bandGeometry( 0.079, 0.028, 0.01, 0.35, Math.PI - 0.35 ), blackSoft, { parent: hs } );
	// sliders + yokes + cups
	for ( const s of [ - 1, 1 ] ) {

		mesh( rbox( 0.006, 0.05, 0.016, 0.002 ), M.brushedAlu, { parent: hs, p: [ s * 0.088, - 0.004, 0 ] } );
		const yoke = mesh( new THREE.TorusGeometry( 0.058, 0.0035, 8, 32, Math.PI ), white, { parent: hs, p: [ s * 0.094, - 0.088, 0 ] } );
		yoke.rotation.set( 0, Math.PI / 2, 0 );
		const cup = group( 'cup', hs, [ s * 0.092, - 0.088, 0 ] );
		const shell = mesh( cyl( 0.046, 0.05, 0.03, 40 ), white, { parent: cup, r: [ 0, 0, Math.PI / 2 ] } );
		shell.scale.set( 1, 1, 1.18 );
		const plate = mesh( cyl( 0.034, 0.034, 0.003, 40 ), M.blackMatte, { parent: cup, r: [ 0, 0, Math.PI / 2 ], p: [ s * 0.0155, 0, 0 ] } );
		plate.scale.set( 1, 1, 1.18 );
		const cushion = mesh( new THREE.TorusGeometry( 0.038, 0.012, 14, 40 ), blackSoft, { parent: cup, p: [ - s * 0.02, 0, 0 ] } );
		cushion.rotation.y = Math.PI / 2;
		cushion.scale.set( 1, 1.18, 1 );
		const inner = mesh( new THREE.CircleGeometry( 0.032, 32 ), blackSoft, { parent: cup, p: [ - s * 0.016, 0, 0 ] } );
		inner.rotation.y = - s * Math.PI / 2;
		inner.scale.set( 1, 1.18, 1 );
		if ( s < 0 ) mesh( cyl( 0.003, 0.003, 0.03, 10 ), M.blackMatte, { parent: cup, p: [ 0, - 0.04, 0.03 ], r: [ 0.6, 0, 0 ] } ); // mic stub

	}

	return g;

}

export function buildPlant() {

	const g = group( 'plant' );
	// matte white ceramic pot
	const potProfile = [ [ 0, 0 ], [ 0.042, 0 ], [ 0.046, 0.004 ], [ 0.055, 0.09 ], [ 0.056, 0.095 ], [ 0.051, 0.095 ], [ 0.048, 0.03 ], [ 0, 0.03 ] ].map( ( [ x, y ] ) => new THREE.Vector2( x, y ) );
	mesh( new THREE.LatheGeometry( potProfile, 48 ), M.ceramicWhite, { parent: g } );
	mesh( cyl( 0.05, 0.05, 0.004, 32 ), M.soil, { parent: g, p: [ 0, 0.083, 0 ], cast: false } );

	// leaves: curved lanceolate blades in a rosette (small pothos / peace-lily-like)
	const leafShape = new THREE.Shape();
	leafShape.moveTo( 0, 0 );
	leafShape.bezierCurveTo( 0.018, 0.02, 0.022, 0.06, 0, 0.1 );
	leafShape.bezierCurveTo( - 0.022, 0.06, - 0.018, 0.02, 0, 0 );
	const leafGeo = new THREE.ShapeGeometry( leafShape, 10 );
	{

		const p = leafGeo.attributes.position;
		for ( let i = 0; i < p.count; i ++ ) {

			const x = p.getX( i ), y = p.getY( i );
			p.setZ( i, - Math.pow( y / 0.1, 2 ) * 0.03 + Math.abs( x ) * 0.25 ); // arch + midrib fold

		}

		leafGeo.computeVertexNormals();

	}

	const rand = ( ( s ) => () => ( s = ( s * 16807 ) % 2147483647 ) / 2147483647 )( 7 );
	for ( let i = 0; i < 22; i ++ ) {

		const a = i * 2.39996;
		const tilt = 0.25 + rand() * 0.75;
		const stemLen = 0.04 + rand() * 0.07;
		const sg = group( 'stem', g, [ 0, 0.085, 0 ] );
		sg.rotation.y = a;
		const stem = mesh( cyl( 0.0015, 0.002, stemLen, 5 ), M.leafLight, { parent: sg, cast: false } );
		stem.rotation.x = tilt * 0.6;
		stem.position.set( 0, stemLen / 2 * Math.cos( tilt * 0.6 ), stemLen / 2 * Math.sin( tilt * 0.6 ) );
		const leaf = mesh( leafGeo, i % 3 === 0 ? M.leafLight : M.leaf, { parent: sg } );
		leaf.position.set( 0, stemLen * Math.cos( tilt * 0.6 ), stemLen * Math.sin( tilt * 0.6 ) );
		leaf.rotation.x = tilt;
		const sc = 0.8 + rand() * 0.5;
		leaf.scale.set( sc, sc, sc );

	}

	return g;

}

export function buildShelf() {

	// Small white 3-tier storage shelf on the right of the desk
	const g = group( 'shelf' );
	const W = 0.42, H = 0.76, D = 0.3, t = 0.016;
	mesh( rbox( t, H, D, 0.002 ), M.whitePlastic, { parent: g, p: [ - W / 2 + t / 2, H / 2, 0 ] } );
	mesh( rbox( t, H, D, 0.002 ), M.whitePlastic, { parent: g, p: [ W / 2 - t / 2, H / 2, 0 ] } );
	for ( const y of [ 0.02, 0.27, 0.515, H - t / 2 ] ) mesh( rbox( W, t, D, 0.002 ), M.whitePlastic, { parent: g, p: [ 0, y, 0 ] } );
	mesh( box( W - 0.004, H - 0.01, 0.004 ), M.whitePlastic, { parent: g, p: [ 0, H / 2, - D / 2 + 0.002 ] } );

	// contents
	const bookCols = [ 0x8c7f72, 0xd8cfc4, 0x5b6470, 0xc8b8a6, 0x3e3f44, 0xe6e1d8, 0x9aa39a ];
	let x = - W / 2 + t + 0.01;
	for ( let i = 0; i < 7; i ++ ) {

		const bw = 0.018 + ( i % 3 ) * 0.008, bh = 0.18 + ( i % 4 ) * 0.02;
		const mat = new THREE.MeshStandardMaterial( { color: bookCols[ i ], roughness: 0.75 } );
		mesh( rbox( bw, bh, 0.2, 0.002 ), mat, { parent: g, p: [ x + bw / 2, 0.278 + bh / 2, 0.02 ] } );
		x += bw + 0.002;

	}

	// leaning book
	const lean = mesh( rbox( 0.022, 0.2, 0.2, 0.002 ), new THREE.MeshStandardMaterial( { color: 0xb7a493, roughness: 0.75 } ), { parent: g, p: [ x + 0.04, 0.278 + 0.098, 0.02 ] } );
	lean.rotation.z = - 0.32;
	// fabric storage bin (bottom)
	const binMat = new THREE.MeshStandardMaterial( { color: 0xcfc6b8, roughness: 0.95, normalMap: M.curtain.normalMap, normalScale: new THREE.Vector2( 0.7, 0.7 ) } );
	mesh( rbox( W - 0.05, 0.22, 0.27, 0.01 ), binMat, { parent: g, p: [ 0, 0.028 + 0.11, 0.005 ] } );
	mesh( rbox( 0.08, 0.02, 0.004, 0.008 ), M.wood, { parent: g, p: [ 0, 0.2, 0.142 ] } );
	// top: small boxes + candle
	mesh( rbox( 0.16, 0.06, 0.12, 0.004 ), M.whiteGloss, { parent: g, p: [ - 0.08, H + 0.03, 0 ] } );
	mesh( rbox( 0.12, 0.04, 0.1, 0.004 ), new THREE.MeshStandardMaterial( { color: 0xd9cbbd, roughness: 0.8 } ), { parent: g, p: [ - 0.08, H + 0.08, 0 ] } );
	mesh( cyl( 0.035, 0.035, 0.07, 32 ), M.ceramicWhite, { parent: g, p: [ 0.11, H + 0.035, 0.02 ] } );
	// middle shelf: storage boxes
	mesh( rbox( 0.17, 0.12, 0.22, 0.004 ), new THREE.MeshStandardMaterial( { color: 0xe9e5de, roughness: 0.6 } ), { parent: g, p: [ 0.1, 0.523 + 0.06, 0.02 ] } );
	mesh( rbox( 0.15, 0.08, 0.2, 0.004 ), new THREE.MeshStandardMaterial( { color: 0xb9b1a6, roughness: 0.7 } ), { parent: g, p: [ - 0.1, 0.523 + 0.04, 0.02 ] } );
	g.position.set( 1.08, 0, - 1.83 );
	return g;

}

export function buildVan() {

	// Small decorative toy camper van (classic split-window style), sits on the PC
	const g = group( 'van' );
	const L = 0.11, Wd = 0.048, H = 0.046;
	const lower = new THREE.MeshPhysicalMaterial( { color: 0x7fb8b0, roughness: 0.25, clearcoat: 0.8, clearcoatRoughness: 0.1 } );
	const upper = new THREE.MeshPhysicalMaterial( { color: 0xf3f1ea, roughness: 0.25, clearcoat: 0.8, clearcoatRoughness: 0.1 } );
	const glassDark = new THREE.MeshPhysicalMaterial( { color: 0x1a2228, roughness: 0.08, metalness: 0.2, clearcoat: 1 } );
	// local: +z = front
	mesh( rbox( Wd, H * 0.48, L, 0.008, 3 ), lower, { parent: g, p: [ 0, 0.008 + H * 0.24, 0 ] } );
	mesh( rbox( Wd - 0.001, H * 0.55, L - 0.004, 0.012, 3 ), upper, { parent: g, p: [ 0, 0.008 + H * 0.69, - 0.001 ] } );
	// V-shaped front panel
	const vShape = new THREE.Shape();
	vShape.moveTo( - Wd / 2 + 0.004, 0.0 );
	vShape.lineTo( 0, - 0.012 );
	vShape.lineTo( Wd / 2 - 0.004, 0.0 );
	vShape.lineTo( Wd / 2 - 0.004, 0.006 );
	vShape.lineTo( 0, - 0.006 );
	vShape.lineTo( - Wd / 2 + 0.004, 0.006 );
	const v = mesh( new THREE.ShapeGeometry( vShape ), upper, { parent: g, p: [ 0, 0.008 + H * 0.46, L / 2 + 0.0002 ] } );
	v.castShadow = false;
	// windows
	for ( const sx of [ - 1, 1 ] ) {

		mesh( rbox( Wd * 0.4, 0.013, 0.002, 0.003 ), glassDark, { parent: g, p: [ sx * Wd * 0.22, 0.008 + H * 0.75, L / 2 - 0.0005 ] } );
		for ( let i = 0; i < 3; i ++ ) mesh( rbox( 0.002, 0.012, 0.022, 0.002 ), glassDark, { parent: g, p: [ sx * ( Wd / 2 + 0.0003 ), 0.008 + H * 0.76, 0.03 - i * 0.027 ] } );

	}

	// wheels
	for ( const [ x, z ] of [ [ - 1, 1 ], [ 1, 1 ], [ - 1, - 1 ], [ 1, - 1 ] ] ) {

		mesh( cyl( 0.0092, 0.0092, 0.007, 20 ), M.rubber, { parent: g, r: [ 0, 0, Math.PI / 2 ], p: [ x * ( Wd / 2 - 0.002 ), 0.0092, z * 0.034 ] } );
		mesh( cyl( 0.005, 0.005, 0.0075, 16 ), M.chrome, { parent: g, r: [ 0, 0, Math.PI / 2 ], p: [ x * ( Wd / 2 - 0.0015 ), 0.0092, z * 0.034 ] } );

	}

	// bumpers + headlights
	mesh( rbox( Wd + 0.002, 0.004, 0.004, 0.0015 ), M.chrome, { parent: g, p: [ 0, 0.01, L / 2 + 0.001 ] } );
	mesh( rbox( Wd + 0.002, 0.004, 0.004, 0.0015 ), M.chrome, { parent: g, p: [ 0, 0.01, - L / 2 - 0.001 ] } );
	for ( const x of [ - 0.015, 0.015 ] ) mesh( cyl( 0.004, 0.004, 0.002, 16 ), M.chrome, { parent: g, r: [ Math.PI / 2, 0, 0 ], p: [ x, 0.02, L / 2 + 0.0006 ] } );
	mesh( cyl( 0.005, 0.005, 0.001, 16 ), M.chrome, { parent: g, r: [ Math.PI / 2, 0, 0 ], p: [ 0, 0.026, L / 2 + 0.0006 ] } );
	return g;

}

export function buildDeskCables( { pcBack, monitorBack } ) {

	const g = group( 'deskCables' );
	const tube = ( pts, r, mat ) => mesh( new THREE.TubeGeometry( new THREE.CatmullRomCurve3( pts.map( ( p ) => new THREE.Vector3( ...p ) ), false, 'centripetal' ), 80, r, 8 ), mat, { parent: g } );
	const y = DESK.top + 0.0035;
	const zb = DESK.zBack + 0.03;
	// DisplayPort: GPU → monitor
	tube( [ [ pcBack.dp.x, pcBack.dp.y, pcBack.dp.z ], [ pcBack.dp.x, pcBack.dp.y - 0.02, pcBack.dp.z - 0.035 ], [ pcBack.dp.x - 0.05, y + 0.01, zb ], [ 0.2, y, zb + 0.005 ], [ - 0.02, y, zb + 0.01 ], [ monitorBack.x + 0.06, y + 0.02, monitorBack.z - 0.04 ], [ monitorBack.x + 0.05, monitorBack.y - 0.04, monitorBack.z - 0.01 ], [ monitorBack.x + 0.05, monitorBack.y, monitorBack.z ] ], 0.0032, M.cableRubber );
	// PC power cable: PSU inlet → over the back edge → floor
	tube( [ [ pcBack.psu.x, pcBack.psu.y, pcBack.psu.z ], [ pcBack.psu.x, pcBack.psu.y - 0.01, pcBack.psu.z - 0.04 ], [ pcBack.psu.x + 0.02, y + 0.004, DESK.zBack + 0.012 ], [ pcBack.psu.x + 0.03, y - 0.01, DESK.zBack - 0.012 ], [ pcBack.psu.x + 0.05, 0.4, DESK.zBack - 0.03 ], [ 0.92, 0.3, ROOM.z0 + 0.03 ], [ 0.95, 0.3, ROOM.z0 + 0.016 ] ], 0.0042, M.cableRubber );
	// Monitor power
	tube( [ [ monitorBack.x - 0.05, monitorBack.y, monitorBack.z ], [ monitorBack.x - 0.05, monitorBack.y - 0.05, monitorBack.z - 0.02 ], [ monitorBack.x - 0.08, y + 0.004, DESK.zBack + 0.015 ], [ monitorBack.x - 0.09, y - 0.01, DESK.zBack - 0.012 ], [ monitorBack.x - 0.1, 0.35, DESK.zBack - 0.03 ], [ monitorBack.x - 0.1, 0.02, DESK.zBack - 0.05 ], [ 0.4, 0.008, ROOM.z0 + 0.05 ], [ 0.9, 0.008, ROOM.z0 + 0.04 ], [ 0.95, 0.27, ROOM.z0 + 0.016 ] ], 0.0035, M.cableRubber );
	// Laptop charger (white USB-C)
	tube( [ [ - 0.75, DESK.top + 0.008, - 1.56 ], [ - 0.78, DESK.top + 0.004, - 1.62 ], [ - 0.74, y, - 1.86 ], [ - 0.7, y, DESK.zBack + 0.02 ], [ - 0.69, y - 0.01, DESK.zBack - 0.012 ], [ - 0.68, 0.3, DESK.zBack - 0.03 ], [ - 0.6, 0.01, ROOM.z0 + 0.06 ] ], 0.0022, M.cableRubberWhite );
	return g;

}
