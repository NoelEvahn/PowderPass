<?php
/**
 * Shop: title and count, a slim toolbar (search, category, brand, condition,
 * order, include sold) that works without JavaScript, then the pieces.
 *
 * @package LabelTrunc
 * @version 8.6.0
 */

defined( 'ABSPATH' ) || exit;

get_header();

$filters  = lt_shop_filters();
$shop_url = wc_get_page_permalink( 'shop' );
$current  = is_product_category() ? get_queried_object() : null;
$title    = $current ? $current->name : ( is_search() ? sprintf( /* translators: %s: search terms */ __( 'Results for “%s”', 'labeltrunc' ), get_search_query() ) : __( 'Shop', 'labeltrunc' ) );
$total    = (int) wc_get_loop_prop( 'total' );
$cats     = get_terms(
	array(
		'taxonomy'   => 'product_cat',
		'hide_empty' => true,
		'exclude'    => array( (int) get_option( 'default_product_cat' ) ),
	)
);
$brands   = lt_shop_brands();
$orderby  = isset( $_GET['orderby'] ) ? sanitize_key( wp_unslash( $_GET['orderby'] ) ) : 'date'; // phpcs:ignore WordPress.Security.NonceVerification.Recommended
$action   = $current ? get_term_link( $current ) : $shop_url;
?>
<header class="lt-page-head lt-wrap">
	<nav class="lt-crumbs" aria-label="<?php esc_attr_e( 'Breadcrumb', 'labeltrunc' ); ?>">
		<ol>
			<li><a href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php esc_html_e( 'Home', 'labeltrunc' ); ?></a></li>
			<?php if ( $current || is_search() ) : ?>
				<li><a href="<?php echo esc_url( $shop_url ); ?>"><?php esc_html_e( 'Shop', 'labeltrunc' ); ?></a></li>
			<?php endif; ?>
			<li aria-current="page"><?php echo esc_html( $title ); ?></li>
		</ol>
	</nav>
	<div class="lt-page-head__row">
		<h1 class="lt-page-head__title"><?php echo esc_html( $title ); ?></h1>
		<p class="lt-meta lt-tabular" role="status"><?php echo esc_html( sprintf( /* translators: %d: number of pieces */ _n( '%d piece', '%d pieces', $total, 'labeltrunc' ), $total ) ); ?></p>
	</div>
	<?php if ( ! $current && ! is_search() ) : ?>
		<p class="lt-page-head__intro"><?php esc_html_e( 'Every piece is one of a kind and guaranteed authentic. Add one to your cart and it is held for you while you decide.', 'labeltrunc' ); ?></p>
	<?php endif; ?>
</header>

<div class="lt-wrap">
	<?php if ( ! is_wp_error( $cats ) && $cats ) : ?>
		<nav class="lt-tabs" aria-label="<?php esc_attr_e( 'Categories', 'labeltrunc' ); ?>">
			<a href="<?php echo esc_url( $shop_url ); ?>"<?php echo ! $current ? ' aria-current="page"' : ''; ?>><?php esc_html_e( 'All', 'labeltrunc' ); ?></a>
			<?php foreach ( $cats as $cat ) : ?>
				<a href="<?php echo esc_url( get_term_link( $cat ) ); ?>"<?php echo ( $current && $current->term_id === $cat->term_id ) ? ' aria-current="page"' : ''; ?>><?php echo esc_html( $cat->name ); ?></a>
			<?php endforeach; ?>
		</nav>
	<?php endif; ?>

	<form class="lt-toolbar" id="search" method="get" action="<?php echo esc_url( $action ); ?>" role="search" data-lt-autosubmit>
		<div class="lt-toolbar__search">
			<label for="lt-q" class="screen-reader-text"><?php esc_html_e( 'Search pieces', 'labeltrunc' ); ?></label>
			<?php echo lt_icon( 'search', 16 ); // phpcs:ignore ?>
			<input id="lt-q" type="search" name="s" value="<?php echo esc_attr( get_search_query() ); ?>" placeholder="<?php esc_attr_e( 'Search name, colour, material', 'labeltrunc' ); ?>">
			<input type="hidden" name="post_type" value="product">
		</div>
		<?php if ( $brands ) : ?>
			<label class="lt-toolbar__field"><span><?php esc_html_e( 'Designer', 'labeltrunc' ); ?></span>
				<select name="brand">
					<option value=""><?php esc_html_e( 'All', 'labeltrunc' ); ?></option>
					<?php foreach ( $brands as $b ) : ?>
						<option value="<?php echo esc_attr( $b->slug ); ?>" <?php selected( $filters['brand'], $b->slug ); ?>><?php echo esc_html( $b->name ); ?></option>
					<?php endforeach; ?>
				</select>
			</label>
		<?php endif; ?>
		<label class="lt-toolbar__field"><span><?php esc_html_e( 'Condition', 'labeltrunc' ); ?></span>
			<select name="condition">
				<option value=""><?php esc_html_e( 'Any', 'labeltrunc' ); ?></option>
				<?php foreach ( ( class_exists( 'LT_Product_Fields' ) ? LT_Product_Fields::CONDITIONS : array() ) as $key => $label ) : ?>
					<option value="<?php echo esc_attr( $key ); ?>" <?php selected( $filters['condition'], $key ); ?>><?php echo esc_html( $label ); ?></option>
				<?php endforeach; ?>
			</select>
		</label>
		<label class="lt-toolbar__field"><span><?php esc_html_e( 'Order', 'labeltrunc' ); ?></span>
			<select name="orderby">
				<option value="date" <?php selected( $orderby, 'date' ); ?>><?php esc_html_e( 'Newest', 'labeltrunc' ); ?></option>
				<option value="price" <?php selected( $orderby, 'price' ); ?>><?php esc_html_e( 'Price, low to high', 'labeltrunc' ); ?></option>
				<option value="price-desc" <?php selected( $orderby, 'price-desc' ); ?>><?php esc_html_e( 'Price, high to low', 'labeltrunc' ); ?></option>
			</select>
		</label>
		<label class="lt-toolbar__check"><input type="checkbox" name="sold" value="1" <?php checked( $filters['sold'] ); ?> data-lt-sold> <?php esc_html_e( 'Show recently sold', 'labeltrunc' ); ?></label>
		<input type="hidden" name="sold" value="0" data-lt-sold-off <?php disabled( $filters['sold'] ); ?>>
		<button class="lt-button lt-button--small" type="submit"><?php esc_html_e( 'Apply', 'labeltrunc' ); ?></button>
	</form>

	<?php woocommerce_output_all_notices(); ?>

	<?php if ( woocommerce_product_loop() && have_posts() ) : ?>
		<ul class="lt-grid lt-grid--3">
			<?php
			while ( have_posts() ) {
				the_post();
				wc_get_template_part( 'content', 'product' );
			}
			?>
		</ul>
		<div class="lt-pagination"><?php woocommerce_pagination(); ?></div>
	<?php else : ?>
		<div class="lt-empty">
			<?php if ( $filters['brand'] || $filters['condition'] || is_search() || ! $filters['sold'] ) : ?>
				<p class="lt-empty__title"><?php esc_html_e( 'No pieces match.', 'labeltrunc' ); ?></p>
				<p><a class="lt-link" href="<?php echo esc_url( $shop_url ); ?>"><?php esc_html_e( 'Clear filters', 'labeltrunc' ); ?></a></p>
			<?php else : ?>
				<p class="lt-empty__title"><?php esc_html_e( 'New pieces are being photographed.', 'labeltrunc' ); ?></p>
				<p><?php esc_html_e( 'Ask us what is in the boutique this week, or follow us for new arrivals.', 'labeltrunc' ); ?></p>
				<p class="lt-actions">
					<?php if ( lt_whatsapp_url() ) : ?>
						<a class="lt-button" href="<?php echo esc_url( lt_whatsapp_url( 'Hi Label Trunc, what is in the boutique this week?' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Ask on WhatsApp', 'labeltrunc' ); ?></a>
					<?php endif; ?>
					<a class="lt-link" href="<?php echo esc_url( add_query_arg( 'topic', 'consign', lt_page_url( 'contact' ) ) ); ?>"><?php esc_html_e( 'Selling a bag? Ask about consigning', 'labeltrunc' ); ?></a>
				</p>
			<?php endif; ?>
		</div>
	<?php endif; ?>
</div>
<?php
get_footer();
