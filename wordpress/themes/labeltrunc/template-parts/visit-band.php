<?php
/**
 * Visit: address, hours or appointment note, and the two ways to get there.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

$visit_image = (int) lt_mod( 'lt_visit_image' );
$hours       = lt_hours_rows();
?>
<section class="lt-visit" aria-labelledby="lt-visit-title">
	<div class="lt-wrap lt-visit__grid<?php echo $visit_image ? ' has-image' : ''; ?>">
		<div class="lt-visit__body">
			<h2 id="lt-visit-title" class="lt-h2"><?php esc_html_e( 'Come and hold things', 'labeltrunc' ); ?></h2>
			<dl class="lt-ledger lt-ledger--light">
				<div><dt><?php esc_html_e( 'Address', 'labeltrunc' ); ?></dt><dd><?php echo implode( '<br>', array_map( 'esc_html', lt_address_lines() ) ); // phpcs:ignore ?></dd></div>
				<?php if ( $hours ) : ?>
					<?php foreach ( $hours as $row ) : ?>
						<div><dt><?php echo esc_html( $row[0] ); ?></dt><dd class="lt-tabular"><?php echo esc_html( $row[1] ); ?></dd></div>
					<?php endforeach; ?>
				<?php else : ?>
					<div><dt><?php esc_html_e( 'Opening hours', 'labeltrunc' ); ?></dt><dd><?php echo esc_html( lt_mod( 'lt_visit_note' ) ?: __( 'Please message us to arrange a time.', 'labeltrunc' ) ); ?></dd></div>
				<?php endif; ?>
				<?php if ( lt_mod( 'lt_phone' ) ) : ?>
					<div><dt><?php esc_html_e( 'Phone', 'labeltrunc' ); ?></dt><dd><a href="<?php echo esc_url( lt_tel_url() ); ?>"><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></a></dd></div>
				<?php endif; ?>
			</dl>
			<div class="lt-actions">
				<?php if ( lt_whatsapp_url() ) : ?>
					<a class="lt-button lt-button--light" href="<?php echo esc_url( lt_whatsapp_url( 'Hi Label Trunc, I would like to book a visit.' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Book a visit on WhatsApp', 'labeltrunc' ); ?></a>
				<?php endif; ?>
				<?php if ( lt_mod( 'lt_maps_url' ) ) : ?>
					<a class="lt-link lt-link--light" href="<?php echo esc_url( lt_mod( 'lt_maps_url' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Directions', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-up-right', 16 ); // phpcs:ignore ?><span class="screen-reader-text"><?php esc_html_e( '(opens Google Maps)', 'labeltrunc' ); ?></span></a>
				<?php endif; ?>
			</div>
		</div>
		<?php if ( $visit_image ) : ?>
			<figure class="lt-visit__media"><?php echo wp_get_attachment_image( $visit_image, 'lt-portrait', false, array( 'sizes' => '(min-width: 1000px) 40vw, 100vw' ) ); ?></figure>
		<?php endif; ?>
	</div>
</section>
