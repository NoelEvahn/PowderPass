// Optional progressive path tracing (three-gpu-pathtracer, WebGL2 fragment-shader
// BVH path tracer). This is genuine Monte-Carlo ray tracing that accumulates
// samples over time — it is not real-time and is meant for stills.
import * as THREE from 'three';

export class PathTraceMode {

	constructor( renderer, scene, camera ) {

		this.renderer = renderer;
		this.scene = scene;
		this.camera = camera;
		this.active = false;
		this.tracer = null;
		this.targetSamples = 400;
		this.onStatus = () => {};
		this._restore = [];

	}

	async start( { prepareScene, restoreScene } ) {

		if ( this.active ) return;
		this.onStatus( 'Loading path tracer…' );
		const { WebGLPathTracer } = await import( 'three-gpu-pathtracer' );
		this.onStatus( 'Building BVH for the scene (a few seconds)…' );
		await new Promise( ( r ) => setTimeout( r, 30 ) );
		prepareScene();
		this.restoreScene = restoreScene;
		if ( ! this.tracer ) {

			this.tracer = new WebGLPathTracer( this.renderer );
			this.tracer.filterGlossyFactor = 0.5;
			this.tracer.bounces = 6;
			this.tracer.transmissiveBounces = 8;
			this.tracer.minSamples = 1;
			this.tracer.renderDelay = 0;
			this.tracer.fadeDuration = 300;
			this.tracer.dynamicLowRes = true;
			this.tracer.lowResScale = 0.35;
			this.tracer.tiles.set( 2, 2 );

		}

		try {

			this.tracer.setScene( this.scene, this.camera );

		} catch ( e ) {

			restoreScene();
			throw e;

		}

		this.active = true;
		this.onStatus( 'Path tracing…' );

	}

	stop() {

		if ( ! this.active ) return;
		this.active = false;
		this.restoreScene?.();
		this.tracer.reset();

	}

	cameraChanged() {

		if ( this.active ) this.tracer.updateCamera();

	}

	render() {

		if ( ! this.active ) return;
		// Stop accumulating once converged; the canvas keeps the last presented frame.
		if ( this.tracer.samples >= this.targetSamples ) {

			this.onStatus( `Path traced · ${ this.targetSamples } samples (done)` );
			return false;

		}

		this.tracer.renderSample();
		this.onStatus( `Path tracing · ${ Math.floor( this.tracer.samples ) } / ${ this.targetSamples } samples` );
		return true;

	}

	get samples() {

		return this.tracer ? this.tracer.samples : 0;

	}

	dispose() {

		this.tracer?.dispose();
		this.tracer = null;

	}

}
