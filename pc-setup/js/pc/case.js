// NZXT H6 Flow (White) — dual-chamber chassis with wrap-around front/left
// tempered glass, three angled intake fans in the front-right corner,
// perforated top and right panels, PSU in the rear chamber.
import * as THREE from 'three';
import { M } from '../core/materials.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, roundedRectPath, extrude } from '../core/geo.js';
import { CASE } from './layout.js';
import { createFan } from './fan.js';

const { W, D, H, feet, sheet, glassT, xL, xR, zB, zF } = CASE;
const solidDepth = new THREE.MeshDepthMaterial(); // matches WebGLShadowMap's default depth material, minus the alpha mask

function perfPlane( w, h, density = 300 ) {

	const g = new THREE.PlaneGeometry( w, h );
	const m = mesh( g, M.makePerforated( w, h, density ) );
	m.castShadow = true;
	// Cast a solid shadow: the sub-millimetre holes would otherwise alias into
	// speckled noise in the shadow map; light through the mesh is diffuse anyway.
	m.customDepthMaterial = solidDepth;
	return m;

}

function backPanel() {

	// Shape in case XY, extruded along Z
	const s = roundedRectShape( W - 0.002, H - feet - 0.004, 0.003, 0, ( feet + H ) / 2 );
	const hole = ( x0, x1, y0, y1, r = 0.002 ) => s.holes.push( roundedRectPath( x1 - x0, y1 - y0, r, ( x0 + x1 ) / 2, ( y0 + y1 ) / 2 ) );
	hole( - 0.022, 0.026, 0.246, 0.402 );            // motherboard I/O
	hole( - 0.135, - 0.03, 0.252, 0.368, 0.004 );     // rear fan grille
	hole( - 0.114, 0.018, 0.088, 0.242 );            // expansion slots
	hole( 0.037, 0.138, 0.025, 0.177 );              // PSU
	const g = extrude( s, sheet, { curveSegments: 6 } );
	const grp = group( 'backPanel' );
	mesh( g, M.whitePowder, { parent: grp, p: [ 0, 0, zB + sheet / 2 ] } );

	const grille = perfPlane( 0.107, 0.118 );
	grille.position.set( - 0.0825, 0.31, zB + sheet / 2 );
	grp.add( grille );

	// expansion slot covers (slots 4..7; slots 1..3 used by the GPU bracket)
	for ( let i = 3; i < 7; i ++ ) {

		const y = 0.227 - i * 0.02032;
		const cover = perfPlane( 0.112, 0.0175, 420 );
		cover.position.set( - 0.048, y, zB + sheet + 0.0004 );
		grp.add( cover );
		mesh( box( 0.012, 0.0175, 0.0014 ), M.whitePowder, { parent: grp, p: [ 0.012, y, zB + sheet + 0.0006 ] } );

	}

	// screw column for slot covers
	for ( let i = 0; i < 7; i ++ ) {

		mesh( cyl( 0.0028, 0.0028, 0.003, 12 ), M.nickel, { parent: grp, r: [ Math.PI / 2, 0, 0 ], p: [ 0.012, 0.227 - i * 0.02032, zB - 0.0012 ] } );

	}

	return grp;

}

function rightPanel() {

	// shape in (z, y), rotated so it lies in the YZ plane at +X
	const s = roundedRectShape( D - 0.002, H - feet - 0.004, 0.003, 0, ( feet + H ) / 2 );
	s.holes.push( roundedRectPath( 0.15, 0.37, 0.004, 0.1225, 0.225 ) );   // front vent (angled fans)
	s.holes.push( roundedRectPath( 0.15, 0.15, 0.004, - 0.125, 0.105 ) );  // PSU vent
	const g = extrude( s, sheet, { curveSegments: 6 } );
	const grp = group( 'rightPanel' );
	const m = mesh( g, M.whitePowder, { parent: grp, r: [ 0, - Math.PI / 2, 0 ] } );
	m.position.x = xR - sheet / 2;
	const v1 = perfPlane( 0.15, 0.37 );
	v1.rotation.y = Math.PI / 2;
	v1.position.set( xR - sheet / 2, 0.225, 0.1225 );
	grp.add( v1 );
	const v2 = perfPlane( 0.15, 0.15 );
	v2.rotation.y = Math.PI / 2;
	v2.position.set( xR - sheet / 2, 0.105, - 0.125 );
	grp.add( v2 );
	// rear thumbscrews
	for ( const y of [ 0.08, 0.37 ] ) mesh( cyl( 0.0045, 0.0045, 0.006, 16 ), M.whitePlastic, { parent: grp, r: [ 0, 0, Math.PI / 2 ], p: [ xR + 0.002, y, zB + 0.004 ] } );
	return grp;

}

function topPanel() {

	const grp = group( 'topPanel' );
	// shape in (x, -z), extruded along local z then rotated so it lies flat
	const s = roundedRectShape( W, D, 0.004 );
	s.holes.push( roundedRectPath( 0.262, 0.345, 0.004, 0, 0.02 ) ); // -z = +0.02 => z from -0.1925 to 0.1525
	const g = extrude( s, 0.003, { curveSegments: 6 } );
	mesh( g, M.whitePowder, { parent: grp, r: [ - Math.PI / 2, 0, 0 ], p: [ 0, H - 0.0015, 0 ] } );
	const perf = perfPlane( 0.262, 0.345, 260 );
	perf.rotation.x = - Math.PI / 2;
	perf.position.set( 0, H - 0.0015, - 0.02 );
	grp.add( perf );

	// Front I/O cluster (top front-right)
	const io = group( 'frontIO', grp, [ 0.075, H, 0.181 ] );
	const pb = mesh( cyl( 0.0062, 0.0062, 0.0025, 32 ), M.whitePlastic, { parent: io, p: [ 0.048, 0.0008, 0 ] } );
	pb.name = 'powerButton';
	mesh( new THREE.TorusGeometry( 0.0066, 0.0006, 6, 32 ), M.lightGrey, { parent: io, r: [ Math.PI / 2, 0, 0 ], p: [ 0.048, 0.0002, 0 ] } );
	// USB-C
	mesh( rbox( 0.0089, 0.002, 0.0034, 0.0015 ), M.blackMatte, { parent: io, p: [ 0.026, 0.0002, 0 ] } );
	// 2x USB-A
	for ( const x of [ 0.008, - 0.010 ] ) {

		mesh( box( 0.0132, 0.002, 0.0055 ), M.blackMatte, { parent: io, p: [ x, 0.0002, 0 ] } );
		mesh( box( 0.0105, 0.0022, 0.0018 ), M.whitePlastic, { parent: io, p: [ x, 0.0003, - 0.0008 ] } );

	}

	// combo audio jack
	mesh( cyl( 0.0022, 0.0022, 0.002, 16 ), M.blackMatte, { parent: io, p: [ - 0.026, 0.0002, 0 ] } );
	return grp;

}

function bottomStructure( parent ) {

	// base plate with vent in the main chamber
	const s = roundedRectShape( W, D, 0.004 );
	s.holes.push( roundedRectPath( 0.15, 0.3, 0.004, - 0.055, 0.0 ) );
	const g = extrude( s, sheet, { curveSegments: 6 } );
	mesh( g, M.whitePowder, { parent: parent, r: [ - Math.PI / 2, 0, 0 ], p: [ 0, feet + 0.004, 0 ] } );
	const perf = perfPlane( 0.15, 0.3 );
	perf.rotation.x = - Math.PI / 2;
	perf.position.set( - 0.055, feet + 0.004, 0 );
	parent.add( perf );
	// dark dust filter below
	mesh( box( 0.152, 0.0008, 0.302 ), M.blackMatte, { parent, p: [ - 0.055, feet + 0.0025, 0 ], cast: false } );

	// bottom rails (frame)
	const railH = 0.016, y = feet + railH / 2;
	mesh( rbox( W, railH, 0.012, 0.002 ), M.whitePowder, { parent, p: [ 0, y, zF - 0.006 ] } );
	mesh( rbox( W, railH, 0.012, 0.002 ), M.whitePowder, { parent, p: [ 0, y, zB + 0.006 ] } );
	mesh( rbox( 0.012, railH, D, 0.002 ), M.whitePowder, { parent, p: [ xL + 0.006, y, 0 ] } );
	mesh( rbox( 0.012, railH, D, 0.002 ), M.whitePowder, { parent, p: [ xR - 0.006, y, 0 ] } );

	// feet: white plastic with rubber pads
	for ( const [ x, z ] of [ [ xL + 0.03, zF - 0.035 ], [ xR - 0.03, zF - 0.035 ], [ xL + 0.03, zB + 0.035 ], [ xR - 0.03, zB + 0.035 ] ] ) {

		mesh( rbox( 0.05, feet - 0.002, 0.03, 0.004 ), M.whitePlastic, { parent, p: [ x, feet / 2 + 0.001, z ] } );
		mesh( rbox( 0.046, 0.002, 0.026, 0.0008 ), M.rubber, { parent, p: [ x, 0.001, z ] } );

	}

}

function frame( parent ) {

	// back-left pillar (glass rests against it)
	mesh( rbox( 0.013, H - feet - 0.02, 0.013, 0.002 ), M.whitePowder, { parent, p: [ xL + 0.0065, ( H + feet ) / 2, zB + 0.0065 ] } );
	// front-right pillar
	mesh( rbox( 0.014, H - feet - 0.02, 0.014, 0.002 ), M.whitePowder, { parent, p: [ xR - 0.007, ( H + feet ) / 2, zF - 0.007 ] } );
	// back-right pillar
	mesh( rbox( 0.01, H - feet - 0.02, 0.01, 0.002 ), M.whitePowder, { parent, p: [ xR - 0.006, ( H + feet ) / 2, zB + 0.006 ] } );
	// top glass seat rails
	const y = H - 0.006;
	mesh( box( W - 0.004, 0.006, 0.01 ), M.whitePowder, { parent, p: [ 0, y, zF - 0.005 - glassT ] } );
	mesh( box( 0.01, 0.006, D - 0.004 ), M.whitePowder, { parent, p: [ xL + 0.005 + glassT, y, 0 ] } );
	mesh( box( W - 0.004, 0.006, 0.012 ), M.whitePowder, { parent, p: [ 0, y, zB + 0.007 ] } );
	mesh( box( 0.012, 0.006, D - 0.004 ), M.whitePowder, { parent, p: [ xR - 0.007, y, 0 ] } );

	// Motherboard tray with cable grommets
	const trayH = H - feet - 0.035;
	const trayD = 0.062 - ( zB + 0.003 );
	const tray = mesh( box( 0.0015, trayH, trayD ), M.whitePowder, { parent, p: [ CASE.trayX + 0.00075, feet + 0.018 + trayH / 2, zB + 0.003 + trayD / 2 ] } );
	tray.name = 'tray';
	const grommet = ( y, z, h, d ) => mesh( rbox( 0.002, h, d, 0.0009 ), M.blackMatte, { parent, p: [ CASE.trayX - 0.0004, y, z ] } );
	grommet( 0.135, 0.054, 0.07, 0.012 );   // 24-pin pass-through
	grommet( 0.25, 0.054, 0.06, 0.012 );
	grommet( 0.418, - 0.165, 0.008, 0.05 ); // EPS at top
	// tray front return flange
	mesh( box( 0.012, trayH, 0.0015 ), M.whitePowder, { parent, p: [ CASE.trayX + 0.006, feet + 0.018 + trayH / 2, 0.062 ] } );

	// Top radiator mounting rails
	for ( const x of [ - 0.137, - 0.022 ] ) mesh( box( 0.006, 0.003, D - 0.02 ), M.whitePowder, { parent, p: [ x, H - 0.0045, 0 ] } );

	// Rear-chamber cable bundle (visible through the right mesh)
	const cablePts = [ [ 0.06, 0.18, - 0.08 ], [ 0.07, 0.24, - 0.02 ], [ 0.07, 0.33, 0.02 ], [ 0.065, 0.405, - 0.12 ] ];
	const curve = new THREE.CatmullRomCurve3( cablePts.map( ( p ) => new THREE.Vector3( ...p ) ) );
	mesh( new THREE.TubeGeometry( curve, 40, 0.009, 10 ), M.cableBlack, { parent } );
	const curve2 = new THREE.CatmullRomCurve3( [ [ 0.05, 0.17, - 0.07 ], [ 0.055, 0.16, 0.02 ], [ 0.045, 0.14, 0.05 ] ].map( ( p ) => new THREE.Vector3( ...p ) ) );
	mesh( new THREE.TubeGeometry( curve2, 30, 0.012, 10 ), M.cableBlack, { parent } );
	// velcro straps
	for ( const z of [ - 0.04, 0.0 ] ) mesh( box( 0.025, 0.012, 0.004 ), M.blackMatte, { parent, p: [ 0.066, 0.22 + z * 0.8, z ] } );

}

function glassPanels() {

	const yb = feet + 0.017, yt = H - 0.003;
	const h = yt - yb;
	const left = group( 'glassLeft' );
	const lz0 = zB + 0.013, lz1 = zF;
	const gl = mesh( box( glassT, h, lz1 - lz0 ), M.glass, { parent: left, p: [ xL + glassT / 2, yb + h / 2, ( lz0 + lz1 ) / 2 ], cast: false } );
	gl.userData.glass = true;
	// exposed front edge of the left pane (green tint) — part of the seamless corner
	const edge = mesh( box( glassT, h, 0.0003 ), M.glassEdge, { parent: left, p: [ xL + glassT / 2, yb + h / 2, zF + 0.0002 ], cast: false } );
	edge.userData.glass = true;

	const front = group( 'glassFront' );
	const fx0 = xL + glassT, fx1 = xR - 0.014;
	const gf = mesh( box( fx1 - fx0, h, glassT ), M.glass, { parent: front, p: [ ( fx0 + fx1 ) / 2, yb + h / 2, zF - glassT / 2 ], cast: false } );
	gf.userData.glass = true;
	// small mounting clips
	for ( const y of [ yb + 0.004, yt - 0.004 ] ) {

		mesh( box( 0.02, 0.003, 0.003 ), M.whitePlastic, { parent: front, p: [ fx1 - 0.015, y, zF - glassT - 0.0015 ] } );
		mesh( box( 0.003, 0.003, 0.02 ), M.whitePlastic, { parent: left, p: [ xL + glassT + 0.0015, y, zB + 0.03 ] } );

	}

	return { left, front };

}

function angledFans( parent ) {

	const grp = group( 'angledFans', parent, [ 0.088, 0, 0.135 ] );
	grp.rotation.y = THREE.MathUtils.degToRad( - 115 ); // fans blow toward the GPU / motherboard
	// bracket plate on the intake side
	const s = roundedRectShape( 0.128, 0.388, 0.003, 0, 0.222 );
	for ( const y of [ 0.1, 0.222, 0.344 ] ) s.holes.push( roundedRectPath( 0.112, 0.112, 0.05, 0, y ) );
	mesh( extrude( s, 0.0012, { curveSegments: 20 } ), M.whitePowder, { parent: grp, p: [ 0, 0, - 0.0135 ] } );
	const fans = [];
	for ( const y of [ 0.1, 0.222, 0.344 ] ) {

		const f = createFan( {
			name: 'F120Q', frameMat: M.whitePlastic, bladeMat: M.fanBladeWhite, hubMat: M.whitePlastic, strutMat: M.whitePlastic, blades: 7, speed: 0.75,
		} );
		f.position.set( 0, y, 0 );
		grp.add( f );
		fans.push( f );

	}

	grp.userData.fans = fans;
	return grp;

}

export function buildCase() {

	const root = group( 'NZXT H6 Flow' );
	const chassis = group( 'chassis', root );
	bottomStructure( chassis );
	frame( chassis );
	const back = backPanel();
	chassis.add( back );

	const right = rightPanel();
	root.add( right );
	const top = topPanel();
	root.add( top );
	const { left, front } = glassPanels();
	root.add( left, front );
	const fans = angledFans( root );

	return { root, chassis, right, top, glassLeft: left, glassFront: front, angledFans: fans };

}
