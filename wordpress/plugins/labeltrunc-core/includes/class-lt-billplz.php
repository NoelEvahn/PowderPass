<?php
/**
 * Thin bridge to the official "Billplz for WooCommerce" plugin, used to ask Billplz
 * whether an order was paid before it is cancelled, and to void a bill once an order
 * is closed so it can no longer be paid. Does nothing if that plugin is not active.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Billplz {

	public static function available() {
		global $bfw_api, $bfw_connect;
		$settings = get_option( 'woocommerce_billplz_settings' );
		return class_exists( 'WC_Billplz_Gateway' ) && is_array( $settings ) && ! empty( $settings['api_key'] ) && isset( $bfw_api, $bfw_connect );
	}

	private static function api() {
		global $bfw_api, $bfw_connect;
		$settings   = get_option( 'woocommerce_billplz_settings' );
		$is_sandbox = isset( $settings['is_sandbox'] ) && 'yes' === $settings['is_sandbox'];
		$bfw_connect->set_api_key( $settings['api_key'], $is_sandbox );
		$bfw_api->set_connect( $bfw_connect );
		return array( $bfw_api, $is_sandbox );
	}

	public static function bill_id( WC_Order $order ) {
		return 'billplz' === $order->get_payment_method() ? (string) $order->get_transaction_id() : '';
	}

	/**
	 * If Billplz says the order's bill is paid, records the payment (through the
	 * Billplz plugin, so its own records stay in step) and returns true.
	 */
	public static function settle_if_paid( WC_Order $order ) {
		$bill_id = self::bill_id( $order );
		if ( ! $bill_id || ! self::available() ) {
			return false;
		}
		try {
			list( $api, $is_sandbox ) = self::api();
			list( $status, $body )    = $api->toArray( $api->getBill( $bill_id ) );
			if ( 200 !== (int) $status || empty( $body['paid'] ) ) {
				return false;
			}
			if ( function_exists( 'bfw_update_bill' ) ) {
				bfw_update_bill( $bill_id, 'paid', $order->get_id() );
			}
			$order->add_order_note( __( 'Billplz confirmed this bill was paid when the shop checked before closing the order.', 'labeltrunc' ) );
			WC_Billplz_Gateway::complete_payment_process( $order, array( 'id' => $bill_id, 'type' => 'requery' ), $is_sandbox );
			return true;
		} catch ( Throwable $e ) {
			wc_get_logger()->warning( 'Billplz requery failed for order ' . $order->get_id() . ': ' . $e->getMessage(), array( 'source' => 'labeltrunc' ) );
			return false;
		}
	}

	/** Voids an unpaid bill so a closed order cannot be paid later. Failures are logged, not fatal. */
	public static function void_bill( WC_Order $order ) {
		$bill_id = self::bill_id( $order );
		if ( ! $bill_id || ! self::available() ) {
			return;
		}
		try {
			list( $api ) = self::api();
			$api->deleteBill( $bill_id );
			// The Billplz plugin's own record stays "due" on purpose: Billplz can still accept
			// a payment that was already in progress, and that callback must reach the
			// double-sale guard (keep the sale if the piece is free, otherwise flag a refund).
		} catch ( Throwable $e ) {
			wc_get_logger()->warning( 'Billplz bill void failed for order ' . $order->get_id() . ': ' . $e->getMessage(), array( 'source' => 'labeltrunc' ) );
		}
	}
}
