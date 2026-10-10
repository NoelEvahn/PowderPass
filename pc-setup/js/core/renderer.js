// Renderer, post-processing chain, quality presets and reflection probe.
import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';
import { FXAAShader } from 'three/addons/shaders/FXAAShader.js';
import { setMaxAnisotropy } from './textures.js';

export const QUALITY = {
	performance: { pixelRatio: 1.0, msaa: 0, fxaa: true, shadow: 1024, ceilingShadow: 512, ao: false, aoScale: 0.5, bloom: true, probe: 128, transmission: 1.0, anisotropy: 4 },
	high: { pixelRatio: 1.5, msaa: 4, fxaa: false, shadow: 2048, ceilingShadow: 1024, ao: true, aoScale: 0.5, bloom: true, probe: 256, transmission: 1.0, anisotropy: 8 },
	ultra: { pixelRatio: 2.0, msaa: 8, fxaa: false, shadow: 4096, ceilingShadow: 2048, ao: true, aoScale: 1.0, bloom: true, probe: 512, transmission: 1.0, anisotropy: 16 },
};

export function createRenderer( container ) {

	// Explicit WebGL2 check for a friendly error message
	const test = document.createElement( 'canvas' );
	const gl2 = test.getContext( 'webgl2' );
	if ( ! gl2 ) throw new Error( 'WebGL 2 is not available in this browser.' );
	gl2.getExtension( 'WEBGL_lose_context' )?.loseContext();

	const renderer = new THREE.WebGLRenderer( {
		antialias: false, powerPreference: 'high-performance', stencil: false, alpha: false,
		preserveDrawingBuffer: false,
	} );
	renderer.outputColorSpace = THREE.SRGBColorSpace;
	renderer.toneMapping = THREE.ACESFilmicToneMapping;
	renderer.toneMappingExposure = 1.0;
	renderer.shadowMap.enabled = true;
	renderer.shadowMap.type = THREE.PCFShadowMap;
	renderer.setClearColor( 0x101012 );
	container.appendChild( renderer.domElement );
	setMaxAnisotropy( Math.min( 16, renderer.capabilities.getMaxAnisotropy() ) );
	return renderer;

}

export class Pipeline {

	constructor( renderer, scene, camera ) {

		this.renderer = renderer;
		this.scene = scene;
		this.camera = camera;
		this.quality = null;
		this.noAO = [];
		this.size = new THREE.Vector2();
		this.bloomParams = [ 5, 0.15 ];
		this.build( QUALITY.high );

	}

	build( q ) {

		if ( this.composer ) {

			for ( const pass of this.composer.passes ) pass.dispose?.();
			this.composer.dispose();

		}

		const r = this.renderer;
		const w = window.innerWidth, h = window.innerHeight;
		const pr = Math.min( window.devicePixelRatio || 1, q.pixelRatio );
		r.setPixelRatio( pr );
		r.setSize( w, h, false );
		r.domElement.style.width = '100%';
		r.domElement.style.height = '100%';

		const samples = Math.min( q.msaa, r.capabilities.maxSamples || 0 );
		const rt = new THREE.WebGLRenderTarget( w * pr, h * pr, { type: THREE.HalfFloatType, samples } );
		this.composer = new EffectComposer( r, rt );
		this.composer.setPixelRatio( pr );
		this.composer.setSize( w, h );

		this.renderPass = new RenderPass( this.scene, this.camera );
		this.composer.addPass( this.renderPass );

		this.gtao = null;
		if ( q.ao ) {

			const aw = Math.round( w * pr * q.aoScale ), ah = Math.round( h * pr * q.aoScale );
			this.gtao = new GTAOPass( this.scene, this.camera, aw, ah );
			this.gtao.output = GTAOPass.OUTPUT.Default;
			this.gtao.blendIntensity = 0.9;
			this.gtao.updateGtaoMaterial( { radius: 0.12, distanceExponent: 1.4, thickness: 1.0, scale: 1.0, samples: 16, distanceFallOff: 1.0, screenSpaceRadius: false } );
			this.gtao.updatePdMaterial( { lumaPhi: 10, depthPhi: 2, normalPhi: 3, radius: 6, rings: 2, samples: 16 } );
			// Hide glass / emissive screens from the AO G-buffer so occlusion is computed
			// on what is behind the glass rather than on the glass itself.
			const noAO = this.noAO;
			const cache = [];
			this.gtao._overrideVisibility = () => {

				for ( const o of noAO ) if ( o.visible ) { o.visible = false; cache.push( o ); }

			};

			this.gtao._restoreVisibility = () => {

				for ( const o of cache ) o.visible = true;
				cache.length = 0;

			};

			// keep GTAO's own resolution decoupled from the composer
			const setSize = this.gtao.setSize.bind( this.gtao );
			this.gtao.setSize = ( W, H ) => setSize( Math.round( W * q.aoScale ), Math.round( H * q.aoScale ) );
			this.composer.addPass( this.gtao );

		}

		this.bloom = null;
		if ( q.bloom ) {

			const div = q === QUALITY.performance ? 2 : 1;
			this.bloom = new UnrealBloomPass( new THREE.Vector2( w / div, h / div ), this.bloomParams[ 1 ], 0.35, this.bloomParams[ 0 ] );
			this.composer.addPass( this.bloom );

		}

		this.composer.addPass( new OutputPass() );

		this.fxaa = null;
		if ( q.fxaa ) {

			this.fxaa = new ShaderPass( FXAAShader );
			this.fxaa.material.uniforms.resolution.value.set( 1 / ( w * pr ), 1 / ( h * pr ) );
			this.composer.addPass( this.fxaa );

		}

		r.transmissionResolutionScale = q.transmission;
		this.quality = q;
		this.pixelRatio = pr;

	}

	setQuality( name, lights ) {

		const q = QUALITY[ name ];
		this.build( q );
		for ( const l of lights.shadowed ) {

			const size = l.isSpotLight ? q.ceilingShadow : q.shadow;
			if ( l.shadow.mapSize.x !== size ) {

				l.shadow.mapSize.set( size, size );
				l.shadow.map?.dispose();
				l.shadow.map = null;

			}

		}

		this.scene.traverse( ( o ) => {

			if ( ! o.material ) return;
			for ( const m of Array.isArray( o.material ) ? o.material : [ o.material ] ) {

				if ( m.alphaToCoverage !== undefined && m.alphaMap ) {

					const a2c = q.msaa > 0;
					if ( m.alphaToCoverage !== a2c ) { m.alphaToCoverage = a2c; m.needsUpdate = true; }

				}

			}

		} );

	}

	setBloom( threshold, strength ) {

		this.bloomParams = [ threshold, strength ];
		if ( this.bloom ) {

			this.bloom.threshold = threshold;
			this.bloom.strength = strength;

		}

	}

	resize() {

		const w = window.innerWidth, h = window.innerHeight;
		this.camera.aspect = w / h;
		this.camera.updateProjectionMatrix();
		this.renderer.setSize( w, h, false );
		this.composer.setPixelRatio( this.pixelRatio );
		this.composer.setSize( w, h );
		if ( this.fxaa ) this.fxaa.material.uniforms.resolution.value.set( 1 / ( w * this.pixelRatio ), 1 / ( h * this.pixelRatio ) );

	}

	render( dt ) {

		this.composer.render( dt );

	}

	// High-resolution still: render at `scale` x the current size into an offscreen
	// composer, read back pixels and return a PNG blob.
	async screenshot( scale = 2 ) {

		const r = this.renderer;
		const w = window.innerWidth, h = window.innerHeight;
		const oldPr = r.getPixelRatio();
		const pr = Math.min( 4, oldPr * scale );
		const maxSize = r.capabilities.maxTextureSize;
		const finalPr = Math.min( pr, maxSize / w, maxSize / h );
		r.setPixelRatio( finalPr );
		r.setSize( w, h, false );
		this.composer.setPixelRatio( finalPr );
		this.composer.setSize( w, h );
		if ( this.fxaa ) this.fxaa.material.uniforms.resolution.value.set( 1 / ( w * finalPr ), 1 / ( h * finalPr ) );
		this.composer.render( 0 );
		const blob = await new Promise( ( res ) => r.domElement.toBlob( res, 'image/png' ) );
		r.setPixelRatio( oldPr );
		this.pixelRatio = oldPr;
		this.resize();
		return blob;

	}

}

// Reflection probe: renders the actual room into a prefiltered cube map (PMREM)
// from a point near the desk, so metals and glass reflect the real scene.
export class ReflectionProbe {

	constructor( renderer, scene ) {

		this.renderer = renderer;
		this.scene = scene;
		this.pmrem = new THREE.PMREMGenerator( renderer );
		this.position = new THREE.Vector3( 0.15, 1.05, - 1.25 );
		this.rt = null;

	}

	update( size = 256, hide = [] ) {

		const vis = hide.map( ( o ) => o.visible );
		hide.forEach( ( o ) => { o.visible = false; } );
		const prevEnv = this.scene.environment;
		this.scene.environment = null;
		const rt = this.pmrem.fromScene( this.scene, 0.0, 0.05, 20, { size, position: this.position } );
		this.scene.environment = rt.texture;
		this.scene.environmentIntensity = 1.0;
		this.rt?.dispose();
		this.rt = rt;
		hide.forEach( ( o, i ) => { o.visible = vis[ i ]; } );
		return prevEnv;

	}

}
