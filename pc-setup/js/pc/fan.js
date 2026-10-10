// Parametric axial fan. Airflow axis is local +Z (air exits toward +Z).
import * as THREE from 'three';
import { mesh, group, roundedRectShape, circlePath, extrude, cyl, mergeGeometries } from '../core/geo.js';

const bladeCache = new Map();

// One curved, pitched blade as a thin double-sided surface with slight thickness.
function bladeGeometry( { rIn, rOut, span, pitch, sweep, thickness = 0.0012 } ) {

	const key = [ rIn, rOut, span, pitch, sweep, thickness ].join( '|' );
	if ( bladeCache.has( key ) ) return bladeCache.get( key );

	const nu = 10, nv = 8;
	const pos = [], idx = [];
	const pt = ( u, v, side ) => {

		const r = rIn + ( rOut - rIn ) * u;
		const chord = span * ( 0.75 + 0.45 * u ); // blades widen toward the tip
		const a = sweep * u * u + ( v - 0.5 ) * chord;
		const camber = Math.sin( v * Math.PI ) * 0.0025 * ( 1 + u );
		const z = ( v - 0.5 ) * pitch * ( 1.05 - 0.35 * u ) + camber + side * thickness * 0.5;
		return [ Math.cos( a ) * r, Math.sin( a ) * r, z ];

	};

	for ( const side of [ 1, - 1 ] ) {

		const base = pos.length / 3;
		for ( let i = 0; i <= nu; i ++ ) for ( let j = 0; j <= nv; j ++ ) pos.push( ...pt( i / nu, j / nv, side ) );
		for ( let i = 0; i < nu; i ++ ) for ( let j = 0; j < nv; j ++ ) {

			const a = base + i * ( nv + 1 ) + j, b = a + nv + 1;
			if ( side > 0 ) idx.push( a, b, a + 1, b, b + 1, a + 1 );
			else idx.push( a, a + 1, b, b, a + 1, b + 1 );

		}

	}

	// tip edge (closes the two sides at u = 1)
	const top = 0, bot = ( nu + 1 ) * ( nv + 1 );
	for ( let j = 0; j < nv; j ++ ) {

		const a = top + nu * ( nv + 1 ) + j, b = bot + nu * ( nv + 1 ) + j;
		idx.push( a, a + 1, b, b, a + 1, b + 1 );

	}

	const g = new THREE.BufferGeometry();
	g.setAttribute( 'position', new THREE.Float32BufferAttribute( pos, 3 ) );
	g.setIndex( idx );
	g.computeVertexNormals();
	bladeCache.set( key, g );
	return g;

}

export function rotorGeometry( { blades = 9, rIn = 0.021, rOut = 0.056, span = 0.55, pitch = 0.014, sweep = 0.35 } ) {

	const one = bladeGeometry( { rIn: rIn - 0.001, rOut, span, pitch, sweep } );
	const list = [];
	for ( let i = 0; i < blades; i ++ ) {

		const g = one.clone();
		g.rotateZ( i / blades * Math.PI * 2 );
		list.push( g );

	}

	const merged = mergeGeometries( list );
	list.forEach( ( g ) => g.dispose() );
	return merged;

}

const frameCache = new Map();
function frameGeometry( size, depth, holeR ) {

	const key = `${ size }|${ depth }|${ holeR }`;
	if ( frameCache.has( key ) ) return frameCache.get( key );
	const s = roundedRectShape( size, size, size * 0.06 );
	s.holes.push( circlePath( holeR ) );
	const sc = size / 2 - size * 0.0625;
	for ( const [ x, y ] of [ [ sc, sc ], [ - sc, sc ], [ sc, - sc ], [ - sc, - sc ] ] ) s.holes.push( circlePath( 0.0022, x, y ) );
	const g = extrude( s, depth, { bevel: 0.0008, curveSegments: 40 } );
	frameCache.set( key, g );
	return g;

}

/**
 * Builds a fan.
 * @returns {THREE.Group} with userData.rotor (spinning group) and userData.rgbMeshes
 */
export function createFan( {
	size = 0.12, depth = 0.025, frameMat, bladeMat, hubMat, strutMat,
	blades = 9, ringMat = null, hubLightMat = null, bladeRing = false, bladeRingMat = null,
	speed = 1, name = 'fan',
} ) {

	const g = group( name );
	const holeR = size * 0.475;
	mesh( frameGeometry( size, depth, holeR ), frameMat, { parent: g } );

	// Motor hub on the exit side, held by struts
	const hubR = size * 0.17;
	const motor = mesh( cyl( hubR * 0.92, hubR * 0.92, depth * 0.35, 32 ), strutMat || frameMat, { parent: g, r: [ Math.PI / 2, 0, 0 ], p: [ 0, 0, depth * 0.32 ] } );
	motor.castShadow = false;
	for ( let i = 0; i < 4; i ++ ) {

		const a = i / 4 * Math.PI * 2 + Math.PI / 4;
		const len = holeR - hubR * 0.85;
		const strut = mesh( new THREE.BoxGeometry( len + 0.004, 0.0035, 0.004 ), strutMat || frameMat, { parent: g } );
		strut.position.set( Math.cos( a ) * ( hubR * 0.85 + len / 2 ), Math.sin( a ) * ( hubR * 0.85 + len / 2 ), depth * 0.42 );
		strut.rotation.z = a;
		strut.castShadow = false;

	}

	// Optional ARGB ring set into the frame face (intake side)
	if ( ringMat ) {

		const ring = mesh( new THREE.TorusGeometry( holeR + 0.0012, 0.0016, 8, 64 ), ringMat, { parent: g, p: [ 0, 0, - depth / 2 + 0.0015 ] } );
		ring.castShadow = false;
		ring.userData.rgb = true;

	}

	// Rotor
	const rotor = group( 'rotor', g );
	rotor.userData.noMerge = true;
	const rot = rotorGeometry( { blades, rIn: hubR, rOut: holeR - 0.0025, pitch: depth * 0.55 } );
	mesh( rot, bladeMat, { parent: rotor, cast: true } );
	const hub = mesh( cyl( hubR, hubR, depth * 0.62, 36 ), hubMat || bladeMat, { parent: rotor, r: [ Math.PI / 2, 0, 0 ], p: [ 0, 0, - depth * 0.05 ] } );
	hub.castShadow = false;
	if ( hubLightMat ) {

		const cap = mesh( new THREE.CircleGeometry( hubR * 0.86, 36 ), hubLightMat, { parent: rotor, p: [ 0, 0, - depth * 0.36 - 0.0002 ], r: [ Math.PI, 0, 0 ] } );
		cap.castShadow = false;

	}

	if ( bladeRing ) {

		const ring = mesh( new THREE.TorusGeometry( holeR - 0.0028, 0.0011, 6, 72 ), bladeRingMat || bladeMat, { parent: rotor, p: [ 0, 0, 0.001 ] } );
		ring.scale.z = 3;

	}

	g.userData.rotor = rotor;
	g.userData.speed = speed;
	return g;

}
