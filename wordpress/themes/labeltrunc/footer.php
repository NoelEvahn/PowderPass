<?php
/**
 * Footer: a compact ledger of visit, contact and shop details with the policies.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;
?>
</main>
<footer class="lt-footer">
	<div class="lt-wrap">
		<div class="lt-footer__grid">
			<div class="lt-footer__col lt-footer__brand">
				<a href="<?php echo esc_url( home_url( '/' ) ); ?>" aria-label="<?php esc_attr_e( 'labeltrunc. Home', 'labeltrunc' ); ?>"><?php echo lt_wordmark(); // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped ?></a>
				<?php if ( lt_mod( 'lt_tagline' ) ) : ?>
					<p><?php echo esc_html( lt_mod( 'lt_tagline' ) ); ?></p>
				<?php endif; ?>
			</div>
			<div class="lt-footer__col">
				<h2 class="lt-footer__h"><?php esc_html_e( 'Visit', 'labeltrunc' ); ?></h2>
				<address>
					<?php echo implode( '<br>', array_map( 'esc_html', lt_address_lines() ) ); // phpcs:ignore ?>
				</address>
				<?php if ( lt_mod( 'lt_maps_url' ) ) : ?>
					<a class="lt-link" href="<?php echo esc_url( lt_mod( 'lt_maps_url' ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Directions', 'labeltrunc' ); ?> <?php echo lt_icon( 'arrow-up-right', 14 ); // phpcs:ignore ?><span class="screen-reader-text"><?php esc_html_e( '(opens Google Maps)', 'labeltrunc' ); ?></span></a>
				<?php endif; ?>
			</div>
			<div class="lt-footer__col">
				<h2 class="lt-footer__h"><?php esc_html_e( 'Contact', 'labeltrunc' ); ?></h2>
				<ul class="lt-footer__list">
					<?php if ( lt_whatsapp_url() ) : ?>
						<li><a href="<?php echo esc_url( lt_whatsapp_url() ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'WhatsApp', 'labeltrunc' ); ?></a> <span><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></span></li>
					<?php endif; ?>
					<?php if ( lt_tel_url() ) : ?>
						<li><a href="<?php echo esc_url( lt_tel_url() ); ?>"><?php esc_html_e( 'Call', 'labeltrunc' ); ?></a> <span><?php echo esc_html( lt_mod( 'lt_phone' ) ); ?></span></li>
					<?php endif; ?>
					<?php if ( lt_mod( 'lt_email' ) ) : ?>
						<li><a href="mailto:<?php echo esc_attr( antispambot( lt_mod( 'lt_email' ) ) ); ?>"><?php echo esc_html( antispambot( lt_mod( 'lt_email' ) ) ); ?></a></li>
					<?php endif; ?>
				</ul>
			</div>
			<div class="lt-footer__col">
				<h2 class="lt-footer__h"><?php esc_html_e( 'Follow', 'labeltrunc' ); ?></h2>
				<ul class="lt-footer__list">
					<?php foreach ( lt_social_links() as $link ) : ?>
						<li><a href="<?php echo esc_url( $link[1] ); ?>" target="_blank" rel="noopener noreferrer"><?php echo esc_html( $link[0] ); ?></a> <span><?php echo esc_html( $link[2] ); ?></span></li>
					<?php endforeach; ?>
				</ul>
			</div>
		</div>
		<div class="lt-footer__legal">
			<?php
			wp_nav_menu(
				array(
					'theme_location' => 'footer',
					'container'      => 'nav',
					'container_aria_label' => __( 'Policies', 'labeltrunc' ),
					'menu_class'     => 'lt-footer__policies',
					'depth'          => 1,
					'fallback_cb'    => false,
				)
			);
			?>
			<p><?php esc_html_e( 'Label Trunc is an independent boutique and is not affiliated with, endorsed by or sponsored by any designer house. Brand names belong to their owners.', 'labeltrunc' ); ?></p>
			<p>&copy; <?php echo esc_html( wp_date( 'Y' ) ); ?> <?php echo esc_html( lt_mod( 'lt_registered_name' ) ?: get_bloginfo( 'name' ) ); ?><?php echo lt_mod( 'lt_ssm' ) ? ' · SSM ' . esc_html( lt_mod( 'lt_ssm' ) ) : ''; ?></p>
		</div>
	</div>
</footer>
<?php wp_footer(); ?>
</body>
</html>
