<?php
/**
 * Plugin Name:       Label Trunc Core
 * Description:       Shop rules for Label Trunc: one-of-a-kind holds, reserve and pay at the boutique, protection against double sales, piece details, enquiries and security hardening.
 * Version:           1.0.0
 * Requires at least: 6.5
 * Requires PHP:      7.4
 * Author:            Label Trunc
 * Text Domain:       labeltrunc
 * WC requires at least: 9.6
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

define( 'LT_CORE_VERSION', '1.0.0' );
define( 'LT_CORE_FILE', __FILE__ );
define( 'LT_CORE_DIR', plugin_dir_path( __FILE__ ) );
define( 'LT_CORE_URL', plugin_dir_url( __FILE__ ) );

require_once LT_CORE_DIR . 'includes/class-lt-settings.php';
require_once LT_CORE_DIR . 'includes/class-lt-schema.php';
require_once LT_CORE_DIR . 'includes/class-lt-security.php';
require_once LT_CORE_DIR . 'includes/class-lt-enquiries.php';
require_once LT_CORE_DIR . 'includes/class-lt-holds.php';
require_once LT_CORE_DIR . 'includes/class-lt-billplz.php';
require_once LT_CORE_DIR . 'includes/class-lt-orders.php';
require_once LT_CORE_DIR . 'includes/class-lt-product-fields.php';
require_once LT_CORE_DIR . 'includes/class-lt-dashboard.php';
require_once LT_CORE_DIR . 'includes/class-lt-cron.php';
require_once LT_CORE_DIR . 'includes/class-lt-setup.php';

register_activation_hook( __FILE__, array( 'LT_Schema', 'activate' ) );
register_deactivation_hook( __FILE__, array( 'LT_Cron', 'deactivate' ) );

// Works with WooCommerce's order tables (HPOS). The classic cart and checkout are used,
// because the reservation and pay-at-boutique rules hook into them.
add_action(
	'before_woocommerce_init',
	static function () {
		if ( class_exists( \Automattic\WooCommerce\Utilities\FeaturesUtil::class ) ) {
			\Automattic\WooCommerce\Utilities\FeaturesUtil::declare_compatibility( 'custom_order_tables', LT_CORE_FILE, true );
			\Automattic\WooCommerce\Utilities\FeaturesUtil::declare_compatibility( 'cart_checkout_blocks', LT_CORE_FILE, false );
		}
	}
);

add_action(
	'plugins_loaded',
	static function () {
		LT_Schema::maybe_upgrade();
		LT_Security::init();
		LT_Enquiries::init();

		if ( ! class_exists( 'WooCommerce' ) ) {
			add_action(
				'admin_notices',
				static function () {
					echo '<div class="notice notice-error"><p>' . esc_html__( 'Label Trunc Core needs WooCommerce. Install and activate WooCommerce first.', 'labeltrunc' ) . '</p></div>';
				}
			);
			return;
		}

		require_once LT_CORE_DIR . 'includes/class-lt-gateway-pay-at-shop.php';
		LT_Settings::init();
		LT_Holds::init();
		LT_Orders::init();
		LT_Product_Fields::init();
		LT_Dashboard::init();
		LT_Cron::init();
		LT_Setup::init();
	},
	20
);
