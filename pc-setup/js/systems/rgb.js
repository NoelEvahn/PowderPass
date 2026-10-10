// RGB lighting controller. Only hardware that actually has LEDs is driven:
// Kingston FURY Beast RGB light bars, DeepCool LQ360 ARGB fans + pump accent,
// AULA F75 Max backlight, Logitech G502 LIGHTSYNC logo/DPI indicator.
import * as THREE from 'three';

export const MODES = {
	white: 'Static white',
	pink: 'Soft pink',
	rainbow: 'Rainbow',
	breathing: 'Breathing',
	off: 'RGB off',
};

const WHITE = new THREE.Color( 1.0, 0.95, 0.9 );
const PINK = new THREE.Color( 1.0, 0.42, 0.66 );

export class RGBController {

	constructor() {

		this.zones = new Map();
		this.mode = 'pink';
		this.powered = true;
		this.time = 0;
		this.breathColor = PINK.clone();
		this._c = new THREE.Color();

	}

	// entries: [{ material, strength, phase }]
	addZone( id, label, entries, { light = null, lightStrength = 0, pcPowered = true } = {} ) {

		this.zones.set( id, { id, label, entries, enabled: true, mode: 'sync', light, lightStrength, pcPowered, level: 0 } );

	}

	setMode( mode ) {

		this.mode = mode;

	}

	zoneColor( zone, phase, out ) {

		const mode = zone.mode === 'sync' ? this.mode : zone.mode;
		if ( ! zone.enabled || mode === 'off' ) return out.setRGB( 0, 0, 0 );
		switch ( mode ) {

			case 'white': return out.copy( WHITE );
			case 'pink': return out.copy( PINK );
			case 'rainbow': return out.setHSL( ( this.time * 0.06 + phase ) % 1, 0.9, 0.5 );
			case 'breathing': {

				const b = 0.08 + 0.92 * Math.pow( 0.5 - 0.5 * Math.cos( this.time * Math.PI * 2 / 4.2 ), 1.6 );
				return out.copy( this.breathColor ).multiplyScalar( b );

			}

		}

		return out.setRGB( 0, 0, 0 );

	}

	update( dt ) {

		this.time += dt;
		const c = this._c;
		for ( const zone of this.zones.values() ) {

			// smooth power transitions (no flicker)
			const target = this.powered || ! zone.pcPowered ? 1 : 0;
			zone.level += ( target - zone.level ) * Math.min( 1, dt * 4 );
			let sum = 0, sr = 0, sg = 0, sb = 0;
			for ( const e of zone.entries ) {

				this.zoneColor( zone, e.phase || 0, c ).multiplyScalar( zone.level );
				e.material.emissive.setRGB( c.r * e.strength, c.g * e.strength, c.b * e.strength );
				sr += c.r; sg += c.g; sb += c.b; sum ++;

			}

			if ( zone.light && sum ) {

				zone.light.color.setRGB( sr / sum, sg / sum, sb / sum );
				const lum = ( sr + sg + sb ) / ( 3 * sum );
				zone.light.intensity = zone.lightStrength * Math.min( 1, lum * 2 );
				// avoid tinting with black
				if ( lum < 1e-3 ) zone.light.intensity = 0;

			}

		}

	}

}
