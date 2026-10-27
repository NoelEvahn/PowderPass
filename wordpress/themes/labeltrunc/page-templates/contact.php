<?php
/**
 * Template Name: Contact
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

get_header();
the_post();
lt_page_header( get_the_title(), get_the_excerpt(), array( get_the_title() => '' ) );
?>
<div class="lt-wrap lt-section lt-split">
	<div class="lt-split__aside">
		<h2 class="lt-h3"><?php esc_html_e( 'Quickest', 'labeltrunc' ); ?></h2>
		<dl class="lt-ledger">
			<?php if ( lt_whatsapp_url() ) : ?>
				<div><dt><?php esc_html_e( 'WhatsApp', 'labeltrunc' ); ?></dt><dd><a href="<?php echo esc_url( lt_whatsapp_url() ); ?>" target="_blank" rel="noopener noreferrer"><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></a></dd></div>
			<?php endif; ?>
			<?php if ( lt_tel_url() ) : ?>
				<div><dt><?php esc_html_e( 'Call', 'labeltrunc' ); ?></dt><dd><a href="<?php echo esc_url( lt_tel_url() ); ?>"><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></a></dd></div>
			<?php endif; ?>
			<?php if ( lt_mod( 'lt_email' ) ) : ?>
				<div><dt><?php esc_html_e( 'Email', 'labeltrunc' ); ?></dt><dd><a href="mailto:<?php echo esc_attr( antispambot( lt_mod( 'lt_email' ) ) ); ?>"><?php echo esc_html( antispambot( lt_mod( 'lt_email' ) ) ); ?></a></dd></div>
			<?php endif; ?>
			<?php foreach ( lt_social_links() as $link ) : ?>
				<div><dt><?php echo esc_html( $link[0] ); ?></dt><dd><a href="<?php echo esc_url( $link[1] ); ?>" target="_blank" rel="noopener noreferrer"><?php echo esc_html( $link[2] ); ?></a></dd></div>
			<?php endforeach; ?>
		</dl>
	</div>
	<div class="lt-split__body" id="enquiry">
		<h2 class="lt-h3"><?php esc_html_e( 'Send an enquiry', 'labeltrunc' ); ?></h2>
		<?php the_content(); ?>
	</div>
</div>
<?php
get_footer();
