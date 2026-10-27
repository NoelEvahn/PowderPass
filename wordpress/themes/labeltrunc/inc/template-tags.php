<?php
/**
 * Small presentation helpers shared by templates.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

/**
 * Inline Lucide icon (lucide.dev, ISC licence). Only the icons the site uses are
 * included. Decorative by default; pass a label to expose it to screen readers.
 */
function lt_icon( $name, $size = 18, $label = '' ) {
	static $icons = array(
		'arrow-right'    => '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
		'arrow-up-right' => '<path d="M7 7h10v10"/><path d="M7 17 17 7"/>',
		'search'         => '<path d="m21 21-4.34-4.34"/><circle cx="11" cy="11" r="8"/>',
		'x'              => '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
		'chevron-down'   => '<path d="m6 9 6 6 6-6"/>',
		'message-circle' => '<path d="M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719"/>',
		'phone'          => '<path d="M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384"/>',
		'mail'           => '<path d="m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7"/><rect x="2" y="4" width="20" height="16" rx="2"/>',
		'map-pin'        => '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/>',
		'minus'          => '<path d="M5 12h14"/>',
		'plus'           => '<path d="M5 12h14"/><path d="M12 5v14"/>',
		'check'          => '<path d="M20 6 9 17l-5-5"/>',
		'clock'          => '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
	);
	if ( ! isset( $icons[ $name ] ) ) {
		return '';
	}
	$a11y = $label ? 'role="img" aria-label="' . esc_attr( $label ) . '"' : 'aria-hidden="true" focusable="false"';
	return sprintf(
		'<svg class="lt-icon lt-icon--%1$s" width="%2$d" height="%2$d" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" %3$s>%4$s</svg>',
		esc_attr( $name ),
		(int) $size,
		$a11y,
		$icons[ $name ] // Static, trusted markup.
	);
}

/** The LT monogram from the brand file. */
function lt_monogram( $size = 32 ) {
	return sprintf(
		'<svg class="lt-monogram" viewBox="0 0 64 64" width="%1$d" height="%1$d" aria-hidden="true" focusable="false"><rect width="64" height="64" fill="#501C2B"/><g fill="#F7EFEA"><rect x="18" y="14" width="7" height="36"/><rect x="14" y="14" width="15" height="2"/><rect x="18" y="48" width="30" height="2.4"/><rect x="45.6" y="42" width="2.4" height="8"/><rect x="20" y="14" width="30" height="2.4"/><rect x="47.6" y="14" width="2.4" height="7"/><rect x="33" y="14" width="7" height="30"/><rect x="29" y="42" width="15" height="2"/></g></svg>',
		(int) $size
	);
}

/** "labeltrunc." wordmark: the brand's bold serif with the burgundy full stop. */
function lt_wordmark() {
	return '<span class="lt-wordmark">' . lt_monogram( 28 ) . '<span class="lt-wordmark__text">labeltrunc<span class="lt-wordmark__dot">.</span></span></span>';
}

function lt_address_lines() {
	return array_values( array_filter( array_map( 'trim', explode( "\n", (string) lt_mod( 'lt_address' ) ) ) ) );
}

/** Opening hours as [days, time] rows from "Days | Time" lines. */
function lt_hours_rows() {
	$rows = array();
	foreach ( array_filter( array_map( 'trim', explode( "\n", (string) lt_mod( 'lt_hours' ) ) ) ) as $line ) {
		$parts  = array_map( 'trim', explode( '|', $line, 2 ) );
		$rows[] = array( $parts[0], $parts[1] ?? '' );
	}
	return $rows;
}

function lt_whatsapp_url( $text = '' ) {
	$number = preg_replace( '/\D/', '', (string) lt_mod( 'lt_whatsapp' ) );
	if ( ! $number ) {
		return '';
	}
	return 'https://wa.me/' . $number . ( $text ? '?text=' . rawurlencode( $text ) : '' );
}

function lt_tel_url() {
	$phone = preg_replace( '/[^\d+]/', '', (string) lt_mod( 'lt_phone' ) );
	return $phone ? 'tel:' . $phone : '';
}

/** Social links as [label, url]. Text labels: Lucide has no brand marks, and words read better. */
function lt_social_links() {
	$links = array();
	foreach ( array(
		'lt_instagram'  => 'Instagram',
		'lt_instagram2' => 'Instagram',
		'lt_tiktok'     => 'TikTok',
		'lt_facebook'   => 'Facebook',
	) as $key => $label ) {
		$url = (string) lt_mod( $key );
		if ( $url ) {
			$handle  = trim( (string) wp_parse_url( $url, PHP_URL_PATH ), '/' );
			$links[] = array( $label, $url, $handle ? '@' . ltrim( $handle, '@' ) : '' );
		}
	}
	return $links;
}

function lt_page_url( $slug ) {
	$page = get_page_by_path( $slug );
	return $page ? get_permalink( $page ) : home_url( '/' . $slug . '/' );
}

/** Small status line for a piece: a square marker and words. Never colour alone. */
function lt_status_markup( $availability ) {
	if ( empty( $availability['state'] ) || 'available' === $availability['state'] || 'unknown' === $availability['state'] ) {
		return '';
	}
	return sprintf(
		'<span class="lt-status lt-status--%1$s"><span class="lt-status__mark" aria-hidden="true"></span>%2$s</span>',
		esc_attr( $availability['state'] ),
		esc_html( $availability['label'] )
	);
}

function lt_availability( $product ) {
	return class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::availability( $product ) : array( 'state' => $product && $product->is_in_stock() ? 'available' : 'sold', 'label' => $product && $product->is_in_stock() ? '' : __( 'Sold', 'labeltrunc' ) );
}

/** Brand name of a piece (WooCommerce Brands), or ''. */
function lt_brand_name( $product ) {
	$terms = get_the_terms( $product->get_id(), 'product_brand' );
	return ( $terms && ! is_wp_error( $terms ) ) ? $terms[0]->name : '';
}

/** Page header used by inner pages: breadcrumb trail, title and optional intro. */
function lt_page_header( $title, $intro = '', $trail = array() ) {
	echo '<header class="lt-page-head lt-wrap">';
	if ( $trail ) {
		echo '<nav class="lt-crumbs" aria-label="' . esc_attr__( 'Breadcrumb', 'labeltrunc' ) . '"><ol><li><a href="' . esc_url( home_url( '/' ) ) . '">' . esc_html__( 'Home', 'labeltrunc' ) . '</a></li>';
		foreach ( $trail as $label => $url ) {
			echo $url ? '<li><a href="' . esc_url( $url ) . '">' . esc_html( $label ) . '</a></li>' : '<li aria-current="page">' . esc_html( $label ) . '</li>';
		}
		echo '</ol></nav>';
	}
	echo '<h1 class="lt-page-head__title">' . esc_html( $title ) . '</h1>';
	if ( $intro ) {
		echo '<p class="lt-page-head__intro">' . esc_html( $intro ) . '</p>';
	}
	echo '</header>';
}

/** True when online payment (Billplz) is set up and offered at checkout. */
function lt_online_payments() {
	if ( ! function_exists( 'WC' ) || ! WC()->payment_gateways() ) {
		return false;
	}
	return isset( WC()->payment_gateways()->get_available_payment_gateways()['billplz'] );
}
