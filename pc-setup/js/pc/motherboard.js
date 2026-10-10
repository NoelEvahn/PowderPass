// MSI MPG B850 EDGE TI WIFI (ATX, silver-white) + Lexar NM790 in M2_1.
import * as THREE from 'three';
import { M, TEX } from '../core/materials.js';
import { labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl } from '../core/geo.js';
import { BOARD, bpos, SOCKET, DIMM, PCIE1, M2_1 } from './layout.js';

// Place a box on the board using board-space extents (u0..u1, v0..v1, height h above PCB).
function onBoard( parent, u0, u1, v0, v1, h, mat, { r = 0.0008, lift = 0 } = {} ) {

	const c = bpos( ( u0 + u1 ) / 2, ( v0 + v1 ) / 2, lift + h / 2 );
	return mesh( r > 0 ? rbox( h, v1 - v0, u1 - u0, r ) : box( h, v1 - v0, u1 - u0 ), mat, { parent, p: [ c.x, c.y, c.z ] } );

}

function finnedHeatsink( parent, u0, u1, v0, v1, h, { fins = 8, along = 'u', mat = M.silverHeatsink } = {} ) {

	const g = group( 'heatsink', parent );
	onBoard( g, u0, u1, v0, v1, h * 0.55, mat, { r: 0.0015 } );
	const n = fins;
	for ( let i = 0; i < n; i ++ ) {

		if ( along === 'u' ) {

			const span = ( u1 - u0 ) / n;
			onBoard( g, u0 + i * span + span * 0.18, u0 + ( i + 1 ) * span - span * 0.18, v0 + 0.001, v1 - 0.001, h * 0.45, mat, { r: 0.0006, lift: h * 0.55 } );

		} else {

			const span = ( v1 - v0 ) / n;
			onBoard( g, u0 + 0.001, u1 - 0.001, v0 + i * span + span * 0.18, v0 + ( i + 1 ) * span - span * 0.18, h * 0.45, mat, { r: 0.0006, lift: h * 0.55 } );

		}

	}

	return g;

}

function textPlane( text, w, h, { color = '#6b6f75', bg = null, font = 'bold 64px Arial', px = 512 } = {} ) {

	const tex = labelTexture( px, Math.round( px * h / w ), ( ctx, cw, ch ) => {

		if ( bg ) {

			ctx.fillStyle = bg; ctx.fillRect( 0, 0, cw, ch );

		}

		ctx.fillStyle = color;
		ctx.font = font;
		ctx.textAlign = 'center';
		ctx.textBaseline = 'middle';
		ctx.fillText( text, cw / 2, ch / 2 );

	} );
	const mat = new THREE.MeshStandardMaterial( { map: tex, transparent: ! bg, roughness: 0.5, metalness: 0.2 } );
	const m = new THREE.Mesh( new THREE.PlaneGeometry( w, h ), mat );
	m.castShadow = false;
	return m;

}

// A plane facing -X (toward the glass) placed at board coordinates.
function boardDecal( parent, plane, u, v, h ) {

	const p = bpos( u, v, h );
	plane.position.copy( p );
	plane.rotation.y = - Math.PI / 2;
	parent.add( plane );
	return plane;

}

export function buildMotherboard() {

	const root = group( 'motherboard' );

	// PCB: textured face + edge box
	const pcbFace = new THREE.Mesh( new THREE.PlaneGeometry( BOARD.w, BOARD.h ), M.pcb );
	pcbFace.rotation.y = - Math.PI / 2;
	pcbFace.position.set( BOARD.surfX - 0.00005, BOARD.topY - BOARD.h / 2, BOARD.rearZ + BOARD.w / 2 );
	pcbFace.receiveShadow = true;
	root.add( pcbFace );
	mesh( box( BOARD.t, BOARD.h, BOARD.w ), M.pcbGreen, { parent: root, p: [ BOARD.surfX + BOARD.t / 2, BOARD.topY - BOARD.h / 2, BOARD.rearZ + BOARD.w / 2 ] } );

	// Mounting screws + standoffs
	for ( const u of [ 0.0064, 0.081, 0.2376 ] ) for ( const v of [ 0.0102, 0.165, 0.288 ] ) {

		if ( u < 0.05 && v < 0.17 ) continue; // under the I/O cover
		const p = bpos( u, v, 0.0012 );
		mesh( cyl( 0.0034, 0.0034, 0.0024, 16 ), M.nickel, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ p.x, p.y, p.z ] } );
		const st = bpos( u, v, - BOARD.t - 0.0032 );
		mesh( cyl( 0.0028, 0.0028, 0.0064, 6 ), M.gold, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ st.x, st.y, st.z ], cast: false } );

	}

	// --- Rear I/O cover (silver, angular) -------------------------------
	const ioCover = group( 'ioCover', root );
	onBoard( ioCover, 0.0, 0.046, 0.004, 0.168, 0.036, M.silverHeatsink, { r: 0.003 } );
	onBoard( ioCover, 0.006, 0.04, 0.012, 0.16, 0.0015, M.whiteGloss, { r: 0.0005, lift: 0.036 } );
	const edgeLabel = textPlane( 'MPG', 0.03, 0.012, { color: '#8b8f95', font: 'bold 150px Arial' } );
	edgeLabel.rotation.z = Math.PI / 2;
	{

		const p = bpos( 0.023, 0.12, 0.0378 );
		edgeLabel.position.copy( p );
		edgeLabel.rotation.set( 0, - Math.PI / 2, Math.PI / 2 );
		ioCover.add( edgeLabel );

	}

	// --- VRM heatsink along the top edge ---------------------------------
	finnedHeatsink( root, 0.046, 0.142, 0.006, 0.042, 0.03, { fins: 9, along: 'u' } );
	// chokes peeking out under the heatsinks
	for ( let i = 0; i < 9; i ++ ) onBoard( root, 0.05 + i * 0.0098, 0.057 + i * 0.0098, 0.044, 0.051, 0.006, M.chip, { r: 0.0005 } );
	for ( let i = 0; i < 10; i ++ ) onBoard( root, 0.048, 0.055, 0.03 + i * 0.012, 0.037 + i * 0.012, 0.006, M.chip, { r: 0.0005 } );

	// EPS 8-pin x2 (top edge)
	for ( const u of [ 0.012, 0.03 ] ) onBoard( root, u, u + 0.016, 0.0, 0.009, 0.013, M.blackMatte, { r: 0.0006 } );

	// --- AM5 socket retention frame + CPU (under the pump) ----------------
	const s = SOCKET;
	onBoard( root, s.u - 0.038, s.u + 0.038, s.v - 0.04, s.v + 0.04, 0.003, M.blackMetal, { r: 0.001 } );
	onBoard( root, s.u - 0.02, s.u + 0.02, s.v - 0.02, s.v + 0.02, 0.0065, M.nickel, { r: 0.002 } ); // IHS

	// SMD capacitors around the socket
	for ( let i = 0; i < 12; i ++ ) {

		const a = i / 12 * Math.PI * 2;
		const p = bpos( s.u + Math.cos( a ) * 0.046, s.v + Math.sin( a ) * 0.046, 0.0015 );
		mesh( cyl( 0.0012, 0.0012, 0.003, 8 ), M.chip, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ p.x, p.y, p.z ], cast: false } );

	}

	// --- DIMM slots ------------------------------------------------------
	for ( const u of DIMM.us ) {

		onBoard( root, u - 0.0028, u + 0.0028, DIMM.v0 - 0.006, DIMM.v0 + DIMM.len + 0.006, 0.0075, M.slot, { r: 0.0006 } );
		// latch tabs
		onBoard( root, u - 0.003, u + 0.003, DIMM.v0 - 0.01, DIMM.v0 - 0.006, 0.012, M.slot, { r: 0.0008 } );

	}

	// --- 24-pin ATX + front-edge connectors ----------------------------
	onBoard( root, 0.229, 0.243, 0.1, 0.155, 0.013, M.blackMatte, { r: 0.0008 } );
	onBoard( root, 0.233, 0.244, 0.175, 0.19, 0.009, M.blackMatte ); // USB-C header
	for ( let i = 0; i < 4; i ++ ) onBoard( root, 0.236, 0.244, 0.205 + i * 0.012, 0.215 + i * 0.012, 0.01, M.slotDark ); // SATA

	// --- PCIe slots ------------------------------------------------------
	const pcie = ( v, u0, len, reinforced ) => {

		onBoard( root, u0, u0 + len, v - 0.0035, v + 0.0035, 0.011, reinforced ? M.nickel : M.slot, { r: 0.0008 } );
		onBoard( root, u0 + len, u0 + len + 0.006, v - 0.004, v + 0.004, 0.013, M.slot, { r: 0.0008 } );

	};

	pcie( PCIE1.v, PCIE1.u0, 0.089, true );
	pcie( 0.218, 0.03, 0.025, false );
	pcie( 0.278, 0.03, 0.089, false );

	// --- M.2 shield heatsinks + chipset heatsink -------------------------
	const m2Heatsink = group( 'm2Heatsink', root );
	finnedHeatsink( m2Heatsink, M2_1.u0 - 0.004, M2_1.u0 + 0.086, M2_1.v - 0.013, M2_1.v + 0.009, 0.0105, { fins: 14, along: 'u' } );
	boardDecal( m2Heatsink, textPlane( 'M.2 SHIELD FROZR', 0.05, 0.006, { color: '#8a8e94', font: 'bold 40px Arial' } ), M2_1.u0 + 0.04, M2_1.v - 0.002, 0.0112 );

	finnedHeatsink( root, 0.02, 0.142, 0.229, 0.268, 0.009, { fins: 6, along: 'v' } );
	const chipset = group( 'chipset', root );
	onBoard( chipset, 0.15, 0.236, 0.196, 0.297, 0.013, M.silverHeatsink, { r: 0.003 } );
	onBoard( chipset, 0.156, 0.23, 0.2, 0.25, 0.0012, M.whiteGloss, { r: 0.0005, lift: 0.013 } );
	boardDecal( chipset, textPlane( 'MPG', 0.03, 0.01, { color: '#7d828a', font: 'bold 170px Arial' } ), 0.193, 0.27, 0.0135 );
	boardDecal( chipset, textPlane( 'B850 EDGE TI', 0.05, 0.007, { color: '#8a8e94', font: 'bold 64px Arial' } ), 0.193, 0.282, 0.0135 );

	// Bottom-edge headers
	for ( let i = 0; i < 9; i ++ ) onBoard( root, 0.02 + i * 0.024, 0.032 + i * 0.024, 0.296, 0.303, 0.007, M.blackMatte, { r: 0.0004 } );

	// --- Rear I/O ports (through the back panel) ------------------------
	const io = group( 'rearIO', root );
	{

		const c = bpos( - 0.006, 0.086, 0.021 );
		mesh( box( 0.042, 0.158, 0.012 ), M.nickel, { parent: io, p: [ c.x, c.y, c.z ] } );
		const ports = [ [ 0.02, 0.014, 0.016 ], [ 0.04, 0.014, 0.016 ], [ 0.06, 0.014, 0.016 ], [ 0.08, 0.014, 0.016 ], [ 0.1, 0.009, 0.009 ], [ 0.118, 0.014, 0.016 ], [ 0.14, 0.012, 0.02 ] ];
		for ( const [ v, hh, ww ] of ports ) {

			for ( const h of [ 0.012, 0.03 ] ) {

				const p = bpos( - 0.0125, v, h );
				mesh( box( hh, ww * 0.5, 0.002 ), M.blackMatte, { parent: io, p: [ p.x, p.y, p.z ], cast: false } );

			}

		}

	}

	// --- Lexar NM790 1TB (2280) under M2_1 heatsink -----------------------
	const ssd = group( 'ssd', root );
	onBoard( ssd, M2_1.u0, M2_1.u0 + 0.08, M2_1.v - 0.011, M2_1.v + 0.011, 0.0008, M.pcbGreen, { r: 0 } );
	const lbl = textPlane( 'Lexar  NM790  1TB', 0.07, 0.019, { color: '#d9d9d9', bg: '#141414', font: 'bold 52px Arial' } );
	boardDecal( ssd, lbl, M2_1.u0 + 0.04, M2_1.v, 0.0009 );
	onBoard( ssd, M2_1.u0 + 0.008, M2_1.u0 + 0.02, M2_1.v - 0.006, M2_1.v + 0.006, 0.0012, M.chip, { lift: 0.0008 } );
	// M.2 key connector
	onBoard( root, M2_1.u0 - 0.006, M2_1.u0, M2_1.v - 0.011, M2_1.v + 0.011, 0.0042, M.blackMatte, { r: 0 } );
	// standoff screw
	{

		const p = bpos( M2_1.u0 + 0.0815, M2_1.v, 0.0015 );
		mesh( cyl( 0.0022, 0.0022, 0.002, 12 ), M.nickel, { parent: root, r: [ 0, 0, Math.PI / 2 ], p: [ p.x, p.y, p.z ] } );

	}

	return { root, m2Heatsink, ssd };

}
