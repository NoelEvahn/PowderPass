<?php
/**
 * Site header: wordmark, main menu, search, account and cart. On phones the menu
 * opens as a full-screen sheet (native dialog: Escape closes, focus returns).
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

$cart_count = ( function_exists( 'WC' ) && WC()->cart ) ? WC()->cart->get_cart_contents_count() : 0;
$account    = function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'myaccount' ) : wp_login_url();
$cart_url   = function_exists( 'wc_get_cart_url' ) ? wc_get_cart_url() : home_url( '/' );
?><!doctype html>
<html <?php language_attributes(); ?>>
<head>
<meta charset="<?php bloginfo( 'charset' ); ?>">
<meta name="viewport" content="width=device-width, initial-scale=1">
<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<?php wp_body_open(); ?>
<a class="lt-skip" href="#main"><?php esc_html_e( 'Skip to content', 'labeltrunc' ); ?></a>
<header class="lt-header">
	<div class="lt-header__inner lt-wrap">
		<a class="lt-header__brand" href="<?php echo esc_url( home_url( '/' ) ); ?>" aria-label="<?php esc_attr_e( 'labeltrunc. Home', 'labeltrunc' ); ?>"><?php echo lt_wordmark(); // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped ?></a>
		<nav class="lt-nav" aria-label="<?php esc_attr_e( 'Main', 'labeltrunc' ); ?>">
			<?php
			wp_nav_menu(
				array(
					'theme_location' => 'primary',
					'container'      => false,
					'menu_class'     => 'lt-nav__list',
					'depth'          => 1,
					'fallback_cb'    => false,
				)
			);
			?>
		</nav>
		<div class="lt-header__tools">
			<a class="lt-tool lt-tool--search" href="<?php echo esc_url( function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'shop' ) . '#search' : home_url( '/?s=' ) ); ?>"><?php echo lt_icon( 'search', 18 ); // phpcs:ignore ?><span class="lt-tool__label"><?php esc_html_e( 'Search', 'labeltrunc' ); ?></span></a>
			<a class="lt-tool lt-tool--account" href="<?php echo esc_url( $account ); ?>"><span class="lt-tool__label"><?php echo is_user_logged_in() ? esc_html__( 'Account', 'labeltrunc' ) : esc_html__( 'Sign in', 'labeltrunc' ); ?></span></a>
			<a class="lt-tool lt-tool--cart" href="<?php echo esc_url( $cart_url ); ?>">
				<span class="lt-tool__label"><?php esc_html_e( 'Cart', 'labeltrunc' ); ?></span>
				<span class="lt-cart-count" data-lt-cart-count><?php echo (int) $cart_count; ?></span>
				<span class="screen-reader-text"><?php echo esc_html( sprintf( /* translators: %d: number of pieces */ _n( '%d piece', '%d pieces', $cart_count, 'labeltrunc' ), $cart_count ) ); ?></span>
			</a>
			<button class="lt-tool lt-tool--menu" type="button" aria-haspopup="dialog" data-lt-menu-open><?php esc_html_e( 'Menu', 'labeltrunc' ); ?></button>
		</div>
	</div>
</header>
<dialog class="lt-sheet" aria-label="<?php esc_attr_e( 'Menu', 'labeltrunc' ); ?>" data-lt-menu>
	<div class="lt-sheet__top lt-wrap">
		<span class="lt-sheet__brand"><?php echo lt_wordmark(); // phpcs:ignore ?></span>
		<button class="lt-tool" type="button" data-lt-menu-close><?php esc_html_e( 'Close', 'labeltrunc' ); ?> <?php echo lt_icon( 'x', 18 ); // phpcs:ignore ?></button>
	</div>
	<nav class="lt-sheet__nav lt-wrap" aria-label="<?php esc_attr_e( 'Mobile', 'labeltrunc' ); ?>">
		<?php
		wp_nav_menu(
			array(
				'theme_location' => 'primary',
				'container'      => false,
				'menu_class'     => 'lt-sheet__list',
				'depth'          => 1,
				'fallback_cb'    => false,
			)
		);
		?>
		<ul class="lt-sheet__secondary">
			<li><a href="<?php echo esc_url( $account ); ?>"><?php echo is_user_logged_in() ? esc_html__( 'Your account', 'labeltrunc' ) : esc_html__( 'Sign in', 'labeltrunc' ); ?></a></li>
			<li><a href="<?php echo esc_url( $cart_url ); ?>"><?php esc_html_e( 'Cart', 'labeltrunc' ); ?> (<?php echo (int) $cart_count; ?>)</a></li>
			<?php if ( lt_whatsapp_url() ) : ?>
				<li><a href="<?php echo esc_url( lt_whatsapp_url() ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'WhatsApp', 'labeltrunc' ); ?> <?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></a></li>
			<?php endif; ?>
		</ul>
	</nav>
</dialog>
<main id="main" class="lt-main" tabindex="-1">
