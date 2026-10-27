<?php
/**
 * Order rules for one-of-a-kind pieces.
 *
 *  - Checkout moves the cart's holds onto the order in one statement; if any hold
 *    has gone, checkout stops.
 *  - Payment confirmation runs under a per-order database lock. A duplicate
 *    confirmation (repeated Billplz callback, requery, redirect) changes nothing.
 *  - A payment only becomes a sale if every piece is still free. Otherwise the
 *    order is set to "Refund due", stock is not touched and staff are emailed.
 *  - Marking a piece out of stock in the editor (a boutique sale) ends cart holds
 *    and cancels unpaid orders holding it.
 *  - Cancelling or refunding a paid order never puts the piece back on sale by
 *    itself; staff use "Return pieces to stock" once the piece is back.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

use Automattic\WooCommerce\Utilities\OrderUtil;

final class LT_Orders {

	const RESERVED   = 'lt-reserved';
	const REFUND_DUE = 'lt-refund-due';

	/** @var array<int,string> Orders whose payment arrived for a piece no longer free. */
	private static $conflicts = array();
	/** @var array<int,bool> Orders being confirmed in this request. */
	private static $in_payment = array();
	/** @var array<int,bool> Duplicate confirmations to ignore. */
	private static $duplicates = array();
	/** @var array<string,bool> Database locks held by this request. */
	private static $locks = array();
	/** @var bool Re-entrancy guard for stock hooks. */
	private static $handling_stock = false;
	/** @var array<int,bool> Orders whose checkout lost a hold. */
	private static $hold_failures = array();

	public static function init() {
		add_action( 'init', array( __CLASS__, 'register_statuses' ) );
		add_filter( 'wc_order_statuses', array( __CLASS__, 'add_statuses' ) );
		add_filter( 'woocommerce_valid_order_statuses_for_cancel', array( __CLASS__, 'customer_can_cancel_reserved' ) );

		add_action( 'woocommerce_after_checkout_validation', array( __CLASS__, 'validate_holds' ), 10, 2 );
		add_action( 'woocommerce_checkout_order_created', array( __CLASS__, 'claim_holds' ) );
		add_action( 'woocommerce_checkout_order_exception', array( __CLASS__, 'on_checkout_exception' ) );

		add_action( 'woocommerce_pre_payment_complete', array( __CLASS__, 'guard_payment' ), 1, 1 );
		add_filter( 'woocommerce_valid_order_statuses_for_payment_complete', array( __CLASS__, 'skip_duplicates' ), 99, 2 );
		add_filter( 'woocommerce_payment_complete_order_status', array( __CLASS__, 'payment_status' ), 99, 2 );
		add_action( 'woocommerce_payment_complete', array( __CLASS__, 'after_payment' ), 99 );
		add_action( 'woocommerce_payment_complete_order_status_' . self::REFUND_DUE, array( __CLASS__, 'after_payment' ), 99 );
		add_filter( 'woocommerce_can_reduce_order_stock', array( __CLASS__, 'can_reduce_stock' ), 10, 2 );
		add_filter( 'woocommerce_can_restore_order_stock', array( __CLASS__, 'can_restore_stock' ), 10, 2 );
		add_action( 'shutdown', array( __CLASS__, 'release_locks' ) );

		add_action( 'woocommerce_order_status_changed', array( __CLASS__, 'on_status_changed' ), 10, 4 );
		add_action( 'woocommerce_product_set_stock', array( __CLASS__, 'on_stock_changed' ) );
		add_action( 'woocommerce_product_set_stock_status', array( __CLASS__, 'on_stock_status_changed' ), 10, 3 );
		add_filter( 'woocommerce_cancel_unpaid_order', array( __CLASS__, 'before_cancel_unpaid' ), 10, 2 );

		add_filter( 'woocommerce_order_actions', array( __CLASS__, 'order_actions' ), 10, 2 );
		add_action( 'woocommerce_order_action_lt_return_to_stock', array( __CLASS__, 'return_to_stock' ) );
		add_action( 'admin_footer', array( __CLASS__, 'refund_restock_default_off' ) );
	}

	/* ------------------------------------------------------------------ */
	/* Statuses                                                            */
	/* ------------------------------------------------------------------ */

	public static function register_statuses() {
		register_post_status(
			'wc-' . self::RESERVED,
			array(
				'label'                     => _x( 'Reserved – pay at boutique', 'Order status', 'labeltrunc' ),
				'public'                    => false,
				'exclude_from_search'       => false,
				'show_in_admin_all_list'    => true,
				'show_in_admin_status_list' => true,
				/* translators: %s: number of orders */
				'label_count'               => _n_noop( 'Reserved <span class="count">(%s)</span>', 'Reserved <span class="count">(%s)</span>', 'labeltrunc' ),
			)
		);
		register_post_status(
			'wc-' . self::REFUND_DUE,
			array(
				'label'                     => _x( 'Refund due', 'Order status', 'labeltrunc' ),
				'public'                    => false,
				'exclude_from_search'       => false,
				'show_in_admin_all_list'    => true,
				'show_in_admin_status_list' => true,
				/* translators: %s: number of orders */
				'label_count'               => _n_noop( 'Refund due <span class="count">(%s)</span>', 'Refund due <span class="count">(%s)</span>', 'labeltrunc' ),
			)
		);
	}

	public static function add_statuses( $statuses ) {
		$out = array();
		foreach ( $statuses as $key => $label ) {
			$out[ $key ] = $label;
			if ( 'wc-pending' === $key ) {
				$out[ 'wc-' . self::RESERVED ] = _x( 'Reserved – pay at boutique', 'Order status', 'labeltrunc' );
			}
			if ( 'wc-refunded' === $key ) {
				$out[ 'wc-' . self::REFUND_DUE ] = _x( 'Refund due', 'Order status', 'labeltrunc' );
			}
		}
		return $out;
	}

	public static function customer_can_cancel_reserved( $statuses ) {
		$statuses[] = self::RESERVED;
		return $statuses;
	}

	/* ------------------------------------------------------------------ */
	/* Helpers                                                             */
	/* ------------------------------------------------------------------ */

	/** Status straight from the database, bypassing object caches. */
	public static function fresh_status( $order_id ) {
		global $wpdb;
		if ( class_exists( OrderUtil::class ) && OrderUtil::custom_orders_table_usage_is_enabled() ) {
			$status = $wpdb->get_var( $wpdb->prepare( "SELECT status FROM {$wpdb->prefix}wc_orders WHERE id = %d", $order_id ) );
		} else {
			$status = $wpdb->get_var( $wpdb->prepare( "SELECT post_status FROM {$wpdb->posts} WHERE ID = %d", $order_id ) );
		}
		return $status ? preg_replace( '/^wc-/', '', $status ) : '';
	}

	/** Re-reads an order without cached data. */
	public static function reload( $order_id ) {
		if ( class_exists( OrderUtil::class ) && OrderUtil::custom_orders_table_usage_is_enabled() ) {
			wp_cache_delete( $order_id, 'orders' );
		}
		clean_post_cache( $order_id );
		return wc_get_order( $order_id );
	}

	private static function lock( $order_id ) {
		global $wpdb;
		$name = 'lt_order_' . (int) $order_id;
		if ( isset( self::$locks[ $name ] ) ) {
			return true;
		}
		$got = (int) $wpdb->get_var( $wpdb->prepare( 'SELECT GET_LOCK(%s, 15)', $name ) );
		if ( 1 === $got ) {
			self::$locks[ $name ] = true;
			return true;
		}
		return false;
	}

	private static function unlock( $order_id ) {
		global $wpdb;
		$name = 'lt_order_' . (int) $order_id;
		if ( isset( self::$locks[ $name ] ) ) {
			$wpdb->query( $wpdb->prepare( 'SELECT RELEASE_LOCK(%s)', $name ) );
			unset( self::$locks[ $name ] );
		}
	}

	public static function release_locks() {
		foreach ( array_keys( self::$locks ) as $name ) {
			self::unlock( (int) str_replace( 'lt_order_', '', $name ) );
		}
	}

	public static function product_ids( WC_Order $order ) {
		$ids = array();
		foreach ( $order->get_items() as $item ) {
			if ( $item instanceof WC_Order_Item_Product && $item->get_product_id() ) {
				$ids[] = (int) $item->get_product_id();
			}
		}
		return array_values( array_unique( $ids ) );
	}

	/** Fresh stock figures for a piece, bypassing caches. */
	private static function stock_of( $product_id ) {
		wp_cache_delete( $product_id, 'post_meta' );
		clean_post_cache( $product_id );
		return array(
			'qty'    => (int) get_post_meta( $product_id, '_stock', true ),
			'status' => (string) get_post_meta( $product_id, '_stock_status', true ),
			'manage' => 'yes' === get_post_meta( $product_id, '_manage_stock', true ),
		);
	}

	public static function stock_label( $product_id ) {
		$sku = get_post_meta( $product_id, '_sku', true );
		return $sku ? $sku : '#' . $product_id;
	}

	/* ------------------------------------------------------------------ */
	/* Checkout                                                            */
	/* ------------------------------------------------------------------ */

	public static function validate_holds( $data, $errors ) {
		if ( ! WC()->cart ) {
			return;
		}
		foreach ( WC()->cart->get_cart() as $item ) {
			$hold = LT_Holds::get( (int) $item['product_id'] );
			if ( ! LT_Holds::is_active( $hold ) || ! LT_Holds::is_mine( $hold ) ) {
				/* translators: %s: piece name */
				$errors->add( 'lt_hold', sprintf( __( 'Your hold on %s has ended. Please return to your cart.', 'labeltrunc' ), $item['data']->get_name() ) );
			}
		}
	}

	public static function claim_holds( WC_Order $order ) {
		$ids = self::product_ids( $order );
		if ( ! $ids ) {
			return;
		}
		$now      = time();
		$earliest = PHP_INT_MAX;
		foreach ( $ids as $id ) {
			$hold = LT_Holds::get( $id );
			if ( $hold ) {
				$earliest = min( $earliest, strtotime( $hold->expires_at . ' UTC' ) );
			}
		}
		$expires_ts = 'lt_pay_at_shop' === $order->get_payment_method()
			? $now + LT_Settings::get( 'pay_at_shop_hold_hours' ) * HOUR_IN_SECONDS
			: max( PHP_INT_MAX === $earliest ? $now : $earliest, $now + LT_Settings::get( 'payment_window_minutes' ) * MINUTE_IN_SECONDS );
		$expires    = gmdate( 'Y-m-d H:i:s', $expires_ts );

		if ( ! LT_Holds::transfer_to_order( $ids, $order->get_id(), $expires, LT_Holds::session_key() ) ) {
			self::$hold_failures[ $order->get_id() ] = true;
			throw new Exception( esc_html__( 'One of your holds ended or the piece has just sold. Please check your cart and try again.', 'labeltrunc' ) );
		}
		$order->update_meta_data( '_lt_hold_expires', $expires );
		$order->save_meta_data();
	}

	public static function on_checkout_exception( $order ) {
		if ( $order instanceof WC_Order && ! empty( self::$hold_failures[ $order->get_id() ] ) ) {
			// Give any holds that did move back to this cart, then close the empty order.
			global $wpdb;
			$table = LT_Schema::table();
			$wpdb->query( $wpdb->prepare( "UPDATE {$table} SET order_id = NULL, session_key = %s WHERE order_id = %d", LT_Holds::session_key(), $order->get_id() ) ); // phpcs:ignore
			$order->update_status( 'cancelled', __( 'Checkout stopped: a hold had ended before the order could be placed.', 'labeltrunc' ) );
		}
	}

	/* ------------------------------------------------------------------ */
	/* Payment confirmation                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * Runs before WooCommerce records a payment. Takes the order's lock, ignores
	 * duplicates, and decides whether the sale can stand.
	 */
	public static function guard_payment( $order_id ) {
		$order_id = (int) $order_id;
		if ( ! self::lock( $order_id ) ) {
			throw new Exception( 'Label Trunc: could not lock order ' . (int) $order_id . ' to record payment.' );
		}
		$status = self::fresh_status( $order_id );
		if ( in_array( $status, array( 'processing', 'completed', self::REFUND_DUE, 'refunded' ), true ) ) {
			self::$duplicates[ $order_id ] = true;
			return;
		}
		self::$in_payment[ $order_id ] = true;

		$order = self::reload( $order_id );
		if ( ! $order || $order->get_order_stock_reduced() ) {
			return; // Stock already belongs to this order (e.g. a reservation paid later).
		}
		$problems = array();
		foreach ( self::product_ids( $order ) as $pid ) {
			$stock = self::stock_of( $pid );
			$hold  = LT_Holds::get( $pid );
			$other = LT_Holds::is_active( $hold ) && (int) $hold->order_id !== $order_id;
			if ( 'instock' !== $stock['status'] || ( $stock['manage'] && $stock['qty'] < 1 ) || $other ) {
				$problems[] = self::stock_label( $pid );
			}
		}
		if ( $problems ) {
			self::$conflicts[ $order_id ] = implode( ', ', $problems );
		}
	}

	public static function skip_duplicates( $statuses, $order ) {
		return ( $order && ! empty( self::$duplicates[ $order->get_id() ] ) ) ? array() : $statuses;
	}

	public static function payment_status( $status, $order_id ) {
		return isset( self::$conflicts[ (int) $order_id ] ) ? self::REFUND_DUE : $status;
	}

	public static function can_reduce_stock( $can, $order ) {
		if ( ! $order ) {
			return $can;
		}
		if ( isset( self::$conflicts[ $order->get_id() ] ) || $order->has_status( self::REFUND_DUE ) ) {
			return false;
		}
		return $can;
	}

	/** Paid orders never put pieces back on sale by themselves. */
	public static function can_restore_stock( $can, $order ) {
		if ( $can && $order && $order->get_date_paid( 'edit' ) ) {
			if ( ! $order->get_meta( '_lt_restock_note' ) ) {
				$order->add_order_note( __( 'The piece stays marked as sold. When it is back in the boutique, use "Return pieces to stock".', 'labeltrunc' ) );
				$order->update_meta_data( '_lt_restock_note', 1 );
				$order->save_meta_data();
			}
			return false;
		}
		return $can;
	}

	public static function after_payment( $order_id ) {
		$order_id = (int) $order_id;
		if ( ! empty( self::$duplicates[ $order_id ] ) ) {
			self::unlock( $order_id );
			return;
		}
		LT_Holds::release_order_holds( $order_id );
		if ( isset( self::$conflicts[ $order_id ] ) ) {
			$order = wc_get_order( $order_id );
			if ( $order ) {
				/* translators: %s: stock numbers */
				$order->add_order_note( sprintf( __( 'REFUND DUE: payment received, but %s was already sold or held by someone else. Stock was not changed. Refund the customer and contact them.', 'labeltrunc' ), self::$conflicts[ $order_id ] ) );
				self::email_staff( $order, __( 'Action needed: refund due', 'labeltrunc' ), __( 'A payment arrived for a piece that is no longer available. Stock was not changed. Please refund the customer and contact them.', 'labeltrunc' ) );
				self::email_customer(
					$order,
					__( 'About your Label Trunc order', 'labeltrunc' ),
					__( 'We have received your payment', 'labeltrunc' ),
					'<p>' . esc_html__( 'Thank you for your payment. Unfortunately the piece was sold just before your payment reached us. We will refund you in full and be in touch shortly.', 'labeltrunc' ) . '</p>'
				);
			}
		}
		unset( self::$in_payment[ $order_id ] );
		self::unlock( $order_id );
	}

	/* ------------------------------------------------------------------ */
	/* Status changes                                                      */
	/* ------------------------------------------------------------------ */

	public static function on_status_changed( $order_id, $from, $to, $order ) {
		if ( in_array( $to, array( 'cancelled', 'failed', 'refunded' ), true ) ) {
			LT_Holds::release_order_holds( $order_id );
			if ( in_array( $from, array( 'pending', 'on-hold' ), true ) ) {
				LT_Billplz::void_bill( $order );
			}
		}
		if ( in_array( $to, array( 'processing', 'completed' ), true ) ) {
			LT_Holds::release_order_holds( $order_id );
		}
		if ( self::RESERVED === $to && 'pending' === $from ) {
			self::email_reserved( $order );
		}
	}

	/**
	 * Ends an unpaid order whose hold ran out. For online orders, Billplz is asked
	 * first: a payment whose callback never arrived is recorded instead.
	 */
	public static function end_unpaid_order( $order_id, $reason = 'expired' ) {
		if ( ! self::lock( $order_id ) ) {
			return;
		}
		try {
			$status = self::fresh_status( $order_id );
			$order  = self::reload( $order_id );
			if ( ! $order || ! in_array( $status, array( 'pending', 'on-hold', self::RESERVED ), true ) ) {
				LT_Holds::release_order_holds( $order_id );
				return;
			}
			if ( 'pending' === $status || 'on-hold' === $status ) {
				self::unlock( $order_id ); // settle_if_paid → payment_complete takes the lock itself.
				if ( LT_Billplz::settle_if_paid( $order ) ) {
					return;
				}
				if ( ! self::lock( $order_id ) || ! in_array( self::fresh_status( $order_id ), array( 'pending', 'on-hold' ), true ) ) {
					return;
				}
				$order = self::reload( $order_id );
				$order->update_status( 'cancelled', 'expired' === $reason ? __( 'Hold ended before payment was received.', 'labeltrunc' ) : $reason );
				return;
			}
			$order->update_status( 'cancelled', __( 'Reservation ended: the piece was not collected in time.', 'labeltrunc' ) );
			self::email_customer(
				$order,
				__( 'Your Label Trunc reservation has ended', 'labeltrunc' ),
				__( 'Your hold has ended', 'labeltrunc' ),
				'<p>' . esc_html__( 'The hold on your reservation has ended and the piece is back in the shop. If you would still like it, message us and we will check whether it is available.', 'labeltrunc' ) . '</p>'
			);
		} finally {
			self::unlock( $order_id );
		}
	}

	/** Before WooCommerce cancels an unpaid order, check Billplz in case the callback was lost. */
	public static function before_cancel_unpaid( $cancel, $order ) {
		if ( $cancel && $order instanceof WC_Order && LT_Billplz::settle_if_paid( $order ) ) {
			return false;
		}
		return $cancel;
	}

	/* ------------------------------------------------------------------ */
	/* Stock changes (boutique sales)                                      */
	/* ------------------------------------------------------------------ */

	public static function on_stock_changed( $product ) {
		if ( ! $product instanceof WC_Product ) {
			return;
		}
		$qty = $product->get_stock_quantity();
		if ( null !== $qty && $qty < 1 ) {
			self::piece_gone( $product->get_id() );
		} elseif ( null !== $qty && $qty >= 1 ) {
			self::piece_back( $product->get_id() );
		}
	}

	public static function on_stock_status_changed( $product_id, $status ) {
		if ( 'outofstock' === $status ) {
			self::piece_gone( (int) $product_id );
		} elseif ( 'instock' === $status ) {
			self::piece_back( (int) $product_id );
		}
	}

	/** A piece is no longer for sale: end holds and unpaid orders that are waiting on it. */
	public static function piece_gone( $product_id ) {
		if ( self::$handling_stock ) {
			return;
		}
		self::$handling_stock = true;
		try {
			if ( ! get_post_meta( $product_id, '_lt_sold_at', true ) ) {
				update_post_meta( $product_id, '_lt_sold_at', time() );
			}
			LT_Holds::release_cart_holds_for_product( $product_id );
			$hold = LT_Holds::get( $product_id );
			if ( ! $hold || ! $hold->order_id ) {
				return;
			}
			$order_id = (int) $hold->order_id;
			if ( ! empty( self::$in_payment[ $order_id ] ) ) {
				return; // This order's own sale.
			}
			$status = self::fresh_status( $order_id );
			if ( in_array( $status, array( 'pending', 'on-hold' ), true ) ) {
				// Sold in the boutique while an online customer was checking out.
				$order = self::reload( $order_id );
				LT_Holds::release_order_holds( $order_id );
				if ( $order ) {
					$who = wp_get_current_user();
					$order->update_status(
						'cancelled',
						/* translators: 1: stock number, 2: staff name */
						sprintf( __( 'Cancelled automatically: %1$s was marked sold or out of stock%2$s.', 'labeltrunc' ), self::stock_label( $product_id ), $who && $who->exists() ? ' by ' . $who->display_name : '' )
					);
					self::email_customer(
						$order,
						__( 'About your Label Trunc order', 'labeltrunc' ),
						__( 'Sorry, this piece is no longer available', 'labeltrunc' ),
						'<p>' . esc_html__( 'The piece in your order was sold in the boutique before payment was completed, so we have cancelled the order. Nothing has been charged. If a payment does go through, we will refund it in full.', 'labeltrunc' ) . '</p>'
					);
				}
			} else {
				LT_Holds::release_order_holds( $order_id );
			}
		} finally {
			self::$handling_stock = false;
		}
	}

	/** A piece is on sale again (restocked by staff). */
	public static function piece_back( $product_id ) {
		delete_post_meta( $product_id, '_lt_sold_at' );
		if ( get_post_meta( $product_id, '_lt_hidden_sold', true ) ) {
			$product = wc_get_product( $product_id );
			if ( $product ) {
				$product->set_catalog_visibility( 'visible' );
				$product->save();
			}
			delete_post_meta( $product_id, '_lt_hidden_sold' );
		}
	}

	/* ------------------------------------------------------------------ */
	/* Admin                                                               */
	/* ------------------------------------------------------------------ */

	public static function order_actions( $actions, $order = null ) {
		if ( $order instanceof WC_Order && $order->has_status( array( 'cancelled', 'refunded', self::REFUND_DUE ) ) ) {
			$actions['lt_return_to_stock'] = __( 'Return pieces to stock (piece is back in the boutique)', 'labeltrunc' );
		}
		return $actions;
	}

	public static function return_to_stock( WC_Order $order ) {
		$labels = array();
		foreach ( self::product_ids( $order ) as $pid ) {
			$product = wc_get_product( $pid );
			if ( ! $product ) {
				continue;
			}
			$product->set_manage_stock( true );
			$product->set_stock_quantity( 1 );
			$product->set_stock_status( 'instock' );
			$product->save();
			$labels[] = self::stock_label( $pid );
		}
		if ( $labels ) {
			/* translators: %s: stock numbers */
			$order->add_order_note( sprintf( __( 'Returned to stock by staff: %s.', 'labeltrunc' ), implode( ', ', $labels ) ), false, true );
		}
	}

	/** In the refund panel, "Restock refunded items" starts unticked. */
	public static function refund_restock_default_off() {
		$screen = function_exists( 'get_current_screen' ) ? get_current_screen() : null;
		if ( ! $screen || ! in_array( $screen->id, array( 'shop_order', 'woocommerce_page_wc-orders' ), true ) ) {
			return;
		}
		echo "<script>jQuery(function($){ $(document.body).on('click', '.refund-items', function(){ $('#restock_refunded_items').prop('checked', false); }); });</script>";
	}

	/* ------------------------------------------------------------------ */
	/* Emails                                                              */
	/* ------------------------------------------------------------------ */

	public static function email_customer( WC_Order $order, $subject, $heading, $body_html ) {
		$to = $order->get_billing_email();
		if ( ! $to ) {
			return;
		}
		$mailer = WC()->mailer();
		$link   = '<p><a href="' . esc_url( $order->get_view_order_url() ) . '">' . esc_html__( 'View your order', 'labeltrunc' ) . '</a></p>';
		$mailer->send( $to, $subject, $mailer->wrap_message( $heading, $body_html . $link ) );
	}

	public static function email_staff( WC_Order $order, $subject, $body_text ) {
		$settings  = get_option( 'woocommerce_new_order_settings' );
		$recipient = is_array( $settings ) && ! empty( $settings['recipient'] ) ? $settings['recipient'] : get_option( 'admin_email' );
		$mailer    = WC()->mailer();
		$body      = '<p>' . esc_html( $body_text ) . '</p><p><a href="' . esc_url( $order->get_edit_order_url() ) . '">' . esc_html( sprintf( 'Order #%s', $order->get_order_number() ) ) . '</a></p>';
		$mailer->send( $recipient, $subject . ' · #' . $order->get_order_number(), $mailer->wrap_message( $subject, $body ) );
	}

	public static function email_reserved( WC_Order $order ) {
		$until   = $order->get_meta( '_lt_hold_expires' );
		$when    = $until ? LT_Holds::format_time( $until ) : '';
		$address = get_theme_mod( 'lt_address', '' );
		self::email_customer(
			$order,
			/* translators: %s: order number */
			sprintf( __( 'Reserved for you: order #%s', 'labeltrunc' ), $order->get_order_number() ),
			__( 'Your piece is reserved', 'labeltrunc' ),
			'<p>' . esc_html( sprintf( /* translators: %s: date and time */ __( 'We are holding your piece until %s. Come to the boutique to pay and collect; message us first and we will have it ready.', 'labeltrunc' ), $when ) ) . '</p>' . ( $address ? '<p>' . nl2br( esc_html( $address ) ) . '</p>' : '' )
		);
		self::email_staff( $order, __( 'New reservation (pay at boutique)', 'labeltrunc' ), sprintf( /* translators: %s: date and time */ __( 'Held until %s.', 'labeltrunc' ), $when ) );
	}
}
