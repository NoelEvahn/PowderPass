// Minimal toolbar + popover UI. All controls call into the app API.
import { SPECS, PICKABLE_ORDER } from '../specs.js';
import { MODES } from '../systems/rgb.js';

const $ = ( s ) => document.querySelector( s );

function el( tag, attrs = {}, ...children ) {

	const e = document.createElement( tag );
	for ( const [ k, v ] of Object.entries( attrs ) ) {

		if ( k === 'class' ) e.className = v;
		else if ( k.startsWith( 'on' ) ) e.addEventListener( k.slice( 2 ), v );
		else if ( v === true ) e.setAttribute( k, '' );
		else if ( v !== false && v != null ) e.setAttribute( k, v );

	}

	for ( const c of children.flat() ) if ( c != null ) e.append( c.nodeType ? c : document.createTextNode( c ) );
	return e;

}

function seg( options, current, onPick ) {

	return el( 'div', { class: 'seg' }, options.map( ( [ value, label ] ) => el( 'button', {
		class: value === current ? 'on' : '',
		onclick: () => onPick( value ),
	}, label ) ) );

}

function toggle( label, checked, onChange, sub ) {

	const input = el( 'input', { type: 'checkbox' } );
	input.checked = checked;
	input.addEventListener( 'change', () => onChange( input.checked ) );
	return el( 'div', { class: 'row' },
		el( 'div', {}, el( 'label', {}, label ), sub ? el( 'div', { class: 'sub' }, sub ) : null ),
		el( 'label', { class: 'switch' }, input, el( 'span' ) ),
	);

}

function section( title, ...children ) {

	return el( 'div', { class: 'pv-section' }, title ? el( 'div', { class: 'pv-title' }, title ) : null, ...children );

}

export class UI {

	constructor( app ) {

		this.app = app;
		this.pop = $( '#popover' );
		this.openMenu = null;
		this.hintTimer = null;

		document.querySelectorAll( '#toolbar [data-menu]' ).forEach( ( b ) => {

			b.addEventListener( 'click', ( e ) => {

				e.stopPropagation();
				this.toggleMenu( b.dataset.menu, b );

			} );

		} );
		$( '#toolbar [data-action="fullscreen"]' ).addEventListener( 'click', () => this.fullscreen() );
		$( '#toolbar [data-action="hideui"]' ).addEventListener( 'click', () => this.setHidden( true ) );
		$( '#ip-close' ).addEventListener( 'click', () => app.exitInspect() );
		$( '#capture-close' ).addEventListener( 'click', () => { $( '#capture' ).hidden = true; } );
		document.addEventListener( 'pointerdown', ( e ) => {

			if ( this.openMenu && ! this.pop.contains( e.target ) && ! e.target.closest( '#toolbar' ) ) this.closeMenu();

		} );
		window.addEventListener( 'keydown', ( e ) => {

			if ( e.target instanceof HTMLInputElement ) return;
			if ( e.code === 'KeyH' ) this.setHidden( ! this.hidden );
			if ( e.code === 'KeyF' && app.camera.mode !== 'free' ) this.fullscreen();
			if ( e.code === 'Escape' ) {

				if ( this.openMenu ) this.closeMenu();
				else if ( app.camera.mode === 'inspect' ) app.exitInspect();

			}

		} );

	}

	toggleMenu( name, btn ) {

		if ( this.openMenu === name ) return this.closeMenu();
		this.openMenu = name;
		document.querySelectorAll( '#toolbar button' ).forEach( ( b ) => b.classList.toggle( 'active', b === btn ) );
		this.renderMenu();
		this.pop.hidden = false;
		// align popover with its button (clamped to viewport)
		const r = btn.getBoundingClientRect();
		const w = this.pop.offsetWidth;
		const cx = Math.min( window.innerWidth - w / 2 - 12, Math.max( w / 2 + 12, r.left + r.width / 2 ) );
		this.pop.style.left = `${ cx }px`;

	}

	closeMenu() {

		this.openMenu = null;
		this.pop.hidden = true;
		document.querySelectorAll( '#toolbar button' ).forEach( ( b ) => b.classList.remove( 'active' ) );

	}

	refresh() {

		if ( this.openMenu ) this.renderMenu();

	}

	renderMenu() {

		const a = this.app, s = a.state;
		const p = this.pop;
		p.replaceChildren();
		const name = this.openMenu;

		if ( name === 'camera' ) {

			p.append( section( 'Camera mode',
				seg( [ [ 'orbit', 'Orbit' ], [ 'free', 'Free' ], [ 'cinematic', 'Cinematic' ] ], a.camera.mode === 'inspect' ? 'orbit' : a.camera.mode, ( m ) => { a.setCameraMode( m ); this.refresh(); } ),
				el( 'div', { class: 'hint' }, {
					orbit: 'Left-drag rotate · right-drag pan · scroll zoom · click a part to inspect',
					free: 'Click scene to capture mouse · WASD · Shift fast · Space up · Ctrl/C down · Esc release',
					cinematic: 'Automated tour with close-ups of the internals',
					inspect: 'Inspecting — drag to rotate around the part, Esc to return',
				}[ a.camera.mode ] ),
			) );
			p.append( section( null, el( 'button', { class: 'btn primary', onclick: () => { a.resetCamera(); this.closeMenu(); } }, 'Reset camera' ) ) );

		} else if ( name === 'components' ) {

			p.append( section( 'Inspect',
				el( 'div', { class: 'comp-list' }, PICKABLE_ORDER.map( ( id ) => el( 'button', { class: 'btn', onclick: () => { a.inspect( id ); this.closeMenu(); } }, SPECS[ id ].kicker ) ) ),
			) );
			p.append( section( 'PC',
				toggle( 'Power', s.power, ( v ) => a.setPower( v ) ),
				toggle( 'Exploded view', s.exploded, ( v ) => a.setExploded( v ) ),
				toggle( 'Tempered glass', s.glass, ( v ) => a.setGlass( v ), 'Hide panels for a clear view' ),
				toggle( 'Component labels', s.labels, ( v ) => a.setLabels( v ) ),
			) );

		} else if ( name === 'lighting' ) {

			p.append( section( 'Room lighting',
				seg( [ [ 'day', 'Day' ], [ 'evening', 'Evening' ], [ 'night', 'Night' ] ], s.lighting, ( v ) => { a.setLighting( v ); this.refresh(); } ),
			) );
			const range = el( 'input', { type: 'range', min: '0.4', max: '2', step: '0.01' } );
			range.value = String( s.exposureScale );
			range.addEventListener( 'input', () => a.setExposureScale( parseFloat( range.value ) ) );
			p.append( section( 'Exposure', range ) );
			const mb = el( 'input', { type: 'range', min: '0.2', max: '1.6', step: '0.01' } );
			mb.value = String( s.monitorBrightness );
			mb.addEventListener( 'input', () => a.setMonitorBrightness( parseFloat( mb.value ) ) );
			p.append( section( 'Monitor brightness', mb ) );

		} else if ( name === 'rgb' ) {

			p.append( section( 'Effect (all zones)',
				el( 'div', { class: 'comp-list' }, Object.entries( MODES ).map( ( [ k, label ] ) => el( 'button', {
					class: 'btn' + ( a.rgb.mode === k ? ' primary' : '' ),
					onclick: () => { a.setRgbMode( k ); this.refresh(); },
				}, label ) ) ),
			) );
			const zoneRows = [];
			for ( const z of a.rgb.zones.values() ) {

				const sel = el( 'select', { style: 'background:#222;color:#ddd;border:1px solid rgba(255,255,255,.1);border-radius:5px;font:inherit;font-size:11px;padding:2px' },
					[ [ 'sync', 'Sync' ], ...Object.entries( MODES ) ].map( ( [ k, l ] ) => {

						const o = el( 'option', { value: k }, l );
						if ( z.mode === k ) o.selected = true;
						return o;

					} ) );
				sel.addEventListener( 'change', () => { z.mode = sel.value; } );
				const input = el( 'input', { type: 'checkbox' } );
				input.checked = z.enabled;
				input.addEventListener( 'change', () => { z.enabled = input.checked; } );
				zoneRows.push( el( 'div', { class: 'row' },
					el( 'label', {}, z.label ),
					el( 'div', { style: 'display:flex;gap:8px;align-items:center' }, sel, el( 'label', { class: 'switch' }, input, el( 'span' ) ) ),
				) );

			}

			p.append( section( 'Zones', ...zoneRows ) );
			const color = el( 'input', { type: 'color', value: '#' + a.rgb.breathColor.getHexString() } );
			color.addEventListener( 'input', () => a.rgb.breathColor.set( color.value ) );
			p.append( section( null, el( 'div', { class: 'row' }, el( 'label', {}, 'Breathing colour' ), color ) ) );
			p.append( el( 'div', { class: 'hint' }, 'Only parts with real LEDs are lit: RAM, LQ360 fans + pump, keyboard, mouse.' ) );

		} else if ( name === 'graphics' ) {

			p.append( section( 'Quality preset',
				seg( [ [ 'performance', 'Performance' ], [ 'high', 'High' ], [ 'ultra', 'Ultra' ] ], s.quality, ( v ) => { a.setQuality( v ); this.refresh(); } ),
				el( 'div', { class: 'hint' }, {
					performance: '1× resolution, FXAA, 1K shadows, no AO',
					high: 'MSAA 4×, 2K soft shadows, GTAO, bloom',
					ultra: 'MSAA 8×, 4K shadows, full-res GTAO, 2× resolution',
				}[ s.quality ] ),
			) );
			p.append( section( null,
				toggle( 'FPS counter', s.fps, ( v ) => a.setFPS( v ) ),
				toggle( 'Fan animation', s.fanAnim, ( v ) => { s.fanAnim = v; } ),
			) );
			p.append( section( 'Path tracing',
				el( 'button', { class: 'btn primary', onclick: () => { this.closeMenu(); s.pathTracing ? a.stopPathTrace() : a.startPathTrace(); } }, s.pathTracing ? 'Exit path-traced mode' : 'Start progressive path tracing' ),
				el( 'div', { class: 'hint' }, 'GPU Monte-Carlo path tracer (three-gpu-pathtracer). Accumulates samples while the camera is still; not real-time.' ),
			) );

		} else if ( name === 'screenshot' ) {

			p.append( section( 'High-resolution capture',
				el( 'button', { class: 'btn', onclick: () => { this.closeMenu(); a.screenshot( 1 ); } }, 'Current resolution' ),
				el( 'button', { class: 'btn', onclick: () => { this.closeMenu(); a.screenshot( 2 ); } }, '2× resolution' ),
				el( 'button', { class: 'btn', onclick: () => { this.closeMenu(); a.screenshot( 3 ); } }, '3× resolution' ),
			) );
			p.append( el( 'div', { class: 'hint' }, 'In path-traced mode the capture saves the accumulated image.' ) );

		}

	}

	showInspect( id ) {

		const spec = SPECS[ id ];
		if ( ! spec ) return;
		$( '#ip-kicker' ).textContent = spec.kicker;
		$( '#ip-title' ).textContent = spec.name;
		const dl = $( '#ip-specs' );
		dl.replaceChildren( ...spec.specs.flatMap( ( [ k, v ] ) => [ el( 'dt', {}, k ), el( 'dd', {}, v ) ] ) );
		$( '#ip-note' ).textContent = spec.note || '';
		$( '#inspect-panel' ).hidden = false;

	}

	hideInspect() {

		$( '#inspect-panel' ).hidden = true;

	}

	showCapture( url ) {

		const box = $( '#capture' );
		const img = $( '#capture-img' );
		if ( img.dataset.url ) URL.revokeObjectURL( img.dataset.url );
		img.src = url;
		img.dataset.url = url;
		box.hidden = false;

	}

	hint( text, ms = 4000 ) {

		const h = $( '#mode-hint' );
		h.textContent = text;
		h.classList.add( 'show' );
		clearTimeout( this.hintTimer );
		this.hintTimer = setTimeout( () => h.classList.remove( 'show' ), ms );

	}

	setHidden( v ) {

		this.hidden = v;
		$( '#hud' ).classList.toggle( 'hidden', v );
		$( '#labels' ).style.visibility = v ? 'hidden' : '';
		$( '#ui-restore' ).hidden = ! v;
		if ( v ) {

			this.closeMenu();
			// restart fade animation
			const r = $( '#ui-restore' );
			r.style.animation = 'none';
			void r.offsetWidth;
			r.style.animation = '';

		}

	}

	fullscreen() {

		if ( document.fullscreenElement ) document.exitFullscreen();
		else document.documentElement.requestFullscreen?.().catch( () => this.hint( 'Fullscreen was blocked by the browser' ) );

	}

	setFPS( text ) {

		$( '#fps' ).textContent = text;

	}

}
