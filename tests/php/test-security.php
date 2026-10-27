<?php

function test_sign_in_pauses_after_five_failures() {
	$user = wp_insert_user( array( 'user_login' => 'shopper' . wp_rand(), 'user_pass' => 'correct-horse-1', 'user_email' => 'shopper' . wp_rand() . '@example.com' ) );
	$login = get_userdata( $user )->user_login;
	delete_transient( 'lt_fail_' . substr( hash_hmac( 'sha256', '127.0.0.1', wp_salt( 'auth' ) ), 0, 32 ) );
	for ( $i = 0; $i < 5; $i++ ) {
		lt_assert( is_wp_error( wp_authenticate( $login, 'wrong' ) ) );
	}
	$locked = wp_authenticate( $login, 'correct-horse-1' );
	lt_assert( is_wp_error( $locked ) && 'lt_locked' === $locked->get_error_code(), 'locked even with the right password' );
	LT_Security::clear_failures();
	lt_assert( ! is_wp_error( wp_authenticate( $login, 'correct-horse-1' ) ), 'works after the pause' );
	require_once ABSPATH . 'wp-admin/includes/user.php';
	wp_delete_user( $user );
}

function test_visitors_cannot_list_users_through_rest() {
	wp_set_current_user( 0 );
	$routes = rest_get_server()->get_routes();
	lt_assert( ! isset( $routes['/wp/v2/users'] ), 'users endpoint hidden from visitors' );
}

function test_new_pieces_get_a_stock_number() {
	$p = lt_product();
	LT_Product_Fields::ensure_sku( $p->get_id() );
	lt_eq( 'LT-' . str_pad( (string) $p->get_id(), 4, '0', STR_PAD_LEFT ), wc_get_product( $p->get_id() )->get_sku() );
}
