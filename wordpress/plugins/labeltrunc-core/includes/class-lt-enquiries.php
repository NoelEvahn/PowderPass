<?php
/**
 * Contact form: saves each enquiry in the admin (Enquiries) and emails the shop.
 * Spam protection without a CAPTCHA: a hidden field, a minimum fill time and a
 * per-connection limit. Use [lt_enquiry_form] on any page.
 *
 * @package LabelTrunc
 */

defined( 'ABSPATH' ) || exit;

final class LT_Enquiries {

	const POST_TYPE = 'lt_enquiry';

	const TOPICS = array(
		'piece'   => 'A piece in the shop',
		'visit'   => 'Booking a visit',
		'consign' => 'Consigning or selling a bag',
		'order'   => 'An order',
		'other'   => 'Something else',
	);

	const STATUSES = array(
		'new'         => 'New',
		'in_progress' => 'Replied / in progress',
		'closed'      => 'Closed',
	);

	public static function init() {
		add_action( 'init', array( __CLASS__, 'register' ) );
		add_shortcode( 'lt_enquiry_form', array( __CLASS__, 'render_form' ) );
		add_action( 'admin_post_nopriv_lt_enquiry', array( __CLASS__, 'handle' ) );
		add_action( 'admin_post_lt_enquiry', array( __CLASS__, 'handle' ) );
		add_filter( 'manage_' . self::POST_TYPE . '_posts_columns', array( __CLASS__, 'columns' ) );
		add_action( 'manage_' . self::POST_TYPE . '_posts_custom_column', array( __CLASS__, 'column' ), 10, 2 );
		add_action( 'add_meta_boxes_' . self::POST_TYPE, array( __CLASS__, 'meta_boxes' ) );
		add_action( 'save_post_' . self::POST_TYPE, array( __CLASS__, 'save_status' ), 10, 2 );
	}

	public static function register() {
		register_post_type(
			self::POST_TYPE,
			array(
				'labels'          => array(
					'name'          => __( 'Enquiries', 'labeltrunc' ),
					'singular_name' => __( 'Enquiry', 'labeltrunc' ),
					'edit_item'     => __( 'Enquiry', 'labeltrunc' ),
					'all_items'     => __( 'Enquiries', 'labeltrunc' ),
				),
				'public'          => false,
				'show_ui'         => true,
				'show_in_menu'    => true,
				'menu_position'   => 56,
				'menu_icon'       => 'dashicons-email-alt',
				'supports'        => array( 'title' ),
				'capability_type' => 'post',
				'capabilities'    => array(
					'create_posts' => 'do_not_allow',
					'edit_posts'   => 'edit_shop_orders',
					'edit_others_posts' => 'edit_shop_orders',
					'delete_posts' => 'edit_shop_orders',
					'delete_others_posts' => 'edit_shop_orders',
					'read_private_posts' => 'edit_shop_orders',
					'edit_post'    => 'edit_shop_orders',
					'delete_post'  => 'edit_shop_orders',
					'read_post'    => 'edit_shop_orders',
				),
				'map_meta_cap'    => false,
				'show_in_rest'    => false,
			)
		);
	}

	private static function ip_key() {
		$ip = isset( $_SERVER['REMOTE_ADDR'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REMOTE_ADDR'] ) ) : 'unknown';
		return 'lt_enq_' . substr( hash_hmac( 'sha256', $ip, wp_salt( 'nonce' ) ), 0, 32 );
	}

	/** Builds a signed timestamp so the minimum fill time cannot be forged. */
	private static function signed_time() {
		$t = (string) time();
		return $t . '.' . substr( hash_hmac( 'sha256', $t, wp_salt( 'nonce' ) ), 0, 16 );
	}

	private static function check_time( $value ) {
		$parts = explode( '.', (string) $value );
		if ( 2 !== count( $parts ) || ! hash_equals( substr( hash_hmac( 'sha256', $parts[0], wp_salt( 'nonce' ) ), 0, 16 ), $parts[1] ) ) {
			return false;
		}
		$age = time() - (int) $parts[0];
		return $age >= 3 && $age < DAY_IN_SECONDS;
	}

	public static function render_form() {
		// phpcs:disable WordPress.Security.NonceVerification.Recommended
		$state   = isset( $_GET['enquiry'] ) ? sanitize_key( wp_unslash( $_GET['enquiry'] ) ) : '';
		$topic   = isset( $_GET['topic'] ) ? sanitize_key( wp_unslash( $_GET['topic'] ) ) : '';
		$piece   = isset( $_GET['piece'] ) ? absint( $_GET['piece'] ) : 0;
		// phpcs:enable
		// phpcs:ignore WordPress.Security.NonceVerification.Recommended
		$token   = isset( $_GET['lt_form'] ) ? preg_replace( '/[^A-Za-z0-9]/', '', sanitize_text_field( wp_unslash( $_GET['lt_form'] ) ) ) : '';
		$old     = $token ? get_transient( 'lt_enq_old_' . $token ) : false;
		$old     = is_array( $old ) ? $old : array();
		$errors  = $old['errors'] ?? array();
		$values  = $old['values'] ?? array();
		$topic   = $values['topic'] ?? ( isset( self::TOPICS[ $topic ] ) ? $topic : '' );
		$product = $piece ? wc_get_product( $piece ) : null;
		if ( $token ) {
			delete_transient( 'lt_enq_old_' . $token );
		}

		ob_start();
		if ( 'sent' === $state ) {
			echo '<div class="lt-form-status lt-form-status--success" role="status" tabindex="-1">' . esc_html__( 'Thank you. Your message is with us and we will reply as soon as we can.', 'labeltrunc' ) . '</div>';
			return ob_get_clean();
		}
		if ( $errors ) {
			echo '<div class="lt-form-status lt-form-status--error" role="alert" tabindex="-1"><p>' . esc_html__( 'Please check the highlighted fields.', 'labeltrunc' ) . '</p><ul>';
			foreach ( $errors as $field => $message ) {
				echo '<li><a href="#lt-' . esc_attr( $field ) . '">' . esc_html( $message ) . '</a></li>';
			}
			echo '</ul></div>';
		}
		$field = static function ( $name, $label, $type = 'text', $required = false, $hint = '' ) use ( $errors, $values ) {
			$id    = 'lt-' . $name;
			$error = $errors[ $name ] ?? '';
			$desc  = trim( ( $hint ? $id . '-hint' : '' ) . ' ' . ( $error ? $id . '-error' : '' ) );
			echo '<div class="lt-field">';
			echo '<label for="' . esc_attr( $id ) . '">' . esc_html( $label ) . ( $required ? '' : ' <span class="lt-optional">' . esc_html__( '(optional)', 'labeltrunc' ) . '</span>' ) . '</label>';
			if ( $hint ) {
				echo '<p class="lt-hint" id="' . esc_attr( $id ) . '-hint">' . esc_html( $hint ) . '</p>';
			}
			if ( 'textarea' === $type ) {
				echo '<textarea id="' . esc_attr( $id ) . '" name="' . esc_attr( $name ) . '" rows="6" maxlength="4000"' . ( $required ? ' required' : '' ) . ( $desc ? ' aria-describedby="' . esc_attr( $desc ) . '"' : '' ) . ( $error ? ' aria-invalid="true"' : '' ) . '>' . esc_textarea( $values[ $name ] ?? '' ) . '</textarea>';
			} else {
				echo '<input id="' . esc_attr( $id ) . '" name="' . esc_attr( $name ) . '" type="' . esc_attr( $type ) . '" value="' . esc_attr( $values[ $name ] ?? '' ) . '" maxlength="200"' . ( $required ? ' required' : '' ) . ( $desc ? ' aria-describedby="' . esc_attr( $desc ) . '"' : '' ) . ( $error ? ' aria-invalid="true"' : '' ) . ( 'email' === $type ? ' autocomplete="email"' : ( 'tel' === $type ? ' autocomplete="tel"' : ( 'name' === $name ? ' autocomplete="name"' : '' ) ) ) . '>';
			}
			if ( $error ) {
				echo '<p class="lt-field-error" id="' . esc_attr( $id ) . '-error">' . esc_html( $error ) . '</p>';
			}
			echo '</div>';
		};
		?>
		<form class="lt-form" method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>" novalidate>
			<input type="hidden" name="action" value="lt_enquiry">
			<?php wp_nonce_field( 'lt_enquiry', 'lt_nonce' ); ?>
			<input type="hidden" name="lt_t" value="<?php echo esc_attr( self::signed_time() ); ?>">
			<input type="hidden" name="return" value="<?php echo esc_url( get_permalink() ); ?>">
			<div class="lt-hp" aria-hidden="true"><label>Website <input type="text" name="website" tabindex="-1" autocomplete="off"></label></div>
			<?php if ( $product ) : ?>
				<input type="hidden" name="piece" value="<?php echo esc_attr( $product->get_id() ); ?>">
				<p class="lt-form-about"><?php echo esc_html( sprintf( /* translators: %s: piece name */ __( 'About: %s', 'labeltrunc' ), $product->get_name() ) ); ?></p>
			<?php endif; ?>
			<?php $field( 'name', __( 'Name', 'labeltrunc' ), 'text', true ); ?>
			<div class="lt-field-pair">
				<?php $field( 'email', __( 'Email', 'labeltrunc' ), 'email', false, __( 'Email or phone, so we can reply.', 'labeltrunc' ) ); ?>
				<?php $field( 'phone', __( 'Phone or WhatsApp', 'labeltrunc' ), 'tel' ); ?>
			</div>
			<div class="lt-field">
				<label for="lt-topic"><?php esc_html_e( 'What is it about?', 'labeltrunc' ); ?></label>
				<select id="lt-topic" name="topic" required<?php echo isset( $errors['topic'] ) ? ' aria-invalid="true" aria-describedby="lt-topic-error"' : ''; ?>>
					<option value=""><?php esc_html_e( 'Choose a topic', 'labeltrunc' ); ?></option>
					<?php foreach ( self::TOPICS as $key => $label ) : ?>
						<option value="<?php echo esc_attr( $key ); ?>" <?php selected( $topic, $key ); ?>><?php echo esc_html( $label ); ?></option>
					<?php endforeach; ?>
				</select>
				<?php if ( isset( $errors['topic'] ) ) : ?>
					<p class="lt-field-error" id="lt-topic-error"><?php echo esc_html( $errors['topic'] ); ?></p>
				<?php endif; ?>
			</div>
			<?php $field( 'message', __( 'Message', 'labeltrunc' ), 'textarea', true ); ?>
			<div class="lt-field lt-check">
				<input type="checkbox" id="lt-consent" name="consent" value="1" required<?php echo isset( $errors['consent'] ) ? ' aria-invalid="true" aria-describedby="lt-consent-error"' : ''; ?>>
				<label for="lt-consent">
					<?php
					$privacy = get_privacy_policy_url();
					echo wp_kses_post(
						sprintf(
							/* translators: %s: privacy notice link */
							__( 'I agree that Label Trunc will use these details to reply to my enquiry, as described in the %s.', 'labeltrunc' ),
							$privacy ? '<a href="' . esc_url( $privacy ) . '">' . esc_html__( 'privacy notice', 'labeltrunc' ) . '</a>' : esc_html__( 'privacy notice', 'labeltrunc' )
						)
					);
					?>
				</label>
				<?php if ( isset( $errors['consent'] ) ) : ?>
					<p class="lt-field-error" id="lt-consent-error"><?php echo esc_html( $errors['consent'] ); ?></p>
				<?php endif; ?>
			</div>
			<button type="submit" class="lt-button"><?php esc_html_e( 'Send message', 'labeltrunc' ); ?></button>
		</form>
		<?php
		return ob_get_clean();
	}

	public static function handle() {
		$return = isset( $_POST['return'] ) ? esc_url_raw( wp_unslash( $_POST['return'] ) ) : home_url( '/' );
		$return = wp_validate_redirect( $return, home_url( '/' ) );
		$sent   = add_query_arg( 'enquiry', 'sent', $return );

		if ( ! isset( $_POST['lt_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['lt_nonce'] ) ), 'lt_enquiry' ) ) {
			wp_safe_redirect( $return );
			exit;
		}
		// Bots: pretend it worked.
		if ( ! empty( $_POST['website'] ) || ! self::check_time( isset( $_POST['lt_t'] ) ? sanitize_text_field( wp_unslash( $_POST['lt_t'] ) ) : '' ) ) {
			wp_safe_redirect( $sent );
			exit;
		}
		$rate = (int) get_transient( self::ip_key() );
		if ( $rate >= 5 ) {
			self::redirect_with_errors( $return, array( 'message' => __( 'Too many messages in a short time. Please wait, or message us on WhatsApp.', 'labeltrunc' ) ), array() );
			exit;
		}

		$values = array(
			'name'    => sanitize_text_field( wp_unslash( $_POST['name'] ?? '' ) ),
			'email'   => sanitize_email( wp_unslash( $_POST['email'] ?? '' ) ),
			'phone'   => sanitize_text_field( wp_unslash( $_POST['phone'] ?? '' ) ),
			'topic'   => sanitize_key( wp_unslash( $_POST['topic'] ?? '' ) ),
			'message' => sanitize_textarea_field( wp_unslash( $_POST['message'] ?? '' ) ),
		);
		$piece  = absint( $_POST['piece'] ?? 0 );
		$errors = array();
		if ( '' === $values['name'] ) {
			$errors['name'] = __( 'Enter your name.', 'labeltrunc' );
		}
		if ( '' === $values['email'] && strlen( preg_replace( '/\D/', '', $values['phone'] ) ) < 8 ) {
			$errors['email'] = __( 'Give us an email address or a phone number so we can reply.', 'labeltrunc' );
		}
		if ( ! empty( $_POST['email'] ) && ! is_email( $values['email'] ) ) {
			$errors['email'] = __( 'Enter a valid email address, like name@example.com.', 'labeltrunc' );
		}
		if ( ! isset( self::TOPICS[ $values['topic'] ] ) ) {
			$errors['topic'] = __( 'Choose what your message is about.', 'labeltrunc' );
		}
		if ( mb_strlen( $values['message'] ) < 5 ) {
			$errors['message'] = __( 'Write a short message.', 'labeltrunc' );
		}
		if ( empty( $_POST['consent'] ) ) {
			$errors['consent'] = __( 'Please agree so we can use your details to reply.', 'labeltrunc' );
		}
		if ( $errors ) {
			self::redirect_with_errors( $return, $errors, $values );
			exit;
		}
		set_transient( self::ip_key(), $rate + 1, 30 * MINUTE_IN_SECONDS );

		$post_id = wp_insert_post(
			array(
				'post_type'   => self::POST_TYPE,
				'post_status' => 'private',
				'post_title'  => sprintf( '%s · %s', $values['name'], self::TOPICS[ $values['topic'] ] ),
			),
			true
		);
		if ( ! is_wp_error( $post_id ) ) {
			foreach ( $values as $key => $value ) {
				update_post_meta( $post_id, '_lt_' . $key, $value );
			}
			update_post_meta( $post_id, '_lt_piece', $piece );
			update_post_meta( $post_id, '_lt_status', 'new' );
			self::notify( $post_id, $values, $piece );
		}
		wp_safe_redirect( $sent . '#enquiry' );
		exit;
	}

	/** Keeps the answers for one page view under a random token (never shared between visitors). */
	private static function redirect_with_errors( $return, $errors, $values ) {
		$token = wp_generate_password( 24, false, false );
		set_transient( 'lt_enq_old_' . $token, compact( 'errors', 'values' ), 10 * MINUTE_IN_SECONDS );
		wp_safe_redirect( add_query_arg( 'lt_form', $token, $return ) . '#enquiry' );
		exit;
	}

	private static function notify( $post_id, $values, $piece ) {
		$to      = get_theme_mod( 'lt_notify_email', '' ) ?: get_option( 'admin_email' );
		$subject = sprintf( 'New enquiry from %s', $values['name'] );
		$lines   = array(
			'Name: ' . $values['name'],
			'Email: ' . ( $values['email'] ?: '-' ),
			'Phone: ' . ( $values['phone'] ?: '-' ),
			'Topic: ' . self::TOPICS[ $values['topic'] ],
		);
		if ( $piece && function_exists( 'wc_get_product' ) && ( $p = wc_get_product( $piece ) ) ) { // phpcs:ignore
			$lines[] = 'Piece: ' . $p->get_name() . ' (' . $p->get_sku() . ')';
		}
		$lines[] = '';
		$lines[] = $values['message'];
		$lines[] = '';
		$lines[] = admin_url( 'post.php?post=' . $post_id . '&action=edit' );
		$headers = $values['email'] ? array( 'Reply-To: ' . $values['name'] . ' <' . $values['email'] . '>' ) : array();
		wp_mail( $to, $subject, implode( "\n", $lines ), $headers );
	}

	public static function columns( $columns ) {
		return array(
			'cb'        => $columns['cb'] ?? '',
			'title'     => __( 'From', 'labeltrunc' ),
			'lt_reach'  => __( 'Reply to', 'labeltrunc' ),
			'lt_status' => __( 'Status', 'labeltrunc' ),
			'date'      => __( 'Received', 'labeltrunc' ),
		);
	}

	public static function column( $column, $post_id ) {
		if ( 'lt_reach' === $column ) {
			echo esc_html( trim( get_post_meta( $post_id, '_lt_email', true ) . ' ' . get_post_meta( $post_id, '_lt_phone', true ) ) );
		}
		if ( 'lt_status' === $column ) {
			$s = get_post_meta( $post_id, '_lt_status', true );
			echo esc_html( self::STATUSES[ $s ] ?? '' );
		}
	}

	public static function meta_boxes( $post ) {
		add_meta_box(
			'lt_enquiry_body',
			__( 'Message', 'labeltrunc' ),
			static function ( $post ) {
				$rows = array(
					__( 'Email', 'labeltrunc' ) => get_post_meta( $post->ID, '_lt_email', true ),
					__( 'Phone', 'labeltrunc' ) => get_post_meta( $post->ID, '_lt_phone', true ),
					__( 'Topic', 'labeltrunc' ) => self::TOPICS[ get_post_meta( $post->ID, '_lt_topic', true ) ] ?? '',
				);
				$piece = (int) get_post_meta( $post->ID, '_lt_piece', true );
				echo '<table class="form-table"><tbody>';
				foreach ( $rows as $label => $value ) {
					echo '<tr><th>' . esc_html( $label ) . '</th><td>' . esc_html( $value ?: '-' ) . '</td></tr>';
				}
				if ( $piece ) {
					echo '<tr><th>' . esc_html__( 'Piece', 'labeltrunc' ) . '</th><td><a href="' . esc_url( get_edit_post_link( $piece ) ) . '">' . esc_html( get_the_title( $piece ) ) . '</a></td></tr>';
				}
				echo '</tbody></table><p style="white-space:pre-wrap;font-size:14px">' . esc_html( get_post_meta( $post->ID, '_lt_message', true ) ) . '</p>';
			},
			self::POST_TYPE,
			'normal',
			'high'
		);
		add_meta_box(
			'lt_enquiry_status',
			__( 'Status', 'labeltrunc' ),
			static function ( $post ) {
				wp_nonce_field( 'lt_enquiry_status', 'lt_status_nonce' );
				$current = get_post_meta( $post->ID, '_lt_status', true );
				echo '<select name="lt_status" style="width:100%">';
				foreach ( self::STATUSES as $key => $label ) {
					echo '<option value="' . esc_attr( $key ) . '" ' . selected( $current, $key, false ) . '>' . esc_html( $label ) . '</option>';
				}
				echo '</select>';
			},
			self::POST_TYPE,
			'side'
		);
	}

	public static function save_status( $post_id, $post ) {
		if ( ! isset( $_POST['lt_status_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['lt_status_nonce'] ) ), 'lt_enquiry_status' ) ) {
			return;
		}
		if ( ! current_user_can( 'edit_shop_orders' ) ) {
			return;
		}
		$status = sanitize_key( wp_unslash( $_POST['lt_status'] ?? '' ) );
		if ( isset( self::STATUSES[ $status ] ) ) {
			update_post_meta( $post_id, '_lt_status', $status );
		}
	}
}
