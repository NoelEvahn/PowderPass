<?php
/**
 * "Label Trunc today" dashboard widget: what needs attention, with links.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Dashboard {

	public static function init() {
		add_action( 'wp_dashboard_setup', array( __CLASS__, 'register' ) );
	}

	public static function register() {
		if ( ! current_user_can( 'edit_shop_orders' ) && ! current_user_can( 'edit_products' ) ) {
			return;
		}
		wp_add_dashboard_widget( 'lt_today', __( 'Label Trunc today', 'labeltrunc' ), array( __CLASS__, 'render' ), null, null, 'normal', 'high' );
	}

	private static function count_orders( $status ) {
		$ids = wc_get_orders(
			array(
				'status' => (array) $status,
				'limit'  => 200,
				'return' => 'ids',
			)
		);
		return count( $ids );
	}

	private static function count_products( $stock_status ) {
		$q = new WP_Query(
			array(
				'post_type'      => 'product',
				'post_status'    => 'publish',
				'posts_per_page' => 1,
				'fields'         => 'ids',
				'meta_query'     => array( // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_query
					array(
						'key'   => '_stock_status',
						'value' => $stock_status,
					),
				),
			)
		);
		return (int) $q->found_posts;
	}

	public static function render() {
		global $wpdb;
		$table      = LT_Schema::table();
		$held       = (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE order_id IS NULL AND expires_at > %s", LT_Holds::now() ) ); // phpcs:ignore
		$orders_url = admin_url( 'admin.php?page=wc-orders' );
		if ( ! class_exists( \Automattic\WooCommerce\Utilities\OrderUtil::class ) || ! \Automattic\WooCommerce\Utilities\OrderUtil::custom_orders_table_usage_is_enabled() ) {
			$orders_url = admin_url( 'edit.php?post_type=shop_order' );
		}
		$new_enquiries = (int) ( new WP_Query(
			array(
				'post_type'      => LT_Enquiries::POST_TYPE,
				'post_status'    => 'private',
				'posts_per_page' => 1,
				'fields'         => 'ids',
				'meta_key'       => '_lt_status', // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_key
				'meta_value'     => 'new', // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_value
			)
		) )->found_posts;

		$rows = array(
			array( __( 'Refund due', 'labeltrunc' ), self::count_orders( LT_Orders::REFUND_DUE ), add_query_arg( 'status', 'wc-' . LT_Orders::REFUND_DUE, $orders_url ), true ),
			array( __( 'To pack or hand over (paid)', 'labeltrunc' ), self::count_orders( 'processing' ), add_query_arg( 'status', 'wc-processing', $orders_url ), true ),
			array( __( 'Reserved, paying at the boutique', 'labeltrunc' ), self::count_orders( LT_Orders::RESERVED ), add_query_arg( 'status', 'wc-' . LT_Orders::RESERVED, $orders_url ), true ),
			array( __( 'Awaiting online payment', 'labeltrunc' ), self::count_orders( 'pending' ), add_query_arg( 'status', 'wc-pending', $orders_url ), false ),
			array( __( 'New enquiries', 'labeltrunc' ), $new_enquiries, admin_url( 'edit.php?post_type=' . LT_Enquiries::POST_TYPE ), true ),
			array( __( 'Pieces in customers\' carts', 'labeltrunc' ), $held, admin_url( 'edit.php?post_type=product' ), false ),
			array( __( 'Pieces for sale', 'labeltrunc' ), self::count_products( 'instock' ), admin_url( 'edit.php?post_type=product&stock_status=instock' ), false ),
			array( __( 'Sold (still shown)', 'labeltrunc' ), self::count_products( 'outofstock' ), admin_url( 'edit.php?post_type=product&stock_status=outofstock' ), false ),
		);
		echo '<table class="widefat striped" style="border:0"><tbody>';
		foreach ( $rows as $row ) {
			list( $label, $count, $url, $action ) = $row;
			$style = ( $action && $count > 0 ) ? 'font-weight:600;color:#501c2b' : '';
			echo '<tr><td><a href="' . esc_url( $url ) . '" style="' . esc_attr( $style ) . '">' . esc_html( $label ) . '</a></td><td style="text-align:right;font-variant-numeric:tabular-nums;' . esc_attr( $style ) . '">' . (int) $count . '</td></tr>';
		}
		echo '</tbody></table>';
		echo '<p style="margin:12px 0 0"><a class="button button-primary" href="' . esc_url( admin_url( 'post-new.php?post_type=product' ) ) . '">' . esc_html__( 'Add a piece', 'labeltrunc' ) . '</a> ';
		echo '<a class="button" href="' . esc_url( admin_url( 'admin.php?page=wc-settings&tab=products&section=labeltrunc' ) ) . '">' . esc_html__( 'Hold rules', 'labeltrunc' ) . '</a></p>';
	}
}
