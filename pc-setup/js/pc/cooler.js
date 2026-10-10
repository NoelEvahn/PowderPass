// DeepCool LQ360 (white) — 360 mm AIO with dashboard-style LCD pump display.
// Radiator 402 x 120 x 27 mm (top mounted), pump 89 x 76 x 64 mm, 3x 120 mm ARGB fans.
import * as THREE from 'three';
import { M, rgbMaterial } from '../core/materials.js';
import { labelTexture, makeCanvas, canvasTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, extrude } from '../core/geo.js';
import { bpos, SOCKET, RAD } from './layout.js';
import { createFan } from './fan.js';

function radiatorFinTexture() {

	const c = makeCanvas( 64, 256 );
	const ctx = c.getContext( '2d' );
	ctx.fillStyle = '#d9d8d5';
	ctx.fillRect( 0, 0, 64, 256 );
	// flat water channels every 32px, zig-zag fins between
	for ( let y = 0; y < 256; y += 32 ) {

		ctx.fillStyle = '#f2f1ee';
		ctx.fillRect( 0, y, 64, 6 );
		ctx.strokeStyle = '#8f8e8b';
		ctx.lineWidth = 1;
		for ( let x = 0; x < 64; x += 3 ) {

			ctx.beginPath(); ctx.moveTo( x, y + 7 ); ctx.lineTo( x + 1.5, y + 31 ); ctx.stroke();

		}

	}

	return canvasTexture( c, { repeat: [ 1, 1 ] } );

}

export function buildCooler( { fanBladeMat, fanHubMat, pumpAccentMat } ) {

	const root = group( 'cooler' );

	// ---------------- Pump ----------------
	const pump = group( 'pump', root );
	const sp = bpos( SOCKET.u, SOCKET.v, 0.0065 ); // on the CPU IHS
	pump.position.copy( sp );
	// local: -X = away from board (display faces -X), Y up, Z toward front
	const PH = 0.064, PY = 0.076, PZ = 0.089;

	// cold plate + mounting ring (dark)
	mesh( cyl( 0.03, 0.03, 0.004, 40 ), M.copper, { parent: pump, r: [ 0, 0, Math.PI / 2 ], p: [ - 0.002, 0, 0 ] } );
	mesh( rbox( 0.008, 0.07, 0.08, 0.003 ), M.blackMatte, { parent: pump, p: [ - 0.007, 0, 0 ] } );
	// AM5 mounting arms + thumbscrews
	for ( const [ y, z ] of [ [ 0.034, 0.038 ], [ - 0.034, 0.038 ], [ 0.034, - 0.038 ], [ - 0.034, - 0.038 ] ] ) {

		const arm = mesh( rbox( 0.004, 0.012, 0.03, 0.0015 ), M.blackMetal, { parent: pump, p: [ - 0.006, y * 1.05, z * 1.05 ] } );
		arm.rotation.x = Math.atan2( y, z );
		mesh( cyl( 0.0035, 0.0035, 0.006, 16 ), M.nickel, { parent: pump, r: [ 0, 0, Math.PI / 2 ], p: [ - 0.007, y * 1.35, z * 1.35 ] } );

	}

	// main body: extruded rounded rect with a soft bevel, extruded along -X
	{

		const s = roundedRectShape( PZ, PY, 0.012 );
		const g = extrude( s, PH - 0.012, { bevel: 0.004, curveSegments: 16 } );
		const body = mesh( g, M.whitePlastic, { parent: pump } );
		body.rotation.y = - Math.PI / 2; // shape x → z, extrusion → -x
		body.position.x = - 0.011 - ( PH - 0.012 ) / 2;

	}

	// top glass cap + LCD
	const topX = - 0.011 - ( PH - 0.012 ) - 0.0005;
	const cap = mesh( rbox( 0.003, PY - 0.012, PZ - 0.012, 0.004 ), M.blackGloss, { parent: pump, p: [ topX - 0.0015, 0, 0 ] } );
	cap.castShadow = false;
	// ARGB accent frame around the cap
	const accent = mesh( rbox( 0.002, PY - 0.006, PZ - 0.006, 0.005 ), pumpAccentMat, { parent: pump, p: [ topX + 0.0005, 0, 0 ], cast: false } );
	accent.userData.rgb = true;

	const lcdCanvas = makeCanvas( 256, 256 );
	const lcdTex = canvasTexture( lcdCanvas, { mipmaps: false } );
	const lcdMat = new THREE.MeshStandardMaterial( {
		name: 'pumpLCD', color: 0x000000, roughness: 0.15, metalness: 0,
		emissive: 0xffffff, emissiveMap: lcdTex, emissiveIntensity: 1.2,
	} );
	const lcd = mesh( new THREE.PlaneGeometry( 0.0539, 0.04915 ), lcdMat, { parent: pump, p: [ topX - 0.0031, 0, 0 ], cast: false } );
	lcd.rotation.y = - Math.PI / 2;
	lcd.userData.noAO = true;

	// tube fittings (front side of pump body, pointing +Z)
	const portLocal = [];
	for ( const dy of [ 0.013, - 0.013 ] ) {

		const px = - 0.05, pz = PZ / 2 + 0.002;
		mesh( cyl( 0.0068, 0.0068, 0.012, 20 ), M.whitePlastic, { parent: pump, r: [ Math.PI / 2, 0, 0 ], p: [ px, dy, pz + 0.004 ] } );
		portLocal.push( new THREE.Vector3( px, dy, pz + 0.011 ) );

	}

	// DeepCool logo on the side
	{

		const t = labelTexture( 512, 64, ( ctx, w, h ) => {

			ctx.fillStyle = '#9a9ca0'; ctx.font = 'bold 40px Arial'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
			ctx.fillText( 'DEEPCOOL', w / 2, h / 2 );

		} );
		const lbl = new THREE.Mesh( new THREE.PlaneGeometry( 0.04, 0.005 ), new THREE.MeshStandardMaterial( { map: t, transparent: true } ) );
		lbl.position.set( - 0.04, - PY / 2 - 0.0005, 0 );
		lbl.rotation.set( Math.PI / 2, 0, Math.PI / 2 );
		pump.add( lbl );

	}

	// ---------------- Radiator + fans ----------------
	const radiator = group( 'radiator', root );
	radiator.position.set( RAD.xC, RAD.yTop - RAD.t / 2, 0 );
	const coreLen = RAD.len - 0.042;
	const finTex = radiatorFinTexture();
	finTex.wrapS = finTex.wrapT = THREE.RepeatWrapping;
	finTex.repeat.set( 1, 7 );
	const coreMat = new THREE.MeshStandardMaterial( { name: 'radCore', map: finTex, roughness: 0.5, metalness: 0.3, color: 0xffffff } );
	const core = mesh( box( RAD.w - 0.006, RAD.t - 0.004, coreLen ), coreMat, { parent: radiator } );
	core.material = [ M.whitePowder, M.whitePowder, coreMat, coreMat, M.whitePowder, M.whitePowder ];
	// side plates
	for ( const sx of [ - 1, 1 ] ) mesh( box( 0.0015, RAD.t, coreLen ), M.whitePowder, { parent: radiator, p: [ sx * ( RAD.w / 2 - 0.0008 ), 0, 0 ] } );
	// end tanks
	for ( const sz of [ - 1, 1 ] ) mesh( rbox( RAD.w, RAD.t + 0.002, 0.021, 0.003 ), M.whitePlastic, { parent: radiator, p: [ 0, 0, sz * ( RAD.len / 2 - 0.0105 ) ] } );
	// side branding
	{

		const t = labelTexture( 1024, 64, ( ctx, w, h ) => {

			ctx.fillStyle = '#a6a8ab'; ctx.font = 'bold 34px Arial'; ctx.textBaseline = 'middle';
			ctx.fillText( 'DEEPCOOL', 40, h / 2 );
			ctx.font = '26px Arial'; ctx.fillText( 'LQ360', 260, h / 2 );

		} );
		const lbl = new THREE.Mesh( new THREE.PlaneGeometry( 0.25, 0.016 ), new THREE.MeshStandardMaterial( { map: t, transparent: true } ) );
		lbl.position.set( - RAD.w / 2 - 0.0002, 0, 0 );
		lbl.rotation.y = - Math.PI / 2;
		radiator.add( lbl );

	}

	// radiator ports on the front tank (pointing down)
	const radPortLocal = [];
	for ( const dx of [ - 0.022, 0.022 ] ) {

		mesh( cyl( 0.0068, 0.0068, 0.01, 20 ), M.whitePlastic, { parent: radiator, p: [ dx, - RAD.t / 2 - 0.004, RAD.len / 2 - 0.011 ] } );
		radPortLocal.push( new THREE.Vector3( dx, - RAD.t / 2 - 0.009, RAD.len / 2 - 0.011 ) );

	}

	const fans = [];
	for ( const z of [ - 0.12, 0, 0.12 ] ) {

		const f = createFan( {
			name: 'LQ360 fan', frameMat: M.whitePlastic, bladeMat: fanBladeMat, hubMat: M.whitePlastic, hubLightMat: fanHubMat,
			strutMat: M.whitePlastic, blades: 9, speed: 0.85,
		} );
		f.position.set( 0, - RAD.t / 2 - RAD.fanT / 2, z );
		f.rotation.x = - Math.PI / 2; // exhaust upward through the radiator
		radiator.add( f );
		fans.push( f );

	}

	// ---------------- Tubes (rebuilt when parts move) ----------------
	const tubes = new THREE.Mesh( new THREE.BufferGeometry(), M.aioTube );
	tubes.castShadow = true;
	tubes.receiveShadow = true;
	tubes.name = 'aioTubes';
	root.add( tubes );

	const tmp = new THREE.Vector3();
	function rebuildTubes() {

		pump.updateMatrix();
		radiator.updateMatrix();
		const geos = [];
		for ( let i = 0; i < 2; i ++ ) {

			const a = portLocal[ i ].clone().applyMatrix4( pump.matrix );
			const b = radPortLocal[ i === 0 ? 1 : 0 ].clone().applyMatrix4( radiator.matrix );
			const pts = [
				a,
				a.clone().add( new THREE.Vector3( - 0.006, 0.004, 0.035 ) ),
				tmp.lerpVectors( a, b, 0.55 ).clone().add( new THREE.Vector3( - 0.018 + i * 0.012, - 0.005, 0.03 ) ),
				b.clone().add( new THREE.Vector3( 0, - 0.04, - 0.004 ) ),
				b,
			];
			const curve = new THREE.CatmullRomCurve3( pts, false, 'centripetal' );
			geos.push( new THREE.TubeGeometry( curve, 64, 0.0058, 12 ) );

		}

		const old = tubes.geometry;
		tubes.geometry = mergeTwo( geos );
		old.dispose();

	}

	rebuildTubes();

	return { root, pump, radiator, fans, tubes, rebuildTubes, lcd: { canvas: lcdCanvas, texture: lcdTex, material: lcdMat } };

}

function mergeTwo( geos ) {

	const out = new THREE.BufferGeometry();
	const pos = [], nor = [], uv = [], idx = [];
	let off = 0;
	for ( const g of geos ) {

		pos.push( ...g.attributes.position.array );
		nor.push( ...g.attributes.normal.array );
		uv.push( ...g.attributes.uv.array );
		for ( const i of g.index.array ) idx.push( i + off );
		off += g.attributes.position.count;
		g.dispose();

	}

	out.setAttribute( 'position', new THREE.Float32BufferAttribute( pos, 3 ) );
	out.setAttribute( 'normal', new THREE.Float32BufferAttribute( nor, 3 ) );
	out.setAttribute( 'uv', new THREE.Float32BufferAttribute( uv, 2 ) );
	out.setIndex( idx );
	return out;

}

// Draws the pump's dashboard LCD (CPU temperature gauge).
export function drawPumpLCD( canvas, temp, load, on ) {

	const ctx = canvas.getContext( '2d' );
	const W = canvas.width, H = canvas.height;
	ctx.fillStyle = '#000';
	ctx.fillRect( 0, 0, W, H );
	if ( ! on ) return;
	const cx = W / 2, cy = H * 0.56, r = W * 0.38;
	const a0 = Math.PI * 0.75, a1 = Math.PI * 2.25;
	const segs = 30;
	const frac = Math.min( 1, Math.max( 0, ( temp - 20 ) / 75 ) );
	for ( let i = 0; i < segs; i ++ ) {

		const t = i / segs;
		const a = a0 + ( a1 - a0 ) * t;
		const lit = t < frac;
		const hue = 190 - t * 190; // cyan → red
		ctx.strokeStyle = lit ? `hsl(${ hue }, 85%, 58%)` : 'rgba(255,255,255,0.07)';
		ctx.lineWidth = 12;
		ctx.beginPath();
		ctx.arc( cx, cy, r, a + 0.02, a + ( a1 - a0 ) / segs - 0.02 );
		ctx.stroke();

	}

	ctx.fillStyle = '#f2f2f2';
	ctx.textAlign = 'center';
	ctx.textBaseline = 'alphabetic';
	ctx.font = 'bold 74px "Segoe UI", Arial, sans-serif';
	ctx.fillText( String( Math.round( temp ) ), cx - 8, cy + 18 );
	ctx.font = 'bold 26px Arial';
	ctx.fillText( '°C', cx + 52, cy - 18 );
	ctx.font = '600 18px Arial';
	ctx.fillStyle = '#9fdcff';
	ctx.fillText( 'CPU TEMP', cx, cy - 52 );
	ctx.fillStyle = '#bbbbbb';
	ctx.font = '600 20px Arial';
	ctx.fillText( `${ Math.round( load ) }%`, cx, cy + 62 );
	ctx.font = '13px Arial';
	ctx.fillStyle = '#777';
	ctx.fillText( 'LOAD', cx, cy + 80 );

}
