<?php
/**
 * Fallback listing (the site has no blog; this covers search results and archives).
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();
$title = is_search() ? sprintf( /* translators: %s: search terms */ __( 'Results for “%s”', 'labeltrunc' ), get_search_query() ) : wp_strip_all_tags( get_the_archive_title() );
lt_page_header( $title ? $title : get_bloginfo( 'name' ) );
?>
<div class="lt-wrap lt-section">
	<?php if ( have_posts() ) : ?>
		<ul class="lt-results">
			<?php
			while ( have_posts() ) :
				the_post();
				?>
				<li><a href="<?php the_permalink(); ?>"><?php the_title(); ?></a> <span class="lt-meta"><?php echo esc_html( get_post_type_object( get_post_type() )->labels->singular_name ); ?></span></li>
			<?php endwhile; ?>
		</ul>
		<?php the_posts_pagination(); ?>
	<?php else : ?>
		<p><?php esc_html_e( 'Nothing found.', 'labeltrunc' ); ?> <a class="lt-link" href="<?php echo esc_url( function_exists( 'wc_get_page_permalink' ) ? wc_get_page_permalink( 'shop' ) : home_url( '/' ) ); ?>"><?php esc_html_e( 'Browse the shop', 'labeltrunc' ); ?></a></p>
	<?php endif; ?>
</div>
<?php
get_footer();
