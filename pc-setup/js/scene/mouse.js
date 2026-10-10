// Logitech G502 LIGHTSPEED — 132 x 75 x 40 mm. Parametric shell with the
// characteristic thumb rest, split main buttons, metal scroll wheel, DPI
// buttons, LIGHTSYNC logo + DPI indicator LEDs (the only lit zones).
import * as THREE from 'three';
import { M, rgbMaterial } from '../core/materials.js';
import { mesh, group, rbox, box, cyl } from '../core/geo.js';
import { DESK } from './desk.js';

const L = 0.132;
const halfW = ( t ) => {

	// t: 0 front → 1 back
	const base = 0.026 + 0.009 * Math.sin( Math.PI * Math.min( 1, t * 1.05 ) );
	return base * Math.pow( Math.sin( Math.PI * Math.min( Math.max( t, 0.0005 ), 0.9995 ) ), 0.16 );

};

const height = ( t ) => {

	const h = 0.024 + 0.0145 * Math.exp( - Math.pow( ( t - 0.64 ) / 0.3, 2 ) );
	return h * Math.pow( Math.sin( Math.PI * Math.min( Math.max( t, 0.0005 ), 0.9995 ) ), 0.22 );

};

// Point on the shell surface. t: 0 front → 1 back, th: 0 (right side) → π (left side).
// Superellipse cross-section gives the G502's flat top and firm shoulders.
export function shellPoint( t, th, out = new THREE.Vector3() ) {

	const z = L / 2 - t * L;
	const w = halfW( t ), h = height( t );
	const c = Math.cos( th ), s = Math.sin( th );
	const n = 2.6;
	let x = Math.sign( c ) * Math.pow( Math.abs( c ), 2 / n ) * w;
	let y = Math.pow( Math.abs( s ), 2 / n ) * h;
	// left thumb-rest wing: a shelf that juts out low on the left flank
	if ( th > Math.PI * 0.62 ) {

		const k = ( th - Math.PI * 0.62 ) / ( Math.PI * 0.38 );
		const g = Math.exp( - Math.pow( ( t - 0.58 ) / 0.2, 2 ) );
		x -= 0.015 * g * Math.pow( k, 1.6 );
		y *= 1 - 0.42 * g * Math.pow( k, 1.2 );

	}

	// right-side flared lip toward the rear
	if ( th < Math.PI * 0.3 ) {

		const k = 1 - th / ( Math.PI * 0.3 );
		x += 0.004 * k * k * Math.exp( - Math.pow( ( t - 0.7 ) / 0.2, 2 ) );

	}

	// main buttons dip slightly toward the front centre
	y -= 0.0015 * Math.exp( - Math.pow( ( t - 0.12 ) / 0.15, 2 ) ) * Math.pow( Math.abs( s ), 4 );
	return out.set( x, y + 0.003, z );

}

function shellGeometry() {

	const nz = 96, nt = 64;
	const pos = [], uvs = [];
	const buckets = [ [], [], [] ]; // 0 satin buttons, 1 matte palm, 2 textured side grips
	const v = new THREE.Vector3();
	for ( let i = 0; i <= nz; i ++ ) {

		for ( let j = 0; j <= nt; j ++ ) {

			shellPoint( i / nz, j / nt * Math.PI, v );
			pos.push( v.x, v.y, v.z );
			uvs.push( j / nt * 3, i / nz * 5 );

		}

	}

	for ( let i = 0; i < nz; i ++ ) for ( let j = 0; j < nt; j ++ ) {

		const a = i * ( nt + 1 ) + j, b = a + nt + 1;
		const t = ( i + 0.5 ) / nz, th = ( j + 0.5 ) / nt * Math.PI;
		const side = ( th < Math.PI * 0.22 || th > Math.PI * 0.74 ) && t > 0.3 && t < 0.92;
		const region = side ? 2 : t < 0.42 ? 0 : 1;
		buckets[ region ].push( a, b, a + 1, b, b + 1, a + 1 );

	}

	const g = new THREE.BufferGeometry();
	g.setAttribute( 'position', new THREE.Float32BufferAttribute( pos, 3 ) );
	g.setAttribute( 'uv', new THREE.Float32BufferAttribute( uvs, 2 ) );
	g.setIndex( buckets.flat() );
	g.computeVertexNormals();
	let start = 0;
	buckets.forEach( ( idx, m ) => {

		g.addGroup( start, idx.length, m );
		start += idx.length;

	} );
	return g;

}

export function buildMouse() {

	const g = group( 'mouse' );
	const matte = new THREE.MeshStandardMaterial( { name: 'g502Matte', color: 0x161618, roughness: 0.62, normalMap: M.darkPlastic.normalMap, normalScale: new THREE.Vector2( 0.1, 0.1 ) } );
	const satin = new THREE.MeshStandardMaterial( { name: 'g502Satin', color: 0x1c1c1f, roughness: 0.38 } );
	const grip = new THREE.MeshStandardMaterial( { name: 'g502Grip', color: 0x202022, roughness: 0.85, normalMap: M.curtain.normalMap, normalScale: new THREE.Vector2( 0.5, 0.5 ) } );
	const shell = mesh( shellGeometry(), [ satin, matte, grip ], { parent: g } );
	for ( const m of [ satin, matte, grip ] ) m.side = THREE.DoubleSide; // shell is open underneath (base plate covers it)
	shell.name = 'shell';

	const P = ( t, th, lift = 0 ) => {

		const p = shellPoint( t, th );
		return [ p.x, p.y + lift, p.z ];

	};

	// base plate (inset under the shell)
	mesh( rbox( 0.058, 0.004, 0.118, 0.012 ), M.blackMatte, { parent: g, p: [ - 0.002, 0.002, 0 ] } );
	for ( const [ x, z ] of [ [ - 0.008, 0.048 ], [ - 0.008, - 0.05 ] ] ) mesh( rbox( 0.026, 0.0008, 0.008, 0.0004 ), M.whiteGloss, { parent: g, p: [ x, 0.0002, z ], cast: false } );

	// split between the main buttons
	const seamA = shellPoint( 0.02, Math.PI / 2 ), seamB = shellPoint( 0.36, Math.PI / 2 );
	const seam = mesh( box( 0.0014, 0.0012, seamA.distanceTo( seamB ) ), M.blackMatte, { parent: g, cast: false } );
	seam.position.lerpVectors( seamA, seamB, 0.5 ).y += 0.0003;
	seam.lookAt( seamB.clone().applyMatrix4( g.matrixWorld ) );
	// scroll wheel (metal with rubber tread) protruding ~5 mm through the channel
	const wp = shellPoint( 0.24, Math.PI / 2 );
	const wheel = group( 'wheel', g, [ 0, wp.y - 0.0065, wp.z ] );
	mesh( cyl( 0.0115, 0.0115, 0.0075, 40 ), M.brushedAlu, { parent: wheel, r: [ 0, 0, Math.PI / 2 ] } );
	mesh( cyl( 0.012, 0.012, 0.0035, 40 ), M.rubber, { parent: wheel, r: [ 0, 0, Math.PI / 2 ] } );
	// DPI shift buttons to the left of the left click, DPI indicator LEDs below them
	for ( let i = 0; i < 2; i ++ ) {

		const b = mesh( rbox( 0.007, 0.003, 0.011, 0.0013 ), satin, { parent: g, p: P( 0.14 + i * 0.1, Math.PI * 0.75, 0.0008 ) } );
		b.rotation.set( - 0.15, 0, 0.55 );

	}

	const rgbMat = rgbMaterial( 'mouseRGB', 0x222222, { roughness: 0.3 } );
	for ( let i = 0; i < 3; i ++ ) {

		const led = mesh( box( 0.0016, 0.0012, 0.0038 ), rgbMat, { parent: g, p: P( 0.2 + i * 0.045, Math.PI * 0.84, 0.0003 ), cast: false } );
		led.rotation.z = 0.9;
		led.userData.rgb = true;

	}

	// thumb buttons (forward / back) + sniper button
	for ( const t of [ 0.38, 0.52 ] ) {

		const tb = mesh( rbox( 0.0035, 0.006, 0.017, 0.0015 ), satin, { parent: g, p: P( t, Math.PI * 0.86 ) } );
		tb.rotation.z = 0.7;

	}

	const sn = mesh( rbox( 0.004, 0.009, 0.011, 0.002 ), M.gunmetal, { parent: g, p: P( 0.25, Math.PI * 0.9 ) } );
	sn.rotation.set( 0, 0.15, 0.4 );

	// LIGHTSYNC G logo on the palm
	const logoShape = new THREE.Shape();
	logoShape.absarc( 0, 0, 0.0065, 0.35, Math.PI * 2 - 0.1, false );
	logoShape.lineTo( 0.0065 * Math.cos( - 0.1 ), - 0.0005 );
	logoShape.lineTo( 0.001, - 0.0005 );
	logoShape.lineTo( 0.001, 0.0015 );
	logoShape.lineTo( 0.0045, 0.0015 );
	logoShape.absarc( 0, 0, 0.0045, 0.35, Math.PI * 2 - 0.4, true );
	const lp = shellPoint( 0.72, Math.PI / 2 );
	const logo = mesh( new THREE.ShapeGeometry( logoShape, 24 ), rgbMat, { parent: g, p: [ 0, lp.y + 0.0004, lp.z ], r: [ - Math.PI / 2 + 0.08, 0, Math.PI ], cast: false } );
	logo.userData.rgb = true;

	g.position.set( 0.2, DESK.top + 0.0004, - 1.45 );
	g.rotation.y = - 0.08;
	return { root: g, rgbMat };

}
