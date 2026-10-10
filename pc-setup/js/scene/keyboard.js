// AULA F75 Max — 75% gasket keyboard, 327.7 x 143.2 mm, 0.85" TFT screen +
// metal knob top-right, south-facing RGB. Colourway approximated as white
// case with white/light-grey PBT caps (exact colourway unknown).
import * as THREE from 'three';
import { M, rgbMaterial } from '../core/materials.js';
import { makeCanvas, canvasTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl } from '../core/geo.js';
import { DESK } from './desk.js';

const U = 0.01905;

// [legend, width(u), gapBefore(u), modifier?]
const ROWS = [
	[ [ 'Esc', 1, 0, 1 ], [ 'F1', 1, 0.25 ], [ 'F2', 1 ], [ 'F3', 1 ], [ 'F4', 1 ], [ 'F5', 1, 0.25, 1 ], [ 'F6', 1, 0, 1 ], [ 'F7', 1, 0, 1 ], [ 'F8', 1, 0, 1 ], [ 'F9', 1, 0.25 ], [ 'F10', 1 ], [ 'F11', 1 ], [ 'F12', 1 ] ],
	[ [ '`', 1 ], [ '1', 1 ], [ '2', 1 ], [ '3', 1 ], [ '4', 1 ], [ '5', 1 ], [ '6', 1 ], [ '7', 1 ], [ '8', 1 ], [ '9', 1 ], [ '0', 1 ], [ '-', 1 ], [ '=', 1 ], [ 'Backspace', 2, 0, 1 ], [ 'Del', 1, 0.25, 1 ] ],
	[ [ 'Tab', 1.5, 0, 1 ], [ 'Q', 1 ], [ 'W', 1 ], [ 'E', 1 ], [ 'R', 1 ], [ 'T', 1 ], [ 'Y', 1 ], [ 'U', 1 ], [ 'I', 1 ], [ 'O', 1 ], [ 'P', 1 ], [ '[', 1 ], [ ']', 1 ], [ '\\', 1.5, 0, 1 ], [ 'PgUp', 1, 0.25, 1 ] ],
	[ [ 'Caps', 1.75, 0, 1 ], [ 'A', 1 ], [ 'S', 1 ], [ 'D', 1 ], [ 'F', 1 ], [ 'G', 1 ], [ 'H', 1 ], [ 'J', 1 ], [ 'K', 1 ], [ 'L', 1 ], [ ';', 1 ], [ "'", 1 ], [ 'Enter', 2.25, 0, 1 ], [ 'PgDn', 1, 0.25, 1 ] ],
	[ [ 'Shift', 2.25, 0, 1 ], [ 'Z', 1 ], [ 'X', 1 ], [ 'C', 1 ], [ 'V', 1 ], [ 'B', 1 ], [ 'N', 1 ], [ 'M', 1 ], [ ',', 1 ], [ '.', 1 ], [ '/', 1 ], [ 'Shift', 1.75, 0, 1 ], [ '↑', 1, 0.25, 1 ], [ 'End', 1, 0, 1 ] ],
	[ [ 'Ctrl', 1.25, 0, 1 ], [ 'Win', 1.25, 0, 1 ], [ 'Alt', 1.25, 0, 1 ], [ '', 6.25 ], [ 'Alt', 1, 0, 1 ], [ 'Fn', 1, 0, 1 ], [ 'Ctrl', 1, 0, 1 ], [ '←', 1, 0.25, 1 ], [ '↓', 1, 0, 1 ], [ '→', 1, 0, 1 ] ],
];
const ROW_Y = [ 0, 1.25, 2.25, 3.25, 4.25, 5.25 ];
const ROW_H = [ 0.0098, 0.0102, 0.0094, 0.0090, 0.0092, 0.0092 ]; // sculpted (cherry-like) heights
const ROW_TILT = [ - 0.12, - 0.1, - 0.02, 0.04, 0.12, 0.14 ];

function legendAtlas() {

	const px = 64;
	const c = makeCanvas( Math.ceil( 16.25 * px ), Math.ceil( 6.25 * px ) );
	const ctx = c.getContext( '2d' );
	ctx.fillStyle = '#ffffff';
	ctx.fillRect( 0, 0, c.width, c.height );
	ctx.fillStyle = '#55565c';
	ctx.textBaseline = 'top';
	ROWS.forEach( ( row, r ) => {

		let x = 0;
		for ( const [ label, w, gap = 0 ] of row ) {

			x += gap;
			const big = label.length === 1;
			ctx.font = big ? '600 22px "Segoe UI", Arial, sans-serif' : '500 12px "Segoe UI", Arial, sans-serif';
			ctx.fillText( label, x * px + 13, ROW_Y[ r ] * px + 12 );
			x += w;

		}

	} );
	return { tex: canvasTexture( c ), w: c.width / px, h: c.height / px };

}

export function buildKeyboard() {

	const g = group( 'keyboard' );
	const W = 0.3277, D = 0.1432;
	const caseMat = new THREE.MeshStandardMaterial( { name: 'kbCase', color: 0xf0efec, roughness: 0.42, normalMap: M.whitePlastic.normalMap, normalScale: new THREE.Vector2( 0.05, 0.05 ) } );

	// Case: bottom tray + top frame (gasket sandwich look)
	mesh( rbox( W, 0.017, D, 0.006, 3 ), caseMat, { parent: g, p: [ 0, 0.0085, 0 ] } );
	mesh( rbox( W - 0.001, 0.008, D - 0.001, 0.005, 3 ), caseMat, { parent: g, p: [ 0, 0.021, 0 ] } );
	mesh( box( W - 0.004, 0.0012, D - 0.004 ), M.lightGrey, { parent: g, p: [ 0, 0.0172, 0 ], cast: false } ); // seam line
	// rubber feet
	for ( const [ x, z ] of [ [ - 0.14, 0.055 ], [ 0.14, 0.055 ], [ - 0.14, - 0.055 ], [ 0.14, - 0.055 ] ] ) mesh( rbox( 0.025, 0.002, 0.008, 0.001 ), M.rubber, { parent: g, p: [ x, - 0.0008, z ], cast: false } );

	const marginX = ( W - 16.25 * U ) / 2, marginZ = ( D - 6.25 * U ) / 2;
	const x0 = - W / 2 + marginX, z0 = - D / 2 + marginZ;

	// inner well (dark) + RGB backlight diffuser visible between caps
	mesh( box( 16.25 * U + 0.002, 0.004, 6.25 * U + 0.002 ), M.darkPlastic, { parent: g, p: [ 0, 0.0225, 0 ], cast: false } );
	const rgbMat = rgbMaterial( 'keyboardRGB', 0xdedede, { roughness: 0.6 } );
	const glow = mesh( new THREE.PlaneGeometry( 16.25 * U, 6.25 * U ), rgbMat, { parent: g, p: [ 0, 0.0247, 0 ], r: [ - Math.PI / 2, 0, 0 ], cast: false } );
	glow.userData.rgb = true;

	// --- Keycaps merged into a single mesh with a legend atlas -------------
	const atlas = legendAtlas();
	const pos = [], nor = [], uv = [], col = [];
	const cAlpha = new THREE.Color( 0xf3f2ef ), cMod = new THREE.Color( 0xc8c9cc );
	const tmpA = new THREE.Vector3(), tmpB = new THREE.Vector3(), n = new THREE.Vector3();
	const pushTri = ( a, b, c, ua, ub, uc, color ) => {

		tmpA.subVectors( b, a ); tmpB.subVectors( c, a ); n.crossVectors( tmpA, tmpB ).normalize();
		for ( const [ p, t ] of [ [ a, ua ], [ b, ub ], [ c, uc ] ] ) {

			pos.push( p.x, p.y, p.z ); nor.push( n.x, n.y, n.z ); uv.push( t[ 0 ], t[ 1 ] ); col.push( color.r, color.g, color.b );

		}

	};

	const capBase = 0.0262;
	ROWS.forEach( ( row, r ) => {

		let ux = 0;
		for ( const [ , w, gap = 0, mod ] of row ) {

			ux += gap;
			const cx = x0 + ( ux + w / 2 ) * U, cz = z0 + ( ROW_Y[ r ] + 0.5 ) * U;
			const bw = w * U - 0.0009, bd = U - 0.0009;
			const tw = bw - 0.0046, td = bd - 0.0058;
			const h = ROW_H[ r ], tilt = ROW_TILT[ r ];
			const y0 = capBase, yF = capBase + h - tilt * 0.003, yB = capBase + h + tilt * 0.003;
			const B = [ [ - bw / 2, y0, bd / 2 ], [ bw / 2, y0, bd / 2 ], [ bw / 2, y0, - bd / 2 ], [ - bw / 2, y0, - bd / 2 ] ].map( ( [ x, y, z ] ) => new THREE.Vector3( cx + x, y, cz + z ) );
			const T = [ [ - tw / 2, yF, td / 2 - 0.0004 ], [ tw / 2, yF, td / 2 - 0.0004 ], [ tw / 2, yB, - td / 2 - 0.0004 ], [ - tw / 2, yB, - td / 2 - 0.0004 ] ].map( ( [ x, y, z ] ) => new THREE.Vector3( cx + x, y, cz + z ) );
			const color = mod ? cMod : cAlpha;
			// atlas region for the top face
			const u0 = ux / atlas.w, u1 = ( ux + w ) / atlas.w;
			const v1 = 1 - ROW_Y[ r ] / atlas.h, v0 = 1 - ( ROW_Y[ r ] + 1 ) / atlas.h;
			const blank = [ u1 - 0.1 / atlas.w, v0 + 0.1 / atlas.h ];
			pushTri( T[ 0 ], T[ 1 ], T[ 2 ], [ u0, v0 ], [ u1, v0 ], [ u1, v1 ], color );
			pushTri( T[ 0 ], T[ 2 ], T[ 3 ], [ u0, v0 ], [ u1, v1 ], [ u0, v1 ], color );
			for ( let i = 0; i < 4; i ++ ) {

				const j = ( i + 1 ) % 4;
				pushTri( B[ i ], B[ j ], T[ j ], blank, blank, blank, color );
				pushTri( B[ i ], T[ j ], T[ i ], blank, blank, blank, color );

			}

			ux += w;

		}

	} );

	const capGeo = new THREE.BufferGeometry();
	capGeo.setAttribute( 'position', new THREE.Float32BufferAttribute( pos, 3 ) );
	capGeo.setAttribute( 'normal', new THREE.Float32BufferAttribute( nor, 3 ) );
	capGeo.setAttribute( 'uv', new THREE.Float32BufferAttribute( uv, 2 ) );
	capGeo.setAttribute( 'color', new THREE.Float32BufferAttribute( col, 3 ) );
	const capMat = new THREE.MeshStandardMaterial( { name: 'keycapsPBT', map: atlas.tex, vertexColors: true, roughness: 0.62, normalMap: M.whitePlastic.normalMap, normalScale: new THREE.Vector2( 0.08, 0.08 ) } );
	mesh( capGeo, capMat, { parent: g } );

	// Top-right: 0.85" TFT screen + metal knob (in place of the extra top-row keys)
	const scrCanvas = makeCanvas( 128, 112 );
	const scrTex = canvasTexture( scrCanvas, { mipmaps: false } );
	const scrMat = new THREE.MeshStandardMaterial( { name: 'kbTFT', color: 0x000000, emissive: 0xffffff, emissiveMap: scrTex, emissiveIntensity: 1, roughness: 0.2 } );
	const sx = x0 + 14.25 * U + U * 0.45, sz = z0 + 0.5 * U;
	mesh( rbox( 0.022, 0.003, 0.019, 0.002 ), M.blackGloss, { parent: g, p: [ sx, 0.0255, sz ] } );
	const scr = mesh( new THREE.PlaneGeometry( 0.0185, 0.0155 ), scrMat, { parent: g, r: [ - Math.PI / 2, 0, 0 ], p: [ sx, 0.0272, sz ], cast: false } );
	scr.userData.noAO = true;

	const knobGeo = new THREE.CylinderGeometry( 0.0088, 0.0088, 0.014, 72, 1 );
	{

		const p = knobGeo.attributes.position;
		for ( let i = 0; i < p.count; i ++ ) {

			const x = p.getX( i ), z = p.getZ( i );
			const a = Math.atan2( z, x );
			const r = Math.hypot( x, z );
			if ( r > 0.008 ) {

				const k = 1 - 0.035 * ( Math.round( a / ( Math.PI * 2 ) * 72 ) % 2 );
				p.setX( i, x * k ); p.setZ( i, z * k );

			}

		}

		knobGeo.computeVertexNormals();

	}

	const knob = mesh( knobGeo, M.brushedAlu, { parent: g, p: [ x0 + 15.75 * U, 0.031, z0 + 0.5 * U ] } );
	knob.name = 'knob';

	g.position.set( MONITOR_X, DESK.top + 0.0035, - 1.47 );
	g.rotation.x = 0.05;
	return { root: g, rgbMat, screen: { canvas: scrCanvas, texture: scrTex, material: scrMat } };

}

const MONITOR_X = - 0.18;

export function drawKeyboardTFT( canvas, on, t ) {

	const ctx = canvas.getContext( '2d' );
	const W = canvas.width, H = canvas.height;
	ctx.fillStyle = '#000';
	ctx.fillRect( 0, 0, W, H );
	if ( ! on ) return;
	// status bar
	ctx.fillStyle = '#e8e8e8';
	ctx.font = 'bold 12px Arial';
	ctx.fillText( '2.4G', 6, 15 );
	ctx.strokeStyle = '#e8e8e8';
	ctx.strokeRect( W - 30, 6, 22, 10 );
	ctx.fillStyle = '#7ee08f';
	ctx.fillRect( W - 28, 8, 15, 6 );
	ctx.fillStyle = '#e8e8e8';
	ctx.fillRect( W - 8, 9, 2, 4 );
	// small pink wave animation (custom GIF slot)
	ctx.strokeStyle = '#f2a1c0';
	ctx.lineWidth = 3;
	for ( let k = 0; k < 3; k ++ ) {

		ctx.beginPath();
		for ( let x = 0; x <= W; x += 4 ) {

			const y = 62 + k * 12 + Math.sin( x * 0.08 + t * 2 + k ) * 6;
			x === 0 ? ctx.moveTo( x, y ) : ctx.lineTo( x, y );

		}

		ctx.globalAlpha = 1 - k * 0.3;
		ctx.stroke();

	}

	ctx.globalAlpha = 1;
	const d = new Date();
	ctx.fillStyle = '#ffffff';
	ctx.font = 'bold 20px Arial';
	ctx.textAlign = 'center';
	ctx.fillText( `${ String( d.getHours() ).padStart( 2, '0' ) }:${ String( d.getMinutes() ).padStart( 2, '0' ) }`, W / 2, 46 );
	ctx.textAlign = 'left';

}
