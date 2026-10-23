import { MigrateUpArgs, MigrateDownArgs, sql } from '@payloadcms/db-postgres'

export async function up({ db, payload, req }: MigrateUpArgs): Promise<void> {
  await db.execute(sql`
   CREATE TYPE "public"."enum_pages_hero_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_hero_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_pages_hero_variant" AS ENUM('home', 'page', 'stage', 'compact');
  CREATE TYPE "public"."enum_pages_hero_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_pages_text_layout" AS ENUM('aside', 'narrow');
  CREATE TYPE "public"."enum_pages_text_size" AS ENUM('body', 'statement');
  CREATE TYPE "public"."enum_pages_split_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_split_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_pages_split_media_position" AS ENUM('left', 'right');
  CREATE TYPE "public"."enum_pages_split_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_pages_mosaic_items_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_kinetic_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_cards_cards_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_pages_cards_cards_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_cards_style" AS ENUM('image', 'list');
  CREATE TYPE "public"."enum_pages_defs_columns" AS ENUM('2', '3', '4');
  CREATE TYPE "public"."enum_pages_defs_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_pages_defs_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_pages_lists_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_pages_steps_steps_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_gallery_layout" AS ENUM('mosaic', 'strip', 'posters');
  CREATE TYPE "public"."enum_pages_people_people_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_res_items_kind" AS ENUM('document', 'website', 'calendar', 'portal', 'email');
  CREATE TYPE "public"."enum_pages_faq_list_categories" AS ENUM('admissions', 'curriculum', 'daily-life', 'support', 'visas', 'community');
  CREATE TYPE "public"."enum_pages_faq_list_source" AS ENUM('categories', 'selected');
  CREATE TYPE "public"."enum_pages_latest_category" AS ENUM('all', 'news', 'announcement', 'achievement', 'event-recap');
  CREATE TYPE "public"."enum_pages_events_list_campus" AS ENUM('all', 'main', 'eyc');
  CREATE TYPE "public"."enum_pages_campuses_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_campuses_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_pages_adm_info_show" AS ENUM('fees', 'apply');
  CREATE TYPE "public"."enum_pages_form_form_type" AS ENUM('enquiry', 'visit', 'open-day', 'contact');
  CREATE TYPE "public"."enum_pages_notice_tone" AS ENUM('info', 'important', 'pending');
  CREATE TYPE "public"."enum_pages_notice_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_cta_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_pages_cta_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_pages_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum___pages_v_hero_v_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_hero_v_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum___pages_v_hero_v_variant" AS ENUM('home', 'page', 'stage', 'compact');
  CREATE TYPE "public"."enum___pages_v_hero_v_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum___pages_v_text_v_layout" AS ENUM('aside', 'narrow');
  CREATE TYPE "public"."enum___pages_v_text_v_size" AS ENUM('body', 'statement');
  CREATE TYPE "public"."enum___pages_v_split_v_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_split_v_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum___pages_v_split_v_media_position" AS ENUM('left', 'right');
  CREATE TYPE "public"."enum___pages_v_split_v_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum___pages_v_mosaic_v_items_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_kinetic_v_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_cards_v_cards_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum___pages_v_cards_v_cards_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_cards_v_style" AS ENUM('image', 'list');
  CREATE TYPE "public"."enum___pages_v_defs_v_columns" AS ENUM('2', '3', '4');
  CREATE TYPE "public"."enum___pages_v_defs_v_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum___pages_v_defs_v_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum___pages_v_lists_v_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum___pages_v_steps_v_steps_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_gallery_v_layout" AS ENUM('mosaic', 'strip', 'posters');
  CREATE TYPE "public"."enum___pages_v_people_v_people_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_res_v_items_kind" AS ENUM('document', 'website', 'calendar', 'portal', 'email');
  CREATE TYPE "public"."enum___pages_v_faq_list_v_categories" AS ENUM('admissions', 'curriculum', 'daily-life', 'support', 'visas', 'community');
  CREATE TYPE "public"."enum___pages_v_faq_list_v_source" AS ENUM('categories', 'selected');
  CREATE TYPE "public"."enum___pages_v_latest_v_category" AS ENUM('all', 'news', 'announcement', 'achievement', 'event-recap');
  CREATE TYPE "public"."enum___pages_v_events_list_v_campus" AS ENUM('all', 'main', 'eyc');
  CREATE TYPE "public"."enum___pages_v_campuses_v_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_campuses_v_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum___pages_v_adm_info_v_show" AS ENUM('fees', 'apply');
  CREATE TYPE "public"."enum___pages_v_form_v_form_type" AS ENUM('enquiry', 'visit', 'open-day', 'contact');
  CREATE TYPE "public"."enum___pages_v_notice_v_tone" AS ENUM('info', 'important', 'pending');
  CREATE TYPE "public"."enum___pages_v_notice_v_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_cta_v_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum___pages_v_cta_v_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum__pages_v_version_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum_stories_category" AS ENUM('news', 'announcement', 'achievement', 'event-recap');
  CREATE TYPE "public"."enum_stories_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum__stories_v_version_category" AS ENUM('news', 'announcement', 'achievement', 'event-recap');
  CREATE TYPE "public"."enum__stories_v_version_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum_events_startsat_tz" AS ENUM('Asia/Kuala_Lumpur');
  CREATE TYPE "public"."enum_events_endsat_tz" AS ENUM('Asia/Kuala_Lumpur');
  CREATE TYPE "public"."enum_events_campus" AS ENUM('main', 'eyc', 'offsite', 'online');
  CREATE TYPE "public"."enum_events_registration_status" AS ENUM('open', 'waitlist', 'full', 'none');
  CREATE TYPE "public"."enum_events_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum__events_v_version_startsat_tz" AS ENUM('Asia/Kuala_Lumpur');
  CREATE TYPE "public"."enum__events_v_version_endsat_tz" AS ENUM('Asia/Kuala_Lumpur');
  CREATE TYPE "public"."enum__events_v_version_campus" AS ENUM('main', 'eyc', 'offsite', 'online');
  CREATE TYPE "public"."enum__events_v_version_registration_status" AS ENUM('open', 'waitlist', 'full', 'none');
  CREATE TYPE "public"."enum__events_v_version_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum_programmes_campus" AS ENUM('main', 'eyc');
  CREATE TYPE "public"."enum_programmes_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_programmes_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum__programmes_v_version_campus" AS ENUM('main', 'eyc');
  CREATE TYPE "public"."enum__programmes_v_version_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum__programmes_v_version_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum_faqs_category" AS ENUM('admissions', 'curriculum', 'daily-life', 'support', 'visas', 'community');
  CREATE TYPE "public"."enum_faqs_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum__faqs_v_version_category" AS ENUM('admissions', 'curriculum', 'daily-life', 'support', 'visas', 'community');
  CREATE TYPE "public"."enum__faqs_v_version_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum_media_approval" AS ENUM('approved', 'pending', 'release-needed');
  CREATE TYPE "public"."enum_reusable_sections_hero_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_hero_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_reusable_sections_hero_variant" AS ENUM('home', 'page', 'stage', 'compact');
  CREATE TYPE "public"."enum_reusable_sections_hero_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_reusable_sections_text_layout" AS ENUM('aside', 'narrow');
  CREATE TYPE "public"."enum_reusable_sections_text_size" AS ENUM('body', 'statement');
  CREATE TYPE "public"."enum_reusable_sections_split_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_split_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_reusable_sections_split_media_position" AS ENUM('left', 'right');
  CREATE TYPE "public"."enum_reusable_sections_split_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_reusable_sections_mosaic_items_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_kinetic_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_cards_cards_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_reusable_sections_cards_cards_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_cards_style" AS ENUM('image', 'list');
  CREATE TYPE "public"."enum_reusable_sections_defs_columns" AS ENUM('2', '3', '4');
  CREATE TYPE "public"."enum_reusable_sections_defs_accent" AS ENUM('violet', 'purple', 'lime', 'gold');
  CREATE TYPE "public"."enum_reusable_sections_defs_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_reusable_sections_lists_background" AS ENUM('plain', 'tint', 'ink');
  CREATE TYPE "public"."enum_reusable_sections_steps_steps_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_gallery_layout" AS ENUM('mosaic', 'strip', 'posters');
  CREATE TYPE "public"."enum_reusable_sections_people_people_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_res_items_kind" AS ENUM('document', 'website', 'calendar', 'portal', 'email');
  CREATE TYPE "public"."enum_reusable_sections_faq_list_categories" AS ENUM('admissions', 'curriculum', 'daily-life', 'support', 'visas', 'community');
  CREATE TYPE "public"."enum_reusable_sections_faq_list_source" AS ENUM('categories', 'selected');
  CREATE TYPE "public"."enum_reusable_sections_latest_category" AS ENUM('all', 'news', 'announcement', 'achievement', 'event-recap');
  CREATE TYPE "public"."enum_reusable_sections_events_list_campus" AS ENUM('all', 'main', 'eyc');
  CREATE TYPE "public"."enum_reusable_sections_campuses_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_campuses_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_reusable_sections_adm_info_show" AS ENUM('fees', 'apply');
  CREATE TYPE "public"."enum_reusable_sections_form_form_type" AS ENUM('enquiry', 'visit', 'open-day', 'contact');
  CREATE TYPE "public"."enum_reusable_sections_notice_tone" AS ENUM('info', 'important', 'pending');
  CREATE TYPE "public"."enum_reusable_sections_notice_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_cta_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_reusable_sections_cta_links_link_appearance" AS ENUM('primary', 'secondary', 'arrow');
  CREATE TYPE "public"."enum_enquiries_type" AS ENUM('enquiry', 'visit', 'open-day', 'contact');
  CREATE TYPE "public"."enum_enquiries_status" AS ENUM('new', 'in-progress', 'responded', 'closed');
  CREATE TYPE "public"."enum_enquiries_stage" AS ENUM('early-years', 'primary', 'secondary', 'sixth-form', 'unsure');
  CREATE TYPE "public"."enum_users_roles" AS ENUM('admin', 'editor', 'contributor', 'admissions');
  CREATE TYPE "public"."enum_site_settings_campuses_key" AS ENUM('main', 'eyc');
  CREATE TYPE "public"."enum_site_settings_social_platform" AS ENUM('facebook', 'instagram', 'linkedin', 'youtube');
  CREATE TYPE "public"."enum_site_settings_announcement_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_header_nav_groups_items_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_header_nav_groups_overview_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_header_utility_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_header_cta_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_footer_columns_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_footer_legal_links_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_footer_memberships_link_type" AS ENUM('internal', 'custom');
  CREATE TYPE "public"."enum_admissions_status" AS ENUM('draft', 'published');
  CREATE TYPE "public"."enum__admissions_v_version_status" AS ENUM('draft', 'published');
  CREATE TABLE "pages_hero_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_pages_hero_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum_pages_hero_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "pages_hero_slides" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"image_id" integer,
  	"caption" varchar
  );
  
  CREATE TABLE "pages_hero" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"variant" "enum_pages_hero_variant" DEFAULT 'page',
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"fact_line" varchar,
  	"media_id" integer,
  	"accent" "enum_pages_hero_accent" DEFAULT 'violet',
  	"stage_label" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_text" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"content" jsonb,
  	"layout" "enum_pages_text_layout" DEFAULT 'aside',
  	"size" "enum_pages_text_size" DEFAULT 'body',
  	"signature" varchar,
  	"signature_role" varchar,
  	"portrait_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_split_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_pages_split_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum_pages_split_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "pages_split" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"body" jsonb,
  	"media_id" integer,
  	"media_position" "enum_pages_split_media_position" DEFAULT 'right',
  	"background" "enum_pages_split_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_facts_facts" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"value" varchar,
  	"label" varchar
  );
  
  CREATE TABLE "pages_facts" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"heading" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_journey" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"horizontal" boolean DEFAULT true,
  	"anchor_id" varchar DEFAULT 'learning-journey',
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_mosaic_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"media_id" integer,
  	"category" varchar,
  	"title" varchar,
  	"text" varchar,
  	"link_type" "enum_pages_mosaic_items_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "pages_mosaic" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_kinetic_words" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"word" varchar
  );
  
  CREATE TABLE "pages_kinetic" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"supporting" varchar,
  	"link_type" "enum_pages_kinetic_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_cards_cards" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"meta" varchar,
  	"text" varchar,
  	"media_id" integer,
  	"accent" "enum_pages_cards_cards_accent" DEFAULT 'violet',
  	"link_type" "enum_pages_cards_cards_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "pages_cards" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"style" "enum_pages_cards_style" DEFAULT 'image',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_defs_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"term" varchar,
  	"tagline" varchar,
  	"description" varchar
  );
  
  CREATE TABLE "pages_defs" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"columns" "enum_pages_defs_columns" DEFAULT '4',
  	"accent" "enum_pages_defs_accent" DEFAULT 'violet',
  	"background" "enum_pages_defs_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_lists_groups" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"items" varchar,
  	"note" varchar
  );
  
  CREATE TABLE "pages_lists" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"background" "enum_pages_lists_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_tbl_columns" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar
  );
  
  CREATE TABLE "pages_tbl_rows_cells" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"value" varchar
  );
  
  CREATE TABLE "pages_tbl_rows" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "pages_tbl" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"caption" varchar,
  	"note" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_steps_steps" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"body" varchar,
  	"link_type" "enum_pages_steps_steps_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "pages_steps" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_gallery_images" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"image_id" integer,
  	"caption" varchar
  );
  
  CREATE TABLE "pages_gallery" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"layout" "enum_pages_gallery_layout" DEFAULT 'mosaic',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_people_people" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"name" varchar,
  	"role" varchar,
  	"image_id" integer,
  	"bio" varchar,
  	"link_type" "enum_pages_people_people_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "pages_people" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_res_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"description" varchar,
  	"kind" "enum_pages_res_items_kind" DEFAULT 'document',
  	"url" varchar,
  	"file_id" integer
  );
  
  CREATE TABLE "pages_res" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_faq_list_categories" (
  	"order" integer NOT NULL,
  	"parent_id" varchar NOT NULL,
  	"value" "enum_pages_faq_list_categories",
  	"id" serial PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "pages_faq_list" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"source" "enum_pages_faq_list_source" DEFAULT 'categories',
  	"searchable" boolean DEFAULT false,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_latest" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"category" "enum_pages_latest_category" DEFAULT 'all',
  	"limit" numeric DEFAULT 3,
  	"show_all_link" boolean DEFAULT true,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_events_list" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"campus" "enum_pages_events_list_campus" DEFAULT 'all',
  	"limit" numeric DEFAULT 4,
  	"empty_text" varchar DEFAULT 'There are no upcoming events published at the moment. Register your interest below and we will let you know when dates are confirmed.',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_campuses_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_pages_campuses_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum_pages_campuses_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "pages_campuses" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"show_maps" boolean DEFAULT false,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_adm_info" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"show" "enum_pages_adm_info_show" DEFAULT 'fees',
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_form" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"form_type" "enum_pages_form_form_type" DEFAULT 'enquiry',
  	"aside" jsonb,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_notice" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"tone" "enum_pages_notice_tone" DEFAULT 'info',
  	"title" varchar,
  	"body" varchar,
  	"link_type" "enum_pages_notice_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_cta_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_pages_cta_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum_pages_cta_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "pages_cta" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"text" varchar,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_marquee_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"text" varchar
  );
  
  CREATE TABLE "pages_marquee" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages_reuse" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"section_id" integer,
  	"block_name" varchar
  );
  
  CREATE TABLE "pages" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"meta_title" varchar,
  	"meta_description" varchar,
  	"meta_image_id" integer,
  	"meta_no_index" boolean DEFAULT false,
  	"slug" varchar,
  	"parent_id" integer,
  	"path" varchar,
  	"nav_label" varchar,
  	"content_owner" varchar,
  	"search_text" varchar,
  	"last_reviewed" timestamp(3) with time zone,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"_status" "enum_pages_status" DEFAULT 'draft'
  );
  
  CREATE TABLE "pages_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer,
  	"programmes_id" integer,
  	"faqs_id" integer
  );
  
  CREATE TABLE "__pages_v_hero_v_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"link_type" "enum___pages_v_hero_v_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum___pages_v_hero_v_links_link_appearance" DEFAULT 'primary',
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_hero_v_slides" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"image_id" integer,
  	"caption" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_hero_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"variant" "enum___pages_v_hero_v_variant" DEFAULT 'page',
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"fact_line" varchar,
  	"media_id" integer,
  	"accent" "enum___pages_v_hero_v_accent" DEFAULT 'violet',
  	"stage_label" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_text_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"content" jsonb,
  	"layout" "enum___pages_v_text_v_layout" DEFAULT 'aside',
  	"size" "enum___pages_v_text_v_size" DEFAULT 'body',
  	"signature" varchar,
  	"signature_role" varchar,
  	"portrait_id" integer,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_split_v_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"link_type" "enum___pages_v_split_v_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum___pages_v_split_v_links_link_appearance" DEFAULT 'primary',
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_split_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"body" jsonb,
  	"media_id" integer,
  	"media_position" "enum___pages_v_split_v_media_position" DEFAULT 'right',
  	"background" "enum___pages_v_split_v_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_facts_v_facts" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"value" varchar,
  	"label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_facts_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"heading" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_journey_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"horizontal" boolean DEFAULT true,
  	"anchor_id" varchar DEFAULT 'learning-journey',
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_mosaic_v_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"media_id" integer,
  	"category" varchar,
  	"title" varchar,
  	"text" varchar,
  	"link_type" "enum___pages_v_mosaic_v_items_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_mosaic_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_kinetic_v_words" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"word" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_kinetic_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"supporting" varchar,
  	"link_type" "enum___pages_v_kinetic_v_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_cards_v_cards" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"meta" varchar,
  	"text" varchar,
  	"media_id" integer,
  	"accent" "enum___pages_v_cards_v_cards_accent" DEFAULT 'violet',
  	"link_type" "enum___pages_v_cards_v_cards_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_cards_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"style" "enum___pages_v_cards_v_style" DEFAULT 'image',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_defs_v_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"term" varchar,
  	"tagline" varchar,
  	"description" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_defs_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"columns" "enum___pages_v_defs_v_columns" DEFAULT '4',
  	"accent" "enum___pages_v_defs_v_accent" DEFAULT 'violet',
  	"background" "enum___pages_v_defs_v_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_lists_v_groups" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"items" varchar,
  	"note" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_lists_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"background" "enum___pages_v_lists_v_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_tbl_v_columns" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_tbl_v_rows_cells" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"value" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_tbl_v_rows" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_tbl_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"caption" varchar,
  	"note" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_steps_v_steps" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"body" varchar,
  	"link_type" "enum___pages_v_steps_v_steps_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_steps_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_gallery_v_images" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"image_id" integer,
  	"caption" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_gallery_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"layout" "enum___pages_v_gallery_v_layout" DEFAULT 'mosaic',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_people_v_people" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"name" varchar,
  	"role" varchar,
  	"image_id" integer,
  	"bio" varchar,
  	"link_type" "enum___pages_v_people_v_people_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_people_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_res_v_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"description" varchar,
  	"kind" "enum___pages_v_res_v_items_kind" DEFAULT 'document',
  	"url" varchar,
  	"file_id" integer,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_res_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_faq_list_v_categories" (
  	"order" integer NOT NULL,
  	"parent_id" integer NOT NULL,
  	"value" "enum___pages_v_faq_list_v_categories",
  	"id" serial PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "__pages_v_faq_list_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"source" "enum___pages_v_faq_list_v_source" DEFAULT 'categories',
  	"searchable" boolean DEFAULT false,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_latest_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"category" "enum___pages_v_latest_v_category" DEFAULT 'all',
  	"limit" numeric DEFAULT 3,
  	"show_all_link" boolean DEFAULT true,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_events_list_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"campus" "enum___pages_v_events_list_v_campus" DEFAULT 'all',
  	"limit" numeric DEFAULT 4,
  	"empty_text" varchar DEFAULT 'There are no upcoming events published at the moment. Register your interest below and we will let you know when dates are confirmed.',
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_campuses_v_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"link_type" "enum___pages_v_campuses_v_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum___pages_v_campuses_v_links_link_appearance" DEFAULT 'primary',
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_campuses_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"show_maps" boolean DEFAULT false,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_adm_info_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"show" "enum___pages_v_adm_info_v_show" DEFAULT 'fees',
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_form_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"form_type" "enum___pages_v_form_v_form_type" DEFAULT 'enquiry',
  	"aside" jsonb,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_notice_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"tone" "enum___pages_v_notice_v_tone" DEFAULT 'info',
  	"title" varchar,
  	"body" varchar,
  	"link_type" "enum___pages_v_notice_v_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_cta_v_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"link_type" "enum___pages_v_cta_v_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"link_appearance" "enum___pages_v_cta_v_links_link_appearance" DEFAULT 'primary',
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_cta_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"text" varchar,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_marquee_v_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"text" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "__pages_v_marquee_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"label" varchar,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "__pages_v_reuse_v" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"section_id" integer,
  	"_uuid" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "_pages_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"parent_id" integer,
  	"version_title" varchar,
  	"version_meta_title" varchar,
  	"version_meta_description" varchar,
  	"version_meta_image_id" integer,
  	"version_meta_no_index" boolean DEFAULT false,
  	"version_slug" varchar,
  	"version_parent_id" integer,
  	"version_path" varchar,
  	"version_nav_label" varchar,
  	"version_content_owner" varchar,
  	"version_search_text" varchar,
  	"version_last_reviewed" timestamp(3) with time zone,
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"version__status" "enum__pages_v_version_status" DEFAULT 'draft',
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean,
  	"autosave" boolean
  );
  
  CREATE TABLE "_pages_v_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer,
  	"programmes_id" integer,
  	"faqs_id" integer
  );
  
  CREATE TABLE "stories" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"category" "enum_stories_category" DEFAULT 'news',
  	"published_at" timestamp(3) with time zone,
  	"excerpt" varchar,
  	"hero_image_id" integer,
  	"content" jsonb,
  	"author_role" varchar,
  	"slug" varchar,
  	"meta_title" varchar,
  	"meta_description" varchar,
  	"meta_image_id" integer,
  	"meta_no_index" boolean DEFAULT false,
  	"search_text" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"_status" "enum_stories_status" DEFAULT 'draft'
  );
  
  CREATE TABLE "stories_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"programmes_id" integer
  );
  
  CREATE TABLE "_stories_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"parent_id" integer,
  	"version_title" varchar,
  	"version_category" "enum__stories_v_version_category" DEFAULT 'news',
  	"version_published_at" timestamp(3) with time zone,
  	"version_excerpt" varchar,
  	"version_hero_image_id" integer,
  	"version_content" jsonb,
  	"version_author_role" varchar,
  	"version_slug" varchar,
  	"version_meta_title" varchar,
  	"version_meta_description" varchar,
  	"version_meta_image_id" integer,
  	"version_meta_no_index" boolean DEFAULT false,
  	"version_search_text" varchar,
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"version__status" "enum__stories_v_version_status" DEFAULT 'draft',
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean,
  	"autosave" boolean
  );
  
  CREATE TABLE "_stories_v_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"programmes_id" integer
  );
  
  CREATE TABLE "events" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"starts_at" timestamp(3) with time zone,
  	"startsat_tz" "enum_events_startsat_tz" DEFAULT 'Asia/Kuala_Lumpur',
  	"ends_at" timestamp(3) with time zone,
  	"endsat_tz" "enum_events_endsat_tz" DEFAULT 'Asia/Kuala_Lumpur',
  	"campus" "enum_events_campus",
  	"audience" varchar,
  	"location" varchar,
  	"summary" varchar,
  	"registration_url" varchar,
  	"registration_status" "enum_events_registration_status" DEFAULT 'open',
  	"organiser" varchar DEFAULT 'Admissions',
  	"slug" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"_status" "enum_events_status" DEFAULT 'draft'
  );
  
  CREATE TABLE "_events_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"parent_id" integer,
  	"version_title" varchar,
  	"version_starts_at" timestamp(3) with time zone,
  	"version_startsat_tz" "enum__events_v_version_startsat_tz" DEFAULT 'Asia/Kuala_Lumpur',
  	"version_ends_at" timestamp(3) with time zone,
  	"version_endsat_tz" "enum__events_v_version_endsat_tz" DEFAULT 'Asia/Kuala_Lumpur',
  	"version_campus" "enum__events_v_version_campus",
  	"version_audience" varchar,
  	"version_location" varchar,
  	"version_summary" varchar,
  	"version_registration_url" varchar,
  	"version_registration_status" "enum__events_v_version_registration_status" DEFAULT 'open',
  	"version_organiser" varchar DEFAULT 'Admissions',
  	"version_slug" varchar,
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"version__status" "enum__events_v_version_status" DEFAULT 'draft',
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean
  );
  
  CREATE TABLE "programmes_highlights" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"text" varchar
  );
  
  CREATE TABLE "programmes" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"name" varchar,
  	"ages" varchar,
  	"order" numeric DEFAULT 1,
  	"year_groups" varchar,
  	"tagline" varchar,
  	"summary" varchar,
  	"campus" "enum_programmes_campus",
  	"image_id" integer,
  	"page_id" integer,
  	"accent" "enum_programmes_accent" DEFAULT 'violet',
  	"slug" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"_status" "enum_programmes_status" DEFAULT 'draft'
  );
  
  CREATE TABLE "_programmes_v_version_highlights" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"text" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "_programmes_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"parent_id" integer,
  	"version_name" varchar,
  	"version_ages" varchar,
  	"version_order" numeric DEFAULT 1,
  	"version_year_groups" varchar,
  	"version_tagline" varchar,
  	"version_summary" varchar,
  	"version_campus" "enum__programmes_v_version_campus",
  	"version_image_id" integer,
  	"version_page_id" integer,
  	"version_accent" "enum__programmes_v_version_accent" DEFAULT 'violet',
  	"version_slug" varchar,
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"version__status" "enum__programmes_v_version_status" DEFAULT 'draft',
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean
  );
  
  CREATE TABLE "faqs" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"question" varchar,
  	"answer" jsonb,
  	"category" "enum_faqs_category",
  	"order" numeric DEFAULT 10,
  	"search_text" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"_status" "enum_faqs_status" DEFAULT 'draft'
  );
  
  CREATE TABLE "_faqs_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"parent_id" integer,
  	"version_question" varchar,
  	"version_answer" jsonb,
  	"version_category" "enum__faqs_v_version_category",
  	"version_order" numeric DEFAULT 10,
  	"version_search_text" varchar,
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"version__status" "enum__faqs_v_version_status" DEFAULT 'draft',
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean
  );
  
  CREATE TABLE "media" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"alt" varchar NOT NULL,
  	"caption" varchar,
  	"approval" "enum_media_approval" DEFAULT 'pending' NOT NULL,
  	"credit" varchar,
  	"source_url" varchar,
  	"release_reference" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"url" varchar,
  	"thumbnail_u_r_l" varchar,
  	"filename" varchar,
  	"mime_type" varchar,
  	"filesize" numeric,
  	"width" numeric,
  	"height" numeric,
  	"focal_x" numeric,
  	"focal_y" numeric,
  	"sizes_thumbnail_url" varchar,
  	"sizes_thumbnail_width" numeric,
  	"sizes_thumbnail_height" numeric,
  	"sizes_thumbnail_mime_type" varchar,
  	"sizes_thumbnail_filesize" numeric,
  	"sizes_thumbnail_filename" varchar,
  	"sizes_card_url" varchar,
  	"sizes_card_width" numeric,
  	"sizes_card_height" numeric,
  	"sizes_card_mime_type" varchar,
  	"sizes_card_filesize" numeric,
  	"sizes_card_filename" varchar,
  	"sizes_feature_url" varchar,
  	"sizes_feature_width" numeric,
  	"sizes_feature_height" numeric,
  	"sizes_feature_mime_type" varchar,
  	"sizes_feature_filesize" numeric,
  	"sizes_feature_filename" varchar,
  	"sizes_hero_url" varchar,
  	"sizes_hero_width" numeric,
  	"sizes_hero_height" numeric,
  	"sizes_hero_mime_type" varchar,
  	"sizes_hero_filesize" numeric,
  	"sizes_hero_filename" varchar,
  	"sizes_og_url" varchar,
  	"sizes_og_width" numeric,
  	"sizes_og_height" numeric,
  	"sizes_og_mime_type" varchar,
  	"sizes_og_filesize" numeric,
  	"sizes_og_filename" varchar
  );
  
  CREATE TABLE "reusable_sections_hero_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_reusable_sections_hero_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL,
  	"link_appearance" "enum_reusable_sections_hero_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "reusable_sections_hero_slides" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"image_id" integer,
  	"caption" varchar
  );
  
  CREATE TABLE "reusable_sections_hero" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"variant" "enum_reusable_sections_hero_variant" DEFAULT 'page' NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"fact_line" varchar,
  	"media_id" integer,
  	"accent" "enum_reusable_sections_hero_accent" DEFAULT 'violet',
  	"stage_label" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_text" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"content" jsonb NOT NULL,
  	"layout" "enum_reusable_sections_text_layout" DEFAULT 'aside',
  	"size" "enum_reusable_sections_text_size" DEFAULT 'body',
  	"signature" varchar,
  	"signature_role" varchar,
  	"portrait_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_split_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_reusable_sections_split_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL,
  	"link_appearance" "enum_reusable_sections_split_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "reusable_sections_split" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"body" jsonb,
  	"media_id" integer NOT NULL,
  	"media_position" "enum_reusable_sections_split_media_position" DEFAULT 'right',
  	"background" "enum_reusable_sections_split_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_facts_facts" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"value" varchar NOT NULL,
  	"label" varchar NOT NULL
  );
  
  CREATE TABLE "reusable_sections_facts" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"heading" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_journey" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"horizontal" boolean DEFAULT true,
  	"anchor_id" varchar DEFAULT 'learning-journey',
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_mosaic_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"media_id" integer NOT NULL,
  	"category" varchar,
  	"title" varchar NOT NULL,
  	"text" varchar,
  	"link_type" "enum_reusable_sections_mosaic_items_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "reusable_sections_mosaic" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_kinetic_words" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"word" varchar NOT NULL
  );
  
  CREATE TABLE "reusable_sections_kinetic" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"supporting" varchar,
  	"link_type" "enum_reusable_sections_kinetic_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_cards_cards" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar NOT NULL,
  	"meta" varchar,
  	"text" varchar,
  	"media_id" integer,
  	"accent" "enum_reusable_sections_cards_cards_accent" DEFAULT 'violet',
  	"link_type" "enum_reusable_sections_cards_cards_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL
  );
  
  CREATE TABLE "reusable_sections_cards" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"style" "enum_reusable_sections_cards_style" DEFAULT 'image',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_defs_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"term" varchar NOT NULL,
  	"tagline" varchar,
  	"description" varchar
  );
  
  CREATE TABLE "reusable_sections_defs" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"columns" "enum_reusable_sections_defs_columns" DEFAULT '4',
  	"accent" "enum_reusable_sections_defs_accent" DEFAULT 'violet',
  	"background" "enum_reusable_sections_defs_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_lists_groups" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar,
  	"items" varchar NOT NULL,
  	"note" varchar
  );
  
  CREATE TABLE "reusable_sections_lists" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"background" "enum_reusable_sections_lists_background" DEFAULT 'plain',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_tbl_columns" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar NOT NULL
  );
  
  CREATE TABLE "reusable_sections_tbl_rows_cells" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"value" varchar
  );
  
  CREATE TABLE "reusable_sections_tbl_rows" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "reusable_sections_tbl" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"caption" varchar NOT NULL,
  	"note" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_steps_steps" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar NOT NULL,
  	"body" varchar NOT NULL,
  	"link_type" "enum_reusable_sections_steps_steps_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "reusable_sections_steps" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_gallery_images" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"image_id" integer NOT NULL,
  	"caption" varchar
  );
  
  CREATE TABLE "reusable_sections_gallery" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar,
  	"intro" varchar,
  	"layout" "enum_reusable_sections_gallery_layout" DEFAULT 'mosaic',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_people_people" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"name" varchar NOT NULL,
  	"role" varchar NOT NULL,
  	"image_id" integer,
  	"bio" varchar,
  	"link_type" "enum_reusable_sections_people_people_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar
  );
  
  CREATE TABLE "reusable_sections_people" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_res_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar NOT NULL,
  	"description" varchar,
  	"kind" "enum_reusable_sections_res_items_kind" DEFAULT 'document',
  	"url" varchar NOT NULL,
  	"file_id" integer
  );
  
  CREATE TABLE "reusable_sections_res" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_faq_list_categories" (
  	"order" integer NOT NULL,
  	"parent_id" varchar NOT NULL,
  	"value" "enum_reusable_sections_faq_list_categories",
  	"id" serial PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "reusable_sections_faq_list" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"source" "enum_reusable_sections_faq_list_source" DEFAULT 'categories',
  	"searchable" boolean DEFAULT false,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_latest" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"category" "enum_reusable_sections_latest_category" DEFAULT 'all',
  	"limit" numeric DEFAULT 3,
  	"show_all_link" boolean DEFAULT true,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_events_list" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"campus" "enum_reusable_sections_events_list_campus" DEFAULT 'all',
  	"limit" numeric DEFAULT 4,
  	"empty_text" varchar DEFAULT 'There are no upcoming events published at the moment. Register your interest below and we will let you know when dates are confirmed.',
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_campuses_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_reusable_sections_campuses_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL,
  	"link_appearance" "enum_reusable_sections_campuses_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "reusable_sections_campuses" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"show_maps" boolean DEFAULT false,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_adm_info" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"show" "enum_reusable_sections_adm_info_show" DEFAULT 'fees' NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_form" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"intro" varchar,
  	"form_type" "enum_reusable_sections_form_form_type" DEFAULT 'enquiry' NOT NULL,
  	"aside" jsonb,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_notice" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"tone" "enum_reusable_sections_notice_tone" DEFAULT 'info',
  	"title" varchar NOT NULL,
  	"body" varchar,
  	"link_type" "enum_reusable_sections_notice_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_cta_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_reusable_sections_cta_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL,
  	"link_appearance" "enum_reusable_sections_cta_links_link_appearance" DEFAULT 'primary'
  );
  
  CREATE TABLE "reusable_sections_cta" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"eyebrow" varchar,
  	"heading" varchar NOT NULL,
  	"text" varchar,
  	"media_id" integer,
  	"anchor_id" varchar,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections_marquee_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"text" varchar NOT NULL
  );
  
  CREATE TABLE "reusable_sections_marquee" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"_path" text NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar NOT NULL,
  	"block_name" varchar
  );
  
  CREATE TABLE "reusable_sections" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"title" varchar NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL
  );
  
  CREATE TABLE "reusable_sections_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer,
  	"programmes_id" integer,
  	"faqs_id" integer
  );
  
  CREATE TABLE "enquiries" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"type" "enum_enquiries_type" NOT NULL,
  	"status" "enum_enquiries_status" DEFAULT 'new',
  	"parent_name" varchar NOT NULL,
  	"email" varchar NOT NULL,
  	"phone" varchar,
  	"stage" "enum_enquiries_stage",
  	"intended_start" varchar,
  	"message" varchar,
  	"consent_privacy" boolean,
  	"consent_updates" boolean,
  	"source_path" varchar,
  	"delivery_emailed" boolean,
  	"delivery_forwarded" boolean,
  	"delivery_error" varchar,
  	"staff_notes" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL
  );
  
  CREATE TABLE "users_roles" (
  	"order" integer NOT NULL,
  	"parent_id" integer NOT NULL,
  	"value" "enum_users_roles",
  	"id" serial PRIMARY KEY NOT NULL
  );
  
  CREATE TABLE "users_sessions" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"created_at" timestamp(3) with time zone,
  	"expires_at" timestamp(3) with time zone NOT NULL
  );
  
  CREATE TABLE "users" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"name" varchar NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"email" varchar NOT NULL,
  	"reset_password_token" varchar,
  	"reset_password_expiration" timestamp(3) with time zone,
  	"salt" varchar,
  	"hash" varchar,
  	"reset_password_requested_at" timestamp(3) with time zone,
  	"login_attempts" numeric DEFAULT 0,
  	"lock_until" timestamp(3) with time zone
  );
  
  CREATE TABLE "payload_kv" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"key" varchar NOT NULL,
  	"data" jsonb NOT NULL
  );
  
  CREATE TABLE "payload_locked_documents" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"global_slug" varchar,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL
  );
  
  CREATE TABLE "payload_locked_documents_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer,
  	"events_id" integer,
  	"programmes_id" integer,
  	"faqs_id" integer,
  	"media_id" integer,
  	"reusable_sections_id" integer,
  	"enquiries_id" integer,
  	"users_id" integer
  );
  
  CREATE TABLE "payload_preferences" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"key" varchar,
  	"value" jsonb,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL
  );
  
  CREATE TABLE "payload_preferences_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"users_id" integer
  );
  
  CREATE TABLE "payload_migrations" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"name" varchar,
  	"batch" numeric,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL
  );
  
  CREATE TABLE "site_settings_campuses_phones" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar NOT NULL,
  	"number" varchar NOT NULL,
  	"calls_only" boolean DEFAULT false
  );
  
  CREATE TABLE "site_settings_campuses" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"name" varchar NOT NULL,
  	"key" "enum_site_settings_campuses_key" NOT NULL,
  	"ages" varchar,
  	"address" varchar NOT NULL,
  	"email" varchar,
  	"map_url" varchar,
  	"map_embed_query" varchar,
  	"directions" varchar,
  	"image_id" integer,
  	"last_verified" timestamp(3) with time zone
  );
  
  CREATE TABLE "site_settings_social" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"platform" "enum_site_settings_social_platform" NOT NULL,
  	"url" varchar NOT NULL
  );
  
  CREATE TABLE "site_settings" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"school_name" varchar DEFAULT 'The International School @ ParkCity' NOT NULL,
  	"short_name" varchar DEFAULT 'ISP' NOT NULL,
  	"location" varchar DEFAULT 'Kuala Lumpur, Malaysia',
  	"general_email" varchar NOT NULL,
  	"admissions_email" varchar,
  	"default_description" varchar NOT NULL,
  	"default_share_image_id" integer,
  	"sister_school_label" varchar,
  	"sister_school_url" varchar,
  	"announcement_enabled" boolean DEFAULT false,
  	"announcement_text" varchar,
  	"announcement_link_type" "enum_site_settings_announcement_link_type" DEFAULT 'internal',
  	"announcement_link_new_tab" boolean,
  	"announcement_link_url" varchar,
  	"announcement_link_label" varchar,
  	"announcement_expires_at" timestamp(3) with time zone,
  	"updated_at" timestamp(3) with time zone,
  	"created_at" timestamp(3) with time zone
  );
  
  CREATE TABLE "site_settings_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer
  );
  
  CREATE TABLE "header_nav_groups_items" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_header_nav_groups_items_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL,
  	"description" varchar
  );
  
  CREATE TABLE "header_nav_groups" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"label" varchar NOT NULL,
  	"summary" varchar,
  	"overview_type" "enum_header_nav_groups_overview_type" DEFAULT 'internal',
  	"overview_new_tab" boolean,
  	"overview_url" varchar,
  	"overview_label" varchar NOT NULL
  );
  
  CREATE TABLE "header_utility_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_header_utility_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL
  );
  
  CREATE TABLE "header" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"cta_type" "enum_header_cta_type" DEFAULT 'internal',
  	"cta_new_tab" boolean,
  	"cta_url" varchar,
  	"cta_label" varchar NOT NULL,
  	"updated_at" timestamp(3) with time zone,
  	"created_at" timestamp(3) with time zone
  );
  
  CREATE TABLE "header_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer
  );
  
  CREATE TABLE "footer_columns_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" varchar NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_footer_columns_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL
  );
  
  CREATE TABLE "footer_columns" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"title" varchar NOT NULL
  );
  
  CREATE TABLE "footer_legal_links" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_footer_legal_links_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL
  );
  
  CREATE TABLE "footer_memberships" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"link_type" "enum_footer_memberships_link_type" DEFAULT 'internal',
  	"link_new_tab" boolean,
  	"link_url" varchar,
  	"link_label" varchar NOT NULL
  );
  
  CREATE TABLE "footer" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"updated_at" timestamp(3) with time zone,
  	"created_at" timestamp(3) with time zone
  );
  
  CREATE TABLE "footer_rels" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"order" integer,
  	"parent_id" integer NOT NULL,
  	"path" varchar NOT NULL,
  	"pages_id" integer,
  	"stories_id" integer
  );
  
  CREATE TABLE "admissions_fee_table" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"item" varchar,
  	"amount" varchar,
  	"note" varchar
  );
  
  CREATE TABLE "admissions_required_documents" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" varchar PRIMARY KEY NOT NULL,
  	"item" varchar
  );
  
  CREATE TABLE "admissions" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"academic_year" varchar,
  	"fee_schedule_url" varchar,
  	"waiver" varchar,
  	"fees_note" varchar,
  	"reviewed_at" timestamp(3) with time zone,
  	"owner" varchar DEFAULT 'Admissions & Finance',
  	"application_url" varchar,
  	"application_note" varchar,
  	"terms_url" varchar,
  	"_status" "enum_admissions_status" DEFAULT 'draft',
  	"updated_at" timestamp(3) with time zone,
  	"created_at" timestamp(3) with time zone
  );
  
  CREATE TABLE "_admissions_v_version_fee_table" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"item" varchar,
  	"amount" varchar,
  	"note" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "_admissions_v_version_required_documents" (
  	"_order" integer NOT NULL,
  	"_parent_id" integer NOT NULL,
  	"id" serial PRIMARY KEY NOT NULL,
  	"item" varchar,
  	"_uuid" varchar
  );
  
  CREATE TABLE "_admissions_v" (
  	"id" serial PRIMARY KEY NOT NULL,
  	"version_academic_year" varchar,
  	"version_fee_schedule_url" varchar,
  	"version_waiver" varchar,
  	"version_fees_note" varchar,
  	"version_reviewed_at" timestamp(3) with time zone,
  	"version_owner" varchar DEFAULT 'Admissions & Finance',
  	"version_application_url" varchar,
  	"version_application_note" varchar,
  	"version_terms_url" varchar,
  	"version__status" "enum__admissions_v_version_status" DEFAULT 'draft',
  	"version_updated_at" timestamp(3) with time zone,
  	"version_created_at" timestamp(3) with time zone,
  	"created_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"updated_at" timestamp(3) with time zone DEFAULT now() NOT NULL,
  	"latest" boolean
  );
  
  ALTER TABLE "pages_hero_links" ADD CONSTRAINT "pages_hero_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_hero"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_hero_slides" ADD CONSTRAINT "pages_hero_slides_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_hero_slides" ADD CONSTRAINT "pages_hero_slides_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_hero"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_hero" ADD CONSTRAINT "pages_hero_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_hero" ADD CONSTRAINT "pages_hero_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_text" ADD CONSTRAINT "pages_text_portrait_id_media_id_fk" FOREIGN KEY ("portrait_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_text" ADD CONSTRAINT "pages_text_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_split_links" ADD CONSTRAINT "pages_split_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_split"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_split" ADD CONSTRAINT "pages_split_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_split" ADD CONSTRAINT "pages_split_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_facts_facts" ADD CONSTRAINT "pages_facts_facts_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_facts"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_facts" ADD CONSTRAINT "pages_facts_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_journey" ADD CONSTRAINT "pages_journey_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_mosaic_items" ADD CONSTRAINT "pages_mosaic_items_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_mosaic_items" ADD CONSTRAINT "pages_mosaic_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_mosaic"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_mosaic" ADD CONSTRAINT "pages_mosaic_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_kinetic_words" ADD CONSTRAINT "pages_kinetic_words_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_kinetic"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_kinetic" ADD CONSTRAINT "pages_kinetic_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_cards_cards" ADD CONSTRAINT "pages_cards_cards_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_cards_cards" ADD CONSTRAINT "pages_cards_cards_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_cards"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_cards" ADD CONSTRAINT "pages_cards_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_defs_items" ADD CONSTRAINT "pages_defs_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_defs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_defs" ADD CONSTRAINT "pages_defs_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_lists_groups" ADD CONSTRAINT "pages_lists_groups_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_lists"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_lists" ADD CONSTRAINT "pages_lists_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_tbl_columns" ADD CONSTRAINT "pages_tbl_columns_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_tbl"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_tbl_rows_cells" ADD CONSTRAINT "pages_tbl_rows_cells_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_tbl_rows"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_tbl_rows" ADD CONSTRAINT "pages_tbl_rows_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_tbl"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_tbl" ADD CONSTRAINT "pages_tbl_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_steps_steps" ADD CONSTRAINT "pages_steps_steps_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_steps"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_steps" ADD CONSTRAINT "pages_steps_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_gallery_images" ADD CONSTRAINT "pages_gallery_images_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_gallery_images" ADD CONSTRAINT "pages_gallery_images_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_gallery"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_gallery" ADD CONSTRAINT "pages_gallery_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_people_people" ADD CONSTRAINT "pages_people_people_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_people_people" ADD CONSTRAINT "pages_people_people_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_people"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_people" ADD CONSTRAINT "pages_people_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_res_items" ADD CONSTRAINT "pages_res_items_file_id_media_id_fk" FOREIGN KEY ("file_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_res_items" ADD CONSTRAINT "pages_res_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_res"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_res" ADD CONSTRAINT "pages_res_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_faq_list_categories" ADD CONSTRAINT "pages_faq_list_categories_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."pages_faq_list"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_faq_list" ADD CONSTRAINT "pages_faq_list_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_latest" ADD CONSTRAINT "pages_latest_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_events_list" ADD CONSTRAINT "pages_events_list_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_campuses_links" ADD CONSTRAINT "pages_campuses_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_campuses"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_campuses" ADD CONSTRAINT "pages_campuses_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_campuses" ADD CONSTRAINT "pages_campuses_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_adm_info" ADD CONSTRAINT "pages_adm_info_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_form" ADD CONSTRAINT "pages_form_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_notice" ADD CONSTRAINT "pages_notice_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_cta_links" ADD CONSTRAINT "pages_cta_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_cta"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_cta" ADD CONSTRAINT "pages_cta_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_cta" ADD CONSTRAINT "pages_cta_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_marquee_items" ADD CONSTRAINT "pages_marquee_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages_marquee"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_marquee" ADD CONSTRAINT "pages_marquee_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_reuse" ADD CONSTRAINT "pages_reuse_section_id_reusable_sections_id_fk" FOREIGN KEY ("section_id") REFERENCES "public"."reusable_sections"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_reuse" ADD CONSTRAINT "pages_reuse_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages" ADD CONSTRAINT "pages_meta_image_id_media_id_fk" FOREIGN KEY ("meta_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages" ADD CONSTRAINT "pages_parent_id_pages_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."pages"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "pages_rels" ADD CONSTRAINT "pages_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_rels" ADD CONSTRAINT "pages_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_rels" ADD CONSTRAINT "pages_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_rels" ADD CONSTRAINT "pages_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "pages_rels" ADD CONSTRAINT "pages_rels_faqs_fk" FOREIGN KEY ("faqs_id") REFERENCES "public"."faqs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_hero_v_links" ADD CONSTRAINT "__pages_v_hero_v_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_hero_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_hero_v_slides" ADD CONSTRAINT "__pages_v_hero_v_slides_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_hero_v_slides" ADD CONSTRAINT "__pages_v_hero_v_slides_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_hero_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_hero_v" ADD CONSTRAINT "__pages_v_hero_v_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_hero_v" ADD CONSTRAINT "__pages_v_hero_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_text_v" ADD CONSTRAINT "__pages_v_text_v_portrait_id_media_id_fk" FOREIGN KEY ("portrait_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_text_v" ADD CONSTRAINT "__pages_v_text_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_split_v_links" ADD CONSTRAINT "__pages_v_split_v_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_split_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_split_v" ADD CONSTRAINT "__pages_v_split_v_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_split_v" ADD CONSTRAINT "__pages_v_split_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_facts_v_facts" ADD CONSTRAINT "__pages_v_facts_v_facts_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_facts_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_facts_v" ADD CONSTRAINT "__pages_v_facts_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_journey_v" ADD CONSTRAINT "__pages_v_journey_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_mosaic_v_items" ADD CONSTRAINT "__pages_v_mosaic_v_items_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_mosaic_v_items" ADD CONSTRAINT "__pages_v_mosaic_v_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_mosaic_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_mosaic_v" ADD CONSTRAINT "__pages_v_mosaic_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_kinetic_v_words" ADD CONSTRAINT "__pages_v_kinetic_v_words_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_kinetic_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_kinetic_v" ADD CONSTRAINT "__pages_v_kinetic_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_cards_v_cards" ADD CONSTRAINT "__pages_v_cards_v_cards_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_cards_v_cards" ADD CONSTRAINT "__pages_v_cards_v_cards_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_cards_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_cards_v" ADD CONSTRAINT "__pages_v_cards_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_defs_v_items" ADD CONSTRAINT "__pages_v_defs_v_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_defs_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_defs_v" ADD CONSTRAINT "__pages_v_defs_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_lists_v_groups" ADD CONSTRAINT "__pages_v_lists_v_groups_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_lists_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_lists_v" ADD CONSTRAINT "__pages_v_lists_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_tbl_v_columns" ADD CONSTRAINT "__pages_v_tbl_v_columns_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_tbl_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_tbl_v_rows_cells" ADD CONSTRAINT "__pages_v_tbl_v_rows_cells_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_tbl_v_rows"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_tbl_v_rows" ADD CONSTRAINT "__pages_v_tbl_v_rows_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_tbl_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_tbl_v" ADD CONSTRAINT "__pages_v_tbl_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_steps_v_steps" ADD CONSTRAINT "__pages_v_steps_v_steps_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_steps_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_steps_v" ADD CONSTRAINT "__pages_v_steps_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_gallery_v_images" ADD CONSTRAINT "__pages_v_gallery_v_images_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_gallery_v_images" ADD CONSTRAINT "__pages_v_gallery_v_images_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_gallery_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_gallery_v" ADD CONSTRAINT "__pages_v_gallery_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_people_v_people" ADD CONSTRAINT "__pages_v_people_v_people_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_people_v_people" ADD CONSTRAINT "__pages_v_people_v_people_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_people_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_people_v" ADD CONSTRAINT "__pages_v_people_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_res_v_items" ADD CONSTRAINT "__pages_v_res_v_items_file_id_media_id_fk" FOREIGN KEY ("file_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_res_v_items" ADD CONSTRAINT "__pages_v_res_v_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_res_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_res_v" ADD CONSTRAINT "__pages_v_res_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_faq_list_v_categories" ADD CONSTRAINT "__pages_v_faq_list_v_categories_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."__pages_v_faq_list_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_faq_list_v" ADD CONSTRAINT "__pages_v_faq_list_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_latest_v" ADD CONSTRAINT "__pages_v_latest_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_events_list_v" ADD CONSTRAINT "__pages_v_events_list_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_campuses_v_links" ADD CONSTRAINT "__pages_v_campuses_v_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_campuses_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_campuses_v" ADD CONSTRAINT "__pages_v_campuses_v_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_campuses_v" ADD CONSTRAINT "__pages_v_campuses_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_adm_info_v" ADD CONSTRAINT "__pages_v_adm_info_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_form_v" ADD CONSTRAINT "__pages_v_form_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_notice_v" ADD CONSTRAINT "__pages_v_notice_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_cta_v_links" ADD CONSTRAINT "__pages_v_cta_v_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_cta_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_cta_v" ADD CONSTRAINT "__pages_v_cta_v_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_cta_v" ADD CONSTRAINT "__pages_v_cta_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_marquee_v_items" ADD CONSTRAINT "__pages_v_marquee_v_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."__pages_v_marquee_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_marquee_v" ADD CONSTRAINT "__pages_v_marquee_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "__pages_v_reuse_v" ADD CONSTRAINT "__pages_v_reuse_v_section_id_reusable_sections_id_fk" FOREIGN KEY ("section_id") REFERENCES "public"."reusable_sections"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "__pages_v_reuse_v" ADD CONSTRAINT "__pages_v_reuse_v_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_pages_v" ADD CONSTRAINT "_pages_v_parent_id_pages_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."pages"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_pages_v" ADD CONSTRAINT "_pages_v_version_meta_image_id_media_id_fk" FOREIGN KEY ("version_meta_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_pages_v" ADD CONSTRAINT "_pages_v_version_parent_id_pages_id_fk" FOREIGN KEY ("version_parent_id") REFERENCES "public"."pages"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_pages_v_rels" ADD CONSTRAINT "_pages_v_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."_pages_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_pages_v_rels" ADD CONSTRAINT "_pages_v_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_pages_v_rels" ADD CONSTRAINT "_pages_v_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_pages_v_rels" ADD CONSTRAINT "_pages_v_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_pages_v_rels" ADD CONSTRAINT "_pages_v_rels_faqs_fk" FOREIGN KEY ("faqs_id") REFERENCES "public"."faqs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "stories" ADD CONSTRAINT "stories_hero_image_id_media_id_fk" FOREIGN KEY ("hero_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "stories" ADD CONSTRAINT "stories_meta_image_id_media_id_fk" FOREIGN KEY ("meta_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "stories_rels" ADD CONSTRAINT "stories_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "stories_rels" ADD CONSTRAINT "stories_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_stories_v" ADD CONSTRAINT "_stories_v_parent_id_stories_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."stories"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_stories_v" ADD CONSTRAINT "_stories_v_version_hero_image_id_media_id_fk" FOREIGN KEY ("version_hero_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_stories_v" ADD CONSTRAINT "_stories_v_version_meta_image_id_media_id_fk" FOREIGN KEY ("version_meta_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_stories_v_rels" ADD CONSTRAINT "_stories_v_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."_stories_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_stories_v_rels" ADD CONSTRAINT "_stories_v_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_events_v" ADD CONSTRAINT "_events_v_parent_id_events_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."events"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "programmes_highlights" ADD CONSTRAINT "programmes_highlights_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "programmes" ADD CONSTRAINT "programmes_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "programmes" ADD CONSTRAINT "programmes_page_id_pages_id_fk" FOREIGN KEY ("page_id") REFERENCES "public"."pages"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_programmes_v_version_highlights" ADD CONSTRAINT "_programmes_v_version_highlights_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_programmes_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_programmes_v" ADD CONSTRAINT "_programmes_v_parent_id_programmes_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."programmes"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_programmes_v" ADD CONSTRAINT "_programmes_v_version_image_id_media_id_fk" FOREIGN KEY ("version_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_programmes_v" ADD CONSTRAINT "_programmes_v_version_page_id_pages_id_fk" FOREIGN KEY ("version_page_id") REFERENCES "public"."pages"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "_faqs_v" ADD CONSTRAINT "_faqs_v_parent_id_faqs_id_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."faqs"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_hero_links" ADD CONSTRAINT "reusable_sections_hero_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_hero"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_hero_slides" ADD CONSTRAINT "reusable_sections_hero_slides_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_hero_slides" ADD CONSTRAINT "reusable_sections_hero_slides_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_hero"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_hero" ADD CONSTRAINT "reusable_sections_hero_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_hero" ADD CONSTRAINT "reusable_sections_hero_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_text" ADD CONSTRAINT "reusable_sections_text_portrait_id_media_id_fk" FOREIGN KEY ("portrait_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_text" ADD CONSTRAINT "reusable_sections_text_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_split_links" ADD CONSTRAINT "reusable_sections_split_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_split"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_split" ADD CONSTRAINT "reusable_sections_split_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_split" ADD CONSTRAINT "reusable_sections_split_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_facts_facts" ADD CONSTRAINT "reusable_sections_facts_facts_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_facts"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_facts" ADD CONSTRAINT "reusable_sections_facts_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_journey" ADD CONSTRAINT "reusable_sections_journey_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_mosaic_items" ADD CONSTRAINT "reusable_sections_mosaic_items_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_mosaic_items" ADD CONSTRAINT "reusable_sections_mosaic_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_mosaic"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_mosaic" ADD CONSTRAINT "reusable_sections_mosaic_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_kinetic_words" ADD CONSTRAINT "reusable_sections_kinetic_words_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_kinetic"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_kinetic" ADD CONSTRAINT "reusable_sections_kinetic_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_cards_cards" ADD CONSTRAINT "reusable_sections_cards_cards_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_cards_cards" ADD CONSTRAINT "reusable_sections_cards_cards_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_cards"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_cards" ADD CONSTRAINT "reusable_sections_cards_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_defs_items" ADD CONSTRAINT "reusable_sections_defs_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_defs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_defs" ADD CONSTRAINT "reusable_sections_defs_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_lists_groups" ADD CONSTRAINT "reusable_sections_lists_groups_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_lists"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_lists" ADD CONSTRAINT "reusable_sections_lists_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_tbl_columns" ADD CONSTRAINT "reusable_sections_tbl_columns_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_tbl"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_tbl_rows_cells" ADD CONSTRAINT "reusable_sections_tbl_rows_cells_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_tbl_rows"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_tbl_rows" ADD CONSTRAINT "reusable_sections_tbl_rows_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_tbl"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_tbl" ADD CONSTRAINT "reusable_sections_tbl_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_steps_steps" ADD CONSTRAINT "reusable_sections_steps_steps_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_steps"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_steps" ADD CONSTRAINT "reusable_sections_steps_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_gallery_images" ADD CONSTRAINT "reusable_sections_gallery_images_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_gallery_images" ADD CONSTRAINT "reusable_sections_gallery_images_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_gallery"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_gallery" ADD CONSTRAINT "reusable_sections_gallery_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_people_people" ADD CONSTRAINT "reusable_sections_people_people_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_people_people" ADD CONSTRAINT "reusable_sections_people_people_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_people"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_people" ADD CONSTRAINT "reusable_sections_people_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_res_items" ADD CONSTRAINT "reusable_sections_res_items_file_id_media_id_fk" FOREIGN KEY ("file_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_res_items" ADD CONSTRAINT "reusable_sections_res_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_res"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_res" ADD CONSTRAINT "reusable_sections_res_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_faq_list_categories" ADD CONSTRAINT "reusable_sections_faq_list_categories_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."reusable_sections_faq_list"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_faq_list" ADD CONSTRAINT "reusable_sections_faq_list_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_latest" ADD CONSTRAINT "reusable_sections_latest_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_events_list" ADD CONSTRAINT "reusable_sections_events_list_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_campuses_links" ADD CONSTRAINT "reusable_sections_campuses_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_campuses"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_campuses" ADD CONSTRAINT "reusable_sections_campuses_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_campuses" ADD CONSTRAINT "reusable_sections_campuses_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_adm_info" ADD CONSTRAINT "reusable_sections_adm_info_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_form" ADD CONSTRAINT "reusable_sections_form_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_notice" ADD CONSTRAINT "reusable_sections_notice_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_cta_links" ADD CONSTRAINT "reusable_sections_cta_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_cta"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_cta" ADD CONSTRAINT "reusable_sections_cta_media_id_media_id_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "reusable_sections_cta" ADD CONSTRAINT "reusable_sections_cta_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_marquee_items" ADD CONSTRAINT "reusable_sections_marquee_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections_marquee"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_marquee" ADD CONSTRAINT "reusable_sections_marquee_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_rels" ADD CONSTRAINT "reusable_sections_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_rels" ADD CONSTRAINT "reusable_sections_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_rels" ADD CONSTRAINT "reusable_sections_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_rels" ADD CONSTRAINT "reusable_sections_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "reusable_sections_rels" ADD CONSTRAINT "reusable_sections_rels_faqs_fk" FOREIGN KEY ("faqs_id") REFERENCES "public"."faqs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "users_roles" ADD CONSTRAINT "users_roles_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "users_sessions" ADD CONSTRAINT "users_sessions_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."payload_locked_documents"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_events_fk" FOREIGN KEY ("events_id") REFERENCES "public"."events"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_programmes_fk" FOREIGN KEY ("programmes_id") REFERENCES "public"."programmes"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_faqs_fk" FOREIGN KEY ("faqs_id") REFERENCES "public"."faqs"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_media_fk" FOREIGN KEY ("media_id") REFERENCES "public"."media"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_reusable_sections_fk" FOREIGN KEY ("reusable_sections_id") REFERENCES "public"."reusable_sections"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_enquiries_fk" FOREIGN KEY ("enquiries_id") REFERENCES "public"."enquiries"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_locked_documents_rels" ADD CONSTRAINT "payload_locked_documents_rels_users_fk" FOREIGN KEY ("users_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_preferences_rels" ADD CONSTRAINT "payload_preferences_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."payload_preferences"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "payload_preferences_rels" ADD CONSTRAINT "payload_preferences_rels_users_fk" FOREIGN KEY ("users_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings_campuses_phones" ADD CONSTRAINT "site_settings_campuses_phones_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."site_settings_campuses"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings_campuses" ADD CONSTRAINT "site_settings_campuses_image_id_media_id_fk" FOREIGN KEY ("image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "site_settings_campuses" ADD CONSTRAINT "site_settings_campuses_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."site_settings"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings_social" ADD CONSTRAINT "site_settings_social_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."site_settings"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings" ADD CONSTRAINT "site_settings_default_share_image_id_media_id_fk" FOREIGN KEY ("default_share_image_id") REFERENCES "public"."media"("id") ON DELETE set null ON UPDATE no action;
  ALTER TABLE "site_settings_rels" ADD CONSTRAINT "site_settings_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."site_settings"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings_rels" ADD CONSTRAINT "site_settings_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "site_settings_rels" ADD CONSTRAINT "site_settings_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_nav_groups_items" ADD CONSTRAINT "header_nav_groups_items_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."header_nav_groups"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_nav_groups" ADD CONSTRAINT "header_nav_groups_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."header"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_utility_links" ADD CONSTRAINT "header_utility_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."header"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_rels" ADD CONSTRAINT "header_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."header"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_rels" ADD CONSTRAINT "header_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "header_rels" ADD CONSTRAINT "header_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_columns_links" ADD CONSTRAINT "footer_columns_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."footer_columns"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_columns" ADD CONSTRAINT "footer_columns_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."footer"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_legal_links" ADD CONSTRAINT "footer_legal_links_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."footer"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_memberships" ADD CONSTRAINT "footer_memberships_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."footer"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_rels" ADD CONSTRAINT "footer_rels_parent_fk" FOREIGN KEY ("parent_id") REFERENCES "public"."footer"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_rels" ADD CONSTRAINT "footer_rels_pages_fk" FOREIGN KEY ("pages_id") REFERENCES "public"."pages"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "footer_rels" ADD CONSTRAINT "footer_rels_stories_fk" FOREIGN KEY ("stories_id") REFERENCES "public"."stories"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "admissions_fee_table" ADD CONSTRAINT "admissions_fee_table_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."admissions"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "admissions_required_documents" ADD CONSTRAINT "admissions_required_documents_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."admissions"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_admissions_v_version_fee_table" ADD CONSTRAINT "_admissions_v_version_fee_table_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_admissions_v"("id") ON DELETE cascade ON UPDATE no action;
  ALTER TABLE "_admissions_v_version_required_documents" ADD CONSTRAINT "_admissions_v_version_required_documents_parent_id_fk" FOREIGN KEY ("_parent_id") REFERENCES "public"."_admissions_v"("id") ON DELETE cascade ON UPDATE no action;
  CREATE INDEX "pages_hero_links_order_idx" ON "pages_hero_links" USING btree ("_order");
  CREATE INDEX "pages_hero_links_parent_id_idx" ON "pages_hero_links" USING btree ("_parent_id");
  CREATE INDEX "pages_hero_slides_order_idx" ON "pages_hero_slides" USING btree ("_order");
  CREATE INDEX "pages_hero_slides_parent_id_idx" ON "pages_hero_slides" USING btree ("_parent_id");
  CREATE INDEX "pages_hero_slides_image_idx" ON "pages_hero_slides" USING btree ("image_id");
  CREATE INDEX "pages_hero_order_idx" ON "pages_hero" USING btree ("_order");
  CREATE INDEX "pages_hero_parent_id_idx" ON "pages_hero" USING btree ("_parent_id");
  CREATE INDEX "pages_hero_path_idx" ON "pages_hero" USING btree ("_path");
  CREATE INDEX "pages_hero_media_idx" ON "pages_hero" USING btree ("media_id");
  CREATE INDEX "pages_text_order_idx" ON "pages_text" USING btree ("_order");
  CREATE INDEX "pages_text_parent_id_idx" ON "pages_text" USING btree ("_parent_id");
  CREATE INDEX "pages_text_path_idx" ON "pages_text" USING btree ("_path");
  CREATE INDEX "pages_text_portrait_idx" ON "pages_text" USING btree ("portrait_id");
  CREATE INDEX "pages_split_links_order_idx" ON "pages_split_links" USING btree ("_order");
  CREATE INDEX "pages_split_links_parent_id_idx" ON "pages_split_links" USING btree ("_parent_id");
  CREATE INDEX "pages_split_order_idx" ON "pages_split" USING btree ("_order");
  CREATE INDEX "pages_split_parent_id_idx" ON "pages_split" USING btree ("_parent_id");
  CREATE INDEX "pages_split_path_idx" ON "pages_split" USING btree ("_path");
  CREATE INDEX "pages_split_media_idx" ON "pages_split" USING btree ("media_id");
  CREATE INDEX "pages_facts_facts_order_idx" ON "pages_facts_facts" USING btree ("_order");
  CREATE INDEX "pages_facts_facts_parent_id_idx" ON "pages_facts_facts" USING btree ("_parent_id");
  CREATE INDEX "pages_facts_order_idx" ON "pages_facts" USING btree ("_order");
  CREATE INDEX "pages_facts_parent_id_idx" ON "pages_facts" USING btree ("_parent_id");
  CREATE INDEX "pages_facts_path_idx" ON "pages_facts" USING btree ("_path");
  CREATE INDEX "pages_journey_order_idx" ON "pages_journey" USING btree ("_order");
  CREATE INDEX "pages_journey_parent_id_idx" ON "pages_journey" USING btree ("_parent_id");
  CREATE INDEX "pages_journey_path_idx" ON "pages_journey" USING btree ("_path");
  CREATE INDEX "pages_mosaic_items_order_idx" ON "pages_mosaic_items" USING btree ("_order");
  CREATE INDEX "pages_mosaic_items_parent_id_idx" ON "pages_mosaic_items" USING btree ("_parent_id");
  CREATE INDEX "pages_mosaic_items_media_idx" ON "pages_mosaic_items" USING btree ("media_id");
  CREATE INDEX "pages_mosaic_order_idx" ON "pages_mosaic" USING btree ("_order");
  CREATE INDEX "pages_mosaic_parent_id_idx" ON "pages_mosaic" USING btree ("_parent_id");
  CREATE INDEX "pages_mosaic_path_idx" ON "pages_mosaic" USING btree ("_path");
  CREATE INDEX "pages_kinetic_words_order_idx" ON "pages_kinetic_words" USING btree ("_order");
  CREATE INDEX "pages_kinetic_words_parent_id_idx" ON "pages_kinetic_words" USING btree ("_parent_id");
  CREATE INDEX "pages_kinetic_order_idx" ON "pages_kinetic" USING btree ("_order");
  CREATE INDEX "pages_kinetic_parent_id_idx" ON "pages_kinetic" USING btree ("_parent_id");
  CREATE INDEX "pages_kinetic_path_idx" ON "pages_kinetic" USING btree ("_path");
  CREATE INDEX "pages_cards_cards_order_idx" ON "pages_cards_cards" USING btree ("_order");
  CREATE INDEX "pages_cards_cards_parent_id_idx" ON "pages_cards_cards" USING btree ("_parent_id");
  CREATE INDEX "pages_cards_cards_media_idx" ON "pages_cards_cards" USING btree ("media_id");
  CREATE INDEX "pages_cards_order_idx" ON "pages_cards" USING btree ("_order");
  CREATE INDEX "pages_cards_parent_id_idx" ON "pages_cards" USING btree ("_parent_id");
  CREATE INDEX "pages_cards_path_idx" ON "pages_cards" USING btree ("_path");
  CREATE INDEX "pages_defs_items_order_idx" ON "pages_defs_items" USING btree ("_order");
  CREATE INDEX "pages_defs_items_parent_id_idx" ON "pages_defs_items" USING btree ("_parent_id");
  CREATE INDEX "pages_defs_order_idx" ON "pages_defs" USING btree ("_order");
  CREATE INDEX "pages_defs_parent_id_idx" ON "pages_defs" USING btree ("_parent_id");
  CREATE INDEX "pages_defs_path_idx" ON "pages_defs" USING btree ("_path");
  CREATE INDEX "pages_lists_groups_order_idx" ON "pages_lists_groups" USING btree ("_order");
  CREATE INDEX "pages_lists_groups_parent_id_idx" ON "pages_lists_groups" USING btree ("_parent_id");
  CREATE INDEX "pages_lists_order_idx" ON "pages_lists" USING btree ("_order");
  CREATE INDEX "pages_lists_parent_id_idx" ON "pages_lists" USING btree ("_parent_id");
  CREATE INDEX "pages_lists_path_idx" ON "pages_lists" USING btree ("_path");
  CREATE INDEX "pages_tbl_columns_order_idx" ON "pages_tbl_columns" USING btree ("_order");
  CREATE INDEX "pages_tbl_columns_parent_id_idx" ON "pages_tbl_columns" USING btree ("_parent_id");
  CREATE INDEX "pages_tbl_rows_cells_order_idx" ON "pages_tbl_rows_cells" USING btree ("_order");
  CREATE INDEX "pages_tbl_rows_cells_parent_id_idx" ON "pages_tbl_rows_cells" USING btree ("_parent_id");
  CREATE INDEX "pages_tbl_rows_order_idx" ON "pages_tbl_rows" USING btree ("_order");
  CREATE INDEX "pages_tbl_rows_parent_id_idx" ON "pages_tbl_rows" USING btree ("_parent_id");
  CREATE INDEX "pages_tbl_order_idx" ON "pages_tbl" USING btree ("_order");
  CREATE INDEX "pages_tbl_parent_id_idx" ON "pages_tbl" USING btree ("_parent_id");
  CREATE INDEX "pages_tbl_path_idx" ON "pages_tbl" USING btree ("_path");
  CREATE INDEX "pages_steps_steps_order_idx" ON "pages_steps_steps" USING btree ("_order");
  CREATE INDEX "pages_steps_steps_parent_id_idx" ON "pages_steps_steps" USING btree ("_parent_id");
  CREATE INDEX "pages_steps_order_idx" ON "pages_steps" USING btree ("_order");
  CREATE INDEX "pages_steps_parent_id_idx" ON "pages_steps" USING btree ("_parent_id");
  CREATE INDEX "pages_steps_path_idx" ON "pages_steps" USING btree ("_path");
  CREATE INDEX "pages_gallery_images_order_idx" ON "pages_gallery_images" USING btree ("_order");
  CREATE INDEX "pages_gallery_images_parent_id_idx" ON "pages_gallery_images" USING btree ("_parent_id");
  CREATE INDEX "pages_gallery_images_image_idx" ON "pages_gallery_images" USING btree ("image_id");
  CREATE INDEX "pages_gallery_order_idx" ON "pages_gallery" USING btree ("_order");
  CREATE INDEX "pages_gallery_parent_id_idx" ON "pages_gallery" USING btree ("_parent_id");
  CREATE INDEX "pages_gallery_path_idx" ON "pages_gallery" USING btree ("_path");
  CREATE INDEX "pages_people_people_order_idx" ON "pages_people_people" USING btree ("_order");
  CREATE INDEX "pages_people_people_parent_id_idx" ON "pages_people_people" USING btree ("_parent_id");
  CREATE INDEX "pages_people_people_image_idx" ON "pages_people_people" USING btree ("image_id");
  CREATE INDEX "pages_people_order_idx" ON "pages_people" USING btree ("_order");
  CREATE INDEX "pages_people_parent_id_idx" ON "pages_people" USING btree ("_parent_id");
  CREATE INDEX "pages_people_path_idx" ON "pages_people" USING btree ("_path");
  CREATE INDEX "pages_res_items_order_idx" ON "pages_res_items" USING btree ("_order");
  CREATE INDEX "pages_res_items_parent_id_idx" ON "pages_res_items" USING btree ("_parent_id");
  CREATE INDEX "pages_res_items_file_idx" ON "pages_res_items" USING btree ("file_id");
  CREATE INDEX "pages_res_order_idx" ON "pages_res" USING btree ("_order");
  CREATE INDEX "pages_res_parent_id_idx" ON "pages_res" USING btree ("_parent_id");
  CREATE INDEX "pages_res_path_idx" ON "pages_res" USING btree ("_path");
  CREATE INDEX "pages_faq_list_categories_order_idx" ON "pages_faq_list_categories" USING btree ("order");
  CREATE INDEX "pages_faq_list_categories_parent_idx" ON "pages_faq_list_categories" USING btree ("parent_id");
  CREATE INDEX "pages_faq_list_order_idx" ON "pages_faq_list" USING btree ("_order");
  CREATE INDEX "pages_faq_list_parent_id_idx" ON "pages_faq_list" USING btree ("_parent_id");
  CREATE INDEX "pages_faq_list_path_idx" ON "pages_faq_list" USING btree ("_path");
  CREATE INDEX "pages_latest_order_idx" ON "pages_latest" USING btree ("_order");
  CREATE INDEX "pages_latest_parent_id_idx" ON "pages_latest" USING btree ("_parent_id");
  CREATE INDEX "pages_latest_path_idx" ON "pages_latest" USING btree ("_path");
  CREATE INDEX "pages_events_list_order_idx" ON "pages_events_list" USING btree ("_order");
  CREATE INDEX "pages_events_list_parent_id_idx" ON "pages_events_list" USING btree ("_parent_id");
  CREATE INDEX "pages_events_list_path_idx" ON "pages_events_list" USING btree ("_path");
  CREATE INDEX "pages_campuses_links_order_idx" ON "pages_campuses_links" USING btree ("_order");
  CREATE INDEX "pages_campuses_links_parent_id_idx" ON "pages_campuses_links" USING btree ("_parent_id");
  CREATE INDEX "pages_campuses_order_idx" ON "pages_campuses" USING btree ("_order");
  CREATE INDEX "pages_campuses_parent_id_idx" ON "pages_campuses" USING btree ("_parent_id");
  CREATE INDEX "pages_campuses_path_idx" ON "pages_campuses" USING btree ("_path");
  CREATE INDEX "pages_campuses_media_idx" ON "pages_campuses" USING btree ("media_id");
  CREATE INDEX "pages_adm_info_order_idx" ON "pages_adm_info" USING btree ("_order");
  CREATE INDEX "pages_adm_info_parent_id_idx" ON "pages_adm_info" USING btree ("_parent_id");
  CREATE INDEX "pages_adm_info_path_idx" ON "pages_adm_info" USING btree ("_path");
  CREATE INDEX "pages_form_order_idx" ON "pages_form" USING btree ("_order");
  CREATE INDEX "pages_form_parent_id_idx" ON "pages_form" USING btree ("_parent_id");
  CREATE INDEX "pages_form_path_idx" ON "pages_form" USING btree ("_path");
  CREATE INDEX "pages_notice_order_idx" ON "pages_notice" USING btree ("_order");
  CREATE INDEX "pages_notice_parent_id_idx" ON "pages_notice" USING btree ("_parent_id");
  CREATE INDEX "pages_notice_path_idx" ON "pages_notice" USING btree ("_path");
  CREATE INDEX "pages_cta_links_order_idx" ON "pages_cta_links" USING btree ("_order");
  CREATE INDEX "pages_cta_links_parent_id_idx" ON "pages_cta_links" USING btree ("_parent_id");
  CREATE INDEX "pages_cta_order_idx" ON "pages_cta" USING btree ("_order");
  CREATE INDEX "pages_cta_parent_id_idx" ON "pages_cta" USING btree ("_parent_id");
  CREATE INDEX "pages_cta_path_idx" ON "pages_cta" USING btree ("_path");
  CREATE INDEX "pages_cta_media_idx" ON "pages_cta" USING btree ("media_id");
  CREATE INDEX "pages_marquee_items_order_idx" ON "pages_marquee_items" USING btree ("_order");
  CREATE INDEX "pages_marquee_items_parent_id_idx" ON "pages_marquee_items" USING btree ("_parent_id");
  CREATE INDEX "pages_marquee_order_idx" ON "pages_marquee" USING btree ("_order");
  CREATE INDEX "pages_marquee_parent_id_idx" ON "pages_marquee" USING btree ("_parent_id");
  CREATE INDEX "pages_marquee_path_idx" ON "pages_marquee" USING btree ("_path");
  CREATE INDEX "pages_reuse_order_idx" ON "pages_reuse" USING btree ("_order");
  CREATE INDEX "pages_reuse_parent_id_idx" ON "pages_reuse" USING btree ("_parent_id");
  CREATE INDEX "pages_reuse_path_idx" ON "pages_reuse" USING btree ("_path");
  CREATE INDEX "pages_reuse_section_idx" ON "pages_reuse" USING btree ("section_id");
  CREATE INDEX "pages_meta_meta_image_idx" ON "pages" USING btree ("meta_image_id");
  CREATE INDEX "pages_slug_idx" ON "pages" USING btree ("slug");
  CREATE INDEX "pages_parent_idx" ON "pages" USING btree ("parent_id");
  CREATE UNIQUE INDEX "pages_path_idx" ON "pages" USING btree ("path");
  CREATE INDEX "pages_updated_at_idx" ON "pages" USING btree ("updated_at");
  CREATE INDEX "pages_created_at_idx" ON "pages" USING btree ("created_at");
  CREATE INDEX "pages__status_idx" ON "pages" USING btree ("_status");
  CREATE INDEX "pages_rels_order_idx" ON "pages_rels" USING btree ("order");
  CREATE INDEX "pages_rels_parent_idx" ON "pages_rels" USING btree ("parent_id");
  CREATE INDEX "pages_rels_path_idx" ON "pages_rels" USING btree ("path");
  CREATE INDEX "pages_rels_pages_id_idx" ON "pages_rels" USING btree ("pages_id");
  CREATE INDEX "pages_rels_stories_id_idx" ON "pages_rels" USING btree ("stories_id");
  CREATE INDEX "pages_rels_programmes_id_idx" ON "pages_rels" USING btree ("programmes_id");
  CREATE INDEX "pages_rels_faqs_id_idx" ON "pages_rels" USING btree ("faqs_id");
  CREATE INDEX "__pages_v_hero_v_links_order_idx" ON "__pages_v_hero_v_links" USING btree ("_order");
  CREATE INDEX "__pages_v_hero_v_links_parent_id_idx" ON "__pages_v_hero_v_links" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_hero_v_slides_order_idx" ON "__pages_v_hero_v_slides" USING btree ("_order");
  CREATE INDEX "__pages_v_hero_v_slides_parent_id_idx" ON "__pages_v_hero_v_slides" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_hero_v_slides_image_idx" ON "__pages_v_hero_v_slides" USING btree ("image_id");
  CREATE INDEX "__pages_v_hero_v_order_idx" ON "__pages_v_hero_v" USING btree ("_order");
  CREATE INDEX "__pages_v_hero_v_parent_id_idx" ON "__pages_v_hero_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_hero_v_path_idx" ON "__pages_v_hero_v" USING btree ("_path");
  CREATE INDEX "__pages_v_hero_v_media_idx" ON "__pages_v_hero_v" USING btree ("media_id");
  CREATE INDEX "__pages_v_text_v_order_idx" ON "__pages_v_text_v" USING btree ("_order");
  CREATE INDEX "__pages_v_text_v_parent_id_idx" ON "__pages_v_text_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_text_v_path_idx" ON "__pages_v_text_v" USING btree ("_path");
  CREATE INDEX "__pages_v_text_v_portrait_idx" ON "__pages_v_text_v" USING btree ("portrait_id");
  CREATE INDEX "__pages_v_split_v_links_order_idx" ON "__pages_v_split_v_links" USING btree ("_order");
  CREATE INDEX "__pages_v_split_v_links_parent_id_idx" ON "__pages_v_split_v_links" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_split_v_order_idx" ON "__pages_v_split_v" USING btree ("_order");
  CREATE INDEX "__pages_v_split_v_parent_id_idx" ON "__pages_v_split_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_split_v_path_idx" ON "__pages_v_split_v" USING btree ("_path");
  CREATE INDEX "__pages_v_split_v_media_idx" ON "__pages_v_split_v" USING btree ("media_id");
  CREATE INDEX "__pages_v_facts_v_facts_order_idx" ON "__pages_v_facts_v_facts" USING btree ("_order");
  CREATE INDEX "__pages_v_facts_v_facts_parent_id_idx" ON "__pages_v_facts_v_facts" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_facts_v_order_idx" ON "__pages_v_facts_v" USING btree ("_order");
  CREATE INDEX "__pages_v_facts_v_parent_id_idx" ON "__pages_v_facts_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_facts_v_path_idx" ON "__pages_v_facts_v" USING btree ("_path");
  CREATE INDEX "__pages_v_journey_v_order_idx" ON "__pages_v_journey_v" USING btree ("_order");
  CREATE INDEX "__pages_v_journey_v_parent_id_idx" ON "__pages_v_journey_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_journey_v_path_idx" ON "__pages_v_journey_v" USING btree ("_path");
  CREATE INDEX "__pages_v_mosaic_v_items_order_idx" ON "__pages_v_mosaic_v_items" USING btree ("_order");
  CREATE INDEX "__pages_v_mosaic_v_items_parent_id_idx" ON "__pages_v_mosaic_v_items" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_mosaic_v_items_media_idx" ON "__pages_v_mosaic_v_items" USING btree ("media_id");
  CREATE INDEX "__pages_v_mosaic_v_order_idx" ON "__pages_v_mosaic_v" USING btree ("_order");
  CREATE INDEX "__pages_v_mosaic_v_parent_id_idx" ON "__pages_v_mosaic_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_mosaic_v_path_idx" ON "__pages_v_mosaic_v" USING btree ("_path");
  CREATE INDEX "__pages_v_kinetic_v_words_order_idx" ON "__pages_v_kinetic_v_words" USING btree ("_order");
  CREATE INDEX "__pages_v_kinetic_v_words_parent_id_idx" ON "__pages_v_kinetic_v_words" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_kinetic_v_order_idx" ON "__pages_v_kinetic_v" USING btree ("_order");
  CREATE INDEX "__pages_v_kinetic_v_parent_id_idx" ON "__pages_v_kinetic_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_kinetic_v_path_idx" ON "__pages_v_kinetic_v" USING btree ("_path");
  CREATE INDEX "__pages_v_cards_v_cards_order_idx" ON "__pages_v_cards_v_cards" USING btree ("_order");
  CREATE INDEX "__pages_v_cards_v_cards_parent_id_idx" ON "__pages_v_cards_v_cards" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_cards_v_cards_media_idx" ON "__pages_v_cards_v_cards" USING btree ("media_id");
  CREATE INDEX "__pages_v_cards_v_order_idx" ON "__pages_v_cards_v" USING btree ("_order");
  CREATE INDEX "__pages_v_cards_v_parent_id_idx" ON "__pages_v_cards_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_cards_v_path_idx" ON "__pages_v_cards_v" USING btree ("_path");
  CREATE INDEX "__pages_v_defs_v_items_order_idx" ON "__pages_v_defs_v_items" USING btree ("_order");
  CREATE INDEX "__pages_v_defs_v_items_parent_id_idx" ON "__pages_v_defs_v_items" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_defs_v_order_idx" ON "__pages_v_defs_v" USING btree ("_order");
  CREATE INDEX "__pages_v_defs_v_parent_id_idx" ON "__pages_v_defs_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_defs_v_path_idx" ON "__pages_v_defs_v" USING btree ("_path");
  CREATE INDEX "__pages_v_lists_v_groups_order_idx" ON "__pages_v_lists_v_groups" USING btree ("_order");
  CREATE INDEX "__pages_v_lists_v_groups_parent_id_idx" ON "__pages_v_lists_v_groups" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_lists_v_order_idx" ON "__pages_v_lists_v" USING btree ("_order");
  CREATE INDEX "__pages_v_lists_v_parent_id_idx" ON "__pages_v_lists_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_lists_v_path_idx" ON "__pages_v_lists_v" USING btree ("_path");
  CREATE INDEX "__pages_v_tbl_v_columns_order_idx" ON "__pages_v_tbl_v_columns" USING btree ("_order");
  CREATE INDEX "__pages_v_tbl_v_columns_parent_id_idx" ON "__pages_v_tbl_v_columns" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_tbl_v_rows_cells_order_idx" ON "__pages_v_tbl_v_rows_cells" USING btree ("_order");
  CREATE INDEX "__pages_v_tbl_v_rows_cells_parent_id_idx" ON "__pages_v_tbl_v_rows_cells" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_tbl_v_rows_order_idx" ON "__pages_v_tbl_v_rows" USING btree ("_order");
  CREATE INDEX "__pages_v_tbl_v_rows_parent_id_idx" ON "__pages_v_tbl_v_rows" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_tbl_v_order_idx" ON "__pages_v_tbl_v" USING btree ("_order");
  CREATE INDEX "__pages_v_tbl_v_parent_id_idx" ON "__pages_v_tbl_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_tbl_v_path_idx" ON "__pages_v_tbl_v" USING btree ("_path");
  CREATE INDEX "__pages_v_steps_v_steps_order_idx" ON "__pages_v_steps_v_steps" USING btree ("_order");
  CREATE INDEX "__pages_v_steps_v_steps_parent_id_idx" ON "__pages_v_steps_v_steps" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_steps_v_order_idx" ON "__pages_v_steps_v" USING btree ("_order");
  CREATE INDEX "__pages_v_steps_v_parent_id_idx" ON "__pages_v_steps_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_steps_v_path_idx" ON "__pages_v_steps_v" USING btree ("_path");
  CREATE INDEX "__pages_v_gallery_v_images_order_idx" ON "__pages_v_gallery_v_images" USING btree ("_order");
  CREATE INDEX "__pages_v_gallery_v_images_parent_id_idx" ON "__pages_v_gallery_v_images" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_gallery_v_images_image_idx" ON "__pages_v_gallery_v_images" USING btree ("image_id");
  CREATE INDEX "__pages_v_gallery_v_order_idx" ON "__pages_v_gallery_v" USING btree ("_order");
  CREATE INDEX "__pages_v_gallery_v_parent_id_idx" ON "__pages_v_gallery_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_gallery_v_path_idx" ON "__pages_v_gallery_v" USING btree ("_path");
  CREATE INDEX "__pages_v_people_v_people_order_idx" ON "__pages_v_people_v_people" USING btree ("_order");
  CREATE INDEX "__pages_v_people_v_people_parent_id_idx" ON "__pages_v_people_v_people" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_people_v_people_image_idx" ON "__pages_v_people_v_people" USING btree ("image_id");
  CREATE INDEX "__pages_v_people_v_order_idx" ON "__pages_v_people_v" USING btree ("_order");
  CREATE INDEX "__pages_v_people_v_parent_id_idx" ON "__pages_v_people_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_people_v_path_idx" ON "__pages_v_people_v" USING btree ("_path");
  CREATE INDEX "__pages_v_res_v_items_order_idx" ON "__pages_v_res_v_items" USING btree ("_order");
  CREATE INDEX "__pages_v_res_v_items_parent_id_idx" ON "__pages_v_res_v_items" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_res_v_items_file_idx" ON "__pages_v_res_v_items" USING btree ("file_id");
  CREATE INDEX "__pages_v_res_v_order_idx" ON "__pages_v_res_v" USING btree ("_order");
  CREATE INDEX "__pages_v_res_v_parent_id_idx" ON "__pages_v_res_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_res_v_path_idx" ON "__pages_v_res_v" USING btree ("_path");
  CREATE INDEX "__pages_v_faq_list_v_categories_order_idx" ON "__pages_v_faq_list_v_categories" USING btree ("order");
  CREATE INDEX "__pages_v_faq_list_v_categories_parent_idx" ON "__pages_v_faq_list_v_categories" USING btree ("parent_id");
  CREATE INDEX "__pages_v_faq_list_v_order_idx" ON "__pages_v_faq_list_v" USING btree ("_order");
  CREATE INDEX "__pages_v_faq_list_v_parent_id_idx" ON "__pages_v_faq_list_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_faq_list_v_path_idx" ON "__pages_v_faq_list_v" USING btree ("_path");
  CREATE INDEX "__pages_v_latest_v_order_idx" ON "__pages_v_latest_v" USING btree ("_order");
  CREATE INDEX "__pages_v_latest_v_parent_id_idx" ON "__pages_v_latest_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_latest_v_path_idx" ON "__pages_v_latest_v" USING btree ("_path");
  CREATE INDEX "__pages_v_events_list_v_order_idx" ON "__pages_v_events_list_v" USING btree ("_order");
  CREATE INDEX "__pages_v_events_list_v_parent_id_idx" ON "__pages_v_events_list_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_events_list_v_path_idx" ON "__pages_v_events_list_v" USING btree ("_path");
  CREATE INDEX "__pages_v_campuses_v_links_order_idx" ON "__pages_v_campuses_v_links" USING btree ("_order");
  CREATE INDEX "__pages_v_campuses_v_links_parent_id_idx" ON "__pages_v_campuses_v_links" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_campuses_v_order_idx" ON "__pages_v_campuses_v" USING btree ("_order");
  CREATE INDEX "__pages_v_campuses_v_parent_id_idx" ON "__pages_v_campuses_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_campuses_v_path_idx" ON "__pages_v_campuses_v" USING btree ("_path");
  CREATE INDEX "__pages_v_campuses_v_media_idx" ON "__pages_v_campuses_v" USING btree ("media_id");
  CREATE INDEX "__pages_v_adm_info_v_order_idx" ON "__pages_v_adm_info_v" USING btree ("_order");
  CREATE INDEX "__pages_v_adm_info_v_parent_id_idx" ON "__pages_v_adm_info_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_adm_info_v_path_idx" ON "__pages_v_adm_info_v" USING btree ("_path");
  CREATE INDEX "__pages_v_form_v_order_idx" ON "__pages_v_form_v" USING btree ("_order");
  CREATE INDEX "__pages_v_form_v_parent_id_idx" ON "__pages_v_form_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_form_v_path_idx" ON "__pages_v_form_v" USING btree ("_path");
  CREATE INDEX "__pages_v_notice_v_order_idx" ON "__pages_v_notice_v" USING btree ("_order");
  CREATE INDEX "__pages_v_notice_v_parent_id_idx" ON "__pages_v_notice_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_notice_v_path_idx" ON "__pages_v_notice_v" USING btree ("_path");
  CREATE INDEX "__pages_v_cta_v_links_order_idx" ON "__pages_v_cta_v_links" USING btree ("_order");
  CREATE INDEX "__pages_v_cta_v_links_parent_id_idx" ON "__pages_v_cta_v_links" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_cta_v_order_idx" ON "__pages_v_cta_v" USING btree ("_order");
  CREATE INDEX "__pages_v_cta_v_parent_id_idx" ON "__pages_v_cta_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_cta_v_path_idx" ON "__pages_v_cta_v" USING btree ("_path");
  CREATE INDEX "__pages_v_cta_v_media_idx" ON "__pages_v_cta_v" USING btree ("media_id");
  CREATE INDEX "__pages_v_marquee_v_items_order_idx" ON "__pages_v_marquee_v_items" USING btree ("_order");
  CREATE INDEX "__pages_v_marquee_v_items_parent_id_idx" ON "__pages_v_marquee_v_items" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_marquee_v_order_idx" ON "__pages_v_marquee_v" USING btree ("_order");
  CREATE INDEX "__pages_v_marquee_v_parent_id_idx" ON "__pages_v_marquee_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_marquee_v_path_idx" ON "__pages_v_marquee_v" USING btree ("_path");
  CREATE INDEX "__pages_v_reuse_v_order_idx" ON "__pages_v_reuse_v" USING btree ("_order");
  CREATE INDEX "__pages_v_reuse_v_parent_id_idx" ON "__pages_v_reuse_v" USING btree ("_parent_id");
  CREATE INDEX "__pages_v_reuse_v_path_idx" ON "__pages_v_reuse_v" USING btree ("_path");
  CREATE INDEX "__pages_v_reuse_v_section_idx" ON "__pages_v_reuse_v" USING btree ("section_id");
  CREATE INDEX "_pages_v_parent_idx" ON "_pages_v" USING btree ("parent_id");
  CREATE INDEX "_pages_v_version_meta_version_meta_image_idx" ON "_pages_v" USING btree ("version_meta_image_id");
  CREATE INDEX "_pages_v_version_version_slug_idx" ON "_pages_v" USING btree ("version_slug");
  CREATE INDEX "_pages_v_version_version_parent_idx" ON "_pages_v" USING btree ("version_parent_id");
  CREATE INDEX "_pages_v_version_version_path_idx" ON "_pages_v" USING btree ("version_path");
  CREATE INDEX "_pages_v_version_version_updated_at_idx" ON "_pages_v" USING btree ("version_updated_at");
  CREATE INDEX "_pages_v_version_version_created_at_idx" ON "_pages_v" USING btree ("version_created_at");
  CREATE INDEX "_pages_v_version_version__status_idx" ON "_pages_v" USING btree ("version__status");
  CREATE INDEX "_pages_v_created_at_idx" ON "_pages_v" USING btree ("created_at");
  CREATE INDEX "_pages_v_updated_at_idx" ON "_pages_v" USING btree ("updated_at");
  CREATE INDEX "_pages_v_latest_idx" ON "_pages_v" USING btree ("latest");
  CREATE INDEX "_pages_v_autosave_idx" ON "_pages_v" USING btree ("autosave");
  CREATE INDEX "_pages_v_rels_order_idx" ON "_pages_v_rels" USING btree ("order");
  CREATE INDEX "_pages_v_rels_parent_idx" ON "_pages_v_rels" USING btree ("parent_id");
  CREATE INDEX "_pages_v_rels_path_idx" ON "_pages_v_rels" USING btree ("path");
  CREATE INDEX "_pages_v_rels_pages_id_idx" ON "_pages_v_rels" USING btree ("pages_id");
  CREATE INDEX "_pages_v_rels_stories_id_idx" ON "_pages_v_rels" USING btree ("stories_id");
  CREATE INDEX "_pages_v_rels_programmes_id_idx" ON "_pages_v_rels" USING btree ("programmes_id");
  CREATE INDEX "_pages_v_rels_faqs_id_idx" ON "_pages_v_rels" USING btree ("faqs_id");
  CREATE INDEX "stories_hero_image_idx" ON "stories" USING btree ("hero_image_id");
  CREATE INDEX "stories_slug_idx" ON "stories" USING btree ("slug");
  CREATE INDEX "stories_meta_meta_image_idx" ON "stories" USING btree ("meta_image_id");
  CREATE INDEX "stories_updated_at_idx" ON "stories" USING btree ("updated_at");
  CREATE INDEX "stories_created_at_idx" ON "stories" USING btree ("created_at");
  CREATE INDEX "stories__status_idx" ON "stories" USING btree ("_status");
  CREATE INDEX "stories_rels_order_idx" ON "stories_rels" USING btree ("order");
  CREATE INDEX "stories_rels_parent_idx" ON "stories_rels" USING btree ("parent_id");
  CREATE INDEX "stories_rels_path_idx" ON "stories_rels" USING btree ("path");
  CREATE INDEX "stories_rels_programmes_id_idx" ON "stories_rels" USING btree ("programmes_id");
  CREATE INDEX "_stories_v_parent_idx" ON "_stories_v" USING btree ("parent_id");
  CREATE INDEX "_stories_v_version_version_hero_image_idx" ON "_stories_v" USING btree ("version_hero_image_id");
  CREATE INDEX "_stories_v_version_version_slug_idx" ON "_stories_v" USING btree ("version_slug");
  CREATE INDEX "_stories_v_version_meta_version_meta_image_idx" ON "_stories_v" USING btree ("version_meta_image_id");
  CREATE INDEX "_stories_v_version_version_updated_at_idx" ON "_stories_v" USING btree ("version_updated_at");
  CREATE INDEX "_stories_v_version_version_created_at_idx" ON "_stories_v" USING btree ("version_created_at");
  CREATE INDEX "_stories_v_version_version__status_idx" ON "_stories_v" USING btree ("version__status");
  CREATE INDEX "_stories_v_created_at_idx" ON "_stories_v" USING btree ("created_at");
  CREATE INDEX "_stories_v_updated_at_idx" ON "_stories_v" USING btree ("updated_at");
  CREATE INDEX "_stories_v_latest_idx" ON "_stories_v" USING btree ("latest");
  CREATE INDEX "_stories_v_autosave_idx" ON "_stories_v" USING btree ("autosave");
  CREATE INDEX "_stories_v_rels_order_idx" ON "_stories_v_rels" USING btree ("order");
  CREATE INDEX "_stories_v_rels_parent_idx" ON "_stories_v_rels" USING btree ("parent_id");
  CREATE INDEX "_stories_v_rels_path_idx" ON "_stories_v_rels" USING btree ("path");
  CREATE INDEX "_stories_v_rels_programmes_id_idx" ON "_stories_v_rels" USING btree ("programmes_id");
  CREATE INDEX "events_slug_idx" ON "events" USING btree ("slug");
  CREATE INDEX "events_updated_at_idx" ON "events" USING btree ("updated_at");
  CREATE INDEX "events_created_at_idx" ON "events" USING btree ("created_at");
  CREATE INDEX "events__status_idx" ON "events" USING btree ("_status");
  CREATE INDEX "_events_v_parent_idx" ON "_events_v" USING btree ("parent_id");
  CREATE INDEX "_events_v_version_version_slug_idx" ON "_events_v" USING btree ("version_slug");
  CREATE INDEX "_events_v_version_version_updated_at_idx" ON "_events_v" USING btree ("version_updated_at");
  CREATE INDEX "_events_v_version_version_created_at_idx" ON "_events_v" USING btree ("version_created_at");
  CREATE INDEX "_events_v_version_version__status_idx" ON "_events_v" USING btree ("version__status");
  CREATE INDEX "_events_v_created_at_idx" ON "_events_v" USING btree ("created_at");
  CREATE INDEX "_events_v_updated_at_idx" ON "_events_v" USING btree ("updated_at");
  CREATE INDEX "_events_v_latest_idx" ON "_events_v" USING btree ("latest");
  CREATE INDEX "programmes_highlights_order_idx" ON "programmes_highlights" USING btree ("_order");
  CREATE INDEX "programmes_highlights_parent_id_idx" ON "programmes_highlights" USING btree ("_parent_id");
  CREATE INDEX "programmes_image_idx" ON "programmes" USING btree ("image_id");
  CREATE INDEX "programmes_page_idx" ON "programmes" USING btree ("page_id");
  CREATE INDEX "programmes_slug_idx" ON "programmes" USING btree ("slug");
  CREATE INDEX "programmes_updated_at_idx" ON "programmes" USING btree ("updated_at");
  CREATE INDEX "programmes_created_at_idx" ON "programmes" USING btree ("created_at");
  CREATE INDEX "programmes__status_idx" ON "programmes" USING btree ("_status");
  CREATE INDEX "_programmes_v_version_highlights_order_idx" ON "_programmes_v_version_highlights" USING btree ("_order");
  CREATE INDEX "_programmes_v_version_highlights_parent_id_idx" ON "_programmes_v_version_highlights" USING btree ("_parent_id");
  CREATE INDEX "_programmes_v_parent_idx" ON "_programmes_v" USING btree ("parent_id");
  CREATE INDEX "_programmes_v_version_version_image_idx" ON "_programmes_v" USING btree ("version_image_id");
  CREATE INDEX "_programmes_v_version_version_page_idx" ON "_programmes_v" USING btree ("version_page_id");
  CREATE INDEX "_programmes_v_version_version_slug_idx" ON "_programmes_v" USING btree ("version_slug");
  CREATE INDEX "_programmes_v_version_version_updated_at_idx" ON "_programmes_v" USING btree ("version_updated_at");
  CREATE INDEX "_programmes_v_version_version_created_at_idx" ON "_programmes_v" USING btree ("version_created_at");
  CREATE INDEX "_programmes_v_version_version__status_idx" ON "_programmes_v" USING btree ("version__status");
  CREATE INDEX "_programmes_v_created_at_idx" ON "_programmes_v" USING btree ("created_at");
  CREATE INDEX "_programmes_v_updated_at_idx" ON "_programmes_v" USING btree ("updated_at");
  CREATE INDEX "_programmes_v_latest_idx" ON "_programmes_v" USING btree ("latest");
  CREATE INDEX "faqs_updated_at_idx" ON "faqs" USING btree ("updated_at");
  CREATE INDEX "faqs_created_at_idx" ON "faqs" USING btree ("created_at");
  CREATE INDEX "faqs__status_idx" ON "faqs" USING btree ("_status");
  CREATE INDEX "_faqs_v_parent_idx" ON "_faqs_v" USING btree ("parent_id");
  CREATE INDEX "_faqs_v_version_version_updated_at_idx" ON "_faqs_v" USING btree ("version_updated_at");
  CREATE INDEX "_faqs_v_version_version_created_at_idx" ON "_faqs_v" USING btree ("version_created_at");
  CREATE INDEX "_faqs_v_version_version__status_idx" ON "_faqs_v" USING btree ("version__status");
  CREATE INDEX "_faqs_v_created_at_idx" ON "_faqs_v" USING btree ("created_at");
  CREATE INDEX "_faqs_v_updated_at_idx" ON "_faqs_v" USING btree ("updated_at");
  CREATE INDEX "_faqs_v_latest_idx" ON "_faqs_v" USING btree ("latest");
  CREATE INDEX "media_updated_at_idx" ON "media" USING btree ("updated_at");
  CREATE INDEX "media_created_at_idx" ON "media" USING btree ("created_at");
  CREATE UNIQUE INDEX "media_filename_idx" ON "media" USING btree ("filename");
  CREATE INDEX "media_sizes_thumbnail_sizes_thumbnail_filename_idx" ON "media" USING btree ("sizes_thumbnail_filename");
  CREATE INDEX "media_sizes_card_sizes_card_filename_idx" ON "media" USING btree ("sizes_card_filename");
  CREATE INDEX "media_sizes_feature_sizes_feature_filename_idx" ON "media" USING btree ("sizes_feature_filename");
  CREATE INDEX "media_sizes_hero_sizes_hero_filename_idx" ON "media" USING btree ("sizes_hero_filename");
  CREATE INDEX "media_sizes_og_sizes_og_filename_idx" ON "media" USING btree ("sizes_og_filename");
  CREATE INDEX "reusable_sections_hero_links_order_idx" ON "reusable_sections_hero_links" USING btree ("_order");
  CREATE INDEX "reusable_sections_hero_links_parent_id_idx" ON "reusable_sections_hero_links" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_hero_slides_order_idx" ON "reusable_sections_hero_slides" USING btree ("_order");
  CREATE INDEX "reusable_sections_hero_slides_parent_id_idx" ON "reusable_sections_hero_slides" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_hero_slides_image_idx" ON "reusable_sections_hero_slides" USING btree ("image_id");
  CREATE INDEX "reusable_sections_hero_order_idx" ON "reusable_sections_hero" USING btree ("_order");
  CREATE INDEX "reusable_sections_hero_parent_id_idx" ON "reusable_sections_hero" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_hero_path_idx" ON "reusable_sections_hero" USING btree ("_path");
  CREATE INDEX "reusable_sections_hero_media_idx" ON "reusable_sections_hero" USING btree ("media_id");
  CREATE INDEX "reusable_sections_text_order_idx" ON "reusable_sections_text" USING btree ("_order");
  CREATE INDEX "reusable_sections_text_parent_id_idx" ON "reusable_sections_text" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_text_path_idx" ON "reusable_sections_text" USING btree ("_path");
  CREATE INDEX "reusable_sections_text_portrait_idx" ON "reusable_sections_text" USING btree ("portrait_id");
  CREATE INDEX "reusable_sections_split_links_order_idx" ON "reusable_sections_split_links" USING btree ("_order");
  CREATE INDEX "reusable_sections_split_links_parent_id_idx" ON "reusable_sections_split_links" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_split_order_idx" ON "reusable_sections_split" USING btree ("_order");
  CREATE INDEX "reusable_sections_split_parent_id_idx" ON "reusable_sections_split" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_split_path_idx" ON "reusable_sections_split" USING btree ("_path");
  CREATE INDEX "reusable_sections_split_media_idx" ON "reusable_sections_split" USING btree ("media_id");
  CREATE INDEX "reusable_sections_facts_facts_order_idx" ON "reusable_sections_facts_facts" USING btree ("_order");
  CREATE INDEX "reusable_sections_facts_facts_parent_id_idx" ON "reusable_sections_facts_facts" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_facts_order_idx" ON "reusable_sections_facts" USING btree ("_order");
  CREATE INDEX "reusable_sections_facts_parent_id_idx" ON "reusable_sections_facts" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_facts_path_idx" ON "reusable_sections_facts" USING btree ("_path");
  CREATE INDEX "reusable_sections_journey_order_idx" ON "reusable_sections_journey" USING btree ("_order");
  CREATE INDEX "reusable_sections_journey_parent_id_idx" ON "reusable_sections_journey" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_journey_path_idx" ON "reusable_sections_journey" USING btree ("_path");
  CREATE INDEX "reusable_sections_mosaic_items_order_idx" ON "reusable_sections_mosaic_items" USING btree ("_order");
  CREATE INDEX "reusable_sections_mosaic_items_parent_id_idx" ON "reusable_sections_mosaic_items" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_mosaic_items_media_idx" ON "reusable_sections_mosaic_items" USING btree ("media_id");
  CREATE INDEX "reusable_sections_mosaic_order_idx" ON "reusable_sections_mosaic" USING btree ("_order");
  CREATE INDEX "reusable_sections_mosaic_parent_id_idx" ON "reusable_sections_mosaic" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_mosaic_path_idx" ON "reusable_sections_mosaic" USING btree ("_path");
  CREATE INDEX "reusable_sections_kinetic_words_order_idx" ON "reusable_sections_kinetic_words" USING btree ("_order");
  CREATE INDEX "reusable_sections_kinetic_words_parent_id_idx" ON "reusable_sections_kinetic_words" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_kinetic_order_idx" ON "reusable_sections_kinetic" USING btree ("_order");
  CREATE INDEX "reusable_sections_kinetic_parent_id_idx" ON "reusable_sections_kinetic" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_kinetic_path_idx" ON "reusable_sections_kinetic" USING btree ("_path");
  CREATE INDEX "reusable_sections_cards_cards_order_idx" ON "reusable_sections_cards_cards" USING btree ("_order");
  CREATE INDEX "reusable_sections_cards_cards_parent_id_idx" ON "reusable_sections_cards_cards" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_cards_cards_media_idx" ON "reusable_sections_cards_cards" USING btree ("media_id");
  CREATE INDEX "reusable_sections_cards_order_idx" ON "reusable_sections_cards" USING btree ("_order");
  CREATE INDEX "reusable_sections_cards_parent_id_idx" ON "reusable_sections_cards" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_cards_path_idx" ON "reusable_sections_cards" USING btree ("_path");
  CREATE INDEX "reusable_sections_defs_items_order_idx" ON "reusable_sections_defs_items" USING btree ("_order");
  CREATE INDEX "reusable_sections_defs_items_parent_id_idx" ON "reusable_sections_defs_items" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_defs_order_idx" ON "reusable_sections_defs" USING btree ("_order");
  CREATE INDEX "reusable_sections_defs_parent_id_idx" ON "reusable_sections_defs" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_defs_path_idx" ON "reusable_sections_defs" USING btree ("_path");
  CREATE INDEX "reusable_sections_lists_groups_order_idx" ON "reusable_sections_lists_groups" USING btree ("_order");
  CREATE INDEX "reusable_sections_lists_groups_parent_id_idx" ON "reusable_sections_lists_groups" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_lists_order_idx" ON "reusable_sections_lists" USING btree ("_order");
  CREATE INDEX "reusable_sections_lists_parent_id_idx" ON "reusable_sections_lists" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_lists_path_idx" ON "reusable_sections_lists" USING btree ("_path");
  CREATE INDEX "reusable_sections_tbl_columns_order_idx" ON "reusable_sections_tbl_columns" USING btree ("_order");
  CREATE INDEX "reusable_sections_tbl_columns_parent_id_idx" ON "reusable_sections_tbl_columns" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_tbl_rows_cells_order_idx" ON "reusable_sections_tbl_rows_cells" USING btree ("_order");
  CREATE INDEX "reusable_sections_tbl_rows_cells_parent_id_idx" ON "reusable_sections_tbl_rows_cells" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_tbl_rows_order_idx" ON "reusable_sections_tbl_rows" USING btree ("_order");
  CREATE INDEX "reusable_sections_tbl_rows_parent_id_idx" ON "reusable_sections_tbl_rows" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_tbl_order_idx" ON "reusable_sections_tbl" USING btree ("_order");
  CREATE INDEX "reusable_sections_tbl_parent_id_idx" ON "reusable_sections_tbl" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_tbl_path_idx" ON "reusable_sections_tbl" USING btree ("_path");
  CREATE INDEX "reusable_sections_steps_steps_order_idx" ON "reusable_sections_steps_steps" USING btree ("_order");
  CREATE INDEX "reusable_sections_steps_steps_parent_id_idx" ON "reusable_sections_steps_steps" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_steps_order_idx" ON "reusable_sections_steps" USING btree ("_order");
  CREATE INDEX "reusable_sections_steps_parent_id_idx" ON "reusable_sections_steps" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_steps_path_idx" ON "reusable_sections_steps" USING btree ("_path");
  CREATE INDEX "reusable_sections_gallery_images_order_idx" ON "reusable_sections_gallery_images" USING btree ("_order");
  CREATE INDEX "reusable_sections_gallery_images_parent_id_idx" ON "reusable_sections_gallery_images" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_gallery_images_image_idx" ON "reusable_sections_gallery_images" USING btree ("image_id");
  CREATE INDEX "reusable_sections_gallery_order_idx" ON "reusable_sections_gallery" USING btree ("_order");
  CREATE INDEX "reusable_sections_gallery_parent_id_idx" ON "reusable_sections_gallery" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_gallery_path_idx" ON "reusable_sections_gallery" USING btree ("_path");
  CREATE INDEX "reusable_sections_people_people_order_idx" ON "reusable_sections_people_people" USING btree ("_order");
  CREATE INDEX "reusable_sections_people_people_parent_id_idx" ON "reusable_sections_people_people" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_people_people_image_idx" ON "reusable_sections_people_people" USING btree ("image_id");
  CREATE INDEX "reusable_sections_people_order_idx" ON "reusable_sections_people" USING btree ("_order");
  CREATE INDEX "reusable_sections_people_parent_id_idx" ON "reusable_sections_people" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_people_path_idx" ON "reusable_sections_people" USING btree ("_path");
  CREATE INDEX "reusable_sections_res_items_order_idx" ON "reusable_sections_res_items" USING btree ("_order");
  CREATE INDEX "reusable_sections_res_items_parent_id_idx" ON "reusable_sections_res_items" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_res_items_file_idx" ON "reusable_sections_res_items" USING btree ("file_id");
  CREATE INDEX "reusable_sections_res_order_idx" ON "reusable_sections_res" USING btree ("_order");
  CREATE INDEX "reusable_sections_res_parent_id_idx" ON "reusable_sections_res" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_res_path_idx" ON "reusable_sections_res" USING btree ("_path");
  CREATE INDEX "reusable_sections_faq_list_categories_order_idx" ON "reusable_sections_faq_list_categories" USING btree ("order");
  CREATE INDEX "reusable_sections_faq_list_categories_parent_idx" ON "reusable_sections_faq_list_categories" USING btree ("parent_id");
  CREATE INDEX "reusable_sections_faq_list_order_idx" ON "reusable_sections_faq_list" USING btree ("_order");
  CREATE INDEX "reusable_sections_faq_list_parent_id_idx" ON "reusable_sections_faq_list" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_faq_list_path_idx" ON "reusable_sections_faq_list" USING btree ("_path");
  CREATE INDEX "reusable_sections_latest_order_idx" ON "reusable_sections_latest" USING btree ("_order");
  CREATE INDEX "reusable_sections_latest_parent_id_idx" ON "reusable_sections_latest" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_latest_path_idx" ON "reusable_sections_latest" USING btree ("_path");
  CREATE INDEX "reusable_sections_events_list_order_idx" ON "reusable_sections_events_list" USING btree ("_order");
  CREATE INDEX "reusable_sections_events_list_parent_id_idx" ON "reusable_sections_events_list" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_events_list_path_idx" ON "reusable_sections_events_list" USING btree ("_path");
  CREATE INDEX "reusable_sections_campuses_links_order_idx" ON "reusable_sections_campuses_links" USING btree ("_order");
  CREATE INDEX "reusable_sections_campuses_links_parent_id_idx" ON "reusable_sections_campuses_links" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_campuses_order_idx" ON "reusable_sections_campuses" USING btree ("_order");
  CREATE INDEX "reusable_sections_campuses_parent_id_idx" ON "reusable_sections_campuses" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_campuses_path_idx" ON "reusable_sections_campuses" USING btree ("_path");
  CREATE INDEX "reusable_sections_campuses_media_idx" ON "reusable_sections_campuses" USING btree ("media_id");
  CREATE INDEX "reusable_sections_adm_info_order_idx" ON "reusable_sections_adm_info" USING btree ("_order");
  CREATE INDEX "reusable_sections_adm_info_parent_id_idx" ON "reusable_sections_adm_info" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_adm_info_path_idx" ON "reusable_sections_adm_info" USING btree ("_path");
  CREATE INDEX "reusable_sections_form_order_idx" ON "reusable_sections_form" USING btree ("_order");
  CREATE INDEX "reusable_sections_form_parent_id_idx" ON "reusable_sections_form" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_form_path_idx" ON "reusable_sections_form" USING btree ("_path");
  CREATE INDEX "reusable_sections_notice_order_idx" ON "reusable_sections_notice" USING btree ("_order");
  CREATE INDEX "reusable_sections_notice_parent_id_idx" ON "reusable_sections_notice" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_notice_path_idx" ON "reusable_sections_notice" USING btree ("_path");
  CREATE INDEX "reusable_sections_cta_links_order_idx" ON "reusable_sections_cta_links" USING btree ("_order");
  CREATE INDEX "reusable_sections_cta_links_parent_id_idx" ON "reusable_sections_cta_links" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_cta_order_idx" ON "reusable_sections_cta" USING btree ("_order");
  CREATE INDEX "reusable_sections_cta_parent_id_idx" ON "reusable_sections_cta" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_cta_path_idx" ON "reusable_sections_cta" USING btree ("_path");
  CREATE INDEX "reusable_sections_cta_media_idx" ON "reusable_sections_cta" USING btree ("media_id");
  CREATE INDEX "reusable_sections_marquee_items_order_idx" ON "reusable_sections_marquee_items" USING btree ("_order");
  CREATE INDEX "reusable_sections_marquee_items_parent_id_idx" ON "reusable_sections_marquee_items" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_marquee_order_idx" ON "reusable_sections_marquee" USING btree ("_order");
  CREATE INDEX "reusable_sections_marquee_parent_id_idx" ON "reusable_sections_marquee" USING btree ("_parent_id");
  CREATE INDEX "reusable_sections_marquee_path_idx" ON "reusable_sections_marquee" USING btree ("_path");
  CREATE INDEX "reusable_sections_updated_at_idx" ON "reusable_sections" USING btree ("updated_at");
  CREATE INDEX "reusable_sections_created_at_idx" ON "reusable_sections" USING btree ("created_at");
  CREATE INDEX "reusable_sections_rels_order_idx" ON "reusable_sections_rels" USING btree ("order");
  CREATE INDEX "reusable_sections_rels_parent_idx" ON "reusable_sections_rels" USING btree ("parent_id");
  CREATE INDEX "reusable_sections_rels_path_idx" ON "reusable_sections_rels" USING btree ("path");
  CREATE INDEX "reusable_sections_rels_pages_id_idx" ON "reusable_sections_rels" USING btree ("pages_id");
  CREATE INDEX "reusable_sections_rels_stories_id_idx" ON "reusable_sections_rels" USING btree ("stories_id");
  CREATE INDEX "reusable_sections_rels_programmes_id_idx" ON "reusable_sections_rels" USING btree ("programmes_id");
  CREATE INDEX "reusable_sections_rels_faqs_id_idx" ON "reusable_sections_rels" USING btree ("faqs_id");
  CREATE INDEX "enquiries_updated_at_idx" ON "enquiries" USING btree ("updated_at");
  CREATE INDEX "enquiries_created_at_idx" ON "enquiries" USING btree ("created_at");
  CREATE INDEX "users_roles_order_idx" ON "users_roles" USING btree ("order");
  CREATE INDEX "users_roles_parent_idx" ON "users_roles" USING btree ("parent_id");
  CREATE INDEX "users_sessions_order_idx" ON "users_sessions" USING btree ("_order");
  CREATE INDEX "users_sessions_parent_id_idx" ON "users_sessions" USING btree ("_parent_id");
  CREATE INDEX "users_updated_at_idx" ON "users" USING btree ("updated_at");
  CREATE INDEX "users_created_at_idx" ON "users" USING btree ("created_at");
  CREATE UNIQUE INDEX "users_email_idx" ON "users" USING btree ("email");
  CREATE UNIQUE INDEX "payload_kv_key_idx" ON "payload_kv" USING btree ("key");
  CREATE INDEX "payload_locked_documents_global_slug_idx" ON "payload_locked_documents" USING btree ("global_slug");
  CREATE INDEX "payload_locked_documents_updated_at_idx" ON "payload_locked_documents" USING btree ("updated_at");
  CREATE INDEX "payload_locked_documents_created_at_idx" ON "payload_locked_documents" USING btree ("created_at");
  CREATE INDEX "payload_locked_documents_rels_order_idx" ON "payload_locked_documents_rels" USING btree ("order");
  CREATE INDEX "payload_locked_documents_rels_parent_idx" ON "payload_locked_documents_rels" USING btree ("parent_id");
  CREATE INDEX "payload_locked_documents_rels_path_idx" ON "payload_locked_documents_rels" USING btree ("path");
  CREATE INDEX "payload_locked_documents_rels_pages_id_idx" ON "payload_locked_documents_rels" USING btree ("pages_id");
  CREATE INDEX "payload_locked_documents_rels_stories_id_idx" ON "payload_locked_documents_rels" USING btree ("stories_id");
  CREATE INDEX "payload_locked_documents_rels_events_id_idx" ON "payload_locked_documents_rels" USING btree ("events_id");
  CREATE INDEX "payload_locked_documents_rels_programmes_id_idx" ON "payload_locked_documents_rels" USING btree ("programmes_id");
  CREATE INDEX "payload_locked_documents_rels_faqs_id_idx" ON "payload_locked_documents_rels" USING btree ("faqs_id");
  CREATE INDEX "payload_locked_documents_rels_media_id_idx" ON "payload_locked_documents_rels" USING btree ("media_id");
  CREATE INDEX "payload_locked_documents_rels_reusable_sections_id_idx" ON "payload_locked_documents_rels" USING btree ("reusable_sections_id");
  CREATE INDEX "payload_locked_documents_rels_enquiries_id_idx" ON "payload_locked_documents_rels" USING btree ("enquiries_id");
  CREATE INDEX "payload_locked_documents_rels_users_id_idx" ON "payload_locked_documents_rels" USING btree ("users_id");
  CREATE INDEX "payload_preferences_key_idx" ON "payload_preferences" USING btree ("key");
  CREATE INDEX "payload_preferences_updated_at_idx" ON "payload_preferences" USING btree ("updated_at");
  CREATE INDEX "payload_preferences_created_at_idx" ON "payload_preferences" USING btree ("created_at");
  CREATE INDEX "payload_preferences_rels_order_idx" ON "payload_preferences_rels" USING btree ("order");
  CREATE INDEX "payload_preferences_rels_parent_idx" ON "payload_preferences_rels" USING btree ("parent_id");
  CREATE INDEX "payload_preferences_rels_path_idx" ON "payload_preferences_rels" USING btree ("path");
  CREATE INDEX "payload_preferences_rels_users_id_idx" ON "payload_preferences_rels" USING btree ("users_id");
  CREATE INDEX "payload_migrations_updated_at_idx" ON "payload_migrations" USING btree ("updated_at");
  CREATE INDEX "payload_migrations_created_at_idx" ON "payload_migrations" USING btree ("created_at");
  CREATE INDEX "site_settings_campuses_phones_order_idx" ON "site_settings_campuses_phones" USING btree ("_order");
  CREATE INDEX "site_settings_campuses_phones_parent_id_idx" ON "site_settings_campuses_phones" USING btree ("_parent_id");
  CREATE INDEX "site_settings_campuses_order_idx" ON "site_settings_campuses" USING btree ("_order");
  CREATE INDEX "site_settings_campuses_parent_id_idx" ON "site_settings_campuses" USING btree ("_parent_id");
  CREATE INDEX "site_settings_campuses_image_idx" ON "site_settings_campuses" USING btree ("image_id");
  CREATE INDEX "site_settings_social_order_idx" ON "site_settings_social" USING btree ("_order");
  CREATE INDEX "site_settings_social_parent_id_idx" ON "site_settings_social" USING btree ("_parent_id");
  CREATE INDEX "site_settings_default_share_image_idx" ON "site_settings" USING btree ("default_share_image_id");
  CREATE INDEX "site_settings_rels_order_idx" ON "site_settings_rels" USING btree ("order");
  CREATE INDEX "site_settings_rels_parent_idx" ON "site_settings_rels" USING btree ("parent_id");
  CREATE INDEX "site_settings_rels_path_idx" ON "site_settings_rels" USING btree ("path");
  CREATE INDEX "site_settings_rels_pages_id_idx" ON "site_settings_rels" USING btree ("pages_id");
  CREATE INDEX "site_settings_rels_stories_id_idx" ON "site_settings_rels" USING btree ("stories_id");
  CREATE INDEX "header_nav_groups_items_order_idx" ON "header_nav_groups_items" USING btree ("_order");
  CREATE INDEX "header_nav_groups_items_parent_id_idx" ON "header_nav_groups_items" USING btree ("_parent_id");
  CREATE INDEX "header_nav_groups_order_idx" ON "header_nav_groups" USING btree ("_order");
  CREATE INDEX "header_nav_groups_parent_id_idx" ON "header_nav_groups" USING btree ("_parent_id");
  CREATE INDEX "header_utility_links_order_idx" ON "header_utility_links" USING btree ("_order");
  CREATE INDEX "header_utility_links_parent_id_idx" ON "header_utility_links" USING btree ("_parent_id");
  CREATE INDEX "header_rels_order_idx" ON "header_rels" USING btree ("order");
  CREATE INDEX "header_rels_parent_idx" ON "header_rels" USING btree ("parent_id");
  CREATE INDEX "header_rels_path_idx" ON "header_rels" USING btree ("path");
  CREATE INDEX "header_rels_pages_id_idx" ON "header_rels" USING btree ("pages_id");
  CREATE INDEX "header_rels_stories_id_idx" ON "header_rels" USING btree ("stories_id");
  CREATE INDEX "footer_columns_links_order_idx" ON "footer_columns_links" USING btree ("_order");
  CREATE INDEX "footer_columns_links_parent_id_idx" ON "footer_columns_links" USING btree ("_parent_id");
  CREATE INDEX "footer_columns_order_idx" ON "footer_columns" USING btree ("_order");
  CREATE INDEX "footer_columns_parent_id_idx" ON "footer_columns" USING btree ("_parent_id");
  CREATE INDEX "footer_legal_links_order_idx" ON "footer_legal_links" USING btree ("_order");
  CREATE INDEX "footer_legal_links_parent_id_idx" ON "footer_legal_links" USING btree ("_parent_id");
  CREATE INDEX "footer_memberships_order_idx" ON "footer_memberships" USING btree ("_order");
  CREATE INDEX "footer_memberships_parent_id_idx" ON "footer_memberships" USING btree ("_parent_id");
  CREATE INDEX "footer_rels_order_idx" ON "footer_rels" USING btree ("order");
  CREATE INDEX "footer_rels_parent_idx" ON "footer_rels" USING btree ("parent_id");
  CREATE INDEX "footer_rels_path_idx" ON "footer_rels" USING btree ("path");
  CREATE INDEX "footer_rels_pages_id_idx" ON "footer_rels" USING btree ("pages_id");
  CREATE INDEX "footer_rels_stories_id_idx" ON "footer_rels" USING btree ("stories_id");
  CREATE INDEX "admissions_fee_table_order_idx" ON "admissions_fee_table" USING btree ("_order");
  CREATE INDEX "admissions_fee_table_parent_id_idx" ON "admissions_fee_table" USING btree ("_parent_id");
  CREATE INDEX "admissions_required_documents_order_idx" ON "admissions_required_documents" USING btree ("_order");
  CREATE INDEX "admissions_required_documents_parent_id_idx" ON "admissions_required_documents" USING btree ("_parent_id");
  CREATE INDEX "admissions__status_idx" ON "admissions" USING btree ("_status");
  CREATE INDEX "_admissions_v_version_fee_table_order_idx" ON "_admissions_v_version_fee_table" USING btree ("_order");
  CREATE INDEX "_admissions_v_version_fee_table_parent_id_idx" ON "_admissions_v_version_fee_table" USING btree ("_parent_id");
  CREATE INDEX "_admissions_v_version_required_documents_order_idx" ON "_admissions_v_version_required_documents" USING btree ("_order");
  CREATE INDEX "_admissions_v_version_required_documents_parent_id_idx" ON "_admissions_v_version_required_documents" USING btree ("_parent_id");
  CREATE INDEX "_admissions_v_version_version__status_idx" ON "_admissions_v" USING btree ("version__status");
  CREATE INDEX "_admissions_v_created_at_idx" ON "_admissions_v" USING btree ("created_at");
  CREATE INDEX "_admissions_v_updated_at_idx" ON "_admissions_v" USING btree ("updated_at");
  CREATE INDEX "_admissions_v_latest_idx" ON "_admissions_v" USING btree ("latest");`)
}

export async function down({ db, payload, req }: MigrateDownArgs): Promise<void> {
  await db.execute(sql`
   DROP TABLE "pages_hero_links" CASCADE;
  DROP TABLE "pages_hero_slides" CASCADE;
  DROP TABLE "pages_hero" CASCADE;
  DROP TABLE "pages_text" CASCADE;
  DROP TABLE "pages_split_links" CASCADE;
  DROP TABLE "pages_split" CASCADE;
  DROP TABLE "pages_facts_facts" CASCADE;
  DROP TABLE "pages_facts" CASCADE;
  DROP TABLE "pages_journey" CASCADE;
  DROP TABLE "pages_mosaic_items" CASCADE;
  DROP TABLE "pages_mosaic" CASCADE;
  DROP TABLE "pages_kinetic_words" CASCADE;
  DROP TABLE "pages_kinetic" CASCADE;
  DROP TABLE "pages_cards_cards" CASCADE;
  DROP TABLE "pages_cards" CASCADE;
  DROP TABLE "pages_defs_items" CASCADE;
  DROP TABLE "pages_defs" CASCADE;
  DROP TABLE "pages_lists_groups" CASCADE;
  DROP TABLE "pages_lists" CASCADE;
  DROP TABLE "pages_tbl_columns" CASCADE;
  DROP TABLE "pages_tbl_rows_cells" CASCADE;
  DROP TABLE "pages_tbl_rows" CASCADE;
  DROP TABLE "pages_tbl" CASCADE;
  DROP TABLE "pages_steps_steps" CASCADE;
  DROP TABLE "pages_steps" CASCADE;
  DROP TABLE "pages_gallery_images" CASCADE;
  DROP TABLE "pages_gallery" CASCADE;
  DROP TABLE "pages_people_people" CASCADE;
  DROP TABLE "pages_people" CASCADE;
  DROP TABLE "pages_res_items" CASCADE;
  DROP TABLE "pages_res" CASCADE;
  DROP TABLE "pages_faq_list_categories" CASCADE;
  DROP TABLE "pages_faq_list" CASCADE;
  DROP TABLE "pages_latest" CASCADE;
  DROP TABLE "pages_events_list" CASCADE;
  DROP TABLE "pages_campuses_links" CASCADE;
  DROP TABLE "pages_campuses" CASCADE;
  DROP TABLE "pages_adm_info" CASCADE;
  DROP TABLE "pages_form" CASCADE;
  DROP TABLE "pages_notice" CASCADE;
  DROP TABLE "pages_cta_links" CASCADE;
  DROP TABLE "pages_cta" CASCADE;
  DROP TABLE "pages_marquee_items" CASCADE;
  DROP TABLE "pages_marquee" CASCADE;
  DROP TABLE "pages_reuse" CASCADE;
  DROP TABLE "pages" CASCADE;
  DROP TABLE "pages_rels" CASCADE;
  DROP TABLE "__pages_v_hero_v_links" CASCADE;
  DROP TABLE "__pages_v_hero_v_slides" CASCADE;
  DROP TABLE "__pages_v_hero_v" CASCADE;
  DROP TABLE "__pages_v_text_v" CASCADE;
  DROP TABLE "__pages_v_split_v_links" CASCADE;
  DROP TABLE "__pages_v_split_v" CASCADE;
  DROP TABLE "__pages_v_facts_v_facts" CASCADE;
  DROP TABLE "__pages_v_facts_v" CASCADE;
  DROP TABLE "__pages_v_journey_v" CASCADE;
  DROP TABLE "__pages_v_mosaic_v_items" CASCADE;
  DROP TABLE "__pages_v_mosaic_v" CASCADE;
  DROP TABLE "__pages_v_kinetic_v_words" CASCADE;
  DROP TABLE "__pages_v_kinetic_v" CASCADE;
  DROP TABLE "__pages_v_cards_v_cards" CASCADE;
  DROP TABLE "__pages_v_cards_v" CASCADE;
  DROP TABLE "__pages_v_defs_v_items" CASCADE;
  DROP TABLE "__pages_v_defs_v" CASCADE;
  DROP TABLE "__pages_v_lists_v_groups" CASCADE;
  DROP TABLE "__pages_v_lists_v" CASCADE;
  DROP TABLE "__pages_v_tbl_v_columns" CASCADE;
  DROP TABLE "__pages_v_tbl_v_rows_cells" CASCADE;
  DROP TABLE "__pages_v_tbl_v_rows" CASCADE;
  DROP TABLE "__pages_v_tbl_v" CASCADE;
  DROP TABLE "__pages_v_steps_v_steps" CASCADE;
  DROP TABLE "__pages_v_steps_v" CASCADE;
  DROP TABLE "__pages_v_gallery_v_images" CASCADE;
  DROP TABLE "__pages_v_gallery_v" CASCADE;
  DROP TABLE "__pages_v_people_v_people" CASCADE;
  DROP TABLE "__pages_v_people_v" CASCADE;
  DROP TABLE "__pages_v_res_v_items" CASCADE;
  DROP TABLE "__pages_v_res_v" CASCADE;
  DROP TABLE "__pages_v_faq_list_v_categories" CASCADE;
  DROP TABLE "__pages_v_faq_list_v" CASCADE;
  DROP TABLE "__pages_v_latest_v" CASCADE;
  DROP TABLE "__pages_v_events_list_v" CASCADE;
  DROP TABLE "__pages_v_campuses_v_links" CASCADE;
  DROP TABLE "__pages_v_campuses_v" CASCADE;
  DROP TABLE "__pages_v_adm_info_v" CASCADE;
  DROP TABLE "__pages_v_form_v" CASCADE;
  DROP TABLE "__pages_v_notice_v" CASCADE;
  DROP TABLE "__pages_v_cta_v_links" CASCADE;
  DROP TABLE "__pages_v_cta_v" CASCADE;
  DROP TABLE "__pages_v_marquee_v_items" CASCADE;
  DROP TABLE "__pages_v_marquee_v" CASCADE;
  DROP TABLE "__pages_v_reuse_v" CASCADE;
  DROP TABLE "_pages_v" CASCADE;
  DROP TABLE "_pages_v_rels" CASCADE;
  DROP TABLE "stories" CASCADE;
  DROP TABLE "stories_rels" CASCADE;
  DROP TABLE "_stories_v" CASCADE;
  DROP TABLE "_stories_v_rels" CASCADE;
  DROP TABLE "events" CASCADE;
  DROP TABLE "_events_v" CASCADE;
  DROP TABLE "programmes_highlights" CASCADE;
  DROP TABLE "programmes" CASCADE;
  DROP TABLE "_programmes_v_version_highlights" CASCADE;
  DROP TABLE "_programmes_v" CASCADE;
  DROP TABLE "faqs" CASCADE;
  DROP TABLE "_faqs_v" CASCADE;
  DROP TABLE "media" CASCADE;
  DROP TABLE "reusable_sections_hero_links" CASCADE;
  DROP TABLE "reusable_sections_hero_slides" CASCADE;
  DROP TABLE "reusable_sections_hero" CASCADE;
  DROP TABLE "reusable_sections_text" CASCADE;
  DROP TABLE "reusable_sections_split_links" CASCADE;
  DROP TABLE "reusable_sections_split" CASCADE;
  DROP TABLE "reusable_sections_facts_facts" CASCADE;
  DROP TABLE "reusable_sections_facts" CASCADE;
  DROP TABLE "reusable_sections_journey" CASCADE;
  DROP TABLE "reusable_sections_mosaic_items" CASCADE;
  DROP TABLE "reusable_sections_mosaic" CASCADE;
  DROP TABLE "reusable_sections_kinetic_words" CASCADE;
  DROP TABLE "reusable_sections_kinetic" CASCADE;
  DROP TABLE "reusable_sections_cards_cards" CASCADE;
  DROP TABLE "reusable_sections_cards" CASCADE;
  DROP TABLE "reusable_sections_defs_items" CASCADE;
  DROP TABLE "reusable_sections_defs" CASCADE;
  DROP TABLE "reusable_sections_lists_groups" CASCADE;
  DROP TABLE "reusable_sections_lists" CASCADE;
  DROP TABLE "reusable_sections_tbl_columns" CASCADE;
  DROP TABLE "reusable_sections_tbl_rows_cells" CASCADE;
  DROP TABLE "reusable_sections_tbl_rows" CASCADE;
  DROP TABLE "reusable_sections_tbl" CASCADE;
  DROP TABLE "reusable_sections_steps_steps" CASCADE;
  DROP TABLE "reusable_sections_steps" CASCADE;
  DROP TABLE "reusable_sections_gallery_images" CASCADE;
  DROP TABLE "reusable_sections_gallery" CASCADE;
  DROP TABLE "reusable_sections_people_people" CASCADE;
  DROP TABLE "reusable_sections_people" CASCADE;
  DROP TABLE "reusable_sections_res_items" CASCADE;
  DROP TABLE "reusable_sections_res" CASCADE;
  DROP TABLE "reusable_sections_faq_list_categories" CASCADE;
  DROP TABLE "reusable_sections_faq_list" CASCADE;
  DROP TABLE "reusable_sections_latest" CASCADE;
  DROP TABLE "reusable_sections_events_list" CASCADE;
  DROP TABLE "reusable_sections_campuses_links" CASCADE;
  DROP TABLE "reusable_sections_campuses" CASCADE;
  DROP TABLE "reusable_sections_adm_info" CASCADE;
  DROP TABLE "reusable_sections_form" CASCADE;
  DROP TABLE "reusable_sections_notice" CASCADE;
  DROP TABLE "reusable_sections_cta_links" CASCADE;
  DROP TABLE "reusable_sections_cta" CASCADE;
  DROP TABLE "reusable_sections_marquee_items" CASCADE;
  DROP TABLE "reusable_sections_marquee" CASCADE;
  DROP TABLE "reusable_sections" CASCADE;
  DROP TABLE "reusable_sections_rels" CASCADE;
  DROP TABLE "enquiries" CASCADE;
  DROP TABLE "users_roles" CASCADE;
  DROP TABLE "users_sessions" CASCADE;
  DROP TABLE "users" CASCADE;
  DROP TABLE "payload_kv" CASCADE;
  DROP TABLE "payload_locked_documents" CASCADE;
  DROP TABLE "payload_locked_documents_rels" CASCADE;
  DROP TABLE "payload_preferences" CASCADE;
  DROP TABLE "payload_preferences_rels" CASCADE;
  DROP TABLE "payload_migrations" CASCADE;
  DROP TABLE "site_settings_campuses_phones" CASCADE;
  DROP TABLE "site_settings_campuses" CASCADE;
  DROP TABLE "site_settings_social" CASCADE;
  DROP TABLE "site_settings" CASCADE;
  DROP TABLE "site_settings_rels" CASCADE;
  DROP TABLE "header_nav_groups_items" CASCADE;
  DROP TABLE "header_nav_groups" CASCADE;
  DROP TABLE "header_utility_links" CASCADE;
  DROP TABLE "header" CASCADE;
  DROP TABLE "header_rels" CASCADE;
  DROP TABLE "footer_columns_links" CASCADE;
  DROP TABLE "footer_columns" CASCADE;
  DROP TABLE "footer_legal_links" CASCADE;
  DROP TABLE "footer_memberships" CASCADE;
  DROP TABLE "footer" CASCADE;
  DROP TABLE "footer_rels" CASCADE;
  DROP TABLE "admissions_fee_table" CASCADE;
  DROP TABLE "admissions_required_documents" CASCADE;
  DROP TABLE "admissions" CASCADE;
  DROP TABLE "_admissions_v_version_fee_table" CASCADE;
  DROP TABLE "_admissions_v_version_required_documents" CASCADE;
  DROP TABLE "_admissions_v" CASCADE;
  DROP TYPE "public"."enum_pages_hero_links_link_type";
  DROP TYPE "public"."enum_pages_hero_links_link_appearance";
  DROP TYPE "public"."enum_pages_hero_variant";
  DROP TYPE "public"."enum_pages_hero_accent";
  DROP TYPE "public"."enum_pages_text_layout";
  DROP TYPE "public"."enum_pages_text_size";
  DROP TYPE "public"."enum_pages_split_links_link_type";
  DROP TYPE "public"."enum_pages_split_links_link_appearance";
  DROP TYPE "public"."enum_pages_split_media_position";
  DROP TYPE "public"."enum_pages_split_background";
  DROP TYPE "public"."enum_pages_mosaic_items_link_type";
  DROP TYPE "public"."enum_pages_kinetic_link_type";
  DROP TYPE "public"."enum_pages_cards_cards_accent";
  DROP TYPE "public"."enum_pages_cards_cards_link_type";
  DROP TYPE "public"."enum_pages_cards_style";
  DROP TYPE "public"."enum_pages_defs_columns";
  DROP TYPE "public"."enum_pages_defs_accent";
  DROP TYPE "public"."enum_pages_defs_background";
  DROP TYPE "public"."enum_pages_lists_background";
  DROP TYPE "public"."enum_pages_steps_steps_link_type";
  DROP TYPE "public"."enum_pages_gallery_layout";
  DROP TYPE "public"."enum_pages_people_people_link_type";
  DROP TYPE "public"."enum_pages_res_items_kind";
  DROP TYPE "public"."enum_pages_faq_list_categories";
  DROP TYPE "public"."enum_pages_faq_list_source";
  DROP TYPE "public"."enum_pages_latest_category";
  DROP TYPE "public"."enum_pages_events_list_campus";
  DROP TYPE "public"."enum_pages_campuses_links_link_type";
  DROP TYPE "public"."enum_pages_campuses_links_link_appearance";
  DROP TYPE "public"."enum_pages_adm_info_show";
  DROP TYPE "public"."enum_pages_form_form_type";
  DROP TYPE "public"."enum_pages_notice_tone";
  DROP TYPE "public"."enum_pages_notice_link_type";
  DROP TYPE "public"."enum_pages_cta_links_link_type";
  DROP TYPE "public"."enum_pages_cta_links_link_appearance";
  DROP TYPE "public"."enum_pages_status";
  DROP TYPE "public"."enum___pages_v_hero_v_links_link_type";
  DROP TYPE "public"."enum___pages_v_hero_v_links_link_appearance";
  DROP TYPE "public"."enum___pages_v_hero_v_variant";
  DROP TYPE "public"."enum___pages_v_hero_v_accent";
  DROP TYPE "public"."enum___pages_v_text_v_layout";
  DROP TYPE "public"."enum___pages_v_text_v_size";
  DROP TYPE "public"."enum___pages_v_split_v_links_link_type";
  DROP TYPE "public"."enum___pages_v_split_v_links_link_appearance";
  DROP TYPE "public"."enum___pages_v_split_v_media_position";
  DROP TYPE "public"."enum___pages_v_split_v_background";
  DROP TYPE "public"."enum___pages_v_mosaic_v_items_link_type";
  DROP TYPE "public"."enum___pages_v_kinetic_v_link_type";
  DROP TYPE "public"."enum___pages_v_cards_v_cards_accent";
  DROP TYPE "public"."enum___pages_v_cards_v_cards_link_type";
  DROP TYPE "public"."enum___pages_v_cards_v_style";
  DROP TYPE "public"."enum___pages_v_defs_v_columns";
  DROP TYPE "public"."enum___pages_v_defs_v_accent";
  DROP TYPE "public"."enum___pages_v_defs_v_background";
  DROP TYPE "public"."enum___pages_v_lists_v_background";
  DROP TYPE "public"."enum___pages_v_steps_v_steps_link_type";
  DROP TYPE "public"."enum___pages_v_gallery_v_layout";
  DROP TYPE "public"."enum___pages_v_people_v_people_link_type";
  DROP TYPE "public"."enum___pages_v_res_v_items_kind";
  DROP TYPE "public"."enum___pages_v_faq_list_v_categories";
  DROP TYPE "public"."enum___pages_v_faq_list_v_source";
  DROP TYPE "public"."enum___pages_v_latest_v_category";
  DROP TYPE "public"."enum___pages_v_events_list_v_campus";
  DROP TYPE "public"."enum___pages_v_campuses_v_links_link_type";
  DROP TYPE "public"."enum___pages_v_campuses_v_links_link_appearance";
  DROP TYPE "public"."enum___pages_v_adm_info_v_show";
  DROP TYPE "public"."enum___pages_v_form_v_form_type";
  DROP TYPE "public"."enum___pages_v_notice_v_tone";
  DROP TYPE "public"."enum___pages_v_notice_v_link_type";
  DROP TYPE "public"."enum___pages_v_cta_v_links_link_type";
  DROP TYPE "public"."enum___pages_v_cta_v_links_link_appearance";
  DROP TYPE "public"."enum__pages_v_version_status";
  DROP TYPE "public"."enum_stories_category";
  DROP TYPE "public"."enum_stories_status";
  DROP TYPE "public"."enum__stories_v_version_category";
  DROP TYPE "public"."enum__stories_v_version_status";
  DROP TYPE "public"."enum_events_startsat_tz";
  DROP TYPE "public"."enum_events_endsat_tz";
  DROP TYPE "public"."enum_events_campus";
  DROP TYPE "public"."enum_events_registration_status";
  DROP TYPE "public"."enum_events_status";
  DROP TYPE "public"."enum__events_v_version_startsat_tz";
  DROP TYPE "public"."enum__events_v_version_endsat_tz";
  DROP TYPE "public"."enum__events_v_version_campus";
  DROP TYPE "public"."enum__events_v_version_registration_status";
  DROP TYPE "public"."enum__events_v_version_status";
  DROP TYPE "public"."enum_programmes_campus";
  DROP TYPE "public"."enum_programmes_accent";
  DROP TYPE "public"."enum_programmes_status";
  DROP TYPE "public"."enum__programmes_v_version_campus";
  DROP TYPE "public"."enum__programmes_v_version_accent";
  DROP TYPE "public"."enum__programmes_v_version_status";
  DROP TYPE "public"."enum_faqs_category";
  DROP TYPE "public"."enum_faqs_status";
  DROP TYPE "public"."enum__faqs_v_version_category";
  DROP TYPE "public"."enum__faqs_v_version_status";
  DROP TYPE "public"."enum_media_approval";
  DROP TYPE "public"."enum_reusable_sections_hero_links_link_type";
  DROP TYPE "public"."enum_reusable_sections_hero_links_link_appearance";
  DROP TYPE "public"."enum_reusable_sections_hero_variant";
  DROP TYPE "public"."enum_reusable_sections_hero_accent";
  DROP TYPE "public"."enum_reusable_sections_text_layout";
  DROP TYPE "public"."enum_reusable_sections_text_size";
  DROP TYPE "public"."enum_reusable_sections_split_links_link_type";
  DROP TYPE "public"."enum_reusable_sections_split_links_link_appearance";
  DROP TYPE "public"."enum_reusable_sections_split_media_position";
  DROP TYPE "public"."enum_reusable_sections_split_background";
  DROP TYPE "public"."enum_reusable_sections_mosaic_items_link_type";
  DROP TYPE "public"."enum_reusable_sections_kinetic_link_type";
  DROP TYPE "public"."enum_reusable_sections_cards_cards_accent";
  DROP TYPE "public"."enum_reusable_sections_cards_cards_link_type";
  DROP TYPE "public"."enum_reusable_sections_cards_style";
  DROP TYPE "public"."enum_reusable_sections_defs_columns";
  DROP TYPE "public"."enum_reusable_sections_defs_accent";
  DROP TYPE "public"."enum_reusable_sections_defs_background";
  DROP TYPE "public"."enum_reusable_sections_lists_background";
  DROP TYPE "public"."enum_reusable_sections_steps_steps_link_type";
  DROP TYPE "public"."enum_reusable_sections_gallery_layout";
  DROP TYPE "public"."enum_reusable_sections_people_people_link_type";
  DROP TYPE "public"."enum_reusable_sections_res_items_kind";
  DROP TYPE "public"."enum_reusable_sections_faq_list_categories";
  DROP TYPE "public"."enum_reusable_sections_faq_list_source";
  DROP TYPE "public"."enum_reusable_sections_latest_category";
  DROP TYPE "public"."enum_reusable_sections_events_list_campus";
  DROP TYPE "public"."enum_reusable_sections_campuses_links_link_type";
  DROP TYPE "public"."enum_reusable_sections_campuses_links_link_appearance";
  DROP TYPE "public"."enum_reusable_sections_adm_info_show";
  DROP TYPE "public"."enum_reusable_sections_form_form_type";
  DROP TYPE "public"."enum_reusable_sections_notice_tone";
  DROP TYPE "public"."enum_reusable_sections_notice_link_type";
  DROP TYPE "public"."enum_reusable_sections_cta_links_link_type";
  DROP TYPE "public"."enum_reusable_sections_cta_links_link_appearance";
  DROP TYPE "public"."enum_enquiries_type";
  DROP TYPE "public"."enum_enquiries_status";
  DROP TYPE "public"."enum_enquiries_stage";
  DROP TYPE "public"."enum_users_roles";
  DROP TYPE "public"."enum_site_settings_campuses_key";
  DROP TYPE "public"."enum_site_settings_social_platform";
  DROP TYPE "public"."enum_site_settings_announcement_link_type";
  DROP TYPE "public"."enum_header_nav_groups_items_link_type";
  DROP TYPE "public"."enum_header_nav_groups_overview_type";
  DROP TYPE "public"."enum_header_utility_links_link_type";
  DROP TYPE "public"."enum_header_cta_type";
  DROP TYPE "public"."enum_footer_columns_links_link_type";
  DROP TYPE "public"."enum_footer_legal_links_link_type";
  DROP TYPE "public"."enum_footer_memberships_link_type";
  DROP TYPE "public"."enum_admissions_status";
  DROP TYPE "public"."enum__admissions_v_version_status";`)
}
