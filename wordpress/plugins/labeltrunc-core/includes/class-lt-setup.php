<?php
/**
 * Tools → Label Trunc setup. One button that prepares WooCommerce, pages, menus
 * and contact details for Label Trunc. It only creates what is missing and never
 * overwrites existing pages, menus or settings that already have a value.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Setup {

	/** Facts already published by Label Trunc (see docs/SOURCES.md). */
	const FACTS = array(
		'lt_tagline'    => 'New and pre-loved designer pieces, curated in Ampang.',
		'lt_address'    => "11-2A, Level 2, Ukay Boulevard\nJln Lingkaran Tengah 2\n68000 Ampang, Selangor\nMalaysia",
		'lt_phone'      => '+60 12-421 1205',
		'lt_whatsapp'   => '60124211205',
		'lt_email'      => 'labeltrunc@gmail.com',
		'lt_maps_url'   => 'https://goo.gl/maps/3uq3UUKJANUe7Prx8',
		'lt_visit_note' => 'Appointments recommended. Message us first and we will have your pieces ready.',
		'lt_hours'      => '',
		'lt_instagram'  => 'https://www.instagram.com/labeltrunc_luxury/',
		'lt_instagram2' => 'https://www.instagram.com/labeltrunc/',
		'lt_tiktok'     => 'https://www.tiktok.com/@labeltrunc',
		'lt_facebook'   => 'https://www.facebook.com/labeltrunc/',
	);

	public static function init() {
		add_action( 'admin_menu', array( __CLASS__, 'menu' ) );
		add_action( 'admin_post_lt_run_setup', array( __CLASS__, 'handle' ) );
	}

	public static function menu() {
		add_management_page( __( 'Label Trunc setup', 'labeltrunc' ), __( 'Label Trunc setup', 'labeltrunc' ), 'manage_options', 'lt-setup', array( __CLASS__, 'page' ) );
	}

	public static function page() {
		$report = get_transient( 'lt_setup_report' );
		delete_transient( 'lt_setup_report' );
		echo '<div class="wrap"><h1>' . esc_html__( 'Label Trunc setup', 'labeltrunc' ) . '</h1>';
		echo '<p style="max-width:720px">' . esc_html__( 'Prepares the shop. It sets the WooCommerce shop settings for Label Trunc (ringgit, Selangor, stock tracking, guest checkout, unpaid orders ending after the payment window). It adds pay at the boutique and collection at the boutique, and creates the About, Buying info, Visit, Contact and policy pages, the menus and the contact details only where they are missing: existing pages, menus and contact details are left exactly as they are. Back up the site before running it on a live site.', 'labeltrunc' ) . '</p>';
		if ( is_array( $report ) ) {
			echo '<div class="notice notice-success"><p><strong>' . esc_html__( 'Setup finished.', 'labeltrunc' ) . '</strong></p><ul style="list-style:disc;margin-left:20px">';
			foreach ( $report as $line ) {
				echo '<li>' . esc_html( $line ) . '</li>';
			}
			echo '</ul></div>';
		}
		echo '<form method="post" action="' . esc_url( admin_url( 'admin-post.php' ) ) . '">';
		wp_nonce_field( 'lt_run_setup' );
		echo '<input type="hidden" name="action" value="lt_run_setup">';
		submit_button( __( 'Run setup', 'labeltrunc' ) );
		echo '</form></div>';
	}

	public static function handle() {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( esc_html__( 'You are not allowed to do this.', 'labeltrunc' ) );
		}
		check_admin_referer( 'lt_run_setup' );
		set_transient( 'lt_setup_report', self::run(), 300 );
		wp_safe_redirect( admin_url( 'tools.php?page=lt-setup' ) );
		exit;
	}

	/** @return string[] What was done. */
	public static function run() {
		$report = array();

		foreach ( self::FACTS as $key => $value ) {
			if ( '' === (string) get_theme_mod( $key, '' ) && '' !== $value ) {
				set_theme_mod( $key, $value );
			}
		}
		$report[] = 'Contact details filled in where empty (Appearance → Customize → Label Trunc).';

		$wc_options = array(
			'woocommerce_currency'                   => 'MYR',
			'woocommerce_default_country'            => 'MY:SGR',
			'woocommerce_manage_stock'               => 'yes',
			'woocommerce_hold_stock_minutes'         => (string) LT_Settings::get( 'payment_window_minutes' ),
			'woocommerce_hide_out_of_stock_items'    => 'no',
			'woocommerce_enable_guest_checkout'      => 'yes',
			'woocommerce_enable_checkout_login_reminder' => 'yes',
			'woocommerce_enable_myaccount_registration' => 'yes',
			'woocommerce_registration_generate_password' => 'no',
			'woocommerce_calc_taxes'                 => 'no',
			'woocommerce_price_num_decimals'         => '0',
			'woocommerce_currency_pos'               => 'left_space',
			'woocommerce_notify_no_stock'            => 'no',
		);
		foreach ( $wc_options as $key => $value ) {
			update_option( $key, $value );
		}
		$report[] = 'WooCommerce: ringgit (RM), Selangor, stock tracking on, unpaid orders end after ' . LT_Settings::get( 'payment_window_minutes' ) . ' minutes, guest checkout and accounts on.';

		$gateway = get_option( 'woocommerce_lt_pay_at_shop_settings', array() );
		if ( empty( $gateway ) ) {
			update_option( 'woocommerce_lt_pay_at_shop_settings', array( 'enabled' => 'yes' ) );
			$report[] = 'Payment: "Reserve now, pay at the boutique" turned on.';
		}

		$report[] = self::shipping_zone();
		$report   = array_merge( $report, self::pages() );
		$report[] = self::classic_cart_checkout();
		$report   = array_merge( $report, self::categories() );
		$report[] = self::menus();
		flush_rewrite_rules();
		return array_filter( $report );
	}

	private static function shipping_zone() {
		if ( ! class_exists( 'WC_Shipping_Zones' ) ) {
			return '';
		}
		$zones = WC_Shipping_Zones::get_zones();
		if ( ! empty( $zones ) ) {
			return 'Shipping zones already exist, so collection settings were left as they are.';
		}
		$zone = new WC_Shipping_Zone();
		$zone->set_zone_name( 'Malaysia' );
		$zone->add_location( 'MY', 'country' );
		$zone->save();
		$instance = $zone->add_shipping_method( 'local_pickup' );
		update_option(
			'woocommerce_local_pickup_' . $instance . '_settings',
			array(
				'title'      => 'Collect at the boutique (Ukay Boulevard, Ampang)',
				'tax_status' => 'none',
				'cost'       => '',
			)
		);
		return 'Shipping: "Collect at the boutique" added for Malaysia. Add delivery in WooCommerce → Settings → Shipping when fees are confirmed.';
	}

	private static function pages() {
		$file   = LT_CORE_DIR . 'data/pages.json';
		$pages  = file_exists( $file ) ? json_decode( (string) file_get_contents( $file ), true ) : array(); // phpcs:ignore WordPress.WP.AlternativeFunctions.file_get_contents_file_get_contents
		$report = array();
		$extra  = array(
			array(
				'slug'    => 'visit',
				'title'   => 'Visit the boutique',
				'excerpt' => 'Appointments recommended. Message us first and we will have your pieces ready.',
				'content' => '',
				'template' => 'page-templates/visit.php',
			),
			array(
				'slug'     => 'contact',
				'title'    => 'Contact us',
				'excerpt'  => 'Ask about a piece, book a visit, or tell us about a bag you would like to consign.',
				'content'  => "<!-- wp:shortcode -->\n[lt_enquiry_form]\n<!-- /wp:shortcode -->",
				'template' => 'page-templates/contact.php',
			),
		);
		foreach ( array_merge( (array) $pages, $extra ) as $page ) {
			if ( empty( $page['slug'] ) ) {
				continue;
			}
			if ( get_page_by_path( $page['slug'] ) ) {
				$report[] = sprintf( 'Page /%s already exists, left unchanged.', $page['slug'] );
				continue;
			}
			$content = 'cookies' === $page['slug'] ? self::cookies_content() : (string) $page['content'];
			$id      = wp_insert_post(
				array(
					'post_type'    => 'page',
					'post_status'  => 'publish',
					'post_title'   => $page['title'],
					'post_name'    => $page['slug'],
					'post_excerpt' => (string) ( $page['excerpt'] ?? '' ),
					'post_content' => wp_slash( $content ),
				),
				true
			);
			if ( is_wp_error( $id ) ) {
				$report[] = sprintf( 'Could not create /%s: %s', $page['slug'], $id->get_error_message() );
				continue;
			}
			if ( ! empty( $page['needsConfirmation'] ) ) {
				update_post_meta( $id, '_lt_needs_confirmation', 1 );
			}
			if ( ! empty( $page['lastUpdated'] ) ) {
				update_post_meta( $id, '_lt_last_updated', sanitize_text_field( $page['lastUpdated'] ) );
			}
			if ( ! empty( $page['seo'] ) ) {
				update_post_meta( $id, '_lt_description', sanitize_text_field( $page['seo'] ) );
			}
			if ( ! empty( $page['lang'] ) ) {
				update_post_meta( $id, '_lt_lang', sanitize_key( $page['lang'] ) );
			}
			if ( ! empty( $page['template'] ) ) {
				update_post_meta( $id, '_wp_page_template', $page['template'] );
			}
			if ( 'privacy' === $page['slug'] ) {
				$current = (int) get_option( 'wp_page_for_privacy_policy' );
				if ( ! $current || 'publish' !== get_post_status( $current ) ) {
					update_option( 'wp_page_for_privacy_policy', $id );
				}
			}
			if ( 'terms' === $page['slug'] && ! get_option( 'woocommerce_terms_page_id' ) ) {
				update_option( 'woocommerce_terms_page_id', $id );
			}
			$report[] = sprintf( 'Created page /%s.', $page['slug'] );
		}
		return $report;
	}

	/** Cookies used by WordPress and WooCommerce on this site (replaces the generic seed text). */
	private static function cookies_content() {
		$rows = array(
			array( 'wp_woocommerce_session_…', 'Links your browser to your cart and the pieces held for you.', 'Up to 2 days' ),
			array( 'woocommerce_items_in_cart, woocommerce_cart_hash', 'Remember whether your cart has pieces in it.', 'Until you close your browser' ),
			array( 'wordpress_logged_in_…, wordpress_sec_…', 'Keep you signed in to your account (and staff to the editor).', 'Until you close your browser, or 14 days with "Remember me"' ),
			array( 'wordpress_test_cookie', 'Checks that your browser accepts cookies when you sign in.', 'Until you close your browser' ),
		);
		$items = '';
		foreach ( $rows as $r ) {
			$items .= '<!-- wp:list-item --><li><strong>' . esc_html( $r[0] ) . '</strong>: ' . esc_html( $r[1] ) . ' ' . esc_html( $r[2] ) . '.</li><!-- /wp:list-item -->';
		}
		return "<!-- wp:paragraph -->\n<p>This website uses only the cookies it needs to work: your cart, the pieces held for you, and signing in. There are no analytics, advertising or tracking cookies, so we do not ask for consent.</p>\n<!-- /wp:paragraph -->\n\n<!-- wp:heading -->\n<h2 class=\"wp-block-heading\">Cookies this site sets</h2>\n<!-- /wp:heading -->\n\n<!-- wp:list {\"className\":\"is-style-lt-ledger\"} -->\n<ul class=\"wp-block-list is-style-lt-ledger\">{$items}</ul>\n<!-- /wp:list -->\n\n<!-- wp:heading -->\n<h2 class=\"wp-block-heading\">Other services</h2>\n<!-- /wp:heading -->\n\n<!-- wp:paragraph -->\n<p>When you pay online you are taken to Billplz, which may set its own cookies under its own policy. Links to Instagram, TikTok, Facebook, WhatsApp and Google Maps open those services. Your hosting provider may set technical cookies [Label Trunc to confirm once the hosting provider is confirmed].</p>\n<!-- /wp:paragraph -->\n\n<!-- wp:paragraph -->\n<p>You can delete cookies in your browser settings. If you do, your cart and any holds on this device are lost; held pieces return to the shop when their hold ends.</p>\n<!-- /wp:paragraph -->";
	}

	/** The holds and pay-at-boutique rules use the classic cart and checkout. */
	private static function classic_cart_checkout() {
		$done = array();
		foreach ( array(
			'cart'     => array( 'woocommerce_cart_page_id', '[woocommerce_cart]', 'woocommerce/cart' ),
			'checkout' => array( 'woocommerce_checkout_page_id', '[woocommerce_checkout]', 'woocommerce/checkout' ),
		) as $name => $cfg ) {
			$id = (int) get_option( $cfg[0] );
			if ( ! $id ) {
				continue;
			}
			$content = (string) get_post_field( 'post_content', $id );
			if ( false !== strpos( $content, '<!-- wp:' . $cfg[2] ) || '' === trim( $content ) ) {
				wp_update_post(
					array(
						'ID'           => $id,
						'post_content' => "<!-- wp:shortcode -->\n{$cfg[1]}\n<!-- /wp:shortcode -->",
					)
				);
				$done[] = $name;
			}
		}
		return $done ? 'Switched the ' . implode( ' and ', $done ) . ' page to the classic layout (needed for holds and pay at the boutique).' : '';
	}

	private static function categories() {
		$report = array();
		foreach ( array( 'Top handle', 'Shoulder' ) as $name ) {
			if ( ! term_exists( $name, 'product_cat' ) ) {
				wp_insert_term( $name, 'product_cat' );
				$report[] = sprintf( 'Added the "%s" category.', $name );
			}
		}
		return $report;
	}

	private static function menus() {
		$locations = get_theme_mod( 'nav_menu_locations', array() );
		if ( ! empty( $locations['primary'] ) ) {
			return 'Menus already assigned, left unchanged.';
		}
		$link = static function ( $slug ) {
			$page = get_page_by_path( $slug );
			return $page ? get_permalink( $page ) : home_url( '/' . $slug . '/' );
		};
		$primary = wp_get_nav_menu_object( 'Label Trunc main' );
		$primary_id = $primary ? $primary->term_id : wp_create_nav_menu( 'Label Trunc main' );
		if ( ! $primary ) {
			$shop = function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'shop' ) : home_url( '/shop/' );
			foreach ( array(
				array( 'Shop', $shop ),
				array( 'About', $link( 'about' ) ),
				array( 'Visit', $link( 'visit' ) ),
				array( 'Buying info', $link( 'info' ) ),
				array( 'Contact', $link( 'contact' ) ),
			) as $i => $item ) {
				wp_update_nav_menu_item(
					$primary_id,
					0,
					array(
						'menu-item-title'    => $item[0],
						'menu-item-url'      => $item[1],
						'menu-item-status'   => 'publish',
						'menu-item-position' => $i + 1,
					)
				);
			}
		}
		$footer    = wp_get_nav_menu_object( 'Label Trunc policies' );
		$footer_id = $footer ? $footer->term_id : wp_create_nav_menu( 'Label Trunc policies' );
		if ( ! $footer ) {
			foreach ( array(
				array( 'Privacy', 'privacy' ),
				array( 'Privasi (BM)', 'privasi' ),
				array( 'Cookies', 'cookies' ),
				array( 'Terms', 'terms' ),
				array( 'Refunds & returns', 'refunds' ),
				array( 'Accessibility', 'accessibility' ),
				array( 'Business details', 'business-details' ),
			) as $i => $item ) {
				wp_update_nav_menu_item(
					$footer_id,
					0,
					array(
						'menu-item-title'    => $item[0],
						'menu-item-url'      => $link( $item[1] ),
						'menu-item-status'   => 'publish',
						'menu-item-position' => $i + 1,
					)
				);
			}
		}
		$locations['primary'] = $primary_id;
		$locations['footer']  = $footer_id;
		set_theme_mod( 'nav_menu_locations', $locations );
		return 'Created the main and policies menus.';
	}
}
