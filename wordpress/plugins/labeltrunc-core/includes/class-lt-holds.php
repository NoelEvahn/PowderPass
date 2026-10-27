<?php
/**
 * Cart holds for one-of-a-kind pieces.
 *
 * Adding a piece to the cart inserts a row keyed by product_id. The insert is
 * atomic (INSERT IGNORE on the primary key), so when two people add the same
 * piece at the same moment exactly one succeeds. Everything else reads this
 * table; the browser is never trusted for availability.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Holds {

	public static function init() {
		add_filter( 'woocommerce_add_to_cart_validation', array( __CLASS__, 'validate_add_to_cart' ), 20, 2 );
		add_action( 'woocommerce_remove_cart_item', array( __CLASS__, 'on_remove_item' ), 10, 2 );
		add_action( 'woocommerce_cart_emptied', array( __CLASS__, 'release_session_holds' ) );
		add_action( 'woocommerce_check_cart_items', array( __CLASS__, 'check_cart' ), 5 );
		add_action( 'wp_login', array( __CLASS__, 'on_login' ), 5, 2 );
		add_filter( 'woocommerce_is_purchasable', array( __CLASS__, 'is_purchasable' ), 20, 2 );
		add_filter( 'woocommerce_get_item_data', array( __CLASS__, 'cart_item_data' ), 10, 2 );
	}

	/* ------------------------------------------------------------------ */
	/* Identity                                                            */
	/* ------------------------------------------------------------------ */

	public static function session_key() {
		if ( ! function_exists( 'WC' ) || ! WC()->session ) {
			return '';
		}
		return (string) WC()->session->get_customer_id();
	}

	/** The pending order this browser is paying for, whose holds also count as "mine". */
	public static function awaiting_order_id() {
		return ( function_exists( 'WC' ) && WC()->session ) ? absint( WC()->session->get( 'order_awaiting_payment' ) ) : 0;
	}

	/** Salted hash of the connection's IP. Not reversible; used only to cap holds per visitor. */
	public static function visitor_hash() {
		$ip = isset( $_SERVER['REMOTE_ADDR'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REMOTE_ADDR'] ) ) : '';
		return hash_hmac( 'sha256', $ip, wp_salt( 'nonce' ) );
	}

	public static function hold_hours() {
		return is_user_logged_in() ? LT_Settings::get( 'cart_hold_hours' ) : LT_Settings::get( 'guest_hold_hours' );
	}

	public static function now() {
		return gmdate( 'Y-m-d H:i:s' );
	}

	/* ------------------------------------------------------------------ */
	/* Reads                                                               */
	/* ------------------------------------------------------------------ */

	public static function get( $product_id ) {
		global $wpdb;
		$table = LT_Schema::table();
		return $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$table} WHERE product_id = %d", $product_id ) ); // phpcs:ignore WordPress.DB.PreparedSQL.InterpolatedNotPrepared
	}

	public static function is_active( $hold ) {
		return $hold && strtotime( $hold->expires_at . ' UTC' ) > time();
	}

	public static function is_mine( $hold ) {
		if ( ! $hold ) {
			return false;
		}
		$key = self::session_key();
		if ( $key && null === $hold->order_id && $hold->session_key === $key ) {
			return true;
		}
		$awaiting = self::awaiting_order_id();
		return $awaiting && (int) $hold->order_id === $awaiting;
	}

	/** Active hold on a piece by someone other than this visitor, or null. */
	public static function held_by_other( $product_id ) {
		$hold = self::get( $product_id );
		if ( ! self::is_active( $hold ) || self::is_mine( $hold ) ) {
			return null;
		}
		return $hold;
	}

	/* ------------------------------------------------------------------ */
	/* Writes                                                              */
	/* ------------------------------------------------------------------ */

	/**
	 * Tries to hold a piece for this visitor's cart.
	 *
	 * @return array{ok:bool,reason?:string,expires_at?:string,already?:bool}
	 */
	public static function acquire( $product_id, array $who = array() ) {
		global $wpdb;
		$table   = LT_Schema::table();
		$key     = $who['session_key'] ?? self::session_key();
		$visitor = $who['visitor_hash'] ?? self::visitor_hash();
		$user_id = $who['user_id'] ?? get_current_user_id();
		$hours   = $who['hours'] ?? self::hold_hours();
		$mine    = static function ( $hold ) use ( $key, $who ) {
			return isset( $who['session_key'] ) ? ( $hold && null === $hold->order_id && $hold->session_key === $key ) : LT_Holds::is_mine( $hold );
		};
		if ( ! $key ) {
			return array(
				'ok'     => false,
				'reason' => 'session',
			);
		}

		self::clear_if_expired( $product_id );

		$existing = self::get( $product_id );
		if ( self::is_active( $existing ) ) {
			return $mine( $existing )
				? array(
					'ok'         => true,
					'expires_at' => $existing->expires_at,
					'already'    => true,
				)
				: array(
					'ok'         => false,
					'reason'     => 'held',
					'expires_at' => $existing->expires_at,
				);
		}

		$in_cart = (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE session_key = %s AND order_id IS NULL AND expires_at > %s", $key, self::now() ) ); // phpcs:ignore
		if ( $in_cart >= LT_Settings::get( 'max_items_per_cart' ) ) {
			return array(
				'ok'     => false,
				'reason' => 'cart_full',
			);
		}
		$by_person = (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE visitor_hash = %s AND order_id IS NULL AND expires_at > %s", $visitor, self::now() ) ); // phpcs:ignore
		if ( $by_person >= LT_Settings::get( 'max_holds_per_visitor' ) ) {
			return array(
				'ok'     => false,
				'reason' => 'visitor_limit',
			);
		}

		$expires  = gmdate( 'Y-m-d H:i:s', time() + $hours * HOUR_IN_SECONDS );
		$inserted = $wpdb->query(
			$wpdb->prepare(
				"INSERT IGNORE INTO {$table} (product_id, session_key, user_id, visitor_hash, expires_at, created_at) VALUES (%d, %s, %d, %s, %s, %s)", // phpcs:ignore
				$product_id,
				$key,
				$user_id,
				$visitor,
				$expires,
				self::now()
			)
		);
		if ( 1 === (int) $inserted ) {
			return array(
				'ok'         => true,
				'expires_at' => $expires,
			);
		}
		// Someone else's insert won the race.
		$winner = self::get( $product_id );
		return $mine( $winner )
			? array(
				'ok'         => true,
				'expires_at' => $winner->expires_at,
				'already'    => true,
			)
			: array(
				'ok'         => false,
				'reason'     => 'held',
				'expires_at' => $winner ? $winner->expires_at : '',
			);
	}

	/** Removes an expired hold on a piece. An expired order hold first ends its order. */
	public static function clear_if_expired( $product_id ) {
		global $wpdb;
		$table = LT_Schema::table();
		$hold  = self::get( $product_id );
		if ( ! $hold || self::is_active( $hold ) ) {
			return;
		}
		if ( $hold->order_id ) {
			LT_Orders::end_unpaid_order( (int) $hold->order_id, 'expired' );
		}
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE product_id = %d AND expires_at <= %s", $product_id, self::now() ) ); // phpcs:ignore
	}

	public static function release( $product_id, $session_key = null ) {
		global $wpdb;
		$table = LT_Schema::table();
		$key   = null === $session_key ? self::session_key() : $session_key;
		if ( ! $key ) {
			return;
		}
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE product_id = %d AND session_key = %s AND order_id IS NULL", $product_id, $key ) ); // phpcs:ignore
	}

	public static function release_session_holds() {
		global $wpdb;
		$key = self::session_key();
		if ( ! $key ) {
			return;
		}
		$table = LT_Schema::table();
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE session_key = %s AND order_id IS NULL", $key ) ); // phpcs:ignore
	}

	public static function release_order_holds( $order_id ) {
		global $wpdb;
		$table = LT_Schema::table();
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE order_id = %d", $order_id ) ); // phpcs:ignore
	}

	/** Cart holds (not order holds) on a piece end, e.g. when it is sold in the boutique. */
	public static function release_cart_holds_for_product( $product_id ) {
		global $wpdb;
		$table = LT_Schema::table();
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE product_id = %d AND order_id IS NULL", $product_id ) ); // phpcs:ignore
	}

	public static function purge_expired_cart_holds() {
		global $wpdb;
		$table = LT_Schema::table();
		return (int) $wpdb->query( $wpdb->prepare( "DELETE FROM {$table} WHERE order_id IS NULL AND expires_at <= %s", self::now() ) ); // phpcs:ignore
	}

	/**
	 * Moves this cart's holds onto an order in one statement. Returns true only if
	 * every piece moved, so a second, simultaneous checkout of the same cart fails.
	 */
	public static function transfer_to_order( array $product_ids, $order_id, $expires_at, $session_key ) {
		global $wpdb;
		if ( ! $product_ids ) {
			return false;
		}
		$table = LT_Schema::table();
		$ids   = implode( ',', array_map( 'absint', $product_ids ) );
		$moved = $wpdb->query(
			$wpdb->prepare(
				"UPDATE {$table} SET order_id = %d, session_key = NULL, expires_at = %s
				 WHERE product_id IN ({$ids}) AND expires_at > %s
				 AND ( (session_key = %s AND order_id IS NULL) OR order_id = %d )", // phpcs:ignore
				$order_id,
				$expires_at,
				self::now(),
				$session_key,
				$order_id
			)
		);
		$held = (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE product_id IN ({$ids}) AND order_id = %d", $order_id ) ); // phpcs:ignore
		return false !== $moved && count( $product_ids ) === $held;
	}

	/* ------------------------------------------------------------------ */
	/* WooCommerce hooks                                                   */
	/* ------------------------------------------------------------------ */

	public static function validate_add_to_cart( $passed, $product_id ) {
		if ( ! $passed ) {
			return false;
		}
		$product = wc_get_product( $product_id );
		if ( ! $product || ! $product->is_in_stock() ) {
			wc_add_notice( __( 'Sorry, this piece has just sold.', 'labeltrunc' ), 'error' );
			return false;
		}
		if ( WC()->session && ! WC()->session->has_session() ) {
			WC()->session->set_customer_session_cookie( true );
		}
		$result = self::acquire( $product_id );
		if ( $result['ok'] ) {
			if ( ! empty( $result['already'] ) && WC()->cart && WC()->cart->find_product_in_cart( WC()->cart->generate_cart_id( $product_id ) ) ) {
				wc_add_notice( sprintf( __( 'Already in your cart. Held for you until %s.', 'labeltrunc' ), self::format_time( $result['expires_at'] ) ), 'notice' );
				return false;
			}
			return true;
		}
		$messages = array(
			'held'          => sprintf(
				/* translators: %s: time the hold ends */
				__( 'Someone is holding this piece until %s. It may come back if they do not buy it.', 'labeltrunc' ),
				self::format_time( $result['expires_at'] ?? '' )
			),
			'cart_full'     => __( 'Your cart is full. Check out or remove a piece before adding another.', 'labeltrunc' ),
			'visitor_limit' => __( 'You are holding as many pieces as we allow at once. Check out or remove one first.', 'labeltrunc' ),
			'session'       => __( 'Please enable cookies so we can hold this piece for you.', 'labeltrunc' ),
		);
		wc_add_notice( $messages[ $result['reason'] ] ?? __( 'This piece cannot be added right now.', 'labeltrunc' ), 'error' );
		return false;
	}

	public static function on_remove_item( $cart_item_key, $cart ) {
		$item = $cart->removed_cart_contents[ $cart_item_key ] ?? ( $cart->cart_contents[ $cart_item_key ] ?? null );
		if ( $item && ! empty( $item['product_id'] ) ) {
			self::release( (int) $item['product_id'] );
		}
	}

	/** Removes pieces whose hold has ended, so checkout can never proceed without a hold. */
	public static function check_cart() {
		if ( ! WC()->cart ) {
			return;
		}
		foreach ( WC()->cart->get_cart() as $key => $item ) {
			$product_id = (int) $item['product_id'];
			$hold       = self::get( $product_id );
			if ( self::is_active( $hold ) && self::is_mine( $hold ) ) {
				continue;
			}
			// Holds are never renewed silently: that would let one person keep a piece indefinitely.
			self::clear_if_expired( $product_id );
			$name = $item['data'] ? $item['data']->get_name() : '';
			WC()->cart->remove_cart_item( $key );
			wc_add_notice(
				self::held_by_other( $product_id ) || ! wc_get_product( $product_id )->is_in_stock()
					/* translators: %s: piece name */
					? sprintf( __( '%s is no longer available, so it was removed from your cart.', 'labeltrunc' ), $name )
					/* translators: %s: piece name */
					: sprintf( __( 'Your hold on %s ended, so it was removed from your cart. You can add it again while it is still available.', 'labeltrunc' ), $name ),
				'error'
			);
		}
	}

	/** When a guest signs in, their holds follow them to their account. */
	public static function on_login( $user_login, $user ) {
		global $wpdb;
		if ( ! function_exists( 'WC' ) || ! WC()->session ) {
			return;
		}
		$guest_key = (string) WC()->session->get_customer_id();
		if ( ! $guest_key || (string) $user->ID === $guest_key ) {
			return;
		}
		$table = LT_Schema::table();
		$wpdb->query( $wpdb->prepare( "UPDATE {$table} SET session_key = %s, user_id = %d WHERE session_key = %s AND order_id IS NULL", (string) $user->ID, $user->ID, $guest_key ) ); // phpcs:ignore
	}

	/** A piece someone else is holding cannot be added to a cart. */
	public static function is_purchasable( $purchasable, $product ) {
		if ( ! $purchasable || is_admin() ) {
			return $purchasable;
		}
		return self::held_by_other( $product->get_id() ) ? false : $purchasable;
	}

	public static function cart_item_data( $data, $item ) {
		$hold = self::get( (int) $item['product_id'] );
		if ( self::is_active( $hold ) && self::is_mine( $hold ) ) {
			$data[] = array(
				'key'     => __( 'Held for you until', 'labeltrunc' ),
				'value'   => esc_html( self::format_time( $hold->expires_at ) ),
				'display' => '<time datetime="' . esc_attr( gmdate( 'c', strtotime( $hold->expires_at . ' UTC' ) ) ) . '">' . esc_html( self::format_time( $hold->expires_at ) ) . '</time>',
			);
		}
		return $data;
	}

	/** "Tue 27 Oct, 3:45pm" in the shop's timezone. */
	public static function format_time( $utc ) {
		if ( ! $utc ) {
			return '';
		}
		return wp_date( 'D j M, g:ia', strtotime( $utc . ' UTC' ) );
	}
}
