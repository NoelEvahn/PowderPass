// Procedurally generated textures (canvas based). Everything is generated at
// load time so the project ships without large binary image assets.
import * as THREE from 'three';

let maxAniso = 8;
export function setMaxAnisotropy( v ) {

	maxAniso = v;

}

export function makeCanvas( w, h ) {

	const c = document.createElement( 'canvas' );
	c.width = w;
	c.height = h;
	return c;

}

export function canvasTexture( canvas, { srgb = true, repeat = null, aniso = true, mipmaps = true } = {} ) {

	const t = new THREE.CanvasTexture( canvas );
	t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
	if ( repeat ) {

		t.wrapS = t.wrapT = THREE.RepeatWrapping;
		t.repeat.set( repeat[ 0 ], repeat[ 1 ] );

	}

	t.anisotropy = aniso ? maxAniso : 1;
	t.generateMipmaps = mipmaps;
	if ( ! mipmaps ) t.minFilter = THREE.LinearFilter;
	t.needsUpdate = true;
	return t;

}

// ---------------------------------------------------------------------------
// Noise helpers
// ---------------------------------------------------------------------------

function mulberry32( a ) {

	return function () {

		a |= 0; a = a + 0x6D2B79F5 | 0;
		let t = Math.imul( a ^ a >>> 15, 1 | a );
		t = t + Math.imul( t ^ t >>> 7, 61 | t ) ^ t;
		return ( ( t ^ t >>> 14 ) >>> 0 ) / 4294967296;

	};

}

export const rng = mulberry32( 1337 );

// Tileable fractal value noise, returns Float32Array in [0,1].
export function tileNoise( w, h, { cells = 8, octaves = 4, seed = 1, persistence = 0.5 } = {} ) {

	const out = new Float32Array( w * h );
	const rand = mulberry32( seed );
	let amp = 1, total = 0, c = cells;
	for ( let o = 0; o < octaves; o ++ ) {

		const lattice = new Float32Array( c * c );
		for ( let i = 0; i < lattice.length; i ++ ) lattice[ i ] = rand();
		for ( let y = 0; y < h; y ++ ) {

			const fy = y / h * c, y0 = Math.floor( fy ), ty = fy - y0;
			const sy = ty * ty * ( 3 - 2 * ty );
			const r0 = ( y0 % c ) * c, r1 = ( ( y0 + 1 ) % c ) * c;
			for ( let x = 0; x < w; x ++ ) {

				const fx = x / w * c, x0 = Math.floor( fx ), tx = fx - x0;
				const sx = tx * tx * ( 3 - 2 * tx );
				const c0 = x0 % c, c1 = ( x0 + 1 ) % c;
				const a = lattice[ r0 + c0 ], b = lattice[ r0 + c1 ];
				const d = lattice[ r1 + c0 ], e = lattice[ r1 + c1 ];
				out[ y * w + x ] += amp * ( ( a + ( b - a ) * sx ) * ( 1 - sy ) + ( d + ( e - d ) * sx ) * sy );

			}

		}

		total += amp;
		amp *= persistence;
		c *= 2;

	}

	for ( let i = 0; i < out.length; i ++ ) out[ i ] /= total;
	return out;

}

// Converts a height field to a tangent-space normal map canvas (wraps edges).
export function heightToNormalCanvas( heights, w, h, strength = 2 ) {

	const c = makeCanvas( w, h );
	const ctx = c.getContext( '2d' );
	const img = ctx.createImageData( w, h );
	for ( let y = 0; y < h; y ++ ) {

		for ( let x = 0; x < w; x ++ ) {

			const l = heights[ y * w + ( x - 1 + w ) % w ], r = heights[ y * w + ( x + 1 ) % w ];
			const u = heights[ ( ( y - 1 + h ) % h ) * w + x ], d = heights[ ( ( y + 1 ) % h ) * w + x ];
			let nx = ( l - r ) * strength, ny = ( d - u ) * strength, nz = 1;
			const len = Math.hypot( nx, ny, nz );
			nx /= len; ny /= len; nz /= len;
			const i = ( y * w + x ) * 4;
			img.data[ i ] = ( nx * 0.5 + 0.5 ) * 255;
			img.data[ i + 1 ] = ( ny * 0.5 + 0.5 ) * 255;
			img.data[ i + 2 ] = ( nz * 0.5 + 0.5 ) * 255;
			img.data[ i + 3 ] = 255;

		}

	}

	ctx.putImageData( img, 0, 0 );
	return c;

}

function grayCanvas( values, w, h, lo, hi ) {

	const c = makeCanvas( w, h );
	const ctx = c.getContext( '2d' );
	const img = ctx.createImageData( w, h );
	for ( let i = 0; i < w * h; i ++ ) {

		const v = ( lo + ( hi - lo ) * values[ i ] ) * 255;
		img.data[ i * 4 ] = img.data[ i * 4 + 1 ] = img.data[ i * 4 + 2 ] = v;
		img.data[ i * 4 + 3 ] = 255;

	}

	ctx.putImageData( img, 0, 0 );
	return c;

}

// ---------------------------------------------------------------------------
// Surface textures
// ---------------------------------------------------------------------------

export function powderCoatNormal() {

	const s = 256;
	const n = tileNoise( s, s, { cells: 64, octaves: 2, seed: 11 } );
	return canvasTexture( heightToNormalCanvas( n, s, s, 1.2 ), { srgb: false, repeat: [ 6, 6 ] } );

}

export function plasticGrainNormal() {

	const s = 256;
	const n = tileNoise( s, s, { cells: 32, octaves: 3, seed: 5 } );
	return canvasTexture( heightToNormalCanvas( n, s, s, 1.6 ), { srgb: false, repeat: [ 8, 8 ] } );

}

export function wallTextures() {

	const s = 512;
	const n = tileNoise( s, s, { cells: 16, octaves: 5, seed: 21, persistence: 0.55 } );
	const normal = canvasTexture( heightToNormalCanvas( n, s, s, 1.4 ), { srgb: false, repeat: [ 4, 3 ] } );
	const rough = canvasTexture( grayCanvas( n, s, s, 0.82, 0.95 ), { srgb: false, repeat: [ 4, 3 ] } );
	return { normal, rough };

}

export function fabricTextures() {

	// plain weave: alternating over/under threads plus slub noise
	const s = 256;
	const h = new Float32Array( s * s );
	const slub = tileNoise( s, s, { cells: 8, octaves: 3, seed: 3 } );
	const threads = 32;
	for ( let y = 0; y < s; y ++ ) {

		for ( let x = 0; x < s; x ++ ) {

			const tx = x / s * threads, ty = y / s * threads;
			const cx = Math.floor( tx ), cy = Math.floor( ty );
			const fx = tx - cx, fy = ty - cy;
			const warpUp = ( cx + cy ) % 2 === 0;
			const warp = Math.sin( fx * Math.PI ), weft = Math.sin( fy * Math.PI );
			h[ y * s + x ] = ( warpUp ? warp * 0.7 + weft * 0.3 : weft * 0.7 + warp * 0.3 ) * 0.8 + slub[ y * s + x ] * 0.2;

		}

	}

	const normal = canvasTexture( heightToNormalCanvas( h, s, s, 1.8 ), { srgb: false, repeat: [ 14, 14 ] } );
	return { normal };

}

export function floorTextures() {

	// Light oak laminate planks (unseen in reference; neutral choice)
	const W = 2048, H = 2048;
	const c = makeCanvas( W, H );
	const ctx = c.getContext( '2d' );
	const rough = makeCanvas( 512, 512 );
	const rctx = rough.getContext( '2d' );
	const rand = mulberry32( 77 );
	const rows = 10, plankH = H / rows;
	ctx.fillStyle = '#c9a982';
	ctx.fillRect( 0, 0, W, H );
	for ( let r = 0; r < rows; r ++ ) {

		let x = - rand() * W * 0.5;
		while ( x < W ) {

			const len = W * ( 0.45 + rand() * 0.35 );
			const base = 0.88 + rand() * 0.18;
			const hue = 32 + rand() * 6;
			ctx.fillStyle = `hsl(${hue}, ${ 34 + rand() * 10 }%, ${ 62 * base }%)`;
			ctx.fillRect( x, r * plankH, len, plankH );
			// grain lines
			ctx.save();
			ctx.beginPath();
			ctx.rect( x, r * plankH, len, plankH );
			ctx.clip();
			for ( let g = 0; g < 46; g ++ ) {

				const gy = r * plankH + rand() * plankH;
				ctx.strokeStyle = `rgba(${ 90 + rand() * 40 },${ 60 + rand() * 25 },${ 30 + rand() * 20 },${ 0.05 + rand() * 0.12 })`;
				ctx.lineWidth = 0.6 + rand() * 2.2;
				ctx.beginPath();
				ctx.moveTo( x, gy );
				const amp = rand() * 6, freq = 0.002 + rand() * 0.006, ph = rand() * 6;
				for ( let px = x; px <= x + len; px += 24 ) ctx.lineTo( px, gy + Math.sin( px * freq + ph ) * amp );
				ctx.stroke();

			}

			// occasional knot
			if ( rand() < 0.25 ) {

				const kx = x + rand() * len, ky = r * plankH + plankH * ( 0.3 + rand() * 0.4 );
				for ( let k = 6; k > 0; k -- ) {

					ctx.strokeStyle = `rgba(100,65,35,${ 0.08 + 0.03 * k })`;
					ctx.beginPath();
					ctx.ellipse( kx, ky, k * 7, k * 2.6, 0, 0, Math.PI * 2 );
					ctx.stroke();

				}

			}

			ctx.restore();
			// seam
			ctx.fillStyle = 'rgba(60,40,22,0.55)';
			ctx.fillRect( x, r * plankH, 2, plankH );
			x += len;

		}

		ctx.fillStyle = 'rgba(60,40,22,0.6)';
		ctx.fillRect( 0, r * plankH, W, 2 );

	}

	// roughness: mostly satin, slight variation per plank
	const n = tileNoise( 512, 512, { cells: 8, octaves: 4, seed: 9 } );
	const img = rctx.createImageData( 512, 512 );
	for ( let i = 0; i < n.length; i ++ ) {

		const v = ( 0.42 + n[ i ] * 0.2 ) * 255;
		img.data[ i * 4 ] = img.data[ i * 4 + 1 ] = img.data[ i * 4 + 2 ] = v;
		img.data[ i * 4 + 3 ] = 255;

	}

	rctx.putImageData( img, 0, 0 );
	return {
		map: canvasTexture( c, { repeat: [ 2, 2 ] } ),
		rough: canvasTexture( rough, { srgb: false, repeat: [ 2, 2 ] } ),
	};

}

export function deskRoughness() {

	// Satin white laminate with faint wear/smudges
	const s = 512;
	const n = tileNoise( s, s, { cells: 4, octaves: 5, seed: 41, persistence: 0.6 } );
	const c = grayCanvas( n, s, s, 0.28, 0.46 );
	const ctx = c.getContext( '2d' );
	const rand = mulberry32( 8 );
	for ( let i = 0; i < 14; i ++ ) {

		const x = rand() * s, y = rand() * s, r = 10 + rand() * 30;
		const g = ctx.createRadialGradient( x, y, 0, x, y, r );
		g.addColorStop( 0, 'rgba(150,150,150,0.25)' );
		g.addColorStop( 1, 'rgba(150,150,150,0)' );
		ctx.fillStyle = g;
		ctx.fillRect( x - r, y - r, r * 2, r * 2 );

	}

	return canvasTexture( c, { srgb: false, repeat: [ 2, 1 ] } );

}

export function brushedTextures( repeat = [ 1, 1 ] ) {

	const s = 512;
	const h = new Float32Array( s * s );
	const rand = mulberry32( 99 );
	const rowVals = new Float32Array( s );
	for ( let y = 0; y < s; y ++ ) rowVals[ y ] = rand();
	for ( let y = 0; y < s; y ++ ) for ( let x = 0; x < s; x ++ ) h[ y * s + x ] = rowVals[ y ] * 0.7 + rand() * 0.3;
	const normal = canvasTexture( heightToNormalCanvas( h, s, s, 0.6 ), { srgb: false, repeat } );
	const rough = canvasTexture( grayCanvas( h, s, s, 0.28, 0.42 ), { srgb: false, repeat } );
	return { normal, rough };

}

// Hex perforation alpha map (white = solid, black = hole)
export function perforationAlpha( { holeRatio = 0.36, size = 128 } = {} ) {

	const pitch = size / 4;
	const r = pitch * holeRatio;
	const rows = 8;
	const hh = Math.round( rows * pitch * 0.8660254 );
	const c2 = makeCanvas( size, hh );
	const ctx2 = c2.getContext( '2d' );
	ctx2.fillStyle = '#fff';
	ctx2.fillRect( 0, 0, size, hh );
	ctx2.fillStyle = '#000';
	const py = hh / rows;
	for ( let row = - 1; row <= rows + 1; row ++ ) {

		const off = ( ( row + 2 ) % 2 ) * pitch / 2;
		for ( let col = - 1; col <= 5; col ++ ) {

			ctx2.beginPath();
			ctx2.arc( col * pitch + off, row * py, r, 0, Math.PI * 2 );
			ctx2.fill();

		}

	}

	const t = canvasTexture( c2, { srgb: false } );
	t.wrapS = t.wrapT = THREE.RepeatWrapping;
	return t;

}

// ---------------------------------------------------------------------------
// Windows 11 desktop with a pink Japanese wave wallpaper
// ---------------------------------------------------------------------------

function drawWaveWallpaper( ctx, W, H ) {

	const bg = ctx.createLinearGradient( 0, 0, 0, H );
	bg.addColorStop( 0, '#0c0a12' );
	bg.addColorStop( 0.6, '#120d18' );
	bg.addColorStop( 1, '#1a1020' );
	ctx.fillStyle = bg;
	ctx.fillRect( 0, 0, W, H );

	const rand = mulberry32( 2024 );
	// faint paper texture dots
	for ( let i = 0; i < 2200; i ++ ) {

		ctx.fillStyle = `rgba(255,190,220,${ rand() * 0.035 })`;
		ctx.fillRect( rand() * W, rand() * H, 1.5, 1.5 );

	}

	// soft glow behind wave
	const glow = ctx.createRadialGradient( W * 0.42, H * 0.45, 10, W * 0.42, H * 0.45, W * 0.45 );
	glow.addColorStop( 0, 'rgba(240,120,170,0.16)' );
	glow.addColorStop( 1, 'rgba(240,120,170,0)' );
	ctx.fillStyle = glow;
	ctx.fillRect( 0, 0, W, H );

	const P = ( x, y ) => [ x * W, y * H ];

	// distant mountain (Fuji) in the trough
	ctx.save();
	ctx.beginPath();
	ctx.moveTo( ...P( 0.56, 0.74 ) );
	ctx.lineTo( ...P( 0.64, 0.6 ) );
	ctx.lineTo( ...P( 0.66, 0.6 ) );
	ctx.lineTo( ...P( 0.75, 0.74 ) );
	ctx.closePath();
	ctx.fillStyle = '#7a3a5a';
	ctx.fill();
	ctx.beginPath();
	ctx.moveTo( ...P( 0.625, 0.625 ) );
	ctx.lineTo( ...P( 0.64, 0.6 ) );
	ctx.lineTo( ...P( 0.66, 0.6 ) );
	ctx.lineTo( ...P( 0.677, 0.627 ) );
	ctx.lineTo( ...P( 0.66, 0.635 ) );
	ctx.lineTo( ...P( 0.648, 0.622 ) );
	ctx.lineTo( ...P( 0.636, 0.636 ) );
	ctx.closePath();
	ctx.fillStyle = '#f6d6e4';
	ctx.fill();
	ctx.restore();

	// Main wave body
	const body = new Path2D();
	body.moveTo( ...P( - 0.02, 0.95 ) );
	body.bezierCurveTo( ...P( 0.06, 0.78 ), ...P( 0.1, 0.5 ), ...P( 0.2, 0.32 ) );
	body.bezierCurveTo( ...P( 0.28, 0.18 ), ...P( 0.42, 0.12 ), ...P( 0.52, 0.18 ) );
	body.bezierCurveTo( ...P( 0.58, 0.22 ), ...P( 0.6, 0.3 ), ...P( 0.56, 0.36 ) );
	// curling lip back inward
	body.bezierCurveTo( ...P( 0.53, 0.3 ), ...P( 0.47, 0.27 ), ...P( 0.42, 0.31 ) );
	body.bezierCurveTo( ...P( 0.36, 0.36 ), ...P( 0.36, 0.5 ), ...P( 0.44, 0.62 ) );
	body.bezierCurveTo( ...P( 0.5, 0.72 ), ...P( 0.58, 0.78 ), ...P( 0.7, 0.82 ) );
	body.bezierCurveTo( ...P( 0.85, 0.86 ), ...P( 0.95, 0.84 ), ...P( 1.02, 0.86 ) );
	body.lineTo( ...P( 1.02, 1.02 ) );
	body.lineTo( ...P( - 0.02, 1.02 ) );
	body.closePath();

	const g = ctx.createLinearGradient( 0, H * 0.15, 0, H );
	g.addColorStop( 0, '#f7b4cf' );
	g.addColorStop( 0.35, '#e17ba5' );
	g.addColorStop( 0.75, '#9c3f6b' );
	g.addColorStop( 1, '#5b2042' );
	ctx.fillStyle = g;
	ctx.fill( body );

	// Striations inside the wave (Hokusai style)
	ctx.save();
	ctx.clip( body );
	ctx.lineCap = 'round';
	for ( let i = 0; i < 26; i ++ ) {

		const t = i / 26;
		ctx.strokeStyle = `rgba(${ 255 - t * 80 },${ 200 - t * 120 },${ 225 - t * 80 },${ 0.55 - t * 0.25 })`;
		ctx.lineWidth = W * 0.0022;
		ctx.beginPath();
		const ox = t * 0.3, oy = t * 0.55;
		ctx.moveTo( ...P( 0.02 + ox * 0.3, 0.98 ) );
		ctx.bezierCurveTo( ...P( 0.1 + ox * 0.5, 0.8 - oy * 0.1 ), ...P( 0.14 + ox * 0.7, 0.5 + oy * 0.3 ), ...P( 0.24 + ox * 0.6, 0.34 + oy * 0.35 ) );
		ctx.bezierCurveTo( ...P( 0.3 + ox * 0.5, 0.24 + oy * 0.4 ), ...P( 0.42 + ox * 0.2, 0.2 + oy * 0.4 ), ...P( 0.5, 0.24 + oy * 0.5 ) );
		ctx.stroke();

	}

	ctx.restore();

	// Foam claws along the crest
	const crest = [];
	for ( let i = 0; i <= 40; i ++ ) {

		const t = i / 40;
		// sample the crest curve approx between (0.2,0.32) -> (0.52,0.18) -> (0.56,0.36)
		const x = 0.2 + t * 0.37 - Math.pow( t, 6 ) * 0.04;
		const y = 0.32 - Math.sin( t * Math.PI * 0.85 ) * 0.16 + Math.pow( t, 5 ) * 0.2;
		crest.push( [ x, y ] );

	}

	ctx.fillStyle = '#fde9f1';
	ctx.strokeStyle = '#fde9f1';
	for ( let i = 2; i < crest.length; i ++ ) {

		const [ x, y ] = crest[ i ];
		const len = 0.012 + rand() * 0.018;
		ctx.lineWidth = W * 0.003;
		ctx.beginPath();
		ctx.moveTo( x * W, y * H );
		ctx.quadraticCurveTo( ( x + len * 0.6 ) * W, ( y - len * 1.3 ) * H, ( x + len * 1.1 ) * W, ( y - len * 0.2 ) * H );
		ctx.stroke();
		ctx.beginPath();
		ctx.arc( ( x + len * 1.1 ) * W, ( y - len * 0.2 ) * H, W * 0.003, 0, Math.PI * 2 );
		ctx.fill();

	}

	// spray
	for ( let i = 0; i < 420; i ++ ) {

		const [ cx, cy ] = crest[ Math.floor( rand() * crest.length ) ];
		const x = cx + ( rand() - 0.3 ) * 0.08, y = cy - rand() * 0.12;
		ctx.fillStyle = `rgba(253,233,241,${ 0.4 + rand() * 0.6 })`;
		ctx.beginPath();
		ctx.arc( x * W, y * H, W * ( 0.001 + rand() * 0.0022 ), 0, Math.PI * 2 );
		ctx.fill();

	}

	// Foreground small wave
	const small = new Path2D();
	small.moveTo( ...P( 0.55, 1.02 ) );
	small.bezierCurveTo( ...P( 0.62, 0.84 ), ...P( 0.72, 0.76 ), ...P( 0.82, 0.8 ) );
	small.bezierCurveTo( ...P( 0.86, 0.82 ), ...P( 0.86, 0.87 ), ...P( 0.83, 0.88 ) );
	small.bezierCurveTo( ...P( 0.8, 0.86 ), ...P( 0.76, 0.88 ), ...P( 0.78, 0.94 ) );
	small.lineTo( ...P( 0.8, 1.02 ) );
	small.closePath();
	const g2 = ctx.createLinearGradient( 0, H * 0.75, 0, H );
	g2.addColorStop( 0, '#f3a6c5' );
	g2.addColorStop( 1, '#6d2a4e' );
	ctx.fillStyle = g2;
	ctx.fill( small );

	// Signature seal
	ctx.fillStyle = 'rgba(214,60,100,0.85)';
	ctx.fillRect( W * 0.9, H * 0.12, W * 0.018, H * 0.05 );
	ctx.fillStyle = 'rgba(250,220,230,0.65)';
	ctx.font = `${ Math.round( H * 0.022 ) }px serif`;
	ctx.textAlign = 'center';
	ctx.fillText( '波', W * 0.909, H * 0.153 );

}

function roundRect( ctx, x, y, w, h, r ) {

	ctx.beginPath();
	ctx.roundRect( x, y, w, h, r );

}

export function drawWindowsDesktop( canvas, timeString = '21:47', dateString = '10/10/2026' ) {

	const ctx = canvas.getContext( '2d' );
	const W = canvas.width, H = canvas.height;
	const s = W / 2560;
	drawWaveWallpaper( ctx, W, H );

	// Desktop icons
	const icons = [
		[ 'Recycle Bin', '#cfd8e3', 'bin' ],
		[ 'This PC', '#6aa9e8', 'pc' ],
		[ 'Steam', '#1b2838', 'steam' ],
		[ 'Discord', '#5865f2', 'disc' ],
	];
	ctx.textAlign = 'center';
	ctx.font = `${ 22 * s }px "Segoe UI", sans-serif`;
	icons.forEach( ( [ name, col, kind ], i ) => {

		const x = 60 * s, y = 40 * s + i * 150 * s;
		ctx.fillStyle = col;
		if ( kind === 'bin' ) {

			ctx.fillRect( x - 22 * s, y + 18 * s, 44 * s, 56 * s );
			ctx.fillStyle = '#9fb0c2';
			ctx.fillRect( x - 28 * s, y + 10 * s, 56 * s, 8 * s );

		} else if ( kind === 'pc' ) {

			roundRect( ctx, x - 34 * s, y + 10 * s, 68 * s, 46 * s, 4 * s );
			ctx.fill();
			ctx.fillStyle = '#2d78c8';
			ctx.fillRect( x - 28 * s, y + 16 * s, 56 * s, 34 * s );
			ctx.fillStyle = '#c8d4e0';
			ctx.fillRect( x - 16 * s, y + 60 * s, 32 * s, 6 * s );

		} else {

			roundRect( ctx, x - 32 * s, y + 8 * s, 64 * s, 64 * s, 14 * s );
			ctx.fill();
			ctx.fillStyle = '#fff';
			ctx.beginPath();
			ctx.arc( x, y + 40 * s, 14 * s, 0, Math.PI * 2 );
			ctx.fill();

		}

		ctx.fillStyle = '#fff';
		ctx.shadowColor = 'rgba(0,0,0,0.8)';
		ctx.shadowBlur = 4 * s;
		ctx.fillText( name, x, y + 102 * s );
		ctx.shadowBlur = 0;

	} );

	// Taskbar (Windows 11, dark mode, centered)
	const tbH = 48 * s;
	ctx.fillStyle = 'rgba(28,24,32,0.86)';
	ctx.fillRect( 0, H - tbH, W, tbH );
	ctx.fillStyle = 'rgba(255,255,255,0.06)';
	ctx.fillRect( 0, H - tbH, W, 1 * s );

	const cy = H - tbH / 2;
	const apps = [ 'win', 'search', 'task', 'explorer', 'edge', 'store', 'steam', 'discord', 'spotify', 'terminal' ];
	const step = 44 * s;
	let x0 = W / 2 - ( apps.length * step + 150 * s ) / 2;
	apps.forEach( ( a ) => {

		const cx = x0 + step / 2;
		const sz = 24 * s;
		switch ( a ) {

			case 'win': {

				const g = ctx.createLinearGradient( cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2 );
				g.addColorStop( 0, '#3cc3ff' ); g.addColorStop( 1, '#0a6fe0' );
				ctx.fillStyle = g;
				const q = sz / 2 - 1.2 * s;
				ctx.fillRect( cx - sz / 2, cy - sz / 2, q, q );
				ctx.fillRect( cx + 1.2 * s, cy - sz / 2, q, q );
				ctx.fillRect( cx - sz / 2, cy + 1.2 * s, q, q );
				ctx.fillRect( cx + 1.2 * s, cy + 1.2 * s, q, q );
				break;

			}

			case 'search': {

				roundRect( ctx, cx - 18 * s, cy - 16 * s, 170 * s, 32 * s, 16 * s );
				ctx.fillStyle = 'rgba(255,255,255,0.08)';
				ctx.fill();
				ctx.strokeStyle = '#ddd';
				ctx.lineWidth = 2 * s;
				ctx.beginPath();
				ctx.arc( cx, cy - 1 * s, 6 * s, 0, Math.PI * 2 );
				ctx.moveTo( cx + 4.5 * s, cy + 3.5 * s );
				ctx.lineTo( cx + 9 * s, cy + 8 * s );
				ctx.stroke();
				ctx.fillStyle = '#aaa';
				ctx.textAlign = 'left';
				ctx.font = `${ 15 * s }px "Segoe UI", sans-serif`;
				ctx.fillText( 'Search', cx + 18 * s, cy + 5 * s );
				x0 += 150 * s;
				break;

			}

			case 'task':
				ctx.strokeStyle = '#ddd'; ctx.lineWidth = 2 * s;
				ctx.strokeRect( cx - 10 * s, cy - 8 * s, 14 * s, 14 * s );
				ctx.strokeRect( cx - 4 * s, cy - 2 * s, 14 * s, 14 * s );
				break;
			case 'explorer':
				ctx.fillStyle = '#f6c344';
				roundRect( ctx, cx - 12 * s, cy - 9 * s, 24 * s, 19 * s, 3 * s ); ctx.fill();
				ctx.fillStyle = '#ffd96a';
				ctx.fillRect( cx - 12 * s, cy - 4 * s, 24 * s, 14 * s );
				break;
			case 'edge': {

				const g = ctx.createLinearGradient( cx - 12 * s, cy - 12 * s, cx + 12 * s, cy + 12 * s );
				g.addColorStop( 0, '#4ade80' ); g.addColorStop( 1, '#0369a1' );
				ctx.fillStyle = g;
				ctx.beginPath(); ctx.arc( cx, cy, 12 * s, 0, Math.PI * 2 ); ctx.fill();
				ctx.fillStyle = '#1c1820';
				ctx.beginPath(); ctx.arc( cx + 2 * s, cy + 2 * s, 5 * s, 0, Math.PI * 2 ); ctx.fill();
				break;

			}

			case 'store':
				ctx.fillStyle = '#e8e8e8';
				roundRect( ctx, cx - 11 * s, cy - 8 * s, 22 * s, 19 * s, 3 * s ); ctx.fill();
				ctx.fillStyle = '#f25022'; ctx.fillRect( cx - 6 * s, cy - 3 * s, 5 * s, 5 * s );
				ctx.fillStyle = '#7fba00'; ctx.fillRect( cx + 1 * s, cy - 3 * s, 5 * s, 5 * s );
				ctx.fillStyle = '#00a4ef'; ctx.fillRect( cx - 6 * s, cy + 4 * s, 5 * s, 5 * s );
				ctx.fillStyle = '#ffb900'; ctx.fillRect( cx + 1 * s, cy + 4 * s, 5 * s, 5 * s );
				break;
			case 'steam':
				ctx.fillStyle = '#2a475e';
				ctx.beginPath(); ctx.arc( cx, cy, 12 * s, 0, Math.PI * 2 ); ctx.fill();
				ctx.fillStyle = '#c7d5e0';
				ctx.beginPath(); ctx.arc( cx + 3 * s, cy - 3 * s, 4 * s, 0, Math.PI * 2 ); ctx.fill();
				break;
			case 'discord':
				ctx.fillStyle = '#5865f2';
				roundRect( ctx, cx - 12 * s, cy - 12 * s, 24 * s, 24 * s, 7 * s ); ctx.fill();
				ctx.fillStyle = '#fff';
				ctx.beginPath(); ctx.arc( cx - 4 * s, cy, 2.4 * s, 0, Math.PI * 2 ); ctx.arc( cx + 4 * s, cy, 2.4 * s, 0, Math.PI * 2 ); ctx.fill();
				break;
			case 'spotify':
				ctx.fillStyle = '#1ed760';
				ctx.beginPath(); ctx.arc( cx, cy, 12 * s, 0, Math.PI * 2 ); ctx.fill();
				ctx.strokeStyle = '#111'; ctx.lineWidth = 2 * s;
				for ( let k = 0; k < 3; k ++ ) {

					ctx.beginPath(); ctx.arc( cx, cy + 12 * s, ( 14 - k * 3.5 ) * s, - 2.3, - 0.85 ); ctx.stroke();

				}

				break;
			case 'terminal':
				ctx.fillStyle = '#2b2b2b';
				roundRect( ctx, cx - 12 * s, cy - 10 * s, 24 * s, 20 * s, 3 * s ); ctx.fill();
				ctx.strokeStyle = '#ccc'; ctx.lineWidth = 2 * s;
				ctx.beginPath(); ctx.moveTo( cx - 7 * s, cy - 4 * s ); ctx.lineTo( cx - 2 * s, cy ); ctx.lineTo( cx - 7 * s, cy + 4 * s ); ctx.stroke();
				break;

		}

		if ( [ 'explorer', 'edge', 'steam', 'discord' ].includes( a ) ) {

			ctx.fillStyle = 'rgba(255,255,255,0.45)';
			roundRect( ctx, cx - 3 * s, H - 5 * s, 6 * s, 3 * s, 1.5 * s ); ctx.fill();

		}

		x0 += step;

	} );

	// Weather widget (left)
	ctx.textAlign = 'left';
	ctx.fillStyle = '#9fc8ff';
	ctx.beginPath(); ctx.arc( 26 * s, cy, 10 * s, 0, Math.PI * 2 ); ctx.fill();
	ctx.fillStyle = '#eee';
	ctx.font = `${ 14 * s }px "Segoe UI", sans-serif`;
	ctx.fillText( '17°C', 44 * s, cy - 2 * s );
	ctx.fillStyle = '#aaa';
	ctx.font = `${ 12 * s }px "Segoe UI", sans-serif`;
	ctx.fillText( 'Clear', 44 * s, cy + 14 * s );

	// System tray (right)
	ctx.textAlign = 'right';
	ctx.fillStyle = '#eee';
	ctx.font = `${ 14 * s }px "Segoe UI", sans-serif`;
	ctx.fillText( timeString, W - 28 * s, cy - 3 * s );
	ctx.fillText( dateString, W - 28 * s, cy + 15 * s );
	ctx.strokeStyle = '#ddd'; ctx.lineWidth = 2 * s;
	const tx = W - 130 * s;
	// wifi
	for ( let k = 0; k < 3; k ++ ) {

		ctx.beginPath(); ctx.arc( tx - 44 * s, cy + 6 * s, ( 4 + k * 4 ) * s, - 2.4, - 0.74 ); ctx.stroke();

	}

	// speaker
	ctx.beginPath(); ctx.moveTo( tx - 16 * s, cy - 3 * s ); ctx.lineTo( tx - 12 * s, cy - 3 * s ); ctx.lineTo( tx - 6 * s, cy - 8 * s ); ctx.lineTo( tx - 6 * s, cy + 8 * s ); ctx.lineTo( tx - 12 * s, cy + 3 * s ); ctx.lineTo( tx - 16 * s, cy + 3 * s ); ctx.closePath(); ctx.stroke();
	ctx.beginPath(); ctx.arc( tx - 4 * s, cy, 6 * s, - 0.9, 0.9 ); ctx.stroke();
	// chevron
	ctx.beginPath(); ctx.moveTo( tx - 82 * s, cy + 3 * s ); ctx.lineTo( tx - 77 * s, cy - 3 * s ); ctx.lineTo( tx - 72 * s, cy + 3 * s ); ctx.stroke();

}

export function desktopTexture() {

	const canvas = makeCanvas( 2048, 1152 );
	drawWindowsDesktop( canvas, currentTime(), currentDate() );
	const tex = canvasTexture( canvas );
	tex.userData.canvas = canvas;
	return tex;

}

export function currentTime() {

	const d = new Date();
	return `${ String( d.getHours() ).padStart( 2, '0' ) }:${ String( d.getMinutes() ).padStart( 2, '0' ) }`;

}

export function currentDate() {

	const d = new Date();
	return `${ d.getMonth() + 1 }/${ d.getDate() }/${ d.getFullYear() }`;

}

// ---------------------------------------------------------------------------
// Motherboard PCB (MSI MPG B850 EDGE TI WIFI — silver/white board)
// 4 px per mm, 305 x 244 mm (ATX)
// ---------------------------------------------------------------------------

export function pcbTexture() {

	const pxmm = 4;
	const W = 244 * pxmm, H = 305 * pxmm; // u = board depth (front->back), v = height
	const c = makeCanvas( W, H );
	const ctx = c.getContext( '2d' );
	const rand = mulberry32( 850 );
	ctx.fillStyle = '#c7c9cb';
	ctx.fillRect( 0, 0, W, H );
	// subtle mottling
	const n = tileNoise( 256, 256, { cells: 16, octaves: 3, seed: 4 } );
	for ( let i = 0; i < 4000; i ++ ) {

		const x = rand() * W, y = rand() * H;
		ctx.fillStyle = `rgba(120,124,128,${ n[ i % n.length ] * 0.05 })`;
		ctx.fillRect( x, y, 6, 6 );

	}

	// traces
	ctx.lineCap = 'round';
	for ( let i = 0; i < 520; i ++ ) {

		ctx.strokeStyle = `rgba(150,154,158,${ 0.35 + rand() * 0.35 })`;
		ctx.lineWidth = 0.8 + rand() * 1.2;
		let x = rand() * W, y = rand() * H;
		ctx.beginPath();
		ctx.moveTo( x, y );
		for ( let k = 0; k < 4; k ++ ) {

			if ( rand() < 0.5 ) x += ( rand() - 0.5 ) * 160; else y += ( rand() - 0.5 ) * 160;
			if ( rand() < 0.3 ) { x += 20; y += 20; }
			ctx.lineTo( x, y );

		}

		ctx.stroke();

	}

	// vias
	for ( let i = 0; i < 1600; i ++ ) {

		ctx.fillStyle = 'rgba(110,112,115,0.8)';
		ctx.beginPath(); ctx.arc( rand() * W, rand() * H, 1.4, 0, Math.PI * 2 ); ctx.fill();

	}

	// silkscreen markings (dark grey on light PCB)
	ctx.fillStyle = '#5f6368';
	ctx.font = `bold ${ 5 * pxmm }px Arial, sans-serif`;
	ctx.save();
	ctx.translate( 40 * pxmm, 250 * pxmm );
	ctx.fillText( 'MPG B850 EDGE TI WIFI', 0, 0 );
	ctx.restore();
	ctx.font = `${ 2.4 * pxmm }px Arial, sans-serif`;
	const marks = [ [ 'PCI_E1', 20, 158 ], [ 'PCI_E2', 20, 222 ], [ 'M2_1', 70, 140 ], [ 'DIMMA1', 168, 30 ], [ 'DIMMB2', 214, 30 ], [ 'JFP1', 210, 296 ], [ 'JUSB1', 160, 296 ], [ 'CPU_FAN1', 120, 18 ], [ 'BAT1', 150, 210 ] ];
	for ( const [ t, x, y ] of marks ) ctx.fillText( t, x * pxmm, y * pxmm );
	// mounting hole rings
	const holes = [ [ 6.35, 10.16 ], [ 6.35, 165 ], [ 6.35, 288 ], [ 163, 10.16 ], [ 163, 165 ], [ 163, 288 ], [ 237, 10.16 ], [ 237, 165 ], [ 237, 288 ] ];
	for ( const [ x, y ] of holes ) {

		ctx.fillStyle = '#d8b26a';
		ctx.beginPath(); ctx.arc( ( 244 - x ) * pxmm, y * pxmm, 4.2 * pxmm, 0, Math.PI * 2 ); ctx.fill();
		ctx.fillStyle = '#2a2a2a';
		ctx.beginPath(); ctx.arc( ( 244 - x ) * pxmm, y * pxmm, 1.8 * pxmm, 0, Math.PI * 2 ); ctx.fill();

	}

	return canvasTexture( c );

}

// Generic text label texture
export function labelTexture( w, h, draw ) {

	const c = makeCanvas( w, h );
	draw( c.getContext( '2d' ), w, h );
	return canvasTexture( c );

}

// ---------------------------------------------------------------------------
// Keycap legend atlas — 16 x 8 cells, each 64 px
// ---------------------------------------------------------------------------

export function keycapAtlas( legends ) {

	const cell = 64, cols = 16, rows = Math.ceil( legends.length / cols );
	const c = makeCanvas( cols * cell, Math.max( 1, rows ) * cell );
	const ctx = c.getContext( '2d' );
	ctx.fillStyle = '#fff';
	ctx.fillRect( 0, 0, c.width, c.height );
	ctx.fillStyle = '#4a4a50';
	ctx.textAlign = 'left';
	ctx.textBaseline = 'top';
	legends.forEach( ( l, i ) => {

		const x = ( i % cols ) * cell, y = Math.floor( i / cols ) * cell;
		if ( ! l ) return;
		const big = l.length === 1;
		ctx.font = `${ big ? 20 : 11 }px "Segoe UI", Arial, sans-serif`;
		ctx.fillText( l, x + 10, y + 9 );

	} );
	return { texture: canvasTexture( c ), cols, rows };

}
