<?php

function test_online_payment_sells_the_piece_and_releases_the_hold() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$order->payment_complete( 'BILL-1' );
	lt_eq( 'processing', lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ), 'stock after sale' );
	lt_eq( null, LT_Holds::get( $p->get_id() ), 'hold released' );
}

function test_duplicate_payment_confirmation_changes_nothing() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$order->payment_complete( 'BILL-1' );
	reset_lt_orders_state();
	$again = wc_get_order( $order->get_id() );
	$stale = wc_get_order( $order->get_id() );
	$stale->set_status( 'pending' ); // a stale copy, as a second callback would hold
	$stale->payment_complete( 'BILL-1' );
	lt_eq( 'processing', lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ), 'stock reduced once only' );
	$notes = wc_get_order_notes( array( 'order_id' => $order->get_id() ) );
	$paid  = array_filter( $notes, static fn( $n ) => false !== strpos( $n->content, 'Payment via' ) || false !== strpos( $n->content, 'Payment complete' ) );
	lt_eq( 1, count( $paid ), 'one payment note' );
}

function test_parallel_duplicate_callbacks_record_payment_once() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	lt_parallel(
		6,
		'while ( microtime( true ) < (float) getenv( "LT_START" ) ) { usleep( 200 ); }
		wc_get_order( ' . $order->get_id() . ' )->payment_complete( "BILL-X" );'
	);
	lt_eq( 'processing', lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ), 'stock never goes below zero' );
	$notes = wc_get_order_notes( array( 'order_id' => $order->get_id() ) );
	$paid  = array_filter( $notes, static fn( $n ) => false !== strpos( $n->content, 'Payment via' ) || false !== strpos( $n->content, 'Payment complete.' ) );
	lt_eq( 1, count( $paid ), 'one payment recorded from six simultaneous callbacks' );
}

function test_boutique_sale_during_online_checkout_cancels_the_order_and_flags_late_payment() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	// Staff sell the piece in the boutique and set stock to 0 in the editor.
	$fresh = wc_get_product( $p->get_id() );
	$fresh->set_stock_quantity( 0 );
	$fresh->save();
	lt_eq( 'cancelled', lt_status( $order->get_id() ), 'online order cancelled' );
	lt_eq( null, LT_Holds::get( $p->get_id() ), 'its hold is gone' );
	lt_assert( count( lt_mails_to( 'buyer@example.com' ) ) >= 1, 'customer is told the piece is no longer available' );
	// The customer still completes payment at Billplz.
	reset_lt_orders_state();
	wc_get_order( $order->get_id() )->payment_complete( 'BILL-LATE' );
	lt_eq( LT_Orders::REFUND_DUE, lt_status( $order->get_id() ), 'payment for a sold piece is flagged' );
	lt_eq( 0, lt_stock( $p->get_id() ), 'stock untouched (not -1)' );
}

function test_late_payment_after_expiry_keeps_sale_when_piece_is_free() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$order->update_status( 'cancelled', 'Hold ended' );
	lt_eq( null, LT_Holds::get( $p->get_id() ) );
	reset_lt_orders_state();
	wc_get_order( $order->get_id() )->payment_complete( 'BILL-LATE' );
	lt_eq( 'processing', lt_status( $order->get_id() ) );
	lt_eq( 0, lt_stock( $p->get_id() ) );
}

function test_late_payment_after_expiry_is_refunded_when_someone_else_holds_it() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$order->update_status( 'cancelled', 'Hold ended' );
	lt_assert( LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'NEXT', 'visitor_hash' => 'vn' ) )['ok'], 'next customer holds it' );
	reset_lt_orders_state();
	wc_get_order( $order->get_id() )->payment_complete( 'BILL-LATE' );
	lt_eq( LT_Orders::REFUND_DUE, lt_status( $order->get_id() ) );
	lt_eq( 1, lt_stock( $p->get_id() ), 'stock unchanged' );
	lt_eq( 'NEXT', LT_Holds::get( $p->get_id() )->session_key, 'the other customer keeps their hold' );
}

function test_cancelling_or_refunding_a_paid_order_does_not_relist_the_piece() {
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$order->payment_complete( 'BILL-1' );
	lt_eq( 0, lt_stock( $p->get_id() ) );
	wc_get_order( $order->get_id() )->update_status( 'cancelled', 'Customer changed mind' );
	lt_eq( 0, lt_stock( $p->get_id() ), 'still sold after cancel' );
	// Staff confirm the piece is back.
	LT_Orders::return_to_stock( wc_get_order( $order->get_id() ) );
	lt_eq( 1, lt_stock( $p->get_id() ), 'back in stock only by staff action' );
}

function test_unpaid_order_is_cancelled_when_its_hold_ends_and_the_piece_returns() {
	global $wpdb;
	$p     = lt_product();
	$order = lt_order_with_hold( $p );
	$wpdb->update( LT_Schema::table(), array( 'expires_at' => gmdate( 'Y-m-d H:i:s', time() - 5 ) ), array( 'product_id' => $p->get_id() ) );
	LT_Cron::release_holds();
	lt_eq( 'cancelled', lt_status( $order->get_id() ) );
	lt_eq( null, LT_Holds::get( $p->get_id() ) );
	lt_eq( 1, lt_stock( $p->get_id() ) );
}
