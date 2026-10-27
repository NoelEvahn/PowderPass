<?php
/**
 * Product card: photo (second photo on hover where there is one), brand and stock
 * number, name, price and status in words. The whole card links to the piece.
 *
 * @package LabelTrunc
 * @version 9.4.0
 */

defined( 'ABSPATH' ) || exit;

global $product;

if ( ! is_a( $product, WC_Product::class ) || ! $product->is_visible() ) {
	return;
}

$availability = lt_availability( $product );
$gallery      = $product->get_gallery_image_ids();
$second       = $gallery ? (int) $gallery[0] : 0;
$brand        = lt_brand_name( $product );
$classes      = array( 'lt-card', 'is-' . $availability['state'] );
?>
<li <?php wc_product_class( $classes, $product ); ?>>
	<a class="lt-card__media" href="<?php echo esc_url( $product->get_permalink() ); ?>" tabindex="-1" aria-hidden="true">
		<?php
		if ( $product->get_image_id() ) {
			echo wp_get_attachment_image( $product->get_image_id(), 'woocommerce_thumbnail', false, array( 'class' => 'lt-card__img', 'sizes' => '(min-width: 1100px) 31vw, (min-width: 700px) 46vw, 48vw', 'alt' => '' ) );
			if ( $second ) {
				echo wp_get_attachment_image( $second, 'woocommerce_thumbnail', false, array( 'class' => 'lt-card__img lt-card__img--alt', 'sizes' => '(min-width: 1100px) 31vw, (min-width: 700px) 46vw, 48vw', 'alt' => '', 'loading' => 'lazy' ) );
			}
		} else {
			echo '<span class="lt-card__noimg">' . esc_html__( 'Photo coming', 'labeltrunc' ) . '</span>';
		}
		?>
	</a>
	<div class="lt-card__body">
		<p class="lt-card__meta">
			<span><?php echo esc_html( $brand ); ?></span>
			<span class="lt-tabular"><?php echo esc_html( $product->get_sku() ); ?></span>
		</p>
		<h3 class="lt-card__title"><a href="<?php echo esc_url( $product->get_permalink() ); ?>"><?php echo esc_html( $product->get_name() ); ?></a></h3>
		<p class="lt-card__price">
			<span class="lt-tabular"><?php echo wp_kses_post( $product->get_price_html() ); ?></span>
			<?php echo lt_status_markup( $availability ); // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped ?>
		</p>
	</div>
</li>
