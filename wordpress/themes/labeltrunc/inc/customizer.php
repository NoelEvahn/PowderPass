<?php
/**
 * Appearance → Customize → Label Trunc: contact details, opening hours, social
 * links and the home page words and photos. Edited once, used everywhere.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

/** Setting definitions: id => [label, type, default, section, description]. */
function lt_customizer_fields() {
	return array(
		// Contact.
		'lt_phone'        => array( 'Phone (as shown)', 'text', '', 'lt_contact', 'e.g. +60 12-421 1205' ),
		'lt_whatsapp'     => array( 'WhatsApp number (digits only)', 'text', '', 'lt_contact', 'Country code first, e.g. 60124211205' ),
		'lt_email'        => array( 'Email', 'email', '', 'lt_contact', '' ),
		'lt_notify_email' => array( 'Send enquiries to', 'email', '', 'lt_contact', 'Leave empty to use the site admin email.' ),
		'lt_address'      => array( 'Address', 'textarea', '', 'lt_contact', 'One line per row.' ),
		'lt_maps_url'     => array( 'Google Maps link', 'url', '', 'lt_contact', 'The "Share" link from Google Maps.' ),
		'lt_hours'        => array( 'Opening hours', 'textarea', '', 'lt_contact', 'One row per line, e.g. "Tue–Sat | 11am – 7pm". Leave empty until confirmed: the site then says appointments are recommended.' ),
		'lt_visit_note'   => array( 'Visit note', 'textarea', '', 'lt_contact', '' ),
		// Social.
		'lt_instagram'    => array( 'Instagram (main)', 'url', '', 'lt_social', '' ),
		'lt_instagram2'   => array( 'Instagram (second account)', 'url', '', 'lt_social', '' ),
		'lt_tiktok'       => array( 'TikTok', 'url', '', 'lt_social', '' ),
		'lt_facebook'     => array( 'Facebook', 'url', '', 'lt_social', '' ),
		// Home.
		'lt_tagline'          => array( 'Tagline', 'text', '', 'lt_home', 'Used in the footer and for search engines.' ),
		'lt_hero_heading'     => array( 'Headline', 'text', 'Designer pieces, new and pre-loved.', 'lt_home', '' ),
		'lt_hero_intro'       => array( 'Introduction', 'textarea', 'A boutique at Ukay Boulevard, Ampang. Hold a piece online for up to a day, pay online or at the boutique, or come and see it first.', 'lt_home', '' ),
		'lt_hero_image'       => array( 'Hero photo (optional)', 'image', '', 'lt_home', 'A real photo of the boutique or your pieces. Without one, the newest piece in stock is shown.' ),
		'lt_statement_lead'   => array( 'Statement: first sentence', 'text', 'Curated, collected, consigned.', 'lt_home', 'Shown large; the next sentence is shown softer.' ),
		'lt_statement_rest'   => array( 'Statement: continuation', 'textarea', 'Every piece is checked and guaranteed authentic, money-back, and held for you while you decide. Luxury deserves to be loved again.', 'lt_home', '' ),
		'lt_about_heading'    => array( 'About: heading', 'text', 'What is Label Trunc?', 'lt_home', '' ),
		'lt_about_body'       => array( 'About: text', 'textarea', 'Label Trunc is a boutique in Ampang for new and pre-loved designer bags. Every piece is checked and guaranteed authentic. Browse what is in the boutique now, reserve online, and collect in person.', 'lt_home', '' ),
		'lt_consign_heading'  => array( 'Consignment: heading', 'text', 'Selling a designer bag?', 'lt_home', '' ),
		'lt_consign_body'     => array( 'Consignment: text', 'textarea', 'Send us a few photos and we will explain how consignment works before anything is agreed.', 'lt_home', '' ),
		'lt_visit_image'      => array( 'Boutique photo (optional)', 'image', '', 'lt_home', 'Shown beside the visit details on the home page.' ),
		// Business.
		'lt_registered_name'  => array( 'Registered business name', 'text', '', 'lt_business', 'Leave empty until confirmed.' ),
		'lt_ssm'              => array( 'SSM registration number', 'text', '', 'lt_business', 'Leave empty until confirmed.' ),
	);
}

/** A Label Trunc setting, with its default. */
function lt_mod( $key ) {
	$fields = lt_customizer_fields();
	return get_theme_mod( $key, isset( $fields[ $key ] ) ? $fields[ $key ][2] : '' );
}

add_action(
	'customize_register',
	static function ( WP_Customize_Manager $wp_customize ) {
		$wp_customize->add_panel(
			'lt_panel',
			array(
				'title'    => __( 'Label Trunc', 'labeltrunc' ),
				'priority' => 20,
			)
		);
		foreach ( array(
			'lt_contact'  => __( 'Contact, address and hours', 'labeltrunc' ),
			'lt_social'   => __( 'Social links', 'labeltrunc' ),
			'lt_home'     => __( 'Home page', 'labeltrunc' ),
			'lt_business' => __( 'Business details', 'labeltrunc' ),
		) as $id => $title ) {
			$wp_customize->add_section(
				$id,
				array(
					'title' => $title,
					'panel' => 'lt_panel',
				)
			);
		}
		foreach ( lt_customizer_fields() as $id => $f ) {
			list( $label, $type, $default, $section, $desc ) = $f;
			$sanitize = array(
				'text'     => 'sanitize_text_field',
				'textarea' => 'sanitize_textarea_field',
				'email'    => 'sanitize_email',
				'url'      => 'esc_url_raw',
				'image'    => 'absint',
			)[ $type ];
			if ( 'lt_whatsapp' === $id ) {
				$sanitize = static fn( $v ) => preg_replace( '/\D/', '', (string) $v );
			}
			$wp_customize->add_setting(
				$id,
				array(
					'default'           => $default,
					'sanitize_callback' => $sanitize,
					'transport'         => 'refresh',
				)
			);
			if ( 'image' === $type ) {
				$wp_customize->add_control(
					new WP_Customize_Media_Control(
						$wp_customize,
						$id,
						array(
							'label'       => $label,
							'description' => $desc,
							'section'     => $section,
							'mime_type'   => 'image',
						)
					)
				);
				continue;
			}
			$wp_customize->add_control(
				$id,
				array(
					'label'       => $label,
					'description' => $desc,
					'section'     => $section,
					'type'        => $type,
				)
			);
		}
	}
);
