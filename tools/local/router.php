<?php
// Router for PHP's built-in server: serve real files, send everything else to WordPress.
$root = $_SERVER['DOCUMENT_ROOT'];
$path = parse_url( $_SERVER['REQUEST_URI'], PHP_URL_PATH );
// Theme and plugin are symlinked from the repo, so check the requested path (no "..") rather than the resolved one.
$file = ( false === strpos( $path, '..' ) ) ? $root . $path : false;
if ( $file && is_file( $file ) ) {
	if ( str_ends_with( $file, '.php' ) ) {
		chdir( dirname( $file ) );
		require $file;
		return true;
	}
	return false;
}
if ( $file && is_dir( $file ) && is_file( $file . '/index.php' ) ) {
	chdir( $file );
	require $file . '/index.php';
	return true;
}
chdir( $root );
require $root . '/index.php';
