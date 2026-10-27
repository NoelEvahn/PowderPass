<?php
/**
 * Database table for holds.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Schema {

	const DB_VERSION = '1';

	public static function table() {
		global $wpdb;
		return $wpdb->prefix . 'lt_holds';
	}

	/**
	 * One row per held piece. The primary key on product_id is what guarantees, at the
	 * database level, that a piece can be held by only one cart or order at a time.
	 */
	public static function create_table() {
		global $wpdb;
		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		$table   = self::table();
		$charset = $wpdb->get_charset_collate();
		dbDelta(
			"CREATE TABLE {$table} (
			product_id bigint(20) unsigned NOT NULL,
			session_key varchar(100) DEFAULT NULL,
			order_id bigint(20) unsigned DEFAULT NULL,
			user_id bigint(20) unsigned DEFAULT NULL,
			visitor_hash char(64) DEFAULT NULL,
			expires_at datetime NOT NULL,
			created_at datetime NOT NULL,
			PRIMARY KEY  (product_id),
			KEY session_key (session_key),
			KEY order_id (order_id),
			KEY visitor_hash (visitor_hash),
			KEY expires_at (expires_at)
			) {$charset};"
		);
		update_option( 'lt_db_version', self::DB_VERSION );
	}

	public static function activate() {
		self::create_table();
		LT_Cron::schedule();
	}

	public static function maybe_upgrade() {
		if ( get_option( 'lt_db_version' ) !== self::DB_VERSION ) {
			self::create_table();
		}
	}
}
