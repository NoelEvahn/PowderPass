// Physically based material library. Materials are shared to minimise
// shader programs and so global changes (e.g. env intensity) propagate.
import * as THREE from 'three';
import * as T from './textures.js';

export const M = {};
export const TEX = {};

export function buildMaterials() {

	TEX.powder = T.powderCoatNormal();
	TEX.grain = T.plasticGrainNormal();
	TEX.wall = T.wallTextures();
	TEX.fabric = T.fabricTextures();
	TEX.floor = T.floorTextures();
	TEX.deskRough = T.deskRoughness();
	TEX.brushed = T.brushedTextures( [ 1, 1 ] );
	TEX.perf = T.perforationAlpha( { holeRatio: 0.34 } );
	TEX.perfFine = T.perforationAlpha( { holeRatio: 0.4 } );
	TEX.pcb = T.pcbTexture();

	const std = ( p ) => new THREE.MeshStandardMaterial( p );
	const phys = ( p ) => new THREE.MeshPhysicalMaterial( p );

	// --- Case & hardware --------------------------------------------------
	M.whitePowder = std( {
		name: 'whitePowder', color: 0xf2f1ee, roughness: 0.52, metalness: 0.0,
		normalMap: TEX.powder, normalScale: new THREE.Vector2( 0.12, 0.12 ),
	} );
	M.whitePlastic = std( { name: 'whitePlastic', color: 0xeeedea, roughness: 0.45, normalMap: TEX.grain, normalScale: new THREE.Vector2( 0.06, 0.06 ) } );
	M.whiteGloss = phys( { name: 'whiteGloss', color: 0xf3f3f1, roughness: 0.25, clearcoat: 0.6, clearcoatRoughness: 0.15 } );
	M.lightGrey = std( { name: 'lightGrey', color: 0xc9cbce, roughness: 0.5 } );
	M.silverHeatsink = std( { name: 'silverHeatsink', color: 0xe4e5e6, roughness: 0.38, metalness: 0.65, normalMap: TEX.brushed.normal, normalScale: new THREE.Vector2( 0.25, 0.25 ) } );

	const perfMat = ( base, tex, rep ) => {

		const t = tex.clone();
		t.needsUpdate = true;
		t.repeat.set( rep[ 0 ], rep[ 1 ] );
		return std( {
			name: 'perforated', color: base, roughness: 0.55, metalness: 0,
			alphaMap: t, alphaTest: 0.5, alphaToCoverage: true, side: THREE.DoubleSide,
			normalMap: TEX.powder, normalScale: new THREE.Vector2( 0.1, 0.1 ),
		} );

	};

	M.makePerforated = ( w, h, density = 300 ) => perfMat( 0xf2f1ee, TEX.perf, [ w * density / 4, h * density / 6.928 ] );

	M.glass = phys( {
		name: 'glass', color: 0xffffff, metalness: 0, roughness: 0.03,
		transmission: 1, thickness: 0.004, ior: 1.52,
		attenuationColor: new THREE.Color( 0xe8f2ee ), attenuationDistance: 0.5,
		specularIntensity: 1, envMapIntensity: 1.0, side: THREE.FrontSide,
	} );
	// LTC area-light specular is inaccurate at near-zero roughness and produces a
	// broad veil over the glass. The glass therefore skips the analytic rect lights;
	// their reflections still appear via the reflection probe (emissive curtain/screen).
	M.glass.onBeforeCompile = patchNoArea;
	M.glass.customProgramCacheKey = () => 'no-rectarea';
	M.glassEdge = phys( { name: 'glassEdge', color: 0x5f8f80, roughness: 0.1, transmission: 0.4, thickness: 0.01, ior: 1.5 } );

	M.darkPlastic = std( { name: 'darkPlastic', color: 0x1c1d20, roughness: 0.55, normalMap: TEX.grain, normalScale: new THREE.Vector2( 0.08, 0.08 ) } );
	M.blackMatte = std( { name: 'blackMatte', color: 0x111214, roughness: 0.75 } );
	M.blackGloss = phys( { name: 'blackGloss', color: 0x0a0a0b, roughness: 0.2, clearcoat: 0.8, clearcoatRoughness: 0.1 } );
	M.blackMetal = std( { name: 'blackMetal', color: 0x161719, roughness: 0.48, metalness: 0.35 } );
	M.gunmetal = std( { name: 'gunmetal', color: 0x3a3c40, roughness: 0.35, metalness: 0.85, normalMap: TEX.brushed.normal, normalScale: new THREE.Vector2( 0.2, 0.2 ) } );
	M.rubber = std( { name: 'rubber', color: 0x161616, roughness: 0.92 } );
	M.brushedAlu = std( {
		name: 'brushedAlu', color: 0xcfd2d6, metalness: 1, roughness: 0.32,
		roughnessMap: TEX.brushed.rough, normalMap: TEX.brushed.normal, normalScale: new THREE.Vector2( 0.25, 0.25 ),
	} );
	M.aluFin = std( { name: 'aluFin', color: 0xb9bdc1, metalness: 1, roughness: 0.42 } );
	M.copper = std( { name: 'copper', color: 0xc77b52, metalness: 1, roughness: 0.28 } );
	M.nickel = std( { name: 'nickel', color: 0xd9dadb, metalness: 1, roughness: 0.18 } );
	M.gold = std( { name: 'gold', color: 0xd9b25e, metalness: 1, roughness: 0.25 } );
	M.chrome = std( { name: 'chrome', color: 0xeeeeee, metalness: 1, roughness: 0.08 } );
	M.pcb = std( { name: 'pcb', map: TEX.pcb, roughness: 0.5, metalness: 0.05 } );
	M.pcbGreen = std( { name: 'pcbDark', color: 0x1d2420, roughness: 0.45 } );
	M.chip = std( { name: 'chip', color: 0x1a1a1c, roughness: 0.35, metalness: 0.2 } );
	M.slot = std( { name: 'slot', color: 0xe8e8e6, roughness: 0.45 } );
	M.slotDark = std( { name: 'slotDark', color: 0x2a2b2e, roughness: 0.5 } );

	const braid = T.makeCanvas( 64, 64 );
	{

		const ctx = braid.getContext( '2d' );
		const h = new Float32Array( 64 * 64 );
		for ( let y = 0; y < 64; y ++ ) for ( let x = 0; x < 64; x ++ ) {

			const a = Math.sin( ( x + y ) / 64 * Math.PI * 8 ), b = Math.sin( ( x - y ) / 64 * Math.PI * 8 );
			h[ y * 64 + x ] = Math.max( a, b ) * 0.5 + 0.5;

		}

		const n = T.heightToNormalCanvas( h, 64, 64, 2.2 );
		ctx.drawImage( n, 0, 0 );

	}

	TEX.braid = T.canvasTexture( braid, { srgb: false, repeat: [ 40, 1 ] } );
	M.cableBlack = std( { name: 'cableBlack', color: 0x1a1a1b, roughness: 0.7, normalMap: TEX.braid, normalScale: new THREE.Vector2( 0.6, 0.6 ) } );
	M.cableWhite = std( { name: 'cableWhite', color: 0xe9e8e4, roughness: 0.65, normalMap: TEX.braid, normalScale: new THREE.Vector2( 0.5, 0.5 ) } );
	M.cableRubber = std( { name: 'cableRubber', color: 0x1b1b1c, roughness: 0.6 } );
	M.cableRubberWhite = std( { name: 'cableRubberWhite', color: 0xe6e5e2, roughness: 0.55 } );
	M.aioTube = std( { name: 'aioTube', color: 0xeeeeec, roughness: 0.62, normalMap: TEX.braid, normalScale: new THREE.Vector2( 0.35, 0.35 ) } );

	// Fan materials
	M.fanBladeWhite = std( { name: 'fanBladeWhite', color: 0xf3f2ef, roughness: 0.45, side: THREE.DoubleSide } );
	M.fanBladeBlack = std( { name: 'fanBladeBlack', color: 0x161618, roughness: 0.5, side: THREE.DoubleSide } );

	// --- Room ---------------------------------------------------------------
	M.wall = std( {
		name: 'wall', color: 0xefe7da, roughness: 0.9,
		roughnessMap: TEX.wall.rough, normalMap: TEX.wall.normal, normalScale: new THREE.Vector2( 0.15, 0.15 ),
	} );
	M.ceiling = std( { name: 'ceiling', color: 0xf4f0e8, roughness: 0.95 } );
	M.skirting = std( { name: 'skirting', color: 0xf5f3ee, roughness: 0.4 } );
	M.floor = std( { name: 'floor', map: TEX.floor.map, roughnessMap: TEX.floor.rough, roughness: 1, color: 0xffffff } );
	M.desk = phys( {
		name: 'desk', color: 0xf3f2ee, roughness: 1, roughnessMap: TEX.deskRough,
		clearcoat: 0.25, clearcoatRoughness: 0.22, metalness: 0,
	} );
	M.deskEdge = std( { name: 'deskEdge', color: 0xebeae6, roughness: 0.4 } );
	M.curtain = phys( {
		name: 'curtain', color: 0xf5f2eb, roughness: 0.92, sheen: 0.6, sheenRoughness: 0.8,
		sheenColor: new THREE.Color( 0xffffff ),
		normalMap: TEX.fabric.normal, normalScale: new THREE.Vector2( 0.5, 0.5 ),
		emissive: new THREE.Color( 0xfff3e0 ), emissiveIntensity: 0.0,
		side: THREE.DoubleSide,
	} );
	M.wood = std( { name: 'wood', color: 0xb58a5c, roughness: 0.6 } );
	M.ceramicWhite = phys( { name: 'ceramic', color: 0xf4f2ee, roughness: 0.2, clearcoat: 0.6, clearcoatRoughness: 0.1 } );
	M.soil = std( { name: 'soil', color: 0x3b2a1e, roughness: 1 } );
	M.leaf = std( { name: 'leaf', color: 0x3f6b2f, roughness: 0.6, side: THREE.DoubleSide } );
	M.leafLight = std( { name: 'leafLight', color: 0x5d8a3c, roughness: 0.55, side: THREE.DoubleSide } );

	return M;

}

// Variant of a material that ignores RectAreaLights (which cannot cast shadows).
// Used for (a) the near-mirror glass and (b) everything inside the PC case, which
// the case walls shield from the window light in reality.
const areaFreeCache = new Map();
const patchNoArea = ( shader ) => {

	shader.fragmentShader = shader.fragmentShader.replace( '#include <lights_fragment_begin>', '#undef RE_Direct_RectArea\n#include <lights_fragment_begin>' );

};

export function areaLightFree( mat, envScale = 1 ) {

	const key = mat.uuid + '|' + envScale;
	if ( areaFreeCache.has( key ) ) return areaFreeCache.get( key );
	const m = mat.clone();
	m.name = mat.name + '_interior';
	if ( m.envMapIntensity !== undefined ) m.envMapIntensity = mat.envMapIntensity * envScale;
	m.onBeforeCompile = patchNoArea;
	m.customProgramCacheKey = () => 'no-rectarea';
	m.userData.base = mat;
	areaFreeCache.set( key, m );
	return m;

}

// Emissive material used for any light-emitting RGB element.
export function rgbMaterial( name, base = 0xf4f4f4, opts = {} ) {

	return new THREE.MeshStandardMaterial( {
		name, color: base, roughness: opts.roughness ?? 0.4, metalness: 0,
		emissive: new THREE.Color( 0x000000 ), emissiveIntensity: 1,
		transparent: !! opts.transparent, opacity: opts.opacity ?? 1,
		side: opts.side ?? THREE.FrontSide,
		toneMapped: true,
	} );

}
