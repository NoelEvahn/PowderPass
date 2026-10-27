<?php
/**
 * Home page. Explain, show, prove, act: what Label Trunc is and where, real
 * pieces from stock, how buying works, and how to visit. Every image is either a
 * photo the boutique uploaded or a photo of a real piece in stock.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();

$newest = array();
if ( function_exists( 'wc_get_products' ) ) {
	$newest = wc_get_products(
		array(
			'status'       => 'publish',
			'stock_status' => 'instock',
			'visibility'   => 'catalog',
			'limit'        => 8,
			'orderby'      => 'date',
			'order'        => 'DESC',
		)
	);
}
$hero_image   = (int) lt_mod( 'lt_hero_image' );
$hero_product = ( ! $hero_image && $newest ) ? $newest[0] : null;
$hold_hours   = class_exists( 'LT_Settings' ) ? LT_Settings::get( 'cart_hold_hours' ) : 24;
$shop_url     = function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'shop' ) : home_url( '/' );
$online       = lt_online_payments();
$address = lt_address_lines();
?>

<section class="lt-hero lt-wrap" aria-labelledby="lt-hero-title">
	<div class="lt-hero__copy">
		<p class="lt-kicker"><?php echo esc_html( $address ? $address[0] : '' ); ?></p>
		<h1 id="lt-hero-title" class="lt-hero__title"><?php echo esc_html( lt_mod( 'lt_hero_heading' ) ); ?></h1>
		<p class="lt-hero__intro"><?php echo esc_html( lt_mod( 'lt_hero_intro' ) ); ?></p>
		<div class="lt-actions">
			<a class="lt-button" href="<?php echo esc_url( $shop_url ); ?>"><?php esc_html_e( 'Shop the boutique', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a>
			<a class="lt-link" href="<?php echo esc_url( lt_page_url( 'visit' ) ); ?>"><?php esc_html_e( 'Plan a visit', 'labeltrunc' ); ?></a>
		</div>
	</div>
	<figure class="lt-hero__media">
		<?php if ( $hero_image ) : ?>
			<?php echo wp_get_attachment_image( $hero_image, 'lt-portrait', false, array( 'fetchpriority' => 'high', 'loading' => 'eager', 'sizes' => '(min-width: 1000px) 46vw, 100vw' ) ); ?>
		<?php elseif ( $hero_product && $hero_product->get_image_id() ) : ?>
			<a href="<?php echo esc_url( $hero_product->get_permalink() ); ?>" class="lt-hero__piece">
				<?php echo wp_get_attachment_image( $hero_product->get_image_id(), 'woocommerce_single', false, array( 'fetchpriority' => 'high', 'loading' => 'eager', 'sizes' => '(min-width: 1000px) 46vw, 100vw' ) ); ?>
			</a>
			<figcaption class="lt-hero__caption">
				<span class="lt-hero__caption-label"><?php esc_html_e( 'Newest in stock', 'labeltrunc' ); ?></span>
				<a href="<?php echo esc_url( $hero_product->get_permalink() ); ?>"><?php echo esc_html( trim( lt_brand_name( $hero_product ) . ' ' . $hero_product->get_name() ) ); ?></a>
				<span class="lt-tabular"><?php echo wp_kses_post( $hero_product->get_price_html() ); ?></span>
			</figcaption>
		<?php else : ?>
			<div class="lt-label-card" role="img" aria-label="<?php echo esc_attr( implode( ', ', $address ) ); ?>">
				<span class="lt-label-card__eyelet" aria-hidden="true"></span>
				<span class="lt-label-card__brand"><?php echo lt_wordmark(); // phpcs:ignore ?></span>
				<span class="lt-label-card__motto"><?php esc_html_e( 'Curated · Collected · Consigned', 'labeltrunc' ); ?></span>
				<span class="lt-label-card__address"><?php echo implode( '<br>', array_map( 'esc_html', $address ) ); // phpcs:ignore ?></span>
			</div>
		<?php endif; ?>
	</figure>
</section>

<section class="lt-statement" aria-label="<?php esc_attr_e( 'About Label Trunc', 'labeltrunc' ); ?>">
	<p class="lt-statement__text lt-wrap">
		<span class="lt-statement__lead"><?php echo esc_html( lt_mod( 'lt_statement_lead' ) ); ?></span>
		<span class="lt-statement__rest"><?php echo esc_html( lt_mod( 'lt_statement_rest' ) ); ?></span>
	</p>
</section>

<section class="lt-section lt-wrap" id="new-in" aria-labelledby="lt-new-title">
	<div class="lt-section__head">
		<h2 id="lt-new-title" class="lt-h2"><?php esc_html_e( 'New in the boutique', 'labeltrunc' ); ?></h2>
		<?php if ( $newest ) : ?>
			<a class="lt-link" href="<?php echo esc_url( $shop_url ); ?>"><?php esc_html_e( 'All pieces', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a>
		<?php endif; ?>
	</div>
	<?php if ( $newest ) : ?>
		<ul class="lt-grid lt-grid--4">
			<?php
			foreach ( array_slice( $newest, 0, 8 ) as $product ) {
				$GLOBALS['product'] = $product; // phpcs:ignore WordPress.WP.GlobalVariablesOverride.Prohibited
				$post_object        = get_post( $product->get_id() );
				setup_postdata( $GLOBALS['post'] = $post_object ); // phpcs:ignore
				wc_get_template_part( 'content', 'product' );
			}
			wp_reset_postdata();
			?>
		</ul>
	<?php else : ?>
		<div class="lt-empty">
			<p class="lt-empty__title"><?php esc_html_e( 'New pieces are being photographed.', 'labeltrunc' ); ?></p>
			<p><?php esc_html_e( 'Our stock is going online now. Until then, see the latest arrivals on Instagram or ask us what is in the boutique this week.', 'labeltrunc' ); ?></p>
			<p class="lt-actions">
				<?php $social = lt_social_links(); ?>
				<?php if ( $social ) : ?>
					<a class="lt-button" href="<?php echo esc_url( $social[0][1] ); ?>" target="_blank" rel="noopener noreferrer"><?php echo esc_html( $social[0][0] . ' ' . $social[0][2] ); ?></a>
				<?php endif; ?>
				<?php if ( lt_whatsapp_url() ) : ?>
					<a class="lt-link" href="<?php echo esc_url( lt_whatsapp_url( 'Hi Label Trunc, what is in the boutique this week?' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Ask on WhatsApp', 'labeltrunc' ); ?></a>
				<?php endif; ?>
			</p>
		</div>
	<?php endif; ?>
</section>

<section class="lt-section lt-section--rule lt-wrap" aria-labelledby="lt-how-title">
	<div class="lt-split">
		<div class="lt-split__aside">
			<h2 id="lt-how-title" class="lt-h2"><?php esc_html_e( 'Hold it, pay, take it home', 'labeltrunc' ); ?></h2>
			<a class="lt-link" href="<?php echo esc_url( lt_page_url( 'info' ) ); ?>"><?php esc_html_e( 'Buying information', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a>
		</div>
		<ol class="lt-steps">
			<li>
				<span class="lt-steps__n lt-tabular">01</span>
				<h3><?php esc_html_e( 'Choose a piece', 'labeltrunc' ); ?></h3>
				<p><?php echo esc_html( sprintf( /* translators: %d: hours */ __( 'Add it to your cart and it is held for you for up to %d hours. Nobody else can buy it meanwhile.', 'labeltrunc' ), $hold_hours ) ); ?></p>
			</li>
			<li>
				<span class="lt-steps__n lt-tabular">02</span>
				<h3><?php echo $online ? esc_html__( 'Pay online, or reserve', 'labeltrunc' ) : esc_html__( 'Reserve it', 'labeltrunc' ); ?></h3>
				<p><?php echo $online ? esc_html__( 'Pay with FPX online banking (Maybank2u, HSBC, Public Bank and more), cards or Touch ’n Go eWallet. Or reserve it and pay at the boutique.', 'labeltrunc' ) : esc_html__( 'Reserve it online and pay when you come in to collect it.', 'labeltrunc' ); ?></p>
			</li>
			<li>
				<span class="lt-steps__n lt-tabular">03</span>
				<h3><?php esc_html_e( 'Collect at the boutique', 'labeltrunc' ); ?></h3>
				<p><?php esc_html_e( 'Pick it up at Ukay Boulevard. Message us first and we will have it ready for you.', 'labeltrunc' ); ?></p>
			</li>
		</ol>
	</div>
</section>

<section class="lt-section lt-section--tint" aria-labelledby="lt-about-title">
	<div class="lt-wrap lt-split">
		<div class="lt-split__aside">
			<h2 id="lt-about-title" class="lt-h2"><?php echo esc_html( lt_mod( 'lt_about_heading' ) ); ?></h2>
		</div>
		<div class="lt-split__body">
			<p class="lt-lead"><?php echo esc_html( lt_mod( 'lt_about_body' ) ); ?></p>
			<a class="lt-link" href="<?php echo esc_url( lt_page_url( 'about' ) ); ?>"><?php esc_html_e( 'More about us', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a>
			<div class="lt-aside-note">
				<h3><?php echo esc_html( lt_mod( 'lt_consign_heading' ) ); ?></h3>
				<p><?php echo esc_html( lt_mod( 'lt_consign_body' ) ); ?></p>
				<a class="lt-link" href="<?php echo esc_url( add_query_arg( 'topic', 'consign', lt_page_url( 'contact' ) ) ); ?>"><?php esc_html_e( 'Ask about consigning', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-right', 16 ); // phpcs:ignore ?></a>
			</div>
		</div>
	</div>
</section>

<?php get_template_part( 'template-parts/visit-band' ); ?>

<?php
get_footer();
