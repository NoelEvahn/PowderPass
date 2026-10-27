<?php
/**
 * Lightweight hardening that suits shared cPanel hosting. A full security plugin
 * (for example Wordfence) can be added on top; nothing here conflicts with one.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Security {

	const MAX_FAILURES = 5;
	const LOCK_MINUTES = 15;

	public static function init() {
		add_action( 'send_headers', array( __CLASS__, 'headers' ) );
		add_filter( 'xmlrpc_enabled', array( __CLASS__, 'xmlrpc_enabled' ) );
		add_filter( 'the_generator', '__return_empty_string' );
		add_filter( 'rest_endpoints', array( __CLASS__, 'hide_user_endpoints' ) );
		add_action( 'template_redirect', array( __CLASS__, 'block_author_enumeration' ), 1 );
		add_filter( 'authenticate', array( __CLASS__, 'check_lockout' ), 30, 3 );
		add_action( 'wp_login_failed', array( __CLASS__, 'record_failure' ) );
		add_action( 'wp_login', array( __CLASS__, 'clear_failures' ) );
		add_action( 'lostpassword_post', array( __CLASS__, 'throttle_password_reset' ), 10, 2 );
		add_filter( 'upload_mimes', array( __CLASS__, 'upload_mimes' ) );
		add_filter( 'upload_size_limit', array( __CLASS__, 'upload_size_limit' ) );
		add_filter( 'wp_handle_upload_prefilter', array( __CLASS__, 'check_image_upload' ) );
	}

	public static function headers() {
		if ( headers_sent() ) {
			return;
		}
		header( 'X-Content-Type-Options: nosniff' );
		header( 'Referrer-Policy: strict-origin-when-cross-origin' );
		header( 'X-Frame-Options: SAMEORIGIN' );
		header( "Content-Security-Policy: frame-ancestors 'self'" );
		header( 'Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()' );
		if ( is_ssl() ) {
			header( 'Strict-Transport-Security: max-age=31536000' );
		}
	}

	/** XML-RPC is a common brute-force target; off unless a plugin (e.g. Jetpack) needs it. */
	public static function xmlrpc_enabled() {
		return (bool) apply_filters( 'lt_enable_xmlrpc', false );
	}

	/** Visitors cannot list user accounts through the REST API. */
	public static function hide_user_endpoints( $endpoints ) {
		if ( is_user_logged_in() && current_user_can( 'list_users' ) ) {
			return $endpoints;
		}
		foreach ( array_keys( $endpoints ) as $route ) {
			if ( 0 === strpos( $route, '/wp/v2/users' ) ) {
				unset( $endpoints[ $route ] );
			}
		}
		return $endpoints;
	}

	public static function block_author_enumeration() {
		// phpcs:ignore WordPress.Security.NonceVerification.Recommended
		if ( ! is_admin() && isset( $_GET['author'] ) ) {
			wp_safe_redirect( home_url( '/' ), 301 );
			exit;
		}
	}

	private static function ip_key( $prefix ) {
		$ip = isset( $_SERVER['REMOTE_ADDR'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REMOTE_ADDR'] ) ) : 'unknown';
		return $prefix . substr( hash_hmac( 'sha256', $ip, wp_salt( 'auth' ) ), 0, 32 );
	}

	/** After five failed sign-ins from one connection, sign-in pauses for 15 minutes (staff and customers). */
	public static function check_lockout( $user, $username, $password ) {
		if ( empty( $username ) && empty( $password ) ) {
			return $user;
		}
		$failures = (int) get_transient( self::ip_key( 'lt_fail_' ) );
		if ( $failures >= self::MAX_FAILURES ) {
			return new WP_Error(
				'lt_locked',
				sprintf(
					/* translators: %d: minutes */
					__( 'Too many failed sign-in attempts. Please wait %d minutes, or reset your password.', 'labeltrunc' ),
					self::LOCK_MINUTES
				)
			);
		}
		return $user;
	}

	public static function record_failure() {
		$key = self::ip_key( 'lt_fail_' );
		set_transient( $key, (int) get_transient( $key ) + 1, self::LOCK_MINUTES * MINUTE_IN_SECONDS );
	}

	public static function clear_failures() {
		delete_transient( self::ip_key( 'lt_fail_' ) );
	}

	/** At most five password-reset emails per connection per hour. */
	public static function throttle_password_reset( $errors ) {
		$key   = self::ip_key( 'lt_reset_' );
		$count = (int) get_transient( $key );
		if ( $count >= 5 ) {
			$errors->add( 'lt_reset_limit', __( 'Too many password reset requests. Please try again in an hour.', 'labeltrunc' ) );
			return;
		}
		set_transient( $key, $count + 1, HOUR_IN_SECONDS );
	}

	/** Staff below administrator may upload only images and PDFs. */
	public static function upload_mimes( $mimes ) {
		if ( current_user_can( 'manage_options' ) ) {
			return $mimes;
		}
		return array_intersect_key(
			$mimes,
			array_flip( array( 'jpg|jpeg|jpe', 'png', 'webp', 'avif', 'gif', 'pdf' ) )
		);
	}

	public static function upload_size_limit( $bytes ) {
		return min( $bytes, 15 * MB_IN_BYTES );
	}

	/** Rejects files that claim to be images but are not. */
	public static function check_image_upload( $file ) {
		if ( ! empty( $file['tmp_name'] ) && preg_match( '/\.(jpe?g|png|webp|avif|gif)$/i', (string) $file['name'] ) ) {
			$info = @getimagesize( $file['tmp_name'] ); // phpcs:ignore WordPress.PHP.NoSilencedErrors.Discouraged
			if ( false === $info ) {
				$file['error'] = __( 'This file is not a valid image.', 'labeltrunc' );
			}
		}
		return $file;
	}
}
