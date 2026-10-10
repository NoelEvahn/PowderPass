// Entry point: builds the scene, wires systems and runs the render loop.
import * as THREE from 'three';
import { createRenderer, Pipeline, ReflectionProbe, QUALITY } from './core/renderer.js';
import { buildMaterials, M } from './core/materials.js';
import { drawWindowsDesktop, currentTime, currentDate } from './core/textures.js';
import { buildRoom } from './scene/room.js';
import { mergeStatic } from './core/geo.js';
import { buildDesk, buildLaptop, buildHeadset, buildPlant, buildShelf, buildVan, buildDeskCables, DESK } from './scene/desk.js';
import { buildMonitor } from './scene/monitor.js';
import { buildKeyboard, drawKeyboardTFT } from './scene/keyboard.js';
import { buildMouse } from './scene/mouse.js';
import { buildLighting, PRESETS } from './scene/lighting.js';
import { buildPC } from './pc/pc.js';
import { drawPumpLCD } from './pc/cooler.js';
import { bpos, SOCKET, CASE } from './pc/layout.js';
import { RGBController } from './systems/rgb.js';
import { CameraRig } from './systems/camera.js';
import { PathTraceMode } from './systems/pathtrace.js';
import { UI } from './ui/ui.js';
import { SPECS } from './specs.js';

const PC_POS = new THREE.Vector3( 0.53, DESK.top, - 1.72 );

const loader = {
	fill: document.getElementById( 'loader-fill' ),
	step: document.getElementById( 'loader-step' ),
	async progress( frac, text ) {

		this.fill.style.width = `${ Math.round( frac * 100 ) }%`;
		this.step.textContent = text;
		// yield so the browser can paint the progress bar
		await new Promise( ( r ) => requestAnimationFrame( () => setTimeout( r, 0 ) ) );

	},
	done() {

		document.getElementById( 'loader' ).classList.add( 'done' );

	},
};

function fatal( msg ) {

	document.getElementById( 'loader' ).classList.add( 'done' );
	document.getElementById( 'fatal' ).hidden = false;
	document.getElementById( 'fatal-msg' ).textContent = msg;

}

async function main() {

	await loader.progress( 0.02, 'Initializing WebGL 2 renderer…' );
	let renderer;
	try {

		renderer = createRenderer( document.getElementById( 'viewport' ) );

	} catch ( e ) {

		fatal( e.message || String( e ) );
		return;

	}

	const scene = new THREE.Scene();
	const camera = new THREE.PerspectiveCamera( 45, window.innerWidth / window.innerHeight, 0.01, 30 );

	await loader.progress( 0.08, 'Generating materials and textures…' );
	buildMaterials();

	await loader.progress( 0.2, 'Building room…' );
	const room = buildRoom();
	scene.add( room.root );
	const desk = buildDesk();
	scene.add( desk );

	await loader.progress( 0.32, 'Modelling NZXT H6 Flow and components…' );
	const pc = buildPC();
	pc.root.position.copy( PC_POS );
	scene.add( pc.root );
	const van = buildVan();
	van.position.set( - 0.02, CASE.H, - 0.03 );
	van.rotation.y = 0.55;
	pc.parts.top.add( van ); // rides along with the top panel in the exploded view

	await loader.progress( 0.5, 'Modelling monitor and peripherals…' );
	const monitor = buildMonitor();
	scene.add( monitor.root );
	const keyboard = buildKeyboard();
	scene.add( keyboard.root );
	const mouse = buildMouse();
	scene.add( mouse.root );
	const laptop = buildLaptop();
	scene.add( laptop );
	const headset = buildHeadset();
	headset.position.set( - 0.6, DESK.top, - 1.86 );
	headset.rotation.y = 0.35;
	scene.add( headset );
	const plant = buildPlant();
	plant.position.set( 0.71, DESK.top, - 1.4 );
	plant.scale.setScalar( 0.82 );
	scene.add( plant );
	const shelf = buildShelf();
	scene.add( shelf );

	pc.root.updateMatrixWorld( true );
	const toWorld = ( v ) => v.clone().applyMatrix4( pc.root.matrixWorld );
	const cables = buildDeskCables( {
		pcBack: {
			dp: toWorld( new THREE.Vector3( - 0.03, 0.22, CASE.zB - 0.006 ) ),
			psu: toWorld( new THREE.Vector3( 0.068, 0.033, CASE.zB - 0.006 ) ),
		},
		monitorBack: monitor.back,
	} );
	scene.add( cables );

	// Tag peripherals for picking
	const tag = ( obj, id ) => obj.traverse( ( o ) => { o.userData.componentId ??= id; } );
	tag( monitor.root, 'monitor' );
	tag( keyboard.root, 'keyboard' );
	tag( mouse.root, 'mouse' );

	// Merge static detail per material to reduce draw calls
	for ( const g of [ room.root, desk, monitor.root, keyboard.root, mouse.root, laptop, headset, plant, shelf, cables ] ) mergeStatic( g );

	await loader.progress( 0.62, 'Setting up lighting…' );
	const lighting = buildLighting( scene, room );

	await loader.progress( 0.7, 'Compiling shaders…' );
	const pipeline = new Pipeline( renderer, scene, camera );
	const probe = new ReflectionProbe( renderer, scene );

	// Objects hidden from AO G-buffer
	scene.traverse( ( o ) => { if ( o.userData.glass || o.userData.noAO ) pipeline.noAO.push( o ); } );

	// --- RGB zones --------------------------------------------------------
	const rgb = new RGBController();
	rgb.addZone( 'ram', 'RAM (FURY Beast)', [ { material: pc.rgbMats.ram, strength: 1.7 } ], { light: pc.lights.ramLight, lightStrength: 0.16 } );
	{

		// per-fan materials so rainbow can phase across the three LQ360 fans
		const entries = [];
		pc.cooler.fans.forEach( ( f, i ) => {

			const blade = pc.rgbMats.aioFans.clone();
			const hub = pc.rgbMats.aioHub.clone();
			f.traverse( ( o ) => {

				if ( o.material === pc.rgbMats.aioFans ) o.material = blade;
				if ( o.material === pc.rgbMats.aioHub ) o.material = hub;

			} );
			entries.push( { material: blade, strength: 0.42, phase: i * 0.12 }, { material: hub, strength: 1.5, phase: i * 0.12 } );

		} );
		rgb.addZone( 'aioFans', 'LQ360 fans', entries, { light: pc.lights.fanLight, lightStrength: 0.2 } );

	}

	rgb.addZone( 'pump', 'LQ360 pump accent', [ { material: pc.rgbMats.pump, strength: 1.6 } ] );
	rgb.addZone( 'keyboard', 'Keyboard backlight', [ { material: keyboard.rgbMat, strength: 1.6 } ] );
	rgb.addZone( 'mouse', 'Mouse LIGHTSYNC', [ { material: mouse.rgbMat, strength: 3.0 } ] );

	// --- App state ----------------------------------------------------------
	const state = {
		power: true, exploded: false, glass: true, labels: false, lighting: 'day', quality: 'high',
		fps: false, fanAnim: true, pathTracing: false, exposureScale: 1, monitorBrightness: 1,
	};
	const power = { level: 1 };
	// minimal clock (THREE.Clock is deprecated in r18x)
	const clock = {
		elapsedTime: 0, last: performance.now(),
		getDelta() {

			const now = performance.now();
			const d = ( now - this.last ) / 1000;
			this.last = now;
			this.elapsedTime += d;
			return d;

		},
	};

	const rig = new CameraRig( camera, renderer.domElement );
	const pt = new PathTraceMode( renderer, scene, camera );
	const ptStatus = document.getElementById( 'pt-status' );

	const app = {
		state, camera: rig, rgb,
		setCameraMode( m ) {

			if ( state.pathTracing ) app.stopPathTrace();
			ui.hideInspect();
			rig.setMode( m );

		},
		resetCamera() {

			ui.hideInspect();
			rig.reset();

		},
		inspect( id ) {

			if ( state.pathTracing ) app.stopPathTrace();
			// parts hidden inside the build are revealed via the exploded view first;
			// the camera frames them once the parts have finished moving
			const hidden = [ 'ssd', 'cpu', 'psu' ];
			if ( hidden.includes( id ) && pc.explode.explode < 1 ) {

				app.setExploded( true );
				ui.hint( 'Opened the exploded view to reveal the ' + SPECS[ id ].kicker.toLowerCase() );
				pendingFocus = id;

			} else focusComponent( id );
			ui.showInspect( id );

		},
		exitInspect() {

			ui.hideInspect();
			rig.exitInspect();

		},
		setPower( v ) {

			state.power = v;
			rgb.powered = v;
			ui.hint( v ? 'Powering on' : 'Shutting down' );
			scheduleProbe( 1.2 );

		},
		setExploded( v ) {

			if ( state.pathTracing ) app.stopPathTrace(); // BVH would need a rebuild every frame
			if ( ! v ) pendingFocus = null;
			state.exploded = v;
			pc.explode.target = v ? 1 : 0;
			ui.refresh();

		},
		setGlass( v ) {

			if ( state.pathTracing ) app.stopPathTrace();
			state.glass = v;
			for ( const g of pc.glass ) g.visible = v;
			scheduleProbe( 0.05 );

		},
		setLabels( v ) {

			state.labels = v;
			labelsEl.style.display = v ? '' : 'none';

		},
		setLighting( name ) {

			state.lighting = name;
			lighting.apply( name, renderer );
			pipeline.setBloom( ...PRESETS[ name ].bloom );
			renderer.toneMappingExposure = PRESETS[ name ].exposure * state.exposureScale;
			scheduleProbe( 0.05 );
			if ( state.pathTracing ) {

				pt.tracer.updateLights();
				pt.tracer.updateMaterials();

			}

		},
		setExposureScale( v ) {

			state.exposureScale = v;
			renderer.toneMappingExposure = PRESETS[ state.lighting ].exposure * v;

		},
		setMonitorBrightness( v ) {

			state.monitorBrightness = v;

		},
		setRgbMode( m ) {

			rgb.setMode( m );

		},
		setQuality( name ) {

			state.quality = name;
			pipeline.setQuality( name, { shadowed: [ lighting.lights.sun, lighting.lights.ceiling ] } );
			scheduleProbe( 0.05 );

		},
		setFPS( v ) {

			state.fps = v;
			document.getElementById( 'fps' ).hidden = ! v;

		},
		async startPathTrace() {

			if ( state.pathTracing ) return;
			ui.hideInspect();
			if ( rig.mode !== 'orbit' ) rig.setMode( 'orbit' );
			ptStatus.hidden = false;
			ptStatus.replaceChildren( document.createTextNode( 'Preparing…' ) );
			try {

				await pt.start( { prepareScene: preparePT, restoreScene: restorePT } );
				state.pathTracing = true;
				labelsEl.style.visibility = 'hidden';

			} catch ( e ) {

				console.error( e );
				ptStatus.hidden = true;
				ui.hint( 'Path tracing failed to start: ' + ( e.message || e ), 6000 );

			}

		},
		stopPathTrace() {

			if ( ! state.pathTracing ) return;
			pt.stop();
			state.pathTracing = false;
			ptStatus.hidden = true;
			labelsEl.style.visibility = '';
			pipeline.resize();

		},
		async screenshot( scale ) {

			let blob;
			if ( state.pathTracing ) {

				blob = await new Promise( ( res ) => {

					pendingCapture = res;

				} );

			} else {

				ui.hint( 'Rendering screenshot…', 2000 );
				blob = await pipeline.screenshot( scale );

			}

			if ( ! blob ) return ui.hint( 'Screenshot failed' );
			const a = document.createElement( 'a' );
			a.href = URL.createObjectURL( blob );
			a.download = `pc-setup-${ new Date().toISOString().replace( /[:.]/g, '-' ) }.png`;
			a.click();
			setTimeout( () => URL.revokeObjectURL( a.href ), 4000 );
			ui.hint( 'Screenshot saved' );

		},
	};

	let pendingCapture = null;
	let pendingFocus = null;
	const ui = new UI( app );
	rig.addEventListener( 'hint', ( e ) => ui.hint( e.text ) );
	rig.addEventListener( 'mode', () => ui.refresh() );

	// --- Path tracing scene preparation ----------------------------------
	const ptSaved = {};
	function preparePT() {

		ptSaved.env = scene.environment;
		ptSaved.hemi = lighting.lights.hemi.intensity;
		scene.environment = null; // enclosed room: light comes from emitters + lights
		scene.background = new THREE.Color( 0x000000 );
		pc.cables.visible = pc.explode.explode < 0.02;

	}

	function restorePT() {

		scene.environment = ptSaved.env;
		scene.background = null;

	}

	ptStatus.addEventListener( 'click', ( e ) => {

		if ( e.target.dataset.act === 'exit' ) app.stopPathTrace();
		if ( e.target.dataset.act === 'save' ) app.screenshot( 1 );

	} );
	pt.onStatus = ( text ) => {

		if ( ptStatus._text === text ) return;
		ptStatus._text = text;
		ptStatus.innerHTML = '';
		ptStatus.append( text );
		const save = document.createElement( 'button' );
		save.className = 'btn'; save.style.width = 'auto'; save.textContent = 'Save'; save.dataset.act = 'save';
		const exit = document.createElement( 'button' );
		exit.className = 'btn'; exit.style.width = 'auto'; exit.textContent = 'Exit'; exit.dataset.act = 'exit';
		ptStatus.append( save, exit );

	};

	// --- Picking / inspection ---------------------------------------------
	const raycaster = new THREE.Raycaster();
	const ndc = new THREE.Vector2();
	const pickRoots = [ pc.root, monitor.root, keyboard.root, mouse.root ];
	let down = null;
	renderer.domElement.addEventListener( 'pointerdown', ( e ) => { down = { x: e.clientX, y: e.clientY, b: e.button }; } );
	renderer.domElement.addEventListener( 'pointerup', ( e ) => {

		if ( ! down || down.b !== 0 || rig.mode === 'free' || rig.mode === 'cinematic' || state.pathTracing ) return;
		if ( Math.hypot( e.clientX - down.x, e.clientY - down.y ) > 5 ) return;
		const id = pick( e.clientX, e.clientY );
		if ( id ) app.inspect( id );

	} );
	renderer.domElement.addEventListener( 'pointermove', ( e ) => {

		if ( rig.mode !== 'orbit' && rig.mode !== 'inspect' ) return renderer.domElement.style.cursor = '';
		if ( e.buttons ) return;
		hoverPending = [ e.clientX, e.clientY ];

	} );
	let hoverPending = null;

	function pick( x, y ) {

		ndc.set( x / window.innerWidth * 2 - 1, - ( y / window.innerHeight ) * 2 + 1 );
		raycaster.setFromCamera( ndc, camera );
		const hits = raycaster.intersectObjects( pickRoots, true );
		for ( const h of hits ) {

			if ( ! h.object.visible || h.object.userData.glass || h.object.userData.noPick ) continue;
			let o = h.object, visible = true;
			while ( o ) {

				if ( ! o.visible ) visible = false;
				o = o.parent;

			}

			if ( ! visible ) continue;
			return h.object.userData.componentId || null;

		}

		return null;

	}

	const focusDirs = {
		case: new THREE.Vector3( - 0.8, 0.45, 1 ), motherboard: new THREE.Vector3( - 1, 0.25, 0.3 ), cpu: new THREE.Vector3( - 1, 0.35, 0.25 ),
		gpu: new THREE.Vector3( - 1, - 0.05, 0.45 ), ram: new THREE.Vector3( - 1, 0.45, 0.35 ), cooler: new THREE.Vector3( - 1, 0.3, 0.6 ),
		ssd: new THREE.Vector3( - 1, 0.3, 0.3 ), psu: new THREE.Vector3( 0.6, 0.35, 1 ),
		monitor: new THREE.Vector3( 0.15, 0.1, 1 ), keyboard: new THREE.Vector3( 0, 0.9, 0.6 ), mouse: new THREE.Vector3( 0.3, 0.9, 0.5 ),
	};

	function componentBox( id ) {

		const box = new THREE.Box3();
		if ( id === 'cpu' ) {

			const c = bpos( SOCKET.u, SOCKET.v, 0.005 ).applyMatrix4( pc.root.matrixWorld );
			return box.setFromCenterAndSize( c, new THREE.Vector3( 0.07, 0.07, 0.07 ) );

		}

		const roots = { monitor: monitor.root, keyboard: keyboard.root, mouse: mouse.root };
		const root = roots[ id ] || pc.root;
		root.updateMatrixWorld( true );
		const tmp = new THREE.Box3();
		root.traverse( ( o ) => {

			if ( ! o.isMesh || o.userData.componentId !== id || ! o.visible ) return;
			if ( id === 'psu' && o.parent?.name === 'cable' ) return;
			o.geometry.computeBoundingBox();
			tmp.copy( o.geometry.boundingBox ).applyMatrix4( o.matrixWorld );
			box.union( tmp );

		} );
		if ( id === 'case' ) box.expandByScalar( 0.02 );
		return box;

	}

	function focusComponent( id ) {

		const box = componentBox( id );
		if ( box.isEmpty() ) return;
		rig.inspect( box, focusDirs[ id ] );

	}

	// --- Labels ----------------------------------------------------------
	const labelsEl = document.getElementById( 'labels' );
	labelsEl.style.display = 'none';
	const labelDefs = [
		[ 'case', pc.anchors.case, pc.root ], [ 'cooler', pc.anchors.cooler, pc.root ], [ 'gpu', pc.anchors.gpu, pc.root ],
		[ 'ram', pc.anchors.ram, pc.root ], [ 'cpu', pc.anchors.cpu, pc.root ], [ 'motherboard', pc.anchors.motherboard, pc.root ],
		[ 'ssd', pc.anchors.ssd, pc.root ], [ 'psu', pc.anchors.psu, pc.root ],
		[ 'monitor', new THREE.Vector3( 0, 0.2, 0 ), monitor.root, new THREE.Vector3( 0, 1.094, 0 ) ],
		[ 'keyboard', new THREE.Vector3( - 0.12, 0.04, 0 ), keyboard.root ],
		[ 'mouse', new THREE.Vector3( 0, 0.05, 0 ), mouse.root ],
	];
	const labels = labelDefs.map( ( [ id, anchor, parent, extra ] ) => {

		const div = document.createElement( 'div' );
		div.className = 'label3d';
		div.textContent = SPECS[ id ].name.replace( / \(.*\)/, '' );
		div.addEventListener( 'click', () => app.inspect( id ) );
		labelsEl.append( div );
		const local = extra ? anchor.clone().add( extra ) : anchor.clone();
		return { div, local, parent, world: new THREE.Vector3() };

	} );
	// the exploded view moves parts, so PC labels follow their parts
	const labelFollow = { gpu: pc.gpu, ram: pc.ram[ 0 ], cooler: pc.cooler.radiator, ssd: pc.mb.ssd, psu: pc.psu, cpu: pc.cooler.pump };
	labels.forEach( ( l, i ) => {

		const id = labelDefs[ i ][ 0 ];
		if ( labelFollow[ id ] ) {

			l.follow = labelFollow[ id ];
			l.followBase = labelFollow[ id ].position.clone();
			if ( id === 'cpu' ) l.followScale = 0; // CPU stays in its socket

		}

	} );
	const _proj = new THREE.Vector3();
	const placed = [];
	function updateLabels() {

		if ( ! state.labels || state.pathTracing ) return;
		const w = window.innerWidth, h = window.innerHeight;
		placed.length = 0;
		for ( const l of labels ) {

			l.world.copy( l.local );
			if ( l.follow && l.followScale !== 0 ) l.world.add( _proj.copy( l.follow.position ).sub( l.followBase ) );
			l.world.applyMatrix4( l.parent.matrixWorld );
			_proj.copy( l.world ).project( camera );
			l.vis = _proj.z < 1 && Math.abs( _proj.x ) < 1.05 && Math.abs( _proj.y ) < 1.05;
			l.sx = ( _proj.x * 0.5 + 0.5 ) * w;
			l.sy = ( - _proj.y * 0.5 + 0.5 ) * h - 10;
			l.w = l.w || l.div.offsetWidth;

		}

		// greedy screen-space de-overlap: stack colliding labels upward
		const order = labels.filter( ( l ) => l.vis ).sort( ( a, b ) => b.sy - a.sy );
		for ( const l of order ) {

			let y = l.sy;
			for ( let guard = 0; guard < 12; guard ++ ) {

				const hit = placed.find( ( p ) => Math.abs( p.x - l.sx ) < ( p.w + l.w ) / 2 + 6 && Math.abs( p.y - y ) < 24 );
				if ( ! hit ) break;
				y = hit.y - 24;

			}

			placed.push( { x: l.sx, y, w: l.w } );
			l.div.style.transform = `translate(${ l.sx }px, ${ y }px) translate(-50%, -100%)`;

		}

		for ( const l of labels ) {

			l.div.style.opacity = l.vis ? '1' : '0';
			l.div.style.pointerEvents = l.vis ? 'auto' : 'none';

		}

	}

	// --- Screens -----------------------------------------------------------
	const sim = { temp: 41, load: 6, nextLCD: 0, nextTFT: 0, nextClock: 0, lastTime: currentTime() };
	function updateScreens( t, dt ) {

		const on = power.level > 0.5;
		// simulated idle CPU telemetry: smooth noise + occasional short bursts
		const burst = Math.max( 0, Math.sin( t * 0.21 ) * Math.sin( t * 0.53 + 1 ) ) * 14;
		const targetTemp = on ? 40 + Math.sin( t * 0.31 ) * 1.6 + Math.sin( t * 1.7 ) * 0.5 + burst : 25;
		sim.temp += ( targetTemp - sim.temp ) * Math.min( 1, dt * 0.8 );
		sim.load = on ? 4 + burst * 2.4 + Math.abs( Math.sin( t * 2.3 ) ) * 3 : 0;
		if ( t > sim.nextLCD ) {

			sim.nextLCD = t + 0.5;
			drawPumpLCD( pc.cooler.lcd.canvas, sim.temp, sim.load, on );
			pc.cooler.lcd.texture.needsUpdate = true;

		}

		if ( t > sim.nextTFT ) {

			sim.nextTFT = t + 0.12;
			drawKeyboardTFT( keyboard.screen.canvas, on, t );
			keyboard.screen.texture.needsUpdate = true;

		}

		if ( t > sim.nextClock ) {

			sim.nextClock = t + 15;
			const now = currentTime();
			if ( now !== sim.lastTime ) {

				sim.lastTime = now;
				drawWindowsDesktop( monitor.desktop.userData.canvas, now, currentDate() );
				monitor.desktop.needsUpdate = true;

			}

		}

		const b = power.level * state.monitorBrightness;
		monitor.screenMat.emissiveIntensity = b * 1.15;
		monitor.ledMat.emissiveIntensity = on ? 0.6 : 0.0;
		monitor.ledMat.emissive.set( on ? 0xffffff : 0xff8a2a );
		pc.cooler.lcd.material.emissiveIntensity = 1.15 * power.level;
		keyboard.screen.material.emissiveIntensity = power.level;
		lighting.setMonitor( b > 0.01, b );

	}

	// --- Reflection probe scheduling ---------------------------------------
	let probeAt = 0.2;
	function scheduleProbe( delay ) {

		probeAt = clock.elapsedTime + delay;

	}

	function updateProbe() {

		const size = QUALITY[ state.quality ].probe;
		// hide the glass + small dynamic items so the probe captures the room
		probe.update( size, [ ...pc.glass ] );

	}

	// --- Init visual state --------------------------------------------------
	app.setLighting( 'day' );
	app.setQuality( 'high' );
	window.addEventListener( 'resize', () => {

		pipeline.resize();
		if ( state.pathTracing ) pt.tracer?.reset();

	} );
	renderer.domElement.addEventListener( 'webglcontextlost', ( e ) => {

		e.preventDefault();
		fatal( 'The WebGL context was lost (GPU reset or driver issue). Reload the page to continue.' );

	} );

	await loader.progress( 0.85, 'Compiling shaders…' );
	// Pre-compile all materials to avoid hitches on first view
	try {

		await renderer.compileAsync( scene, camera );

	} catch ( e ) {

		console.warn( 'compileAsync failed, falling back to lazy compile', e );

	}

	await loader.progress( 0.95, 'Capturing reflection probe…' );
	updateScreens( 0, 0.016 );
	updateProbe();
	probeAt = Infinity;
	await loader.progress( 1, 'Ready' );
	loader.done();
	ui.hint( 'Drag to orbit · Right-drag to pan · Scroll to zoom · Click a component to inspect', 6000 );

	// --- Loop ------------------------------------------------------------------
	clock.getDelta();
	let frames = 0, fpsTime = 0;
	const lastCam = new THREE.Matrix4();
	const fanSpeeds = new Map( pc.fans.map( ( f ) => [ f, 0 ] ) );
	pc.gpu.userData.fans.forEach( ( f ) => { f.userData.speed = 0; } ); // 0 dB fan stop at idle

	const manual = new URLSearchParams( location.search ).has( 'manual' ); // automated testing: step frames explicitly
	const frame = ( fixedDt ) => {

		let dt;
		if ( fixedDt !== undefined ) {

			dt = fixedDt;
			clock.elapsedTime += fixedDt;

		} else dt = Math.min( 0.05, clock.getDelta() );
		const t = clock.elapsedTime;

		power.level += ( ( state.power ? 1 : 0 ) - power.level ) * Math.min( 1, dt * 2.2 );
		rig.update( dt );
		pc.updateExplode( dt );
		if ( pendingFocus && pc.explode.explode === 1 ) {

			focusComponent( pendingFocus );
			pendingFocus = null;

		}

		// fans: spin up / down smoothly with power
		for ( const f of pc.fans ) {

			const target = state.power && state.fanAnim ? f.userData.speed * 9 : 0;
			const cur = fanSpeeds.get( f ) + ( target - fanSpeeds.get( f ) ) * Math.min( 1, dt * 1.2 );
			fanSpeeds.set( f, cur );
			f.userData.rotor.rotation.z -= cur * dt;

		}

		rgb.update( dt );
		updateScreens( t, dt );

		if ( t > probeAt ) {

			probeAt = Infinity;
			updateProbe();

		}

		if ( hoverPending ) {

			const [ x, y ] = hoverPending;
			hoverPending = null;
			renderer.domElement.style.cursor = pick( x, y ) ? 'pointer' : '';

		}

		if ( state.pathTracing ) {

			if ( ! lastCam.equals( camera.matrixWorld ) ) {

				lastCam.copy( camera.matrixWorld );
				pt.cameraChanged();

			}

			pt.render();
			if ( pendingCapture ) {

				// present a fresh frame and snapshot it in the same task
				const res = pendingCapture;
				pendingCapture = null;
				pt.tracer.renderSample();
				renderer.domElement.toBlob( res, 'image/png' );

			}

		} else {

			pipeline.render( dt );

		}

		updateLabels();

		frames ++;
		fpsTime += dt;
		if ( fpsTime >= 0.5 ) {

			if ( state.fps ) ui.setFPS( `${ Math.round( frames / fpsTime ) } fps · ${ state.quality }` );
			frames = 0;
			fpsTime = 0;

		}

	};

	if ( ! manual ) renderer.setAnimationLoop( () => frame() );

	// expose for debugging / automated checks
	window.__app = {
		app, scene, renderer, camera, pc, rig, pipeline, state, M, lighting,
		step( n = 1, dt = 1 / 60 ) { for ( let i = 0; i < n; i ++ ) frame( dt ); },
	};

}

main().catch( ( e ) => {

	console.error( e );
	fatal( 'Initialization error: ' + ( e.message || e ) );

} );
