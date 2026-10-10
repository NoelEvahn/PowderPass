// Camera modes: orbit, free-fly (pointer lock + WASD), inspection, cinematic.
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { PointerLockControls } from 'three/addons/controls/PointerLockControls.js';
import { ROOM } from '../scene/room.js';

const easeInOut = ( t ) => t < 0.5 ? 4 * t * t * t : 1 - Math.pow( - 2 * t + 2, 3 ) / 2;

export const HOME = {
	position: new THREE.Vector3( 0.95, 1.32, - 0.42 ),
	target: new THREE.Vector3( 0.12, 0.98, - 1.72 ),
};

export class CameraRig extends THREE.EventDispatcher {

	constructor( camera, dom ) {

		super();
		this.camera = camera;
		this.dom = dom;
		this.mode = 'orbit';
		camera.position.copy( HOME.position );

		const oc = new OrbitControls( camera, dom );
		oc.enableDamping = true;
		oc.dampingFactor = 0.08;
		oc.rotateSpeed = 0.6;
		oc.zoomSpeed = 0.9;
		oc.panSpeed = 0.8;
		oc.screenSpacePanning = true;
		oc.minDistance = 0.12;
		oc.maxDistance = 3.4;
		oc.maxPolarAngle = Math.PI * 0.92;
		oc.target.copy( HOME.target );
		oc.update();
		this.orbit = oc;

		this.pointer = new PointerLockControls( camera, dom );
		this.pointer.pointerSpeed = 0.7;
		this.keys = new Set();
		this.velocity = new THREE.Vector3();
		this.pointer.addEventListener( 'unlock', () => {

			if ( this.mode === 'free' ) this.dispatchEvent( { type: 'hint', text: 'Click to look around · WASD move · Shift fast · Space / Ctrl up/down · Esc release' } );

		} );
		this.pointer.addEventListener( 'lock', () => this.dispatchEvent( { type: 'hint', text: 'WASD move · Shift fast · Space up · Ctrl (or C) down · Esc release mouse' } ) );

		window.addEventListener( 'keydown', ( e ) => {

			if ( this.mode !== 'free' ) return;
			if ( e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement ) return;
			this.keys.add( e.code );
			if ( [ 'Space', 'ControlLeft', 'ControlRight' ].includes( e.code ) || ( e.ctrlKey && e.code === 'KeyW' ) ) e.preventDefault();

		} );
		window.addEventListener( 'keyup', ( e ) => this.keys.delete( e.code ) );
		window.addEventListener( 'blur', () => this.keys.clear() );
		dom.addEventListener( 'click', () => {

			if ( this.mode === 'free' && ! this.pointer.isLocked ) this.pointer.lock();

		} );

		this.tween = null;
		this.cinematic = null;
		this.saved = null;
		this._bounds = new THREE.Box3(
			new THREE.Vector3( ROOM.x0 + 0.12, 0.08, ROOM.z0 + 0.2 ),
			new THREE.Vector3( ROOM.x1 - 0.12, ROOM.h - 0.08, ROOM.z1 - 0.12 ),
		);

	}

	setMode( mode ) {

		if ( mode === this.mode && mode !== 'orbit' ) return;
		const prev = this.mode;
		if ( prev === 'free' ) {

			this.pointer.unlock();
			this.keys.clear();
			// re-derive an orbit target in front of the camera
			const dir = new THREE.Vector3();
			this.camera.getWorldDirection( dir );
			this.orbit.target.copy( this.camera.position ).addScaledVector( dir, 1.0 );

		}

		if ( prev === 'cinematic' ) this.cinematic = null;
		this.mode = mode;
		this.orbit.enabled = mode === 'orbit' || mode === 'inspect';
		if ( mode === 'free' ) {

			this.tween = null;
			this.dispatchEvent( { type: 'hint', text: 'Click the scene to capture the mouse · WASD move · Space / Ctrl up/down' } );

		} else if ( mode === 'cinematic' ) {

			this.startCinematic();
			this.dispatchEvent( { type: 'hint', text: 'Cinematic tour · choose another camera mode to exit' } );

		} else if ( mode === 'orbit' ) {

			this.dispatchEvent( { type: 'hint', text: 'Drag to orbit · Right-drag to pan · Scroll to zoom · Click a component to inspect' } );

		}

		this.dispatchEvent( { type: 'mode', mode } );

	}

	flyTo( position, target, duration = 1.4, onDone ) {

		this.tween = {
			t: 0, duration,
			p0: this.camera.position.clone(), p1: position.clone(),
			t0: this.orbit.target.clone(), t1: target.clone(),
			onDone,
		};

	}

	reset() {

		if ( this.mode !== 'orbit' ) this.setMode( 'orbit' );
		this.flyTo( HOME.position, HOME.target, 1.3 );

	}

	// Frame an object (Box3 in world space) from a pleasing direction
	inspect( box, preferredDir ) {

		if ( this.mode === 'free' ) this.setMode( 'orbit' );
		if ( this.mode === 'cinematic' ) this.setMode( 'orbit' );
		if ( this.mode !== 'inspect' ) this.saved = { position: this.camera.position.clone(), target: this.orbit.target.clone() };
		this.mode = 'inspect';
		this.orbit.enabled = true;
		const center = box.getCenter( new THREE.Vector3() );
		const radius = box.getSize( new THREE.Vector3() ).length() * 0.5;
		const fov = THREE.MathUtils.degToRad( this.camera.fov );
		const dist = Math.max( 0.14, radius / Math.sin( fov / 2 ) * 0.95 );
		const dir = ( preferredDir ? preferredDir.clone() : this.camera.position.clone().sub( center ) ).normalize();
		const pos = center.clone().addScaledVector( dir, dist );
		this.clampToRoom( pos );
		this.flyTo( pos, center, 1.5 );
		this.dispatchEvent( { type: 'mode', mode: 'inspect' } );

	}

	exitInspect() {

		if ( this.mode !== 'inspect' ) return;
		this.mode = 'orbit';
		const s = this.saved || HOME;
		this.flyTo( s.position, s.target, 1.3 );
		this.saved = null;
		this.dispatchEvent( { type: 'mode', mode: 'orbit' } );

	}

	clampToRoom( v ) {

		return this._bounds.clampPoint( v, v );

	}

	// ---- Cinematic path ---------------------------------------------------
	startCinematic() {

		const k = ( p, t, d ) => ( { p: new THREE.Vector3( ...p ), t: new THREE.Vector3( ...t ), d } );
		// Keyframes: wide establishing → PC glass corner → internals → RGB → monitor → wide
		const keys = [
			k( [ 1.35, 1.45, 0.25 ], [ 0.05, 0.95, - 1.7 ], 7 ),
			k( [ 0.95, 1.18, - 0.95 ], [ 0.45, 1.0, - 1.72 ], 6 ),
			k( [ 0.15, 1.06, - 1.42 ], [ 0.5, 1.03, - 1.75 ], 6 ),
			k( [ 0.24, 1.12, - 1.62 ], [ 0.48, 1.08, - 1.76 ], 6 ),  // RAM + pump close-up
			k( [ 0.26, 1.02, - 1.5 ], [ 0.5, 0.95, - 1.76 ], 6 ),    // GPU
			k( [ 0.36, 1.22, - 1.36 ], [ 0.47, 1.14, - 1.74 ], 6 ),   // radiator fans
			k( [ 0.0, 1.0, - 1.05 ], [ - 0.18, 1.0, - 1.8 ], 7 ),    // monitor
			k( [ - 0.7, 1.15, - 0.9 ], [ - 0.05, 0.85, - 1.6 ], 6 ), // keyboard / desk sweep
			k( [ - 1.2, 1.55, 0.35 ], [ 0.1, 0.95, - 1.6 ], 7 ),
		];
		const pos = new THREE.CatmullRomCurve3( keys.map( ( x ) => x.p ), true, 'centripetal' );
		const tgt = new THREE.CatmullRomCurve3( keys.map( ( x ) => x.t ), true, 'centripetal' );
		const total = keys.reduce( ( a, x ) => a + x.d, 0 );
		// First glide smoothly onto the path start
		this.cinematic = { pos, tgt, total, t: 0, ready: false };
		this.flyTo( pos.getPoint( 0 ), tgt.getPoint( 0 ), 2.0, () => { if ( this.cinematic ) this.cinematic.ready = true; } );

	}

	update( dt ) {

		const cam = this.camera;
		if ( this.tween ) {

			const tw = this.tween;
			tw.t = Math.min( 1, tw.t + dt / tw.duration );
			const e = easeInOut( tw.t );
			cam.position.lerpVectors( tw.p0, tw.p1, e );
			this.orbit.target.lerpVectors( tw.t0, tw.t1, e );
			cam.lookAt( this.orbit.target );
			if ( tw.t >= 1 ) {

				this.tween = null;
				tw.onDone?.();

			}

			if ( this.mode === 'orbit' || this.mode === 'inspect' ) this.orbit.update();
			return;

		}

		switch ( this.mode ) {

			case 'orbit':
			case 'inspect':
				this.orbit.update();
				this.clampToRoom( cam.position );
				break;
			case 'free': {

				const fast = this.keys.has( 'ShiftLeft' ) || this.keys.has( 'ShiftRight' );
				const speed = fast ? 2.4 : 0.9;
				const input = new THREE.Vector3(
					( this.keys.has( 'KeyD' ) ? 1 : 0 ) - ( this.keys.has( 'KeyA' ) ? 1 : 0 ),
					( this.keys.has( 'Space' ) ? 1 : 0 ) - ( this.keys.has( 'ControlLeft' ) || this.keys.has( 'ControlRight' ) || this.keys.has( 'KeyC' ) ? 1 : 0 ),
					( this.keys.has( 'KeyW' ) ? 1 : 0 ) - ( this.keys.has( 'KeyS' ) ? 1 : 0 ),
				);
				const fwd = new THREE.Vector3();
				cam.getWorldDirection( fwd );
				fwd.y = 0;
				fwd.normalize();
				const right = new THREE.Vector3().crossVectors( fwd, cam.up ).normalize();
				const desired = new THREE.Vector3().addScaledVector( right, input.x ).addScaledVector( fwd, input.z );
				desired.y = input.y;
				if ( desired.lengthSq() > 0 ) desired.normalize().multiplyScalar( speed );
				// smooth acceleration / deceleration
				this.velocity.lerp( desired, Math.min( 1, dt * 10 ) );
				cam.position.addScaledVector( this.velocity, dt );
				this.clampToRoom( cam.position );
				break;

			}

			case 'cinematic': {

				const c = this.cinematic;
				if ( ! c || ! c.ready ) break;
				c.t = ( c.t + dt / c.total ) % 1;
				c.pos.getPoint( c.t, cam.position );
				c.tgt.getPoint( c.t, this.orbit.target );
				cam.lookAt( this.orbit.target );
				break;

			}

		}

	}

}
