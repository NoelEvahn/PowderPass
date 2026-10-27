<?php
/**
 * Styles, scripts and font preloading. WooCommerce's own stylesheets are replaced
 * by the theme's (one file, styled to the boutique) to keep pages light.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

add_action(
	'wp_enqueue_scripts',
	static function () {
		$css = LT_THEME_DIR . '/assets/css/theme.css';
		$js  = LT_THEME_DIR . '/assets/js/theme.js';
		wp_enqueue_style( 'labeltrunc', LT_THEME_URI . '/assets/css/theme.css', array(), (string) filemtime( $css ) );
		wp_enqueue_script( 'labeltrunc', LT_THEME_URI . '/assets/js/theme.js', array(), (string) filemtime( $js ), array( 'strategy' => 'defer', 'in_footer' => true ) );

		// Not used by this theme.
		wp_dequeue_style( 'classic-theme-styles' );
		wp_dequeue_style( 'wc-blocks-style' );
		if ( ! is_singular() || ! has_blocks() ) {
			wp_dequeue_style( 'wp-block-library' );
			wp_dequeue_style( 'global-styles' );
		}
	},
	20
);

add_filter( 'woocommerce_enqueue_styles', '__return_empty_array' );

add_action(
	'wp_head',
	static function () {
		foreach ( array( 'newsreader-normal.woff2', 'instrument-sans-normal.woff2' ) as $font ) {
			printf( '<link rel="preload" href="%s" as="font" type="font/woff2" crossorigin>' . "\n", esc_url( LT_THEME_URI . '/assets/fonts/' . $font ) );
		}
		echo '<meta name="theme-color" content="#f7f2ea">' . "\n";
	},
	1
);

// The site icon falls back to the LT monogram when none is set in the Customizer.
add_action(
	'wp_head',
	static function () {
		if ( ! has_site_icon() ) {
			printf( '<link rel="icon" href="%s" type="image/svg+xml">' . "\n", esc_url( LT_THEME_URI . '/labeltrunc-icon.svg' ) );
			printf( '<link rel="apple-touch-icon" href="%s">' . "\n", esc_url( LT_THEME_URI . '/labeltrunc-icon.png' ) );
		}
	},
	2
);

// Remove emoji scripts: the site uses none.
remove_action( 'wp_head', 'print_emoji_detection_script', 7 );
remove_action( 'wp_print_styles', 'print_emoji_styles' );
