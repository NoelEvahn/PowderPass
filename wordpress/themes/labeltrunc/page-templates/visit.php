<?php
/**
 * Template Name: Visit the boutique
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();
the_post();
lt_page_header( get_the_title(), get_the_excerpt(), array( get_the_title() => '' ) );
$hours = lt_hours_rows();
?>
<div class="lt-wrap lt-section lt-split">
	<div class="lt-split__aside">
		<dl class="lt-ledger">
			<div><dt><?php esc_html_e( 'Address', 'labeltrunc' ); ?></dt><dd><?php echo implode( '<br>', array_map( 'esc_html', lt_address_lines() ) ); // phpcs:ignore ?></dd></div>
			<?php if ( $hours ) : ?>
				<?php foreach ( $hours as $row ) : ?>
					<div><dt><?php echo esc_html( $row[0] ); ?></dt><dd class="lt-tabular"><?php echo esc_html( $row[1] ); ?></dd></div>
				<?php endforeach; ?>
			<?php else : ?>
				<div><dt><?php esc_html_e( 'Opening hours', 'labeltrunc' ); ?></dt><dd><?php esc_html_e( 'Please message us to arrange a time.', 'labeltrunc' ); ?></dd></div>
			<?php endif; ?>
			<?php if ( lt_mod( 'lt_phone' ) ) : ?>
				<div><dt><?php esc_html_e( 'Phone', 'labeltrunc' ); ?></dt><dd><a href="<?php echo esc_url( lt_tel_url() ); ?>"><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></a></dd></div>
			<?php endif; ?>
			<?php if ( lt_mod( 'lt_email' ) ) : ?>
				<div><dt><?php esc_html_e( 'Email', 'labeltrunc' ); ?></dt><dd><a href="mailto:<?php echo esc_attr( antispambot( lt_mod( 'lt_email' ) ) ); ?>"><?php echo esc_html( antispambot( lt_mod( 'lt_email' ) ) ); ?></a></dd></div>
			<?php endif; ?>
		</dl>
	</div>
	<div class="lt-split__body">
		<h2 class="lt-h3"><?php esc_html_e( 'Book a time', 'labeltrunc' ); ?></h2>
		<p class="lt-lead"><?php echo esc_html( lt_mod( 'lt_visit_note' ) ); ?></p>
		<div class="lt-actions">
			<?php if ( lt_whatsapp_url() ) : ?>
				<a class="lt-button" href="<?php echo esc_url( lt_whatsapp_url( 'Hi Label Trunc, I would like to book a visit.' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Book on WhatsApp', 'labeltrunc' ); ?></a>
			<?php endif; ?>
			<?php if ( lt_mod( 'lt_maps_url' ) ) : ?>
				<a class="lt-link" href="<?php echo esc_url( lt_mod( 'lt_maps_url' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Get directions', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-up-right', 16 ); // phpcs:ignore ?><span class="screen-reader-text"><?php esc_html_e( '(opens Google Maps)', 'labeltrunc' ); ?></span></a>
			<?php endif; ?>
			<a class="lt-link" href="<?php echo esc_url( add_query_arg( 'topic', 'visit', lt_page_url( 'contact' ) ) ); ?>"><?php esc_html_e( 'Or send a message', 'labeltrunc' ); ?></a>
		</div>
		<?php if ( get_the_content() ) : ?>
			<div class="lt-prose"><?php the_content(); ?></div>
		<?php endif; ?>
	</div>
</div>
<?php
get_footer();
