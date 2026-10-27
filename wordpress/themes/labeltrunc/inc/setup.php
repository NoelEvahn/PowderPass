<?php
/**
 * Theme supports, menus, image sizes and editor styles.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

add_action(
	'after_setup_theme',
	static function () {
		load_theme_textdomain( 'labeltrunc', LT_THEME_DIR . '/languages' );
		add_theme_support( 'title-tag' );
		add_theme_support( 'post-thumbnails' );
		add_theme_support( 'responsive-embeds' );
		add_theme_support( 'html5', array( 'search-form', 'gallery', 'caption', 'style', 'script', 'navigation-widgets' ) );
		add_theme_support( 'editor-styles' );
		add_editor_style( 'assets/css/editor.css' );
		add_theme_support(
			'woocommerce',
			array(
				'thumbnail_image_width' => 800,
				'single_image_width'    => 1400,
				'product_grid'          => array(
					'default_columns' => 3,
					'default_rows'    => 6,
				),
			)
		);
		register_nav_menus(
			array(
				'primary' => __( 'Main menu', 'labeltrunc' ),
				'footer'  => __( 'Policies (footer)', 'labeltrunc' ),
			)
		);
		// Photographs are cropped by WooCommerce to 4:5, the boutique's portrait frame.
		add_image_size( 'lt-portrait', 1100, 1375, true );
	}
);

// Product images: portrait 4:5 crops with the focal area kept in the centre.
add_filter(
	'woocommerce_get_image_size_thumbnail',
	static function () {
		return array(
			'width'  => 800,
			'height' => 1000,
			'crop'   => 1,
		);
	}
);
add_filter(
	'woocommerce_get_image_size_single',
	static function () {
		return array(
			'width'  => 1400,
			'height' => 1750,
			'crop'   => 1,
		);
	}
);

// A ledger style for lists in the editor: label in bold, then one line, with hairlines.
add_action(
	'init',
	static function () {
		register_block_style(
			'core/list',
			array(
				'name'  => 'lt-ledger',
				'label' => __( 'Ledger (hairline rows)', 'labeltrunc' ),
			)
		);
	}
);

// No comments anywhere on the site.
add_filter( 'comments_open', '__return_false', 20 );
add_filter( 'pings_open', '__return_false', 20 );
