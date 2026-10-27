<?php
/**
 * Runs PHP inside the local WordPress (a small stand-in for `wp eval-file`).
 * Usage: php tools/local/wp-eval.php /path/to/wordpress script.php [args...]
 */
$lt_wp_dir = $argv[1];
$lt_script = $argv[2];
$_SERVER['HTTP_HOST']   = parse_url( getenv( 'LT_HOME' ) ?: 'http://localhost:8080', PHP_URL_HOST );
$_SERVER['REQUEST_URI'] = '/';
$_SERVER['REMOTE_ADDR'] = getenv( 'LT_IP' ) ?: '127.0.0.1';
define( 'WP_ADMIN', getenv( 'LT_AS_ADMIN' ) === '1' );
require $lt_wp_dir . '/wp-load.php';
$lt_args = array_slice( $argv, 3 );
require $lt_script;
