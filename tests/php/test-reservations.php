<?php

function lt_reserve( WC_Product $p ) {
	$order = lt_order_with_hold( $p, 'lt_pay_at_shop' );
	if ( ! WC()->cart ) {
		wc_load_cart();
	}
	$gateway = new LT_Gateway_Pay_At_Shop();
	$result  = $gateway->process_payment( $order->get_id() );
	lt_eq( 'success', $result['result'] );
	return wc_get_order( $order->get_id() );
}

function test_reserve_and_pay_at_boutique_holds_stock_until_expiry() {
	$p     = lt_product();
	$order = lt_reserve( $p );
	lt_eq( LT_Orders::RESERVED, lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ), 'nobody else can buy it' );
	$until = strtotime( $order->get_meta( '_lt_hold_expires' ) . ' UTC' );
	lt_assert( abs( $until - ( time() + 24 * HOUR_IN_SECONDS ) ) < 120, 'held for 24 hours' );
	$a = LT_Product_Fields::availability( wc_get_product( $p->get_id() ) );
	lt_eq( 'reserved', $a['state'] );
	// Hold ends without payment.
	$order->update_meta_data( '_lt_hold_expires', gmdate( 'Y-m-d H:i:s', time() - 5 ) );
	$order->save();
	LT_Cron::release_holds();
	lt_eq( 'cancelled', lt_status( $order->get_id() ) );
	lt_eq( 1, lt_stock( $p->get_id() ), 'unpaid reservation returns the piece' );
}

function test_staff_mark_reservation_paid_in_boutique() {
	$p     = lt_product();
	$order = lt_reserve( $p );
	$order->update_status( 'completed', 'Paid in boutique' );
	lt_eq( 'completed', lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ), 'stock reduced once' );
	lt_assert( (bool) wc_get_order( $order->get_id() )->get_date_paid(), 'date paid recorded' );
}
