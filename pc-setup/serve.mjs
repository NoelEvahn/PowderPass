// Zero-dependency static file server for local development.
// Usage: node serve.mjs [port]   (default port 8080)
import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve( fileURLToPath( new URL( '.', import.meta.url ) ) );
const port = Number( process.argv[ 2 ] || process.env.PORT || 8080 );

const types = {
	'.html': 'text/html; charset=utf-8',
	'.js': 'text/javascript; charset=utf-8',
	'.mjs': 'text/javascript; charset=utf-8',
	'.css': 'text/css; charset=utf-8',
	'.json': 'application/json',
	'.png': 'image/png',
	'.jpg': 'image/jpeg',
	'.jpeg': 'image/jpeg',
	'.webp': 'image/webp',
	'.svg': 'image/svg+xml',
	'.hdr': 'application/octet-stream',
	'.map': 'application/json',
};

const server = http.createServer( async ( req, res ) => {

	try {

		const urlPath = decodeURIComponent( new URL( req.url, 'http://localhost' ).pathname );
		let filePath = normalize( join( root, urlPath ) );
		if ( filePath !== root && ! filePath.startsWith( root + sep ) ) {

			res.writeHead( 403 ).end( 'Forbidden' );
			return;

		}

		const info = await stat( filePath ).catch( () => null );
		if ( info && info.isDirectory() ) filePath = join( filePath, 'index.html' );

		const body = await readFile( filePath );
		res.writeHead( 200, {
			'Content-Type': types[ extname( filePath ).toLowerCase() ] || 'application/octet-stream',
			'Cache-Control': 'no-cache',
		} );
		res.end( body );

	} catch {

		res.writeHead( 404, { 'Content-Type': 'text/plain' } ).end( 'Not found' );

	}

} );

server.listen( port, () => {

	console.log( `PC setup viewer running at http://localhost:${ port }/` );

} );
