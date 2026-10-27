<?php
/**
 * Integration tests for the Label Trunc commerce rules, run inside a real
 * WordPress + WooCommerce install against a disposable test database.
 *
 *   LT_DB_NAME=labeltrunc_wp_test php tests/php/run.php /path/to/wordpress [filter]
 */
$lt_wp_dir = $argv[1] ?? '/home/user/wp-local';
$lt_filter = $argv[2] ?? '';
if ( ! getenv( 'LT_DB_NAME' ) || false === strpos( getenv( 'LT_DB_NAME' ), 'test' ) ) {
	fwrite( STDERR, "Refusing to run: set LT_DB_NAME to a database whose name contains 'test'.\n" );
	exit( 2 );
}
$_SERVER['HTTP_HOST']   = 'localhost';
$_SERVER['REQUEST_URI'] = '/';
$_SERVER['REMOTE_ADDR'] = '127.0.0.1';
define( 'LT_TESTS', true );
require $lt_wp_dir . '/wp-load.php';
require __DIR__ . '/lib.php';

$files = glob( __DIR__ . '/test-*.php' );
foreach ( $files as $file ) {
	require $file;
}
$tests  = array_filter( get_defined_functions()['user'], static fn( $f ) => 0 === strpos( $f, 'test_' ) && ( '' === $GLOBALS['lt_filter'] || false !== strpos( $f, $GLOBALS['lt_filter'] ) ) );
$passed = 0;
$failed = array();
foreach ( $tests as $test ) {
	lt_reset();
	try {
		$test();
		$passed++;
		echo "  ✓ {$test}\n";
	} catch ( Throwable $e ) {
		$failed[] = $test;
		echo "  ✗ {$test}\n      " . str_replace( "\n", "\n      ", $e->getMessage() ) . "\n      at " . basename( $e->getFile() ) . ':' . $e->getLine() . "\n";
	}
}
echo "\n" . $passed . ' passed, ' . count( $failed ) . " failed\n";
exit( $failed ? 1 : 0 );
