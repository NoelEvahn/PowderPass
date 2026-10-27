<?php
// Router for PHP's built-in server: serve real files, send everything else to WordPress.
$root = $_SERVER['DOCUMENT_ROOT'];
$path = parse_url( $_SERVER['REQUEST_URI'], PHP_URL_PATH );
$file = realpath( $root . $path );
if ( $file && is_file( $file ) && str_starts_with( $file, realpath( $root ) ) ) {
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
