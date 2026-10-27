<?php

function test_hold_belongs_to_one_cart_only() {
	$p = lt_product();
	$a = LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'va' ) );
	lt_assert( $a['ok'], 'first cart should get the hold' );
	$hours = ( strtotime( $a['expires_at'] . ' UTC' ) - time() ) / 3600;
	lt_assert( $hours > 23.9 && $hours <= 24, 'default hold is 24 hours, got ' . $hours );
	$again = LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'va' ) );
	lt_assert( $again['ok'] && ! empty( $again['already'] ), 'same cart again is a no-op' );
	$b = LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'B', 'visitor_hash' => 'vb' ) );
	lt_eq( false, $b['ok'], 'second cart' );
	lt_eq( 'held', $b['reason'] );
	LT_Holds::release( $p->get_id(), 'A' );
	lt_assert( LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'B', 'visitor_hash' => 'vb' ) )['ok'], 'free after release' );
}

function test_twenty_simultaneous_shoppers_exactly_one_wins() {
	$p   = lt_product();
	$out = lt_parallel(
		20,
		'while ( microtime( true ) < (float) getenv( "LT_START" ) ) { usleep( 200 ); }
		$r = LT_Holds::acquire( ' . $p->get_id() . ', array( "session_key" => "S" . getenv( "LT_I" ), "visitor_hash" => "V" . getenv( "LT_I" ) ) );
		echo $r["ok"] ? "WIN" : "LOSE";'
	);
	$wins = count( array_filter( $out, static fn( $o ) => 'WIN' === substr( $o, -3 ) ) );
	lt_eq( 1, $wins, 'winners among 20 parallel requests (' . implode( ',', $out ) . ')' );
	global $wpdb;
	lt_eq( '1', $wpdb->get_var( $wpdb->prepare( 'SELECT COUNT(*) FROM ' . LT_Schema::table() . ' WHERE product_id = %d', $p->get_id() ) ) );
}

function test_hold_limits_per_cart_and_per_visitor() {
	update_option( 'lt_max_items_per_cart', 2 );
	update_option( 'lt_max_holds_per_visitor', 3 );
	$ps = array( lt_product(), lt_product(), lt_product(), lt_product() );
	lt_assert( LT_Holds::acquire( $ps[0]->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'same' ) )['ok'] );
	lt_assert( LT_Holds::acquire( $ps[1]->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'same' ) )['ok'] );
	lt_eq( 'cart_full', LT_Holds::acquire( $ps[2]->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'same' ) )['reason'] );
	// Clearing cookies gives a new cart, but the same connection is capped.
	lt_assert( LT_Holds::acquire( $ps[2]->get_id(), array( 'session_key' => 'B', 'visitor_hash' => 'same' ) )['ok'] );
	lt_eq( 'visitor_limit', LT_Holds::acquire( $ps[3]->get_id(), array( 'session_key' => 'C', 'visitor_hash' => 'same' ) )['reason'] );
}

function test_hold_never_exceeds_24_hours() {
	update_option( 'lt_cart_hold_hours', 72 );
	lt_eq( 24, LT_Settings::get( 'cart_hold_hours' ), 'cart hold is capped' );
	update_option( 'lt_guest_hold_hours', 3 );
	lt_eq( 3, LT_Settings::get( 'guest_hold_hours' ) );
}

function test_expired_hold_frees_the_piece() {
	global $wpdb;
	$p = lt_product();
	LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'A', 'visitor_hash' => 'va' ) );
	$wpdb->update( LT_Schema::table(), array( 'expires_at' => gmdate( 'Y-m-d H:i:s', time() - 5 ) ), array( 'product_id' => $p->get_id() ) );
	lt_assert( LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'B', 'visitor_hash' => 'vb' ) )['ok'], 'someone else can hold it after expiry' );
}

function test_checkout_claims_holds_once_even_when_submitted_twice_at_once() {
	$p = lt_product();
	lt_assert( LT_Holds::acquire( $p->get_id(), array( 'session_key' => 'CART', 'visitor_hash' => 'v' ) )['ok'] );
	$o1  = wc_create_order();
	$o2  = wc_create_order();
	$out = lt_parallel(
		2,
		'while ( microtime( true ) < (float) getenv( "LT_START" ) ) { usleep( 200 ); }
		$order = getenv( "LT_I" ) === "0" ? ' . $o1->get_id() . ' : ' . $o2->get_id() . ';
		echo LT_Holds::transfer_to_order( array( ' . $p->get_id() . ' ), $order, gmdate( "Y-m-d H:i:s", time() + 1800 ), "CART" ) ? "MOVED" : "NO";'
	);
	$moved = count( array_filter( $out, static fn( $o ) => 'MOVED' === substr( $o, -5 ) ) );
	lt_eq( 1, $moved, 'only one order gets the hold (' . implode( ',', $out ) . ')' );
}
