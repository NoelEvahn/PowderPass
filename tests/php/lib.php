<?php
/** Tiny assertion library and fixtures for the integration tests. */

function lt_assert( $cond, $message = 'Assertion failed' ) {
	if ( ! $cond ) {
		throw new Exception( $message );
	}
}
function lt_eq( $expected, $actual, $message = '' ) {
	if ( $expected !== $actual ) {
		throw new Exception( ( $message ? $message . ': ' : '' ) . 'expected ' . var_export( $expected, true ) . ', got ' . var_export( $actual, true ) );
	}
}

/** Emails sent during a test (captured, never delivered). */
$GLOBALS['lt_mails'] = array();
add_filter(
	'pre_wp_mail',
	static function ( $null, $atts ) {
		$GLOBALS['lt_mails'][] = $atts;
		return true;
	},
	10,
	2
);

function lt_mails_to( $email ) {
	return array_values( array_filter( $GLOBALS['lt_mails'], static fn( $m ) => false !== strpos( implode( ',', (array) $m['to'] ), $email ) ) );
}

/** Clean slate: no products, orders or holds; default rules. */
function lt_reset() {
	global $wpdb;
	$wpdb->query( 'DELETE FROM ' . LT_Schema::table() );
	foreach ( wc_get_orders( array( 'limit' => -1, 'status' => 'any', 'return' => 'ids' ) ) as $id ) {
		$o = wc_get_order( $id );
		if ( $o ) {
			$o->delete( true );
		}
	}
	foreach ( get_posts( array( 'post_type' => 'product', 'post_status' => 'any', 'numberposts' => -1, 'fields' => 'ids' ) ) as $id ) {
		wp_delete_post( $id, true );
	}
	foreach ( array_keys( LT_Settings::RULES ) as $key ) {
		delete_option( 'lt_' . $key );
	}
	wp_set_current_user( 0 );
	reset_lt_orders_state();
	$GLOBALS['lt_mails'] = array();
}

/** Clears the per-request static state LT_Orders keeps between payments. */
function reset_lt_orders_state() {
	$r = new ReflectionClass( 'LT_Orders' );
	foreach ( array( 'conflicts', 'in_payment', 'duplicates', 'hold_failures' ) as $prop ) {
		$p = $r->getProperty( $prop );
		$p->setAccessible( true );
		$p->setValue( null, array() );
	}
	LT_Orders::release_locks();
}

function lt_product( $args = array() ) {
	$p = new WC_Product_Simple();
	$p->set_name( $args['name'] ?? 'Test piece ' . wp_generate_password( 6, false ) );
	$p->set_regular_price( (string) ( $args['price'] ?? 1000 ) );
	$p->set_manage_stock( true );
	$p->set_stock_quantity( $args['stock'] ?? 1 );
	$p->set_sold_individually( true );
	$p->set_status( 'publish' );
	$p->save();
	return $p;
}

function lt_stock( $product_id ) {
	wp_cache_delete( $product_id, 'post_meta' );
	clean_post_cache( $product_id );
	return (int) get_post_meta( $product_id, '_stock', true );
}

/** An order for one piece, as checkout would create it, with the piece's hold moved onto it. */
function lt_order_with_hold( WC_Product $product, $method = 'billplz', $session = null ) {
	$session = $session ?? 'sess-' . wp_generate_password( 8, false );
	$hold    = LT_Holds::acquire( $product->get_id(), array( 'session_key' => $session, 'visitor_hash' => 'v-' . $session ) );
	lt_assert( $hold['ok'], 'could not hold piece for the order' );
	$order = wc_create_order( array( 'created_via' => 'checkout' ) );
	$order->add_product( $product, 1 );
	$order->set_payment_method( $method );
	$order->set_billing_email( 'buyer@example.com' );
	$order->calculate_totals();
	$order->save();
	$moved = LT_Holds::transfer_to_order( array( $product->get_id() ), $order->get_id(), gmdate( 'Y-m-d H:i:s', time() + 1800 ), $session );
	lt_assert( $moved, 'hold did not move to the order' );
	return wc_get_order( $order->get_id() );
}

function lt_status( $order_id ) {
	return LT_Orders::fresh_status( $order_id );
}

/** Runs a PHP snippet in N separate processes at the same moment; returns their outputs. */
function lt_parallel( $n, $php_body ) {
	$script = tempnam( sys_get_temp_dir(), 'ltp' ) . '.php';
	file_put_contents( $script, "<?php\n" . $php_body );
	$start = microtime( true ) + 1.0; // all processes begin together
	$procs = array();
	for ( $i = 0; $i < $n; $i++ ) {
		$cmd = sprintf(
			'LT_DB_NAME=%s LT_START=%F LT_I=%d php %s %s %s',
			escapeshellarg( getenv( 'LT_DB_NAME' ) ),
			$start,
			$i,
			escapeshellarg( dirname( __DIR__, 2 ) . '/tools/local/wp-eval.php' ),
			escapeshellarg( $GLOBALS['lt_wp_dir'] ),
			escapeshellarg( $script )
		);
		$procs[] = popen( $cmd . ' 2>&1', 'r' );
	}
	$out = array();
	foreach ( $procs as $p ) {
		$out[] = trim( stream_get_contents( $p ) );
		pclose( $p );
	}
	unlink( $script );
	return $out;
}
