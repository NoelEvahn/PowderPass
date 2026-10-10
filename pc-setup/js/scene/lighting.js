// Room lighting rig + day / evening / night presets.
// Light sources are physically motivated: diffuse daylight through the curtain
// (rect area light + soft shadowed directional), a ceiling fixture (spot with
// shadows), the monitor (rect area light) and hardware RGB spill.
import * as THREE from 'three';
import { RectAreaLightUniformsLib } from 'three/addons/lights/RectAreaLightUniformsLib.js';
import { ROOM, WINDOW } from './room.js';
import { MONITOR } from './monitor.js';

export const PRESETS = {
	day: {
		exposure: 0.78,
		bloom: [ 5.5, 0.14 ], // [threshold (linear HDR), strength]: only very bright emitters glow in daylight
		hemi: [ 0xfff4e8, 0xb39a7e, 0.55 ],
		window: [ 0xfff3e2, 5.2 ],
		sun: [ 0xfff0dc, 2.3, new THREE.Vector3( 0.9, 3.3, - 4.2 ) ],
		ceiling: 0,
		curtainGlow: [ 0xfff2df, 0.42 ],
		sky: 0xe8f0f7,
	},
	evening: {
		exposure: 0.95,
		bloom: [ 3.2, 0.12 ],
		hemi: [ 0xffe2c4, 0x7a5c44, 0.22 ],
		window: [ 0xffb07a, 2.4 ],
		sun: [ 0xff9d5c, 0.65, new THREE.Vector3( 2.8, 1.7, - 4.2 ) ],
		ceiling: 22,
		curtainGlow: [ 0xffb88a, 0.26 ],
		sky: 0xf2a676,
	},
	night: {
		exposure: 1.35,
		bloom: [ 2.2, 0.12 ],
		hemi: [ 0x5b6a96, 0x1c1612, 0.07 ],
		window: [ 0x51638f, 0.35 ],
		sun: [ 0x8090c0, 0.12, new THREE.Vector3( - 1.5, 3.0, - 4.2 ) ],
		ceiling: 0,
		curtainGlow: [ 0x4a5a86, 0.035 ],
		sky: 0x1b2236,
	},
};

export function buildLighting( scene, { curtainMat, skyMat, lampMat } ) {

	RectAreaLightUniformsLib.init();
	const L = {};

	L.hemi = new THREE.HemisphereLight( 0xffffff, 0x886644, 0.5 );
	L.hemi.position.set( 0, ROOM.h, 0 );
	scene.add( L.hemi );

	// Curtain acts as a large diffuse emitter
	const ww = WINDOW.x1 - WINDOW.x0 + 0.6;
	L.window = new THREE.RectAreaLight( 0xffffff, 5, ww, WINDOW.y1 - WINDOW.y0 + 0.3 );
	L.window.position.set( 0, ( WINDOW.y0 + WINDOW.y1 ) / 2, ROOM.z0 + 0.13 );
	L.window.lookAt( 0, ( WINDOW.y0 + WINDOW.y1 ) / 2, 0 );
	scene.add( L.window );

	// Directional "daylight" key that gives grounded soft shadows
	L.sun = new THREE.DirectionalLight( 0xffffff, 1.5 );
	L.sun.castShadow = true;
	L.sun.shadow.mapSize.set( 2048, 2048 );
	L.sun.shadow.radius = 3;
	L.sun.shadow.bias = - 0.0002;
	L.sun.shadow.normalBias = 0.012;
	const sc = L.sun.shadow.camera;
	sc.left = - 1.05; sc.right = 1.05; sc.top = 0.95; sc.bottom = - 0.95; sc.near = 0.5; sc.far = 9; // tight around the desk for texel density
	L.sun.target.position.set( 0.1, 0.75, - 1.6 );
	scene.add( L.sun, L.sun.target );

	// Ceiling fixture (warm 2700K)
	L.ceiling = new THREE.SpotLight( 0xffd6a8, 0, 0, Math.PI / 2.6, 1, 2 );
	L.ceiling.position.set( 0, ROOM.h - 0.07, - 0.2 );
	L.ceiling.target.position.set( 0, 0, - 0.6 );
	L.ceiling.castShadow = true;
	L.ceiling.shadow.mapSize.set( 1024, 1024 );
	L.ceiling.shadow.radius = 4;
	L.ceiling.shadow.bias = - 0.0003;
	L.ceiling.shadow.normalBias = 0.015;
	L.ceiling.shadow.camera.near = 0.2;
	L.ceiling.shadow.camera.far = 4;
	scene.add( L.ceiling, L.ceiling.target );

	// Monitor emission onto desk/keyboard/wall
	L.monitor = new THREE.RectAreaLight( 0xe6c4d6, 3, MONITOR.screenW, MONITOR.screenH );
	L.monitor.position.set( MONITOR.x, MONITOR.cy + 0.006, MONITOR.zFront + 0.002 );
	L.monitor.lookAt( MONITOR.x, MONITOR.cy + 0.006, MONITOR.zFront + 1 );
	scene.add( L.monitor );

	const state = { preset: 'day', monitorOn: true };

	function apply( name, renderer ) {

		const p = PRESETS[ name ];
		state.preset = name;
		renderer.toneMappingExposure = p.exposure;
		L.hemi.color.set( p.hemi[ 0 ] ); L.hemi.groundColor.set( p.hemi[ 1 ] ); L.hemi.intensity = p.hemi[ 2 ];
		L.window.color.set( p.window[ 0 ] ); L.window.intensity = p.window[ 1 ];
		L.sun.color.set( p.sun[ 0 ] ); L.sun.intensity = p.sun[ 1 ];
		L.sun.position.copy( p.sun[ 2 ] );
		L.ceiling.intensity = p.ceiling;
		lampMat.emissiveIntensity = p.ceiling > 0 ? 2.2 : 0;
		curtainMat.emissive.set( p.curtainGlow[ 0 ] );
		curtainMat.emissiveIntensity = p.curtainGlow[ 1 ];
		skyMat.emissive.set( p.sky );

	}

	function setMonitor( on, brightness = 1 ) {

		state.monitorOn = on;
		L.monitor.intensity = on ? 3.2 * brightness : 0;

	}

	return { lights: L, apply, setMonitor, state };

}
