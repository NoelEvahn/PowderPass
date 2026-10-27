<?php
/**
 * Scheduled jobs. WP-Cron runs them when the site is visited; on cPanel, add a
 * real cron job (see the README) so they run on time when the site is quiet.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Cron {

	const EVERY_FIVE = 'lt_every_five_minutes';

	public static function init() {
		add_filter( 'cron_schedules', array( __CLASS__, 'schedules' ) );
		add_action( 'lt_release_holds', array( __CLASS__, 'release_holds' ) );
		add_action( 'lt_daily', array( __CLASS__, 'daily' ) );
		add_action( 'init', array( __CLASS__, 'schedule' ) );
	}

	public static function schedules( $schedules ) {
		$schedules[ self::EVERY_FIVE ] = array(
			'interval' => 5 * MINUTE_IN_SECONDS,
			'display'  => __( 'Every five minutes', 'labeltrunc' ),
		);
		return $schedules;
	}

	public static function schedule() {
		if ( ! wp_next_scheduled( 'lt_release_holds' ) ) {
			wp_schedule_event( time() + 60, self::EVERY_FIVE, 'lt_release_holds' );
		}
		if ( ! wp_next_scheduled( 'lt_daily' ) ) {
			wp_schedule_event( time() + 300, 'daily', 'lt_daily' );
		}
	}

	public static function deactivate() {
		wp_clear_scheduled_hook( 'lt_release_holds' );
		wp_clear_scheduled_hook( 'lt_daily' );
	}

	/** Ends cart holds, unpaid orders and reservations whose time is up. */
	public static function release_holds() {
		global $wpdb;
		LT_Holds::purge_expired_cart_holds();

		$table = LT_Schema::table();
		$now   = LT_Holds::now();
		$ids   = $wpdb->get_col( $wpdb->prepare( "SELECT DISTINCT order_id FROM {$table} WHERE order_id IS NOT NULL AND expires_at <= %s LIMIT 50", $now ) ); // phpcs:ignore
		foreach ( $ids as $order_id ) {
			LT_Orders::end_unpaid_order( (int) $order_id, 'expired' );
		}

		// Reservations (their holds are released once stock is reduced, so they are found by date).
		$reserved = wc_get_orders(
			array(
				'status'     => array( LT_Orders::RESERVED ),
				'limit'      => 50,
				'meta_query' => array( // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_query
					array(
						'key'     => '_lt_hold_expires',
						'value'   => $now,
						'compare' => '<=',
						'type'    => 'DATETIME',
					),
				),
				'return'     => 'ids',
			)
		);
		foreach ( $reserved as $order_id ) {
			LT_Orders::end_unpaid_order( (int) $order_id, 'expired' );
		}

		// Catch Billplz payments whose callback never arrived.
		if ( LT_Billplz::available() ) {
			$pending = wc_get_orders(
				array(
					'status'         => array( 'pending' ),
					'payment_method' => 'billplz',
					'date_created'   => '<' . ( time() - 5 * MINUTE_IN_SECONDS ),
					'limit'          => 20,
				)
			);
			foreach ( $pending as $order ) {
				LT_Billplz::settle_if_paid( $order );
			}
		}
	}

	/** Hides sold pieces once they have been shown as Sold for the chosen number of days. */
	public static function daily() {
		$days   = LT_Settings::get( 'show_sold_days' );
		$cutoff = time() - $days * DAY_IN_SECONDS;
		$query  = new WP_Query(
			array(
				'post_type'      => 'product',
				'post_status'    => 'publish',
				'posts_per_page' => 100,
				'fields'         => 'ids',
				'meta_query'     => array( // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_query
					array(
						'key'   => '_stock_status',
						'value' => 'outofstock',
					),
					array(
						'key'     => '_lt_sold_at',
						'value'   => $cutoff,
						'compare' => '<=',
						'type'    => 'NUMERIC',
					),
					array(
						'key'     => '_lt_hidden_sold',
						'compare' => 'NOT EXISTS',
					),
				),
			)
		);
		foreach ( $query->posts as $product_id ) {
			$product = wc_get_product( $product_id );
			if ( $product && 'hidden' !== $product->get_catalog_visibility() ) {
				$product->set_catalog_visibility( 'hidden' );
				$product->save();
				update_post_meta( $product_id, '_lt_hidden_sold', 1 );
			}
		}
	}
}
