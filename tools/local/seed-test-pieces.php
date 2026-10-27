<?php
/**
 * LOCAL DEVELOPMENT ONLY: adds clearly labelled test pieces with generated
 * "TEST PHOTO" images so layouts can be checked. Never run on a live site.
 * Usage: php tools/local/wp-eval.php /path/to/wordpress tools/local/seed-test-pieces.php
 */
if ( false === strpos( home_url(), 'localhost' ) ) {
	fwrite( STDERR, "Refusing: only for a localhost site.\n" );
	exit( 1 );
}
require_once ABSPATH . 'wp-admin/includes/image.php';

function lt_seed_image( $label, $shade ) {
	$im = imagecreatetruecolor( 1200, 1500 );
	$bg = imagecolorallocate( $im, 230 - $shade, 220 - $shade, 205 - $shade );
	$fg = imagecolorallocate( $im, 80, 28, 43 );
	imagefill( $im, 0, 0, $bg );
	imagerectangle( $im, 40, 40, 1159, 1459, $fg );
	imagestring( $im, 5, 520, 730, 'TEST PHOTO', $fg );
	imagestring( $im, 5, 520, 760, $label, $fg );
	$up   = wp_upload_dir();
	$file = $up['path'] . '/lt-test-' . sanitize_title( $label ) . '.jpg';
	imagejpeg( $im, $file, 80 );
	imagedestroy( $im );
	$id = wp_insert_attachment( array( 'post_mime_type' => 'image/jpeg', 'post_title' => 'TEST ' . $label, 'post_status' => 'inherit' ), $file );
	wp_update_attachment_metadata( $id, wp_generate_attachment_metadata( $id, $file ) );
	update_post_meta( $id, '_wp_attachment_image_alt', 'Test photo ' . $label );
	return $id;
}

$cats  = array( 'Top handle', 'Shoulder' );
$conds = array_keys( LT_Product_Fields::CONDITIONS );
for ( $i = 1; $i <= 7; $i++ ) {
	$name = sprintf( 'Test piece %02d', $i );
	if ( get_posts( array( 'post_type' => 'product', 'title' => $name, 'post_status' => 'any', 'fields' => 'ids' ) ) ) { continue; }
	$p = new WC_Product_Simple();
	$p->set_name( $name );
	$p->set_status( 'publish' );
	$p->set_regular_price( (string) ( 1500 + $i * 850 ) );
	$p->set_manage_stock( true );
	$p->set_stock_quantity( 7 === $i ? 0 : 1 );
	$p->set_sold_individually( true );
	$p->set_description( 'Test description for layout checks. Not a real piece.' );
	$img = lt_seed_image( $name . ' A', $i * 6 );
	$p->set_image_id( $img );
	if ( $i % 2 ) { $p->set_gallery_image_ids( array( lt_seed_image( $name . ' B', $i * 6 + 20 ) ) ); }
	$term = get_term_by( 'name', $cats[ $i % 2 ], 'product_cat' );
	if ( $term ) { $p->set_category_ids( array( $term->term_id ) ); }
	$p->update_meta_data( '_lt_condition', $conds[ $i % count( $conds ) ] );
	$p->update_meta_data( '_lt_colour', 'Test colour' );
	$id = $p->save();
	if ( taxonomy_exists( 'product_brand' ) ) { wp_set_object_terms( $id, 'Test brand ' . ( 1 + $i % 3 ), 'product_brand' ); }
	if ( 7 === $i ) { update_post_meta( $id, '_lt_sold_at', time() ); }
	echo "created $name #$id\n";
}
