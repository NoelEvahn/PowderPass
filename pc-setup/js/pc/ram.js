// Kingston FURY Beast RGB DDR5-6000 (white heat spreader, top light bar).
// 133.35 x 42.2 x ~7 mm per module.
import * as THREE from 'three';
import { M, rgbMaterial } from '../core/materials.js';
import { labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box } from '../core/geo.js';
import { bpos, DIMM } from './layout.js';

let labelMat = null;
function furyLabel() {

	if ( labelMat ) return labelMat;
	const tex = labelTexture( 512, 96, ( ctx, w, h ) => {

		ctx.fillStyle = '#3a3b3f';
		ctx.font = 'italic 900 56px Arial Black, Arial, sans-serif';
		ctx.textBaseline = 'middle';
		ctx.fillText( 'FURY', 18, h / 2 + 2 );
		ctx.font = '600 20px Arial, sans-serif';
		ctx.fillStyle = '#6a6c70';
		ctx.fillText( 'BEAST  DDR5 RGB', 210, h / 2 + 2 );
		ctx.fillStyle = '#c9cacc';
		ctx.fillRect( 420, 30, 70, 2 );
		ctx.fillRect( 420, 64, 70, 2 );

	} );
	labelMat = new THREE.MeshStandardMaterial( { map: tex, transparent: true, roughness: 0.4 } );
	return labelMat;

}

export function buildRam( lightMat ) {

	const sticks = [];
	const H = 0.0422, L = DIMM.len, T = 0.0071;
	for ( const idx of DIMM.used ) {

		const u = DIMM.us[ idx ];
		const g = group( `ram${ idx }` );
		// local frame: x = height direction (toward -X in case), y along length, z thickness
		const base = bpos( u, DIMM.v0 + L / 2, 0.0035 ); // seated in slot
		g.position.copy( base );

		// PCB (visible only at bottom edge)
		mesh( box( 0.031, L, 0.0012 ), M.pcbGreen, { parent: g, p: [ - 0.0155, 0, 0 ] } );
		// gold contacts hidden in slot
		// Heat spreader: two shells with faceted top profile
		for ( const side of [ - 1, 1 ] ) {

			const shell = mesh( rbox( 0.034, L - 0.001, 0.0026, 0.0009 ), M.whitePowder, { parent: g, p: [ - 0.004 - 0.017, 0, side * 0.0022 ] } );
			shell.name = 'spreader';
			// angular accent cut (raised facet)
			const facet = mesh( rbox( 0.012, L * 0.36, 0.0008, 0.0003 ), M.whiteGloss, { parent: g, p: [ - 0.026, - L * 0.24, side * 0.0036 ] } );
			facet.rotation.x = side * 0.0;
			mesh( rbox( 0.006, L * 0.22, 0.0008, 0.0003 ), M.lightGrey, { parent: g, p: [ - 0.012, L * 0.3, side * 0.0036 ] } );

		}

		// top light bar housing + diffuser (RGB)
		mesh( rbox( 0.004, L - 0.004, T, 0.0012 ), M.whitePowder, { parent: g, p: [ - 0.0385, 0, 0 ] } );
		const bar = mesh( rbox( 0.004, L - 0.012, T - 0.0016, 0.0012 ), lightMat, { parent: g, p: [ - 0.0405, 0, 0 ], cast: false } );
		bar.userData.rgb = true;
		// zig-zag facets on the diffuser
		for ( let i = 0; i < 6; i ++ ) {

			const seg = mesh( box( 0.0012, 0.012, T - 0.0012 ), lightMat, { parent: g, p: [ - 0.0428, - L / 2 + 0.014 + i * 0.021, 0 ], cast: false } );
			seg.rotation.x = 0.0;

		}

		// labels on both faces
		for ( const side of [ - 1, 1 ] ) {

			const lbl = new THREE.Mesh( new THREE.PlaneGeometry( L * 0.62, 0.0115 ), furyLabel() );
			lbl.position.set( - 0.021, - L * 0.08, side * 0.0036 + side * 0.0005 );
			lbl.rotation.set( 0, side > 0 ? 0 : Math.PI, side > 0 ? Math.PI / 2 : - Math.PI / 2 );
			g.add( lbl );

		}

		// Orient: local x = height (toward the glass, -X case), local y = along the slot (Y case), z = Z case.
		g.userData.component = 'ram';
		sticks.push( g );

	}

	return sticks;

}

export function ramLightMaterial() {

	return rgbMaterial( 'ramLight', 0xf4f4f4, { roughness: 0.3 } );

}
