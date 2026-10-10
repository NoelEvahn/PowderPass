// Corsair RMe 750W (RM750e) — 150 x 86 x 140 mm, matte black, 120 mm fan.
// Mounted in the H6 Flow rear chamber with the fan facing the right side vent.
import * as THREE from 'three';
import { M } from '../core/materials.js';
import { labelTexture } from '../core/textures.js';
import { mesh, group, rbox, box, cyl, roundedRectShape, circlePath, extrude } from '../core/geo.js';
import { CASE, PSU } from './layout.js';
import { createFan } from './fan.js';

export function buildPsu() {

	const g = group( 'psu' );
	// local frame == case frame; PSU body: x thickness 86, y height 150, z length 140
	const cx = CASE.trayX + 0.0035 + PSU.h / 2;
	const cy = CASE.feet + 0.008 + PSU.w / 2;
	const cz = CASE.zB + 0.002 + PSU.l / 2;
	g.position.set( cx, cy, cz );

	mesh( rbox( PSU.h, PSU.w, PSU.l, 0.002 ), M.blackMatte, { parent: g } );

	// Fan grille side (+X)
	const grille = new THREE.Group();
	grille.position.set( PSU.h / 2 + 0.0004, 0, 0 );
	grille.rotation.y = Math.PI / 2;
	g.add( grille );
	for ( let r = 0.012; r < 0.06; r += 0.0085 ) {

		mesh( new THREE.TorusGeometry( r, 0.0007, 6, 64 ), M.blackMetal, { parent: grille, cast: false } );

	}

	for ( let i = 0; i < 4; i ++ ) {

		const bar = mesh( box( 0.12, 0.0016, 0.0012 ), M.blackMetal, { parent: grille, cast: false } );
		bar.rotation.z = i * Math.PI / 4;

	}

	const fan = createFan( { name: 'psuFan', size: 0.12, depth: 0.025, frameMat: M.blackMatte, bladeMat: M.fanBladeBlack, hubMat: M.blackMatte, blades: 7, speed: 0.35 } );
	fan.position.set( PSU.h / 2 - 0.016, 0, 0 );
	fan.rotation.y = - Math.PI / 2;
	g.add( fan );

	// Side label (-X side, faces the motherboard tray — hidden) and top label
	const label = labelTexture( 512, 512, ( ctx, w, h ) => {

		ctx.fillStyle = '#121214';
		ctx.fillRect( 0, 0, w, h );
		ctx.fillStyle = '#e8e8e8';
		ctx.font = 'bold 54px Arial';
		ctx.fillText( 'CORSAIR', 40, 120 );
		ctx.font = 'bold 90px Arial';
		ctx.fillText( 'RM750e', 40, 230 );
		ctx.font = '24px Arial';
		ctx.fillStyle = '#b5b5b5';
		ctx.fillText( '750 WATT  ·  80 PLUS GOLD  ·  ATX 3.1', 40, 290 );
		ctx.strokeStyle = '#d9b25e';
		ctx.lineWidth = 3;
		ctx.strokeRect( 40, 330, 120, 120 );
		ctx.fillStyle = '#d9b25e';
		ctx.font = 'bold 22px Arial';
		ctx.fillText( '80 PLUS', 52, 380 );
		ctx.fillText( 'GOLD', 66, 415 );

	} );
	const lbl = new THREE.Mesh( new THREE.PlaneGeometry( PSU.l * 0.8, PSU.w * 0.8 ), new THREE.MeshStandardMaterial( { map: label, roughness: 0.6 } ) );
	lbl.position.set( 0, PSU.w / 2 + 0.0003, 0 );
	lbl.rotation.set( - Math.PI / 2, 0, Math.PI / 2 );
	lbl.scale.set( 0.5, 0.45, 1 );
	g.add( lbl );

	// Back face (-Z): AC inlet, switch, honeycomb vent
	const back = new THREE.Group();
	back.position.set( 0, 0, - PSU.l / 2 - 0.0003 );
	g.add( back );
	mesh( rbox( 0.032, 0.026, 0.004, 0.002 ), M.blackGloss, { parent: back, p: [ - 0.01, - 0.045, 0 ] } );
	mesh( box( 0.022, 0.014, 0.005 ), M.chip, { parent: back, p: [ - 0.01, - 0.045, - 0.0005 ] } );
	mesh( rbox( 0.012, 0.02, 0.005, 0.001 ), M.blackGloss, { parent: back, p: [ 0.025, - 0.045, 0 ] } );
	const vent = M.makePerforated( 0.07, 0.07, 500 ).clone();
	vent.color.set( 0x1a1a1b );
	const vm = mesh( new THREE.PlaneGeometry( 0.07, 0.07 ), vent, { parent: back, p: [ 0, 0.03, - 0.0002 ] } );
	vm.rotation.y = Math.PI;

	// Modular sockets on the front face (+Z)
	const front = new THREE.Group();
	front.position.set( 0, 0, PSU.l / 2 + 0.0002 );
	g.add( front );
	for ( let i = 0; i < 6; i ++ ) mesh( box( 0.016, 0.009, 0.004 ), M.chip, { parent: front, p: [ - 0.02 + ( i % 2 ) * 0.04, 0.05 - Math.floor( i / 2 ) * 0.03, 0 ] } );

	g.userData.fan = fan;
	return g;

}
