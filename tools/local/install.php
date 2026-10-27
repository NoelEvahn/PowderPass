<?php
/**
 * Local development install: php tools/local/install.php /path/to/wordpress
 * Creates the site, activates WooCommerce + Label Trunc Core (+ Billplz if present) and the theme.
 * Never used on a real site.
 */
$lt_wp_dir = $argv[1] ?? '/home/user/wp-local';
define( 'WP_INSTALLING', true );
$_SERVER['HTTP_HOST'] = parse_url( getenv( 'LT_HOME' ) ?: 'http://localhost:8080', PHP_URL_HOST );
require $lt_wp_dir . '/wp-load.php';
require_once ABSPATH . 'wp-admin/includes/upgrade.php';
require_once ABSPATH . 'wp-admin/includes/plugin.php';

if ( ! is_blog_installed() ) {
	$r = wp_install( 'Label Trunc', 'admin', 'admin@example.com', false, '', getenv( 'LT_ADMIN_PASS' ) ?: 'local-admin-password-1' );
	echo "Installed WordPress (user admin)\n";
}
update_option( 'permalink_structure', '/%postname%/' );
update_option( 'timezone_string', 'Asia/Kuala_Lumpur' );
update_option( 'blogdescription', 'New and pre-loved designer pieces, curated in Ampang.' );
foreach ( array( 'woocommerce/woocommerce.php', 'billplz-for-woocommerce/billplzWoo.php', 'labeltrunc-core/labeltrunc-core.php' ) as $plugin ) {
	if ( file_exists( WP_PLUGIN_DIR . '/' . $plugin ) && ! is_plugin_active( $plugin ) ) {
		$res = activate_plugin( $plugin, '', false, false );
		echo is_wp_error( $res ) ? "Failed $plugin: " . $res->get_error_message() . "\n" : "Activated $plugin\n";
	}
}
if ( wp_get_theme( 'labeltrunc' )->exists() ) {
	switch_theme( 'labeltrunc' );
	echo "Theme: labeltrunc\n";
}
