<?php
/**
 * Descriptions, social sharing tags and store structured data. Skipped when an
 * SEO plugin (Yoast, Rank Math, SEOPress, AIOSEO) is active, to avoid duplicates.
 * WooCommerce adds Product structured data on piece pages itself.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

function lt_seo_plugin_active() {
	return defined( 'WPSEO_VERSION' ) || class_exists( 'RankMath' ) || defined( 'SEOPRESS_VERSION' ) || defined( 'AIOSEO_VERSION' );
}

function lt_meta_description() {
	if ( is_front_page() ) {
		return (string) ( lt_mod( 'lt_tagline' ) ?: get_bloginfo( 'description' ) );
	}
	if ( function_exists( 'is_product' ) && is_product() ) {
		$product = wc_get_product( get_queried_object_id() );
		if ( $product ) {
			$text = $product->get_short_description() ?: $product->get_description();
			$bits = array_filter( array( lt_brand_name( $product ), $product->get_name(), class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::condition_label( $product ) : '' ) );
			return wp_trim_words( wp_strip_all_tags( $text ? $text : implode( '. ', $bits ) . '. Available at Label Trunc, Ampang.' ), 30, '…' );
		}
	}
	if ( is_singular() ) {
		$custom = get_post_meta( get_queried_object_id(), '_lt_description', true );
		if ( $custom ) {
			return $custom;
		}
		$excerpt = get_post_field( 'post_excerpt', get_queried_object_id() );
		if ( $excerpt ) {
			return wp_trim_words( $excerpt, 30, '…' );
		}
	}
	if ( function_exists( 'is_shop' ) && is_shop() ) {
		return __( 'New and pre-loved designer bags at Label Trunc, Ampang. Every piece is one of a kind and guaranteed authentic.', 'labeltrunc' );
	}
	return (string) get_bloginfo( 'description' );
}

add_action(
	'wp_head',
	static function () {
		if ( lt_seo_plugin_active() ) {
			return;
		}
		$desc = lt_meta_description();
		if ( $desc ) {
			printf( '<meta name="description" content="%s">' . "\n", esc_attr( $desc ) );
		}
		$title = wp_get_document_title();
		$url   = is_singular() ? get_permalink() : home_url( add_query_arg( array() ) );
		$image = '';
		if ( is_singular() && has_post_thumbnail() ) {
			$image = get_the_post_thumbnail_url( null, 'large' );
		}
		printf( '<meta property="og:site_name" content="%s">' . "\n", esc_attr( get_bloginfo( 'name' ) ) );
		printf( '<meta property="og:title" content="%s">' . "\n", esc_attr( $title ) );
		printf( '<meta property="og:type" content="%s">' . "\n", ( function_exists( 'is_product' ) && is_product() ) ? 'product' : 'website' );
		printf( '<meta property="og:url" content="%s">' . "\n", esc_url( $url ) );
		printf( '<meta property="og:locale" content="en_MY">' . "\n" );
		if ( $desc ) {
			printf( '<meta property="og:description" content="%s">' . "\n", esc_attr( $desc ) );
		}
		if ( $image ) {
			printf( '<meta property="og:image" content="%s">' . "\n", esc_url( $image ) );
			echo '<meta name="twitter:card" content="summary_large_image">' . "\n";
		}
		// Policies awaiting confirmation and account pages are not for search results.
		if ( ( function_exists( 'is_account_page' ) && ( is_account_page() || is_cart() || is_checkout() ) ) ) {
			echo '<meta name="robots" content="noindex, follow">' . "\n";
		}
	},
	5
);

add_action(
	'wp_footer',
	static function () {
		if ( ! is_front_page() || lt_seo_plugin_active() ) {
			return;
		}
		$address = lt_address_lines();
		$data    = array(
			'@context'    => 'https://schema.org',
			'@type'       => 'Store',
			'name'        => get_bloginfo( 'name' ),
			'description' => (string) lt_mod( 'lt_tagline' ),
			'url'         => home_url( '/' ),
			'telephone'   => (string) lt_mod( 'lt_phone' ),
			'email'       => (string) lt_mod( 'lt_email' ),
			'image'       => LT_THEME_URI . '/labeltrunc-icon.png',
			'address'     => array(
				'@type'           => 'PostalAddress',
				'streetAddress'   => implode( ', ', array_slice( $address, 0, 2 ) ),
				'addressLocality' => 'Ampang',
				'addressRegion'   => 'Selangor',
				'postalCode'      => '68000',
				'addressCountry'  => 'MY',
			),
			'sameAs'      => array_values( array_map( static fn( $l ) => $l[1], lt_social_links() ) ),
		);
		if ( lt_hours_rows() ) {
			$data['openingHours'] = array_map( static fn( $r ) => trim( $r[0] . ' ' . $r[1] ), lt_hours_rows() );
		}
		echo '<script type="application/ld+json">' . wp_json_encode( array_filter( $data ), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE ) . '</script>' . "\n";
	}
);
