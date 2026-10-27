<?php
/**
 * Label Trunc theme.
 *
 * Editorial boutique theme: cream canvas, burgundy for action only, hairlines in
 * place of cards, a serif for editorial moments and a quiet sans for everything
 * else. Shop rules (holds, pay at the boutique, double-sale protection) live in
 * the Label Trunc Core plugin; this theme only presents them.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

define( 'LT_THEME_VERSION', '1.0.0' );
define( 'LT_THEME_DIR', get_template_directory() );
define( 'LT_THEME_URI', get_template_directory_uri() );

require LT_THEME_DIR . '/inc/setup.php';
require LT_THEME_DIR . '/inc/assets.php';
require LT_THEME_DIR . '/inc/customizer.php';
require LT_THEME_DIR . '/inc/template-tags.php';
require LT_THEME_DIR . '/inc/seo.php';

if ( class_exists( 'WooCommerce' ) ) {
	require LT_THEME_DIR . '/inc/woocommerce.php';
}
