<?php
/**
 * "Reserve now, pay at the boutique" payment method.
 *
 * The order becomes "Reserved – pay at boutique", stock is reduced so nobody else
 * can buy the piece, and the reservation ends automatically after the hold time.
 * Staff mark the order Processing or Completed when the customer pays in person.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

class LT_Gateway_Pay_At_Shop extends WC_Payment_Gateway {

	public function __construct() {
		$this->id                 = 'lt_pay_at_shop';
		$this->method_title       = __( 'Reserve and pay at the boutique', 'labeltrunc' );
		$this->method_description = __( 'Customers reserve a piece online and pay when they collect it. The piece is held for the time set in Products → Label Trunc holds.', 'labeltrunc' );
		$this->has_fields         = false;
		$this->supports           = array( 'products' );

		$this->init_form_fields();
		$this->init_settings();

		$this->title       = $this->get_option( 'title' );
		$this->description = $this->get_option( 'description' );

		add_action( 'woocommerce_update_options_payment_gateways_' . $this->id, array( $this, 'process_admin_options' ) );
		add_action( 'woocommerce_thankyou_' . $this->id, array( $this, 'thankyou_page' ) );
	}

	public function init_form_fields() {
		$this->form_fields = array(
			'enabled'     => array(
				'title'   => __( 'Enable', 'labeltrunc' ),
				'type'    => 'checkbox',
				'label'   => __( 'Offer "reserve now, pay at the boutique"', 'labeltrunc' ),
				'default' => 'yes',
			),
			'title'       => array(
				'title'   => __( 'Title', 'labeltrunc' ),
				'type'    => 'text',
				'default' => __( 'Reserve now, pay at the boutique', 'labeltrunc' ),
			),
			'description' => array(
				'title'       => __( 'Description', 'labeltrunc' ),
				'type'        => 'textarea',
				'description' => __( '{hours} is replaced with the hold time.', 'labeltrunc' ),
				'default'     => __( 'We hold the piece for {hours} hours. Pay and collect at Ukay Boulevard, Ampang.', 'labeltrunc' ),
			),
		);
	}

	public function get_description() {
		$hours = LT_Settings::get( 'pay_at_shop_hold_hours' );
		return apply_filters( 'woocommerce_gateway_description', str_replace( '{hours}', (string) $hours, $this->description ), $this->id );
	}

	/** Only for collection at the boutique: a delivered order must be paid online. */
	public function is_available() {
		if ( ! parent::is_available() ) {
			return false;
		}
		if ( ! WC()->cart || ! WC()->cart->needs_shipping() ) {
			return true;
		}
		$chosen = (array) ( WC()->session ? WC()->session->get( 'chosen_shipping_methods' ) : array() );
		foreach ( $chosen as $method ) {
			if ( is_string( $method ) && ( 0 === strpos( $method, 'local_pickup' ) || 0 === strpos( $method, 'pickup_location' ) ) ) {
				return true;
			}
		}
		return false;
	}

	public function process_payment( $order_id ) {
		$order   = wc_get_order( $order_id );
		$expires = gmdate( 'Y-m-d H:i:s', time() + LT_Settings::get( 'pay_at_shop_hold_hours' ) * HOUR_IN_SECONDS );

		global $wpdb;
		$table = LT_Schema::table();
		$wpdb->query( $wpdb->prepare( "UPDATE {$table} SET expires_at = %s WHERE order_id = %d", $expires, $order_id ) ); // phpcs:ignore
		$order->update_meta_data( '_lt_hold_expires', $expires );
		$order->save_meta_data();

		/* translators: %s: date and time */
		$order->update_status( LT_Orders::RESERVED, sprintf( __( 'Reserved online until %s. Customer pays at the boutique.', 'labeltrunc' ), LT_Holds::format_time( $expires ) ) );
		wc_reduce_stock_levels( $order_id );
		// Record that this order owns the stock, so cancelling it puts the piece back.
		$order->get_data_store()->set_stock_reduced( $order_id, true );
		WC()->cart->empty_cart();

		return array(
			'result'   => 'success',
			'redirect' => $this->get_return_url( $order ),
		);
	}

	public function thankyou_page( $order_id ) {
		$order = wc_get_order( $order_id );
		$until = $order ? $order->get_meta( '_lt_hold_expires' ) : '';
		echo '<div class="lt-reserved-note"><p>' . esc_html(
			sprintf(
				/* translators: %s: date and time */
				__( 'Your piece is reserved until %s. Come to the boutique to pay and collect. Please message us first so it is ready for you.', 'labeltrunc' ),
				LT_Holds::format_time( $until )
			)
		) . '</p></div>';
	}
}

add_filter(
	'woocommerce_payment_gateways',
	static function ( $gateways ) {
		$gateways[] = 'LT_Gateway_Pay_At_Shop';
		return $gateways;
	}
);
