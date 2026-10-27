<?php
/**
 * LOCAL DEVELOPMENT ONLY: puts the labelled test pieces back on sale and clears
 * holds and test orders before the browser tests. Refuses on non-localhost sites.
 */
if ( false === strpos( home_url(), 'localhost' ) ) {
	fwrite( STDERR, "Refusing: only for a localhost site.\n" );
	exit( 1 );
}
global $wpdb;
update_option( 'woocommerce_coming_soon', 'no' ); // local only: the live site goes live from WooCommerce > Settings > Site visibility
$wpdb->query( "DELETE FROM {$wpdb->prefix}lt_holds" );
foreach ( wc_get_orders( array( 'limit' => -1, 'return' => 'ids', 'status' => array_keys( wc_get_order_statuses() ) ) ) as $order_id ) {
	wc_get_order( $order_id )->delete( true );
}
$ids = get_posts( array( 'post_type' => 'product', 'post_status' => 'any', 's' => 'Test piece', 'fields' => 'ids', 'posts_per_page' => -1 ) );
foreach ( $ids as $id ) {
	$p = wc_get_product( $id );
	if ( 'Test piece 07' === $p->get_name() ) { continue; } // stays sold
	$p->set_stock_quantity( 1 );
	$p->set_stock_status( 'instock' );
	$p->set_catalog_visibility( 'visible' );
	$p->save();
	delete_post_meta( $id, '_lt_sold_at' );
}
$wpdb->query( "DELETE FROM {$wpdb->options} WHERE option_name LIKE '_transient_lt_%' OR option_name LIKE '_transient_timeout_lt_%'" );
$wpdb->query( "DELETE FROM {$wpdb->prefix}woocommerce_sessions" );
echo 'reset ' . count( $ids ) . " test pieces\n";
