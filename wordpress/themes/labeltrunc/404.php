<?php
/**
 * Not found.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();
lt_page_header( __( 'We could not find that page', 'labeltrunc' ), __( 'The link may be old, or the piece may have sold and been taken down.', 'labeltrunc' ) );
?>
<div class="lt-wrap lt-section">
	<ul class="lt-ledger">
		<li><a class="lt-link" href="<?php echo esc_url( function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'shop' ) : home_url( '/' ) ); ?>"><?php esc_html_e( 'Browse the shop', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a></li>
		<li><a class="lt-link" href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php esc_html_e( 'Home', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a></li>
		<li><a class="lt-link" href="<?php echo esc_url( lt_page_url( 'contact' ) ); ?>"><?php esc_html_e( 'Contact us', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a></li>
	</ul>
</div>
<?php
get_footer();
