<?php
/**
 * Shop rules settings (WooCommerce → Settings → Products → Label Trunc).
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Settings {

	/** Each rule: default and allowed range. Holds in a cart are capped at 24 hours. */
	const RULES = array(
		'cart_hold_hours'        => array( 24, 1, 24 ),
		'guest_hold_hours'       => array( 24, 1, 24 ),
		'pay_at_shop_hold_hours' => array( 24, 1, 168 ),
		'payment_window_minutes' => array( 30, 10, 120 ),
		'max_items_per_cart'     => array( 3, 1, 10 ),
		'max_holds_per_visitor'  => array( 5, 1, 30 ),
		'show_sold_days'         => array( 30, 0, 365 ),
	);

	public static function init() {
		add_filter( 'woocommerce_get_sections_products', array( __CLASS__, 'add_section' ) );
		add_filter( 'woocommerce_get_settings_products', array( __CLASS__, 'settings' ), 10, 2 );
	}

	/** A rule's value, always within its allowed range. */
	public static function get( $key ) {
		$rule = self::RULES[ $key ];
		$raw  = get_option( 'lt_' . $key, $rule[0] );
		$val  = is_numeric( $raw ) ? (int) $raw : $rule[0];
		return max( $rule[1], min( $rule[2], $val ) );
	}

	public static function add_section( $sections ) {
		$sections['labeltrunc'] = __( 'Label Trunc holds', 'labeltrunc' );
		return $sections;
	}

	public static function settings( $settings, $section ) {
		if ( 'labeltrunc' !== $section ) {
			return $settings;
		}
		$field = static function ( $key, $title, $desc ) {
			$rule = LT_Settings::RULES[ $key ];
			return array(
				'id'                => 'lt_' . $key,
				'title'             => $title,
				'desc'              => $desc,
				'type'              => 'number',
				'default'           => $rule[0],
				'custom_attributes' => array(
					'min'  => $rule[1],
					'max'  => $rule[2],
					'step' => 1,
				),
				'desc_tip'          => false,
			);
		};
		return array(
			array(
				'title' => __( 'Holds and reservations', 'labeltrunc' ),
				'type'  => 'title',
				'desc'  => __( 'Every piece is one of a kind. Adding it to a cart holds it for that customer; nobody else can buy it until the hold ends.', 'labeltrunc' ),
				'id'    => 'lt_holds',
			),
			$field( 'cart_hold_hours', __( 'Cart hold, signed-in customers (hours)', 'labeltrunc' ), __( 'Maximum 24.', 'labeltrunc' ) ),
			$field( 'guest_hold_hours', __( 'Cart hold, guests (hours)', 'labeltrunc' ), __( 'Maximum 24. A shorter guest hold, for example 2 to 4 hours, stops pieces being blocked by people who never come back.', 'labeltrunc' ) ),
			$field( 'pay_at_shop_hold_hours', __( 'Reserve and pay at the boutique: hold (hours)', 'labeltrunc' ), __( 'How long a reservation keeps the piece before it returns to the shop.', 'labeltrunc' ) ),
			$field( 'payment_window_minutes', __( 'Minimum time to pay online (minutes)', 'labeltrunc' ), __( 'If a cart hold is nearly over at checkout, it is extended by this much so the customer can finish paying.', 'labeltrunc' ) ),
			$field( 'max_items_per_cart', __( 'Most pieces in one cart', 'labeltrunc' ), '' ),
			$field( 'max_holds_per_visitor', __( 'Most pieces one visitor can hold', 'labeltrunc' ), __( 'Counted across carts from the same connection, so clearing cookies does not allow unlimited holds.', 'labeltrunc' ) ),
			$field( 'show_sold_days', __( 'Keep sold pieces visible (days)', 'labeltrunc' ), __( 'Sold pieces stay in the shop marked Sold, then are hidden. 0 hides them straight away.', 'labeltrunc' ) ),
			array(
				'type' => 'sectionend',
				'id'   => 'lt_holds',
			),
		);
	}
}
