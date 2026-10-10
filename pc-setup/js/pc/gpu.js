// ASUS PRIME Radeon RX 9060 XT OC 16GB — 304 x 126 x 50 mm, 2.5-slot,
// three Axial-tech fans with barrier rings, vented backplate, single 8-pin.
// No RGB lighting on this card.
import * as THREE from 'three';
import { M } from '../core/materials.js';
import { labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, roundedRectPath, circlePath, extrude, mergeGeometries } from '../core/geo.js';
import { CASE, BOARD, bpos, PCIE1, GPU } from './layout.js';
import { createFan } from './fan.js';

let shroudMat, accentMat;

export function buildGpu() {

	shroudMat ||= new THREE.MeshStandardMaterial( { name: 'gpuShroud', color: 0x1f2023, roughness: 0.55, metalness: 0.05 } );
	accentMat ||= new THREE.MeshStandardMaterial( { name: 'gpuAccent', color: 0x6d7076, roughness: 0.35, metalness: 0.7 } );

	const root = group( 'gpu' );
	// Frame: origin at the PCIe slot top / bracket plane intersection.
	const slot = bpos( 0, PCIE1.v, 0 );
	const zBracket = CASE.zB + 0.0035;
	root.position.set( BOARD.surfX, slot.y, zBracket );
	// local: -X = card height toward the glass, -Y = cooler side (fans face down), +Z = card length
	const L = GPU.L, Hc = GPU.H, T = GPU.T;
	const x0 = - 0.002, x1 = - Hc + 0.002;            // card spans x
	const xc = ( x0 + x1 ) / 2, hw = x0 - x1;
	const yTop = 0.0035, yBot = yTop - T; // PCB plane sits at local y = 0 (slot centre)

	// PCB + gold fingers in slot
	mesh( box( 0.11, 0.0016, 0.24 ), M.pcbGreen, { parent: root, p: [ - 0.058, 0, 0.004 + 0.12 ] } );
	mesh( box( 0.008, 0.0014, 0.072 ), M.gold, { parent: root, p: [ 0.003, 0, 0.04 + 0.036 ], cast: false } );

	// Backplate (top, faces the CPU) with flow-through cutout at the end
	{

		const s = roundedRectShape( L - 0.004, hw, 0.004, L / 2, 0 );
		s.holes.push( roundedRectPath( 0.055, hw * 0.7, 0.01, L - 0.04, 0 ) );
		for ( let i = 0; i < 5; i ++ ) s.holes.push( roundedRectPath( 0.025, 0.003, 0.0015, 0.05 + i * 0.006 * 0, - 0.03 + i * 0.012 ) );
		const g = extrude( s, 0.0016, { curveSegments: 8 } );
		// shape is in (z, x') ; rotate so extrusion goes along Y
		const bp = mesh( g, M.blackMetal, { parent: root } );
		bp.rotation.set( Math.PI / 2, 0, Math.PI / 2 );
		bp.position.set( xc, yTop - 0.0008, 0 );
		const bpl = labelTexture( 512, 64, ( ctx, w, h ) => {

			ctx.fillStyle = '#7d8086';
			ctx.font = 'bold 34px Arial';
			ctx.textBaseline = 'middle';
			ctx.fillText( '/ASUS   PRIME', 8, h / 2 );

		} );
		const lbl = new THREE.Mesh( new THREE.PlaneGeometry( 0.12, 0.015 ), new THREE.MeshStandardMaterial( { map: bpl, transparent: true, roughness: 0.4, metalness: 0.4 } ) );
		lbl.rotation.set( - Math.PI / 2, 0, Math.PI / 2 );
		lbl.position.set( xc + 0.02, yTop + 0.0002, 0.12 );
		root.add( lbl );

	}

	// Heatsink fin stack (visible through shroud gaps / flow-through)
	{

		const fins = [];
		const n = 56;
		for ( let i = 0; i < n; i ++ ) {

			const f = new THREE.BoxGeometry( hw - 0.012, 0.025, 0.0004 );
			f.translate( xc, - 0.018, 0.012 + i * ( L - 0.03 ) / n );
			fins.push( f );

		}

		const merged = mergeGeometries( fins );
		fins.forEach( ( f ) => f.dispose() );
		mesh( merged, M.aluFin, { parent: root, cast: false } );
		// base plate & heat pipes
		mesh( box( hw - 0.02, 0.003, L - 0.04 ), M.aluFin, { parent: root, p: [ xc, - 0.004, L / 2 ] } );
		for ( let i = 0; i < 4; i ++ ) {

			const xp = x1 + 0.006;
			const z = 0.07 + i * 0.012;
			const curve = new THREE.CatmullRomCurve3( [
				new THREE.Vector3( - 0.04, yTop - 0.03, z ),
				new THREE.Vector3( xp + 0.012, yTop - 0.028, z + 0.02 ),
				new THREE.Vector3( xp, yTop - 0.02, z + 0.08 ),
				new THREE.Vector3( xp + 0.004, yTop - 0.012, 0.24 + i * 0.008 ),
			] );
			mesh( new THREE.TubeGeometry( curve, 24, 0.003, 8 ), M.copper, { parent: root, cast: false } );

		}

	}

	// Shroud: bottom plate with three fan openings + perimeter walls
	{

		const s = roundedRectShape( L - 0.002, hw, 0.006, L / 2, 0 );
		const fanZ = [ 0.058, 0.152, 0.246 ];
		for ( const z of fanZ ) s.holes.push( circlePath( 0.0465, z, 0 ) );
		const g = extrude( s, 0.004, { curveSegments: 48 } );
		const plate = mesh( g, shroudMat, { parent: root } );
		plate.rotation.set( Math.PI / 2, 0, Math.PI / 2 );
		plate.position.set( xc, yBot + 0.002, 0 );

		// fans (axial-tech with barrier ring) — air enters from below
		root.userData.fans = [];
		for ( const z of fanZ ) {

			const f = createFan( {
				name: 'axialFan', size: 0.098, depth: 0.014, frameMat: shroudMat, bladeMat: M.fanBladeBlack,
				hubMat: M.blackMatte, strutMat: M.blackMatte, blades: 9, bladeRing: true, speed: 0.6,
			} );
			// hide the square frame; shroud plate provides the opening
			f.children[ 0 ].visible = false;
			f.position.set( xc, yBot + 0.009, z );
			f.rotation.x = - Math.PI / 2; // local +Z → world +Y (air pushed into heatsink)
			root.add( f );
			root.userData.fans.push( f );
			// hub sticker
			const st = labelTexture( 128, 128, ( ctx, w, h ) => {

				ctx.fillStyle = '#202124'; ctx.beginPath(); ctx.arc( 64, 64, 64, 0, Math.PI * 2 ); ctx.fill();
				ctx.fillStyle = '#c9ccd0'; ctx.font = 'bold 30px Arial'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
				ctx.fillText( 'ASUS', 64, 64 );

			} );
			const sticker = new THREE.Mesh( new THREE.CircleGeometry( 0.014, 32 ), new THREE.MeshStandardMaterial( { map: st, roughness: 0.4 } ) );
			sticker.rotation.x = Math.PI / 2;
			sticker.position.set( 0, 0, - 0.0055 );
			sticker.rotation.set( Math.PI, 0, 0 );
			f.userData.rotor.add( sticker );

		}

		// side walls
		const wallH = T - 0.008;
		mesh( rbox( 0.004, wallH, L - 0.004, 0.0015 ), shroudMat, { parent: root, p: [ x1 + 0.002, yBot + wallH / 2 + 0.002, L / 2 ] } ); // top edge (toward glass)
		mesh( rbox( hw, 0.012, 0.004, 0.0015 ), shroudMat, { parent: root, p: [ xc, yBot + 0.008, L - 0.002 ] } );           // end cap
		// light accent strips on the edge facing the glass (paint, not lighting)
		mesh( box( 0.0006, 0.0018, 0.08 ), accentMat, { parent: root, p: [ x1 - 0.0002, yBot + 0.02, 0.03 ] } );
		mesh( box( 0.0006, 0.0018, 0.05 ), accentMat, { parent: root, p: [ x1 - 0.0002, yBot + 0.028, L - 0.035 ] } );
		// PRIME branding on the visible edge
		const brand = labelTexture( 1024, 96, ( ctx, w, h ) => {

			ctx.fillStyle = '#d6d8dc';
			ctx.font = 'bold 54px Arial';
			ctx.textBaseline = 'middle';
			ctx.fillText( 'PRIME', 360, h / 2 );
			ctx.font = '36px Arial';
			ctx.fillStyle = '#9da1a7';
			ctx.fillText( 'RADEON  RX 9060 XT', 560, h / 2 );

		} );
		const bm = new THREE.Mesh( new THREE.PlaneGeometry( L * 0.95, 0.009 ), new THREE.MeshStandardMaterial( { map: brand, transparent: true, roughness: 0.4 } ) );
		bm.rotation.y = - Math.PI / 2;
		bm.position.set( x1 - 0.0003, yBot + 0.024, L / 2 );
		root.add( bm );
		// corner bevel pieces
		mesh( box( 0.012, 0.004, L - 0.01 ), accentMat, { parent: root, p: [ x1 + 0.007, yBot + 0.001, L / 2 ] } );

	}

	// I/O bracket (3-slot height) with vents, 3x DP + HDMI
	{

		const bg = group( 'bracket', root );
		const bh = 0.0605;
		const s = roundedRectShape( 0.12, bh, 0.001, - 0.062, yTop + 0.004 - bh / 2 );
		for ( let i = 0; i < 9; i ++ ) s.holes.push( roundedRectPath( 0.004, 0.022, 0.0015, - 0.08 - i * 0.0042 + 0.04, yTop - 0.04 ) );
		mesh( extrude( s, 0.001, { curveSegments: 4 } ), M.nickel, { parent: bg, p: [ 0, 0, - 0.0005 ] } );
		const ports = [ [ - 0.022, 'dp' ], [ - 0.044, 'hdmi' ], [ - 0.066, 'dp' ] ];
		for ( const [ x ] of ports ) mesh( box( 0.017, 0.0055, 0.006 ), M.blackMatte, { parent: bg, p: [ x, yTop - 0.012, - 0.002 ] } );
		mesh( cyl( 0.0028, 0.0028, 0.006, 12 ), M.nickel, { parent: bg, r: [ Math.PI / 2, 0, 0 ], p: [ 0.012, yTop - 0.004, - 0.004 ] } );

	}

	// 8-pin PCIe power socket on the edge facing the glass
	mesh( box( 0.009, 0.0085, 0.021 ), M.blackMatte, { parent: root, p: [ x1 - 0.003, yTop - 0.012, 0.215 ] } );

	root.userData.powerPlug = new THREE.Vector3( x1 - 0.0075, yTop - 0.012, 0.215 );
	return root;

}
