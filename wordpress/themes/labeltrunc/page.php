<?php
/**
 * Pages: About, Buying info and the policy pages, edited in the block editor.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();
while ( have_posts() ) :
	the_post();
	$lang    = get_post_meta( get_the_ID(), '_lt_lang', true );
	$updated = get_post_meta( get_the_ID(), '_lt_last_updated', true );
	$pending = get_post_meta( get_the_ID(), '_lt_needs_confirmation', true );
	$is_wc   = function_exists( 'is_cart' ) && ( is_cart() || is_checkout() || is_account_page() );
	?>
	<article <?php post_class( $is_wc ? 'lt-shop-page' : 'lt-page' ); ?><?php echo $lang ? ' lang="' . esc_attr( $lang ) . '"' : ''; ?>>
		<?php lt_page_header( get_the_title(), $is_wc ? '' : get_the_excerpt(), $is_wc ? array() : array( get_the_title() => '' ) ); ?>
		<?php if ( $updated || $pending ) : ?>
			<div class="lt-wrap lt-page-meta">
				<?php if ( $updated ) : ?>
					<p class="lt-meta"><?php esc_html_e( 'Last updated', 'labeltrunc' ); ?> <time datetime="<?php echo esc_attr( $updated ); ?>"><?php echo esc_html( wp_date( 'j F Y', strtotime( $updated ) ) ); ?></time></p>
				<?php endif; ?>
				<?php if ( $pending ) : ?>
					<p class="lt-notice lt-notice--pending"><strong><?php esc_html_e( 'Awaiting confirmation.', 'labeltrunc' ); ?></strong> <?php esc_html_e( 'Parts of this page are still being confirmed by Label Trunc. Items in [square brackets] are not final.', 'labeltrunc' ); ?></p>
				<?php endif; ?>
			</div>
		<?php endif; ?>
		<div class="<?php echo $is_wc ? 'lt-wrap lt-wc' : 'lt-wrap lt-prose-wrap'; ?>">
			<div class="<?php echo $is_wc ? '' : 'lt-prose'; ?>">
				<?php the_content(); ?>
			</div>
		</div>
	</article>
	<?php
endwhile;
get_footer();
