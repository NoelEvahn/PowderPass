<?php
/**
 * Piece details for the product editor, one-of-a-kind stock rules, stock numbers
 * and the availability column. Also exposes read helpers used by the theme.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Product_Fields {

	const CONDITIONS = array(
		'new'       => 'Brand new',
		'unused'    => 'Unused (never carried)',
		'excellent' => 'Pre-loved: excellent',
		'very-good' => 'Pre-loved: very good',
		'good'      => 'Pre-loved: good',
		'fair'      => 'Pre-loved: fair',
	);

	const INCLUDES = array(
		'dust-bag'          => 'Dust bag',
		'box'               => 'Box',
		'receipt'           => 'Receipt',
		'authenticity-card' => 'Authenticity card',
		'care-booklet'      => 'Care booklet',
		'strap'             => 'Strap',
		'lock-keys'         => 'Lock and keys',
		'rain-cover'        => 'Rain cover',
		'ribbon'            => 'Ribbon / flower',
	);

	const DETAILS = array(
		'colour'     => 'Colour',
		'material'   => 'Material',
		'hardware'   => 'Hardware',
		'dimensions' => 'Size',
		'year'       => 'Year / series',
	);

	public static function init() {
		add_filter( 'woocommerce_product_data_tabs', array( __CLASS__, 'tab' ) );
		add_action( 'woocommerce_product_data_panels', array( __CLASS__, 'panel' ) );
		add_action( 'woocommerce_admin_process_product_object', array( __CLASS__, 'save' ) );
		add_action( 'woocommerce_new_product', array( __CLASS__, 'ensure_sku' ) );
		add_action( 'woocommerce_update_product', array( __CLASS__, 'ensure_sku' ) );
		add_filter( 'wp_insert_post_data', array( __CLASS__, 'require_photo_and_price' ), 10, 2 );
		add_action( 'admin_notices', array( __CLASS__, 'publish_notice' ) );
		add_filter( 'manage_edit-product_columns', array( __CLASS__, 'columns' ), 20 );
		add_action( 'manage_product_posts_custom_column', array( __CLASS__, 'column' ), 10, 2 );
		add_action( 'admin_footer', array( __CLASS__, 'new_product_defaults' ) );
	}

	/* ------------------------------------------------------------------ */
	/* Editor                                                              */
	/* ------------------------------------------------------------------ */

	public static function tab( $tabs ) {
		$tabs['lt_piece'] = array(
			'label'    => __( 'Piece details', 'labeltrunc' ),
			'target'   => 'lt_piece_data',
			'class'    => array(),
			'priority' => 5,
		);
		return $tabs;
	}

	public static function panel() {
		global $post;
		$includes = (array) get_post_meta( $post->ID, '_lt_includes', true );
		echo '<div id="lt_piece_data" class="panel woocommerce_options_panel">';
		echo '<p class="form-field" style="padding-left:12px;color:#646970">' . esc_html__( 'Describe this exact piece. Shown on its page in the shop.', 'labeltrunc' ) . '</p>';
		woocommerce_wp_select(
			array(
				'id'          => '_lt_condition',
				'label'       => __( 'Condition', 'labeltrunc' ),
				'options'     => array( '' => __( 'Choose…', 'labeltrunc' ) ) + self::CONDITIONS,
				'desc_tip'    => true,
				'description' => __( 'Required before publishing.', 'labeltrunc' ),
			)
		);
		woocommerce_wp_textarea_input(
			array(
				'id'          => '_lt_condition_notes',
				'label'       => __( 'Condition notes', 'labeltrunc' ),
				'placeholder' => __( 'Be specific: corner wear, marks on hardware, inside the bag.', 'labeltrunc' ),
			)
		);
		echo '<fieldset class="form-field"><legend style="float:left;width:150px;margin:0 0 0 -150px">' . esc_html__( 'Comes with', 'labeltrunc' ) . '</legend><ul style="margin:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:6px 16px">';
		foreach ( self::INCLUDES as $key => $label ) {
			printf(
				'<li style="margin:0"><label style="float:none;width:auto;margin:0;display:inline-flex;gap:6px;align-items:center"><input type="checkbox" name="_lt_includes[]" value="%1$s" %2$s style="margin:0"> %3$s</label></li>',
				esc_attr( $key ),
				checked( in_array( $key, $includes, true ), true, false ),
				esc_html( $label )
			);
		}
		echo '</ul></fieldset>';
		foreach ( self::DETAILS as $key => $label ) {
			woocommerce_wp_text_input(
				array(
					'id'    => '_lt_' . $key,
					'label' => $label,
				)
			);
		}
		echo '</div>';
	}

	public static function save( WC_Product $product ) {
		// phpcs:disable WordPress.Security.NonceVerification.Missing -- WooCommerce verifies the product form nonce.
		$condition = isset( $_POST['_lt_condition'] ) ? sanitize_key( wp_unslash( $_POST['_lt_condition'] ) ) : '';
		$product->update_meta_data( '_lt_condition', isset( self::CONDITIONS[ $condition ] ) ? $condition : '' );
		$product->update_meta_data( '_lt_condition_notes', isset( $_POST['_lt_condition_notes'] ) ? sanitize_textarea_field( wp_unslash( $_POST['_lt_condition_notes'] ) ) : '' );
		$includes = isset( $_POST['_lt_includes'] ) ? array_map( 'sanitize_key', (array) wp_unslash( $_POST['_lt_includes'] ) ) : array();
		$product->update_meta_data( '_lt_includes', array_values( array_intersect( $includes, array_keys( self::INCLUDES ) ) ) );
		foreach ( array_keys( self::DETAILS ) as $key ) {
			$product->update_meta_data( '_lt_' . $key, isset( $_POST[ '_lt_' . $key ] ) ? sanitize_text_field( wp_unslash( $_POST[ '_lt_' . $key ] ) ) : '' );
		}
		// phpcs:enable

		// Every piece is unique: one in stock at most, one per order.
		$product->set_sold_individually( true );
		$product->set_manage_stock( true );
		$product->set_backorders( 'no' );
		$qty = $product->get_stock_quantity();
		$product->set_stock_quantity( null === $qty ? 1 : max( 0, min( 1, (int) $qty ) ) );
	}

	/** Gives every piece a stock number like LT-0042 unless staff set their own SKU. */
	public static function ensure_sku( $product_id ) {
		static $busy = false;
		if ( $busy ) {
			return;
		}
		$product = wc_get_product( $product_id );
		if ( $product && ! $product->get_sku() ) {
			$busy = true;
			try {
				$product->set_sku( 'LT-' . str_pad( (string) $product_id, 4, '0', STR_PAD_LEFT ) );
				$product->save();
			} catch ( WC_Data_Exception $e ) {
				// An identical SKU exists already; leave it for staff to set.
			}
			$busy = false;
		}
	}

	/** A piece cannot be published without a photo, a price and a condition. */
	public static function require_photo_and_price( $data, $postarr ) {
		if ( 'product' !== $data['post_type'] || 'publish' !== $data['post_status'] || empty( $postarr['ID'] ) ) {
			return $data;
		}
		// phpcs:disable WordPress.Security.NonceVerification.Missing
		if ( ! isset( $_POST['_regular_price'] ) ) {
			return $data; // Not the product edit form (e.g. stock change, import, REST).
		}
		$missing = array();
		$thumb   = isset( $_POST['_thumbnail_id'] ) ? (int) $_POST['_thumbnail_id'] : (int) get_post_thumbnail_id( $postarr['ID'] );
		if ( $thumb <= 0 ) {
			$missing[] = __( 'a product photo', 'labeltrunc' );
		}
		$price = isset( $_POST['_regular_price'] ) ? wc_format_decimal( wp_unslash( $_POST['_regular_price'] ) ) : '';
		if ( '' === $price || (float) $price <= 0 ) {
			$missing[] = __( 'a price', 'labeltrunc' );
		}
		if ( empty( $_POST['_lt_condition'] ) ) {
			$missing[] = __( 'the condition (Piece details tab)', 'labeltrunc' );
		}
		// phpcs:enable
		if ( $missing ) {
			$data['post_status'] = 'draft';
			set_transient( 'lt_publish_blocked_' . get_current_user_id(), $missing, 60 );
		}
		return $data;
	}

	public static function publish_notice() {
		$missing = get_transient( 'lt_publish_blocked_' . get_current_user_id() );
		if ( ! $missing ) {
			return;
		}
		delete_transient( 'lt_publish_blocked_' . get_current_user_id() );
		/* translators: %s: list of missing items */
		echo '<div class="notice notice-warning"><p>' . esc_html( sprintf( __( 'Saved as a draft, not published. Add %s, then press Publish.', 'labeltrunc' ), implode( ', ', $missing ) ) ) . '</p></div>';
	}

	/** On a new piece, stock management starts on with one in stock and one per order. */
	public static function new_product_defaults() {
		$screen = function_exists( 'get_current_screen' ) ? get_current_screen() : null;
		if ( ! $screen || 'product' !== $screen->id || 'add' !== $screen->action ) {
			return;
		}
		echo "<script>jQuery(function($){ $('#_manage_stock').prop('checked', true).trigger('change'); $('#_stock').val(1); $('#_sold_individually').prop('checked', true); });</script>";
	}

	/* ------------------------------------------------------------------ */
	/* Admin list                                                          */
	/* ------------------------------------------------------------------ */

	public static function columns( $columns ) {
		$out = array();
		foreach ( $columns as $key => $label ) {
			if ( 'is_in_stock' === $key ) {
				$out['lt_availability'] = __( 'Availability', 'labeltrunc' );
				continue;
			}
			$out[ $key ] = $label;
		}
		if ( ! isset( $out['lt_availability'] ) ) {
			$out['lt_availability'] = __( 'Availability', 'labeltrunc' );
		}
		return $out;
	}

	public static function column( $column, $post_id ) {
		if ( 'lt_availability' !== $column ) {
			return;
		}
		$a = self::availability( wc_get_product( $post_id ) );
		echo '<strong>' . esc_html( $a['label'] ) . '</strong>';
		if ( ! empty( $a['detail'] ) ) {
			echo '<br><span style="color:#646970">' . esc_html( $a['detail'] ) . '</span>';
		}
		if ( ! empty( $a['order_id'] ) ) {
			$order = wc_get_order( $a['order_id'] );
			if ( $order ) {
				echo '<br><a href="' . esc_url( $order->get_edit_order_url() ) . '">' . esc_html( sprintf( 'Order #%s', $order->get_order_number() ) ) . '</a>';
			}
		}
	}

	/* ------------------------------------------------------------------ */
	/* Read helpers (theme)                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * Where a piece stands right now.
	 *
	 * @return array{state:string,label:string,detail?:string,order_id?:int,until?:string}
	 */
	public static function availability( $product ) {
		if ( ! $product instanceof WC_Product ) {
			return array(
				'state' => 'unknown',
				'label' => '',
			);
		}
		if ( ! $product->is_in_stock() ) {
			$sold_at = (int) get_post_meta( $product->get_id(), '_lt_sold_at', true );
			$reserved_order = self::reserving_order( $product->get_id() );
			if ( $reserved_order ) {
				return array(
					'state'    => 'reserved',
					'label'    => __( 'Reserved', 'labeltrunc' ),
					'detail'   => __( 'Waiting for payment at the boutique', 'labeltrunc' ),
					'order_id' => $reserved_order,
				);
			}
			return array(
				'state'  => 'sold',
				'label'  => __( 'Sold', 'labeltrunc' ),
				'detail' => $sold_at ? wp_date( 'j M Y', $sold_at ) : '',
			);
		}
		$hold = LT_Holds::get( $product->get_id() );
		if ( LT_Holds::is_active( $hold ) ) {
			$mine = ! is_admin() && LT_Holds::is_mine( $hold );
			return array(
				'state'    => $mine ? 'in_cart' : 'held',
				'label'    => $mine ? __( 'In your cart', 'labeltrunc' ) : ( $hold->order_id ? __( 'Awaiting payment', 'labeltrunc' ) : __( 'On hold', 'labeltrunc' ) ),
				/* translators: %s: date and time */
				'detail'   => sprintf( __( 'until %s', 'labeltrunc' ), LT_Holds::format_time( $hold->expires_at ) ),
				'until'    => $hold->expires_at,
				'order_id' => $hold->order_id ? (int) $hold->order_id : 0,
			);
		}
		return array(
			'state' => 'available',
			'label' => __( 'Available', 'labeltrunc' ),
		);
	}

	/** The reservation (pay at boutique) currently holding a piece's stock, if any. */
	private static function reserving_order( $product_id ) {
		static $cache = array();
		if ( isset( $cache[ $product_id ] ) ) {
			return $cache[ $product_id ];
		}
		$orders = wc_get_orders(
			array(
				'status' => array( LT_Orders::RESERVED ),
				'limit'  => 20,
				'return' => 'objects',
			)
		);
		$cache[ $product_id ] = 0;
		foreach ( $orders as $order ) {
			if ( in_array( (int) $product_id, LT_Orders::product_ids( $order ), true ) ) {
				$cache[ $product_id ] = $order->get_id();
				break;
			}
		}
		return $cache[ $product_id ];
	}

	public static function condition_label( WC_Product $product ) {
		$key = $product->get_meta( '_lt_condition' );
		return self::CONDITIONS[ $key ] ?? '';
	}

	public static function includes_labels( WC_Product $product ) {
		$keys = (array) $product->get_meta( '_lt_includes' );
		return array_values( array_intersect_key( self::INCLUDES, array_flip( $keys ) ) );
	}

	/** Filled-in detail rows: [label, value]. */
	public static function details( WC_Product $product ) {
		$rows = array();
		foreach ( self::DETAILS as $key => $label ) {
			$value = (string) $product->get_meta( '_lt_' . $key );
			if ( '' !== trim( $value ) ) {
				$rows[] = array( $label, $value );
			}
		}
		return $rows;
	}
}
