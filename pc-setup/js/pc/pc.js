// Assembles the complete PC inside the NZXT H6 Flow and provides the
// exploded-view animation.
import * as THREE from 'three';
import { M, rgbMaterial, areaLightFree } from '../core/materials.js';
import { group, mesh, mergeStatic } from '../core/geo.js';
import { buildCase } from './case.js';
import { buildMotherboard } from './motherboard.js';
import { buildRam, ramLightMaterial } from './ram.js';
import { buildGpu } from './gpu.js';
import { buildCooler } from './cooler.js';
import { buildPsu } from './psu.js';
import { CASE, bpos } from './layout.js';

function cableBundle( parent, points, { strands = 8, rows = 2, r = 0.0019, mat = M.cableBlack, spreadAxis = new THREE.Vector3( 0, 0, 1 ), rowAxis = new THREE.Vector3( 1, 0, 0 ) } = {} ) {

	const perRow = Math.ceil( strands / rows );
	const geos = [];
	for ( let row = 0; row < rows; row ++ ) {

		for ( let i = 0; i < perRow; i ++ ) {

			const off = spreadAxis.clone().multiplyScalar( ( i - ( perRow - 1 ) / 2 ) * r * 2.05 ).add( rowAxis.clone().multiplyScalar( ( row - ( rows - 1 ) / 2 ) * r * 2.05 ) );
			const curve = new THREE.CatmullRomCurve3( points.map( ( p ) => new THREE.Vector3( ...p ).add( off ) ), false, 'centripetal' );
			geos.push( new THREE.TubeGeometry( curve, 40, r, 6 ) );

		}

	}

	const g = group( 'cable', parent );
	for ( const geo of geos ) mesh( geo, mat, { parent: g } );
	return g;

}

export function buildPC() {

	const pc = group( 'PC' );
	const parts = buildCase();
	pc.add( parts.root );

	// RGB materials (one per controllable zone)
	const rgbMats = {
		ram: ramLightMaterial(),
		aioFans: rgbMaterial( 'aioFanBlade', 0xf2f1ee, { roughness: 0.5, side: THREE.DoubleSide } ),
		aioHub: rgbMaterial( 'aioFanHub', 0xf4f4f4, { roughness: 0.35 } ),
		pump: rgbMaterial( 'pumpAccent', 0xf0f0f0, { roughness: 0.3 } ),
	};

	const mb = buildMotherboard();
	pc.add( mb.root );
	const ram = buildRam( rgbMats.ram );
	ram.forEach( ( s ) => pc.add( s ) );
	const gpu = buildGpu();
	pc.add( gpu );
	const cooler = buildCooler( { fanBladeMat: rgbMats.aioFans, fanHubMat: rgbMats.aioHub, pumpAccentMat: rgbMats.pump } );
	pc.add( cooler.root );
	const psu = buildPsu();
	pc.add( psu );

	// --- Cables (Corsair RMe ships black flat/sleeved modular cables) ------
	const cables = group( 'cables', pc );
	// 24-pin ATX: from tray grommet around the board's front edge into the connector
	const c24 = bpos( 0.236, 0.1275, 0.013 );
	cableBundle( cables, [
		[ c24.x + 0.001, c24.y, c24.z ], [ c24.x - 0.012, c24.y, c24.z + 0.002 ], [ c24.x - 0.016, c24.y, c24.z + 0.02 ],
		[ 0.012, c24.y - 0.002, 0.073 ], [ CASE.trayX + 0.004, c24.y - 0.004, 0.057 ], [ CASE.trayX + 0.012, c24.y - 0.03, 0.045 ],
	], { strands: 24, rows: 2, r: 0.0018, spreadAxis: new THREE.Vector3( 0, 1, 0 ), rowAxis: new THREE.Vector3( 0, 0, 1 ) } );

	// EPS 8-pin from the top grommet
	const eps = bpos( 0.028, 0.0045, 0.013 );
	cableBundle( cables, [
		[ eps.x + 0.001, eps.y, eps.z ], [ eps.x - 0.01, eps.y + 0.004, eps.z ], [ eps.x - 0.006, 0.421, eps.z ],
		[ CASE.trayX - 0.002, 0.421, - 0.165 ], [ CASE.trayX + 0.01, 0.412, - 0.165 ],
	], { strands: 8, rows: 2, r: 0.0019, spreadAxis: new THREE.Vector3( 0, 0, 1 ), rowAxis: new THREE.Vector3( 0, 1, 0 ) } );

	// PCIe 8-pin to the GPU's outer edge, down past the card into the front grommet
	gpu.updateMatrix();
	const plug = gpu.userData.powerPlug.clone().applyMatrix4( gpu.matrix );
	cableBundle( cables, [
		[ plug.x + 0.002, plug.y, plug.z ], [ plug.x - 0.014, plug.y, plug.z + 0.002 ], [ - 0.128, plug.y - 0.03, plug.z + 0.012 ],
		[ - 0.124, 0.166, 0.045 ], [ - 0.07, 0.135, 0.068 ], [ 0.0, 0.128, 0.074 ], [ CASE.trayX + 0.004, 0.13, 0.057 ], [ CASE.trayX + 0.014, 0.12, 0.04 ],
	], { strands: 8, rows: 2, r: 0.0019, spreadAxis: new THREE.Vector3( 0, 0, 1 ), rowAxis: new THREE.Vector3( 0, 1, 0 ) } );

	// Reduce draw calls on static detail
	mergeStatic( parts.chassis );
	mergeStatic( cables );

	// Interior RGB spill lights (no shadows)
	const ramLight = new THREE.PointLight( 0xffffff, 0, 0.3, 2 );
	ramLight.position.copy( bpos( 0.165, 0.1, 0.07 ) );
	pc.add( ramLight );
	const fanLight = new THREE.PointLight( 0xffffff, 0, 0.45, 2 );
	fanLight.position.set( - 0.08, 0.345, 0.0 );
	pc.add( fanLight );

	// --- Component registry (ids used by inspection/labels) --------------
	const tag = ( obj, id ) => obj.traverse( ( o ) => { o.userData.componentId ??= id; } );
	tag( cooler.pump, 'cooler' );
	tag( cooler.radiator, 'cooler' );
	cooler.tubes.userData.componentId = 'cooler';
	tag( gpu, 'gpu' );
	ram.forEach( ( s ) => tag( s, 'ram' ) );
	tag( mb.ssd, 'ssd' );
	tag( mb.m2Heatsink, 'ssd' );
	tag( mb.root, 'motherboard' );
	tag( psu, 'psu' );
	tag( parts.root, 'case' );
	tag( cables, 'psu' );

	// Interior shading: parts inside the case are shielded from the window's
	// (shadowless) area light by the chassis; swap to area-light-free variants and
	// scale down the room reflection probe slightly to account for enclosure.
	const exterior = new Set( [ parts.top, parts.right ] );
	for ( const m of Object.values( rgbMats ) ) m.userData.keep = true; // driven by the RGB controller
	cooler.lcd.material.userData.keep = true;                            // driven by the screen system
	const swap = ( m ) => ( m.userData?.keep ? m : areaLightFree( m, 0.7 ) );
	pc.traverse( ( o ) => {

		if ( ! o.isMesh || o.userData.glass ) return;
		for ( let p = o; p; p = p.parent ) if ( exterior.has( p ) ) return;
		o.material = Array.isArray( o.material ) ? o.material.map( swap ) : swap( o.material );

	} );

	// Merge static sub-parts per material (cuts draw calls; moving parts are
	// separate groups flagged noMerge and are left intact).
	mb.m2Heatsink.userData.noMerge = true;
	mb.ssd.userData.noMerge = true;
	for ( const g of [ mb.root, gpu, psu, cooler.radiator, cooler.pump, ...ram, parts.top, parts.right, parts.angledFans ] ) mergeStatic( g );

	// Glass objects (for toggling + raycast pass-through)
	const glass = [];
	pc.traverse( ( o ) => { if ( o.userData.glass ) glass.push( o ); } );

	// Fans to animate
	const fans = [ ...parts.angledFans.userData.fans, ...cooler.fans, ...gpu.userData.fans, psu.userData.fan ];

	// --- Exploded view ----------------------------------------------------
	const explodeParts = [
		{ obj: parts.glassLeft, off: [ - 0.2, 0, 0 ], d: 0 },
		{ obj: parts.glassFront, off: [ 0, 0, 0.22 ], d: 0 },
		{ obj: parts.top, off: [ 0, 0.3, 0 ], d: 0.05 },
		{ obj: parts.right, off: [ 0.3, 0, 0 ], d: 0.05 },
		{ obj: cooler.radiator, off: [ 0, 0.17, 0 ], d: 0.2 },
		{ obj: parts.angledFans, off: [ 0.09, 0, 0.13 ], d: 0.2 },
		{ obj: gpu, off: [ - 0.17, - 0.03, 0 ], d: 0.3 },
		{ obj: psu, off: [ 0.2, 0, 0 ], d: 0.32 },
		{ obj: cooler.pump, off: [ - 0.13, 0.02, 0 ], d: 0.4 },
		{ obj: ram[ 0 ], off: [ - 0.1, 0.02, 0 ], d: 0.45 },
		{ obj: ram[ 1 ], off: [ - 0.1, 0.02, 0.035 ], d: 0.5 },
		{ obj: mb.m2Heatsink, off: [ - 0.06, 0.075, 0 ], d: 0.45 }, // lifts up and away to reveal the SSD
		{ obj: mb.ssd, off: [ - 0.04, 0, 0 ], d: 0.5 },
	];
	for ( const p of explodeParts ) {

		p.base = p.obj.position.clone();
		p.offset = new THREE.Vector3( ...p.off );

	}

	const state = { explode: 0, target: 0 };
	const ease = ( t ) => t < 0.5 ? 4 * t * t * t : 1 - Math.pow( - 2 * t + 2, 3 ) / 2;
	function updateExplode( dt ) {

		if ( state.explode === state.target ) return false;
		const dir = Math.sign( state.target - state.explode );
		state.explode = THREE.MathUtils.clamp( state.explode + dir * dt / 1.8, 0, 1 );
		if ( Math.abs( state.explode - state.target ) < 1e-4 ) state.explode = state.target;
		for ( const p of explodeParts ) {

			// staggered: each part moves within [d, d + 0.5] of the global timeline
			const local = THREE.MathUtils.clamp( ( state.explode - p.d ) / 0.5, 0, 1 );
			p.obj.position.copy( p.base ).addScaledVector( p.offset, ease( local ) );

		}

		cables.visible = state.explode < 0.02;
		cooler.rebuildTubes();
		return true;

	}

	const anchors = {
		case: new THREE.Vector3( 0, CASE.H + 0.05, 0.0 ),
		motherboard: bpos( 0.2, 0.2, 0.02 ),
		cpu: bpos( 0.1, 0.095, 0.07 ),
		cooler: new THREE.Vector3( - 0.08, 0.39, 0.0 ),
		gpu: new THREE.Vector3( - 0.06, 0.18, 0.02 ),
		ram: bpos( 0.165, 0.04, 0.045 ),
		ssd: bpos( 0.09, 0.157, 0.012 ),
		psu: new THREE.Vector3( 0.08, 0.17, - 0.13 ),
	};

	return {
		root: pc, parts, mb, ram, gpu, cooler, psu, cables, glass, fans, rgbMats,
		lights: { ramLight, fanLight }, anchors,
		explode: state, updateExplode, explodeParts,
	};

}
