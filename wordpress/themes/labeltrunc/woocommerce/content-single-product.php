<?php
/**
 * A piece: photographs on the left (a swipeable row on phones), and a sticky
 * panel with price, availability in words, the cart button and the facts.
 *
 * @package LabelTrunc
 * @version 3.6.0
 */

defined( 'ABSPATH' ) || exit;

global $product;

do_action( 'woocommerce_before_single_product' );

if ( post_password_required() ) {
	echo get_the_password_form(); // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped
	return;
}

$availability = lt_availability( $product );
$images       = array_values( array_filter( array_merge( array( (int) $product->get_image_id() ), array_map( 'intval', $product->get_gallery_image_ids() ) ) ) );
$brand_terms  = get_the_terms( $product->get_id(), 'product_brand' );
$brand        = ( $brand_terms && ! is_wp_error( $brand_terms ) ) ? $brand_terms[0] : null;
$condition    = class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::condition_label( $product ) : '';
$includes     = class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::includes_labels( $product ) : array();
$details      = class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::details( $product ) : array();
$notes        = (string) $product->get_meta( '_lt_condition_notes' );
$hold_hours   = class_exists( 'LT_Settings' ) ? LT_Settings::get( is_user_logged_in() ? 'cart_hold_hours' : 'guest_hold_hours' ) : 24;
$ask_url      = add_query_arg( array( 'topic' => 'piece', 'piece' => $product->get_id() ), lt_page_url( 'contact' ) ) . '#enquiry';
$wa_url       = lt_whatsapp_url( sprintf( 'Hi Label Trunc, I am interested in %s (%s) %s', trim( ( $brand ? $brand->name . ' ' : '' ) . $product->get_name() ), $product->get_sku(), $product->get_permalink() ) );
$can_buy      = $product->is_purchasable() && $product->is_in_stock();
?>
<div id="product-<?php the_ID(); ?>" <?php wc_product_class( 'lt-product lt-wrap', $product ); ?>>
	<nav class="lt-crumbs" aria-label="<?php esc_attr_e( 'Breadcrumb', 'labeltrunc' ); ?>">
		<ol>
			<li><a href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php esc_html_e( 'Home', 'labeltrunc' ); ?></a></li>
			<li><a href="<?php echo esc_url( wc_get_page_permalink( 'shop' ) ); ?>"><?php esc_html_e( 'Shop', 'labeltrunc' ); ?></a></li>
			<?php if ( $brand ) : ?>
				<li><a href="<?php echo esc_url( add_query_arg( 'brand', $brand->slug, wc_get_page_permalink( 'shop' ) ) ); ?>"><?php echo esc_html( $brand->name ); ?></a></li>
			<?php endif; ?>
			<li aria-current="page"><?php echo esc_html( $product->get_name() ); ?></li>
		</ol>
	</nav>

	<div class="lt-product__grid">
		<section class="lt-product__gallery" aria-label="<?php esc_attr_e( 'Photographs', 'labeltrunc' ); ?>" data-lt-gallery>
			<ul class="lt-product__images" tabindex="0" aria-label="<?php esc_attr_e( 'Photographs; scroll to see more', 'labeltrunc' ); ?>">
				<?php foreach ( $images as $i => $image_id ) : ?>
					<li class="lt-product__image">
						<?php
						echo wp_get_attachment_image(
							$image_id,
							'woocommerce_single',
							false,
							array(
								'loading'       => 0 === $i ? 'eager' : 'lazy',
								'fetchpriority' => 0 === $i ? 'high' : 'auto',
								'sizes'         => '(min-width: 1000px) 56vw, 100vw',
							)
						);
						?>
					</li>
				<?php endforeach; ?>
				<?php if ( ! $images ) : ?>
					<li class="lt-product__image lt-product__image--none"><span><?php esc_html_e( 'Photographs coming soon', 'labeltrunc' ); ?></span></li>
				<?php endif; ?>
			</ul>
			<?php if ( count( $images ) > 1 ) : ?>
				<p class="lt-product__counter lt-tabular" aria-hidden="true"><span data-lt-gallery-current>1</span> / <?php echo count( $images ); ?></p>
			<?php endif; ?>
		</section>

		<div class="lt-product__panel">
			<div class="lt-product__sticky">
				<p class="lt-product__meta">
					<?php if ( $brand ) : ?>
						<a href="<?php echo esc_url( add_query_arg( 'brand', $brand->slug, wc_get_page_permalink( 'shop' ) ) ); ?>"><?php echo esc_html( $brand->name ); ?></a>
					<?php endif; ?>
					<span class="lt-tabular"><?php echo esc_html( $product->get_sku() ); ?></span>
				</p>
				<h1 class="lt-product__title"><?php echo esc_html( $product->get_name() ); ?></h1>
				<p class="lt-product__price lt-tabular"><?php echo wp_kses_post( $product->get_price_html() ); ?></p>

				<div class="lt-product__buy" id="lt-buy">
					<?php if ( $can_buy ) : ?>
						<?php woocommerce_template_single_add_to_cart(); ?>
						<p class="lt-product__note"><?php echo esc_html( sprintf( /* translators: %d: hours */ __( 'One of a kind. Adding it to your cart holds it for you for up to %d hours.', 'labeltrunc' ), $hold_hours ) ); ?></p>
					<?php elseif ( 'in_cart' === $availability['state'] ) : ?>
						<p class="lt-product__state"><?php echo lt_status_markup( $availability ); // phpcs:ignore ?> <?php echo esc_html( $availability['detail'] ?? '' ); ?></p>
						<a class="lt-button lt-button--block" href="<?php echo esc_url( wc_get_cart_url() ); ?>"><?php esc_html_e( 'Go to your cart', 'labeltrunc' ); ?></a>
					<?php elseif ( in_array( $availability['state'], array( 'held', 'reserved' ), true ) ) : ?>
						<p class="lt-product__state"><?php echo lt_status_markup( $availability ); // phpcs:ignore ?> <?php echo esc_html( $availability['detail'] ?? '' ); ?></p>
						<p class="lt-product__note"><?php esc_html_e( 'Someone is holding this piece. If they do not buy it, it comes back to the shop. Ask us to let you know.', 'labeltrunc' ); ?></p>
					<?php else : ?>
						<p class="lt-product__state"><?php echo lt_status_markup( array( 'state' => 'sold', 'label' => __( 'Sold', 'labeltrunc' ) ) ); // phpcs:ignore ?></p>
						<p class="lt-product__note"><?php esc_html_e( 'This piece has found a new home. Ask us about similar pieces.', 'labeltrunc' ); ?></p>
					<?php endif; ?>
					<p class="lt-product__ask">
						<a class="lt-link" href="<?php echo esc_url( $ask_url ); ?>"><?php esc_html_e( 'Ask about this piece', 'labeltrunc' ); ?></a>
						<?php if ( $wa_url ) : ?>
							<a class="lt-link" href="<?php echo esc_url( $wa_url ); ?>" target="_blank" rel="noopener noreferrer"><?php echo lt_icon( 'message-circle', 16 ); // phpcs:ignore ?> <?php esc_html_e( 'WhatsApp', 'labeltrunc' ); ?></a>
						<?php endif; ?>
					</p>
				</div>

				<dl class="lt-ledger lt-product__facts">
					<?php if ( $condition ) : ?>
						<div><dt><?php esc_html_e( 'Condition', 'labeltrunc' ); ?></dt><dd><?php echo esc_html( $condition ); ?></dd></div>
					<?php endif; ?>
					<?php if ( $includes ) : ?>
						<div><dt><?php esc_html_e( 'Comes with', 'labeltrunc' ); ?></dt><dd><?php echo esc_html( implode( ', ', $includes ) ); ?></dd></div>
					<?php endif; ?>
					<?php foreach ( $details as $row ) : ?>
						<div><dt><?php echo esc_html( $row[0] ); ?></dt><dd><?php echo esc_html( $row[1] ); ?></dd></div>
					<?php endforeach; ?>
				</dl>

				<?php if ( $notes ) : ?>
					<div class="lt-product__notes">
						<h2 class="lt-label"><?php esc_html_e( 'Condition notes', 'labeltrunc' ); ?></h2>
						<p><?php echo esc_html( $notes ); ?></p>
					</div>
				<?php endif; ?>

				<?php if ( trim( (string) $product->get_description() ) ) : ?>
					<div class="lt-prose lt-product__desc"><?php the_content(); ?></div>
				<?php endif; ?>

				<ul class="lt-assure">
					<li><?php esc_html_e( 'Guaranteed authentic, with a money-back guarantee.', 'labeltrunc' ); ?></li>
					<li><?php lt_online_payments() ? esc_html_e( 'Pay online (FPX, cards, Touch ’n Go) or reserve and pay at the boutique.', 'labeltrunc' ) : esc_html_e( 'Reserve online and pay when you collect at the boutique.', 'labeltrunc' ); ?></li>
					<li><?php esc_html_e( 'Collect at Ukay Boulevard, Ampang. See it in person first by appointment.', 'labeltrunc' ); ?></li>
				</ul>
			</div>
		</div>
	</div>

	<?php
	$related = lt_related_products( $product );
	if ( $related ) :
		?>
		<section class="lt-section lt-section--rule" aria-labelledby="lt-related-title">
			<div class="lt-section__head"><h2 id="lt-related-title" class="lt-h2"><?php esc_html_e( 'More from the boutique', 'labeltrunc' ); ?></h2></div>
			<ul class="lt-grid lt-grid--4">
				<?php
				$main = $product;
				foreach ( $related as $rp ) {
					$GLOBALS['product'] = $rp; // phpcs:ignore WordPress.WP.GlobalVariablesOverride.Prohibited
					wc_get_template_part( 'content', 'product' );
				}
				$GLOBALS['product'] = $main; // phpcs:ignore WordPress.WP.GlobalVariablesOverride.Prohibited
				?>
			</ul>
		</section>
	<?php endif; ?>

	<?php if ( $can_buy ) : ?>
		<div class="lt-buybar" data-lt-buybar hidden>
			<span class="lt-buybar__price lt-tabular"><?php echo wp_kses_post( $product->get_price_html() ); ?></span>
			<a class="lt-button lt-button--small" href="#lt-buy"><?php esc_html_e( 'Add to cart', 'labeltrunc' ); ?></a>
		</div>
	<?php endif; ?>
</div>
<?php
do_action( 'woocommerce_after_single_product' );
