<?php
/**
 * WooCommerce presentation: layout wrappers, shop filters, card and product page
 * structure, checkout fields and account menu. Shop rules are in the plugin.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

// The theme provides its own page structure, breadcrumbs and no sidebars.
remove_action( 'woocommerce_before_main_content', 'woocommerce_output_content_wrapper', 10 );
remove_action( 'woocommerce_after_main_content', 'woocommerce_output_content_wrapper_end', 10 );
remove_action( 'woocommerce_before_main_content', 'woocommerce_breadcrumb', 20 );
remove_action( 'woocommerce_sidebar', 'woocommerce_get_sidebar', 10 );
remove_action( 'woocommerce_before_shop_loop', 'woocommerce_result_count', 20 );
remove_action( 'woocommerce_before_shop_loop', 'woocommerce_catalog_ordering', 30 );
remove_action( 'woocommerce_after_single_product_summary', 'woocommerce_output_product_data_tabs', 10 );
remove_action( 'woocommerce_after_single_product_summary', 'woocommerce_upsell_display', 15 );
remove_action( 'woocommerce_after_single_product_summary', 'woocommerce_output_related_products', 20 );
add_filter( 'woocommerce_sale_flash', '__return_empty_string' );

// Pieces are bought from their own page, not from the grid, so no AJAX cart is needed.
add_filter( 'option_woocommerce_enable_ajax_add_to_cart', static fn() => 'no' );
add_filter( 'option_woocommerce_cart_redirect_after_add', static fn() => 'no' );
add_action(
	'wp_enqueue_scripts',
	static function () {
		wp_dequeue_script( 'wc-cart-fragments' );
	},
	30
);

add_filter( 'loop_shop_per_page', static fn() => 24 );
add_filter( 'loop_shop_columns', static fn() => 3 );

add_filter(
	'woocommerce_product_single_add_to_cart_text',
	static fn() => __( 'Add to cart', 'labeltrunc' )
);

/* -------------------------------------------------------------------- */
/* Shop filters (server-side; work without JavaScript)                  */
/* -------------------------------------------------------------------- */

/** Current filter values from the query string, validated. */
function lt_shop_filters() {
	// phpcs:disable WordPress.Security.NonceVerification.Recommended
	$brand     = isset( $_GET['brand'] ) ? sanitize_title( wp_unslash( $_GET['brand'] ) ) : '';
	$condition = isset( $_GET['condition'] ) ? sanitize_key( wp_unslash( $_GET['condition'] ) ) : '';
	$sold      = ! isset( $_GET['sold'] ) || '0' !== $_GET['sold'];
	// phpcs:enable
	if ( $condition && class_exists( 'LT_Product_Fields' ) && ! isset( LT_Product_Fields::CONDITIONS[ $condition ] ) ) {
		$condition = '';
	}
	if ( $brand && ! term_exists( $brand, 'product_brand' ) ) {
		$brand = '';
	}
	return compact( 'brand', 'condition', 'sold' );
}

add_action(
	'woocommerce_product_query',
	static function ( WP_Query $q ) {
		$f = lt_shop_filters();
		if ( $f['brand'] ) {
			$tax   = (array) $q->get( 'tax_query' );
			$tax[] = array(
				'taxonomy' => 'product_brand',
				'field'    => 'slug',
				'terms'    => $f['brand'],
			);
			$q->set( 'tax_query', $tax );
		}
		$meta = (array) $q->get( 'meta_query' );
		if ( $f['condition'] ) {
			$meta[] = array(
				'key'   => '_lt_condition',
				'value' => $f['condition'],
			);
		}
		if ( ! $f['sold'] ) {
			$meta[] = array(
				'key'   => '_stock_status',
				'value' => 'instock',
			);
		}
		$q->set( 'meta_query', $meta );
		$q->set( 'lt_shop', true );
	}
);

// Available pieces first, sold pieces last, then the chosen order.
add_filter(
	'posts_clauses',
	static function ( $clauses, $q ) {
		global $wpdb;
		if ( ! $q->get( 'lt_shop' ) ) {
			return $clauses;
		}
		$clauses['join']   .= " LEFT JOIN {$wpdb->wc_product_meta_lookup} lt_lookup ON lt_lookup.product_id = {$wpdb->posts}.ID";
		$clauses['orderby'] = 'lt_lookup.stock_status ASC' . ( $clauses['orderby'] ? ', ' . $clauses['orderby'] : '' );
		return $clauses;
	},
	20,
	2
);

/** Brands that currently have published pieces, for the filter. */
function lt_shop_brands() {
	$terms = get_terms(
		array(
			'taxonomy'   => 'product_brand',
			'hide_empty' => true,
		)
	);
	return is_wp_error( $terms ) ? array() : $terms;
}

/* -------------------------------------------------------------------- */
/* Checkout and account                                                 */
/* -------------------------------------------------------------------- */

// Collection at the boutique needs a name, phone and email; the address is only for delivery.
add_filter(
	'woocommerce_checkout_fields',
	static function ( $fields ) {
		unset( $fields['billing']['billing_company'], $fields['order']['order_comments']['placeholder'] );
		foreach ( array( 'billing_address_1', 'billing_address_2', 'billing_city', 'billing_postcode', 'billing_state' ) as $key ) {
			if ( isset( $fields['billing'][ $key ] ) ) {
				$fields['billing'][ $key ]['required'] = false;
			}
		}
		if ( isset( $fields['billing']['billing_address_1'] ) ) {
			$fields['billing']['billing_address_1']['label']       = __( 'Address (only needed for delivery)', 'labeltrunc' );
			$fields['billing']['billing_address_1']['placeholder'] = '';
		}
		if ( isset( $fields['billing']['billing_phone'] ) ) {
			$fields['billing']['billing_phone']['required'] = true;
			$fields['billing']['billing_phone']['label']    = __( 'Phone / WhatsApp', 'labeltrunc' );
		}
		if ( isset( $fields['order']['order_comments'] ) ) {
			$fields['order']['order_comments']['label'] = __( 'Note for the boutique', 'labeltrunc' );
		}
		return $fields;
	}
);

// WooCommerce's address script re-applies each country's rules in the browser, so the
// same "address is optional" rule has to live in the locale data too.
$lt_locale_rules = static function ( $locale ) {
	$rules = array(
		'address_1' => array( 'required' => false, 'label' => __( 'Address (only needed for delivery)', 'labeltrunc' ), 'placeholder' => '' ),
		'city'      => array( 'required' => false ),
		'state'     => array( 'required' => false ),
		'postcode'  => array( 'required' => false ),
		'phone'     => array( 'required' => true, 'label' => __( 'Phone / WhatsApp', 'labeltrunc' ) ),
	);
	$apply = static function ( $fields ) use ( $rules ) {
		foreach ( $rules as $key => $values ) {
			$fields[ $key ] = array_merge( isset( $fields[ $key ] ) ? (array) $fields[ $key ] : array(), $values );
		}
		return $fields;
	};
	if ( isset( $locale['address_1'] ) || isset( $locale['postcode'] ) ) { // the default locale (a flat field list)
		return $apply( $locale );
	}
	foreach ( $locale as $country => $fields ) {
		$locale[ $country ] = $apply( (array) $fields );
	}
	return $locale;
};
add_filter( 'woocommerce_get_country_locale', $lt_locale_rules, 20 );
add_filter( 'woocommerce_get_country_locale_default', $lt_locale_rules, 20 );

// The address is optional only when collecting; any delivery method still needs one.
add_action(
	'woocommerce_after_checkout_validation',
	static function ( $data, $errors ) {
		$methods  = (array) WC()->session->get( 'chosen_shipping_methods', array() );
		$delivery = false;
		foreach ( $methods as $method ) {
			if ( $method && ! preg_match( '/^(local_pickup|pickup_location)/', (string) $method ) ) {
				$delivery = true;
			}
		}
		if ( ! $delivery || ! WC()->cart->needs_shipping() ) {
			return;
		}
		$prefix = ! empty( $data['ship_to_different_address'] ) ? 'shipping_' : 'billing_';
		foreach ( array( 'address_1' => __( 'Address', 'labeltrunc' ), 'city' => __( 'Town / City', 'labeltrunc' ), 'postcode' => __( 'Postcode', 'labeltrunc' ) ) as $key => $label ) {
			if ( empty( $data[ $prefix . $key ] ) ) {
				/* translators: %s: field name */
				$errors->add( 'lt_address', sprintf( __( '%s is needed for delivery.', 'labeltrunc' ), $label ) );
			}
		}
	},
	10,
	2
);

add_filter(
	'woocommerce_account_menu_items',
	static function ( $items ) {
		unset( $items['downloads'] );
		if ( isset( $items['dashboard'] ) ) {
			$items['dashboard'] = __( 'Overview', 'labeltrunc' );
		}
		if ( isset( $items['edit-address'] ) ) {
			$items['edit-address'] = __( 'Address', 'labeltrunc' );
		}
		return $items;
	}
);

// A line under the checkout button about how payment is handled.
add_action(
	'woocommerce_review_order_after_submit',
	static function () {
		if ( lt_online_payments() ) {
			echo '<p class="lt-checkout-safety">' . esc_html__( 'Online payments are processed by Billplz. We never see your card or bank details.', 'labeltrunc' ) . '</p>';
		}
	}
);

// Reviews are off: pieces are one of a kind.
add_filter( 'woocommerce_product_tabs', static fn() => array(), 98 );
add_filter( 'option_woocommerce_enable_reviews', static fn() => 'no' );

/** Related pieces: same brand or category, available, never the same piece. */
function lt_related_products( WC_Product $product, $limit = 4 ) {
	$ids = wc_get_related_products( $product->get_id(), $limit * 2 );
	$out = array();
	foreach ( $ids as $id ) {
		$p = wc_get_product( $id );
		if ( $p && $p->is_visible() && $p->is_in_stock() ) {
			$out[] = $p;
		}
		if ( count( $out ) >= $limit ) {
			break;
		}
	}
	return $out;
}
