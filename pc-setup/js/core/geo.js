// Geometry helpers shared by all model builders.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';

const geoCache = new Map();

// Cached rounded box. Radius is clamped so it never exceeds half the smallest side.
export function rbox( w, h, d, r = 0.002, seg = 2 ) {

	r = Math.min( r, w / 2 - 1e-5, h / 2 - 1e-5, d / 2 - 1e-5 );
	const key = `rb${ w.toFixed( 5 ) }|${ h.toFixed( 5 ) }|${ d.toFixed( 5 ) }|${ r.toFixed( 5 ) }|${ seg }`;
	if ( ! geoCache.has( key ) ) geoCache.set( key, new RoundedBoxGeometry( w, h, d, seg, Math.max( r, 1e-5 ) ) );
	return geoCache.get( key );

}

export function box( w, h, d ) {

	const key = `b${ w.toFixed( 5 ) }|${ h.toFixed( 5 ) }|${ d.toFixed( 5 ) }`;
	if ( ! geoCache.has( key ) ) geoCache.set( key, new THREE.BoxGeometry( w, h, d ) );
	return geoCache.get( key );

}

export function cyl( rt, rb, h, seg = 24, open = false ) {

	const key = `c${ rt }|${ rb }|${ h }|${ seg }|${ open }`;
	if ( ! geoCache.has( key ) ) geoCache.set( key, new THREE.CylinderGeometry( rt, rb, h, seg, 1, open ) );
	return geoCache.get( key );

}

// Create a mesh with transform options.
export function mesh( geometry, material, o = {} ) {

	const m = new THREE.Mesh( geometry, material );
	if ( o.p ) m.position.set( o.p[ 0 ], o.p[ 1 ], o.p[ 2 ] );
	if ( o.r ) m.rotation.set( o.r[ 0 ], o.r[ 1 ], o.r[ 2 ] );
	if ( o.s ) m.scale.set( o.s[ 0 ], o.s[ 1 ], o.s[ 2 ] );
	m.castShadow = o.cast !== false;
	m.receiveShadow = o.receive !== false;
	if ( o.name ) m.name = o.name;
	if ( o.parent ) o.parent.add( m );
	return m;

}

export function group( name, parent, p ) {

	const g = new THREE.Group();
	g.name = name || '';
	if ( p ) g.position.set( p[ 0 ], p[ 1 ], p[ 2 ] );
	if ( parent ) parent.add( g );
	return g;

}

export function roundedRectShape( w, h, r, cx = 0, cy = 0 ) {

	const s = new THREE.Shape();
	const x = cx - w / 2, y = cy - h / 2;
	r = Math.min( r, w / 2, h / 2 );
	s.moveTo( x + r, y );
	s.lineTo( x + w - r, y );
	s.quadraticCurveTo( x + w, y, x + w, y + r );
	s.lineTo( x + w, y + h - r );
	s.quadraticCurveTo( x + w, y + h, x + w - r, y + h );
	s.lineTo( x + r, y + h );
	s.quadraticCurveTo( x, y + h, x, y + h - r );
	s.lineTo( x, y + r );
	s.quadraticCurveTo( x, y, x + r, y );
	return s;

}

export function roundedRectPath( w, h, r, cx = 0, cy = 0 ) {

	const s = roundedRectShape( w, h, r, cx, cy );
	const p = new THREE.Path();
	p.curves = s.curves;
	return p;

}

export function circlePath( r, cx = 0, cy = 0, seg = 32 ) {

	const p = new THREE.Path();
	p.absarc( cx, cy, r, 0, Math.PI * 2, true );
	return p;

}

// Extrude along +z, centered on z.
export function extrude( shape, depth, { bevel = 0, curveSegments = 24, center = true } = {} ) {

	const g = new THREE.ExtrudeGeometry( shape, {
		depth: depth - bevel * 2,
		bevelEnabled: bevel > 0,
		bevelThickness: bevel,
		bevelSize: bevel,
		bevelSegments: 2,
		curveSegments,
	} );
	if ( center ) g.translate( 0, 0, - depth / 2 + bevel );
	return g;

}

// Merge child meshes of a group that share a material into single meshes.
// Keeps transforms baked. Useful to cut draw calls on static detail.
export function mergeStatic( root, { keepNames = false } = {} ) {

	root.updateMatrixWorld( true );
	const inv = new THREE.Matrix4().copy( root.matrixWorld ).invert();
	const buckets = new Map();
	const remove = [];
	root.traverse( ( o ) => {

		if ( ! o.isMesh || o.isInstancedMesh || o.userData.noMerge || o === root || Array.isArray( o.material ) ) return;
		if ( ! o.visible ) return;
		if ( o.userData.glass || o.userData.noAO || o.userData.noPick || o.geometry.attributes.color ) return;
		// skip meshes inside sub-groups that must stay dynamic
		let p = o.parent, dynamic = false;
		while ( p && p !== root ) {

			if ( p.userData.noMerge ) dynamic = true;
			p = p.parent;

		}

		if ( dynamic ) return;
		const id = o.userData.componentId;
		const key = o.material.uuid + ( o.castShadow ? 'c' : '' ) + ( o.receiveShadow ? 'r' : '' ) + ( id || '' );
		if ( ! buckets.has( key ) ) buckets.set( key, { material: o.material, geos: [], cast: o.castShadow, receive: o.receiveShadow, id } );
		const g = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
		// normalise attribute sets
		for ( const name of Object.keys( g.attributes ) ) if ( ! [ 'position', 'normal', 'uv' ].includes( name ) ) g.deleteAttribute( name );
		if ( ! g.attributes.uv ) g.setAttribute( 'uv', new THREE.Float32BufferAttribute( new Float32Array( g.attributes.position.count * 2 ), 2 ) );
		if ( ! g.attributes.normal ) g.computeVertexNormals();
		g.applyMatrix4( new THREE.Matrix4().multiplyMatrices( inv, o.matrixWorld ) );
		buckets.get( key ).geos.push( g );
		remove.push( o );

	} );

	for ( const o of remove ) o.parent.remove( o );
	for ( const { material, geos, cast, receive, id } of buckets.values() ) {

		const merged = mergeGeometries( geos, false );
		geos.forEach( ( g ) => g.dispose() );
		if ( ! merged ) continue;
		const m = new THREE.Mesh( merged, material );
		m.castShadow = cast;
		m.receiveShadow = receive;
		if ( id ) m.userData.componentId = id;
		root.add( m );

	}

	return root;

}

// Smooth tube following points (Catmull-Rom).
export function tubeAlong( points, radius, { tubular = 64, radial = 12, tension = 0.5 } = {} ) {

	const curve = new THREE.CatmullRomCurve3( points.map( ( p ) => ( p.isVector3 ? p : new THREE.Vector3( ...p ) ) ), false, 'catmullrom', tension );
	return new THREE.TubeGeometry( curve, tubular, radius, radial, false );

}

export { mergeGeometries };
