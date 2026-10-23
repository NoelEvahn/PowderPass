import { MigrateUpArgs, MigrateDownArgs, sql } from '@payloadcms/db-postgres'

export async function up({ db, payload, req }: MigrateUpArgs): Promise<void> {
  await db.execute(sql`
   CREATE TYPE "public"."enum_pages_cards_layout" AS ENUM('grid', 'horizontal');
  CREATE TYPE "public"."enum_pages_defs_items_icon" AS ENUM('scale', 'shield-check', 'clipboard-check', 'earth', 'party-popper', 'lightbulb', 'flame', 'heart-handshake', 'mountain', 'users', 'refresh-ccw', 'handshake', 'search', 'footprints', 'rocket', 'messages-square', 'compass', 'book-open', 'sparkles', 'star');
  CREATE TYPE "public"."enum_pages_defs_display" AS ENUM('grid', 'spiral');
  CREATE TYPE "public"."enum_pages_steps_layout" AS ENUM('stack', 'horizontal');
  CREATE TYPE "public"."enum___pages_v_cards_v_layout" AS ENUM('grid', 'horizontal');
  CREATE TYPE "public"."enum___pages_v_defs_v_items_icon" AS ENUM('scale', 'shield-check', 'clipboard-check', 'earth', 'party-popper', 'lightbulb', 'flame', 'heart-handshake', 'mountain', 'users', 'refresh-ccw', 'handshake', 'search', 'footprints', 'rocket', 'messages-square', 'compass', 'book-open', 'sparkles', 'star');
  CREATE TYPE "public"."enum___pages_v_defs_v_display" AS ENUM('grid', 'spiral');
  CREATE TYPE "public"."enum___pages_v_steps_v_layout" AS ENUM('stack', 'horizontal');
  CREATE TYPE "public"."enum_reusable_sections_cards_layout" AS ENUM('grid', 'horizontal');
  CREATE TYPE "public"."enum_reusable_sections_defs_items_icon" AS ENUM('scale', 'shield-check', 'clipboard-check', 'earth', 'party-popper', 'lightbulb', 'flame', 'heart-handshake', 'mountain', 'users', 'refresh-ccw', 'handshake', 'search', 'footprints', 'rocket', 'messages-square', 'compass', 'book-open', 'sparkles', 'star');
  CREATE TYPE "public"."enum_reusable_sections_defs_display" AS ENUM('grid', 'spiral');
  CREATE TYPE "public"."enum_reusable_sections_steps_layout" AS ENUM('stack', 'horizontal');
  ALTER TABLE "pages_cards" ADD COLUMN "layout" "enum_pages_cards_layout" DEFAULT 'grid';
  ALTER TABLE "pages_defs_items" ADD COLUMN "icon" "enum_pages_defs_items_icon";
  ALTER TABLE "pages_defs" ADD COLUMN "display" "enum_pages_defs_display" DEFAULT 'grid';
  ALTER TABLE "pages_steps" ADD COLUMN "layout" "enum_pages_steps_layout" DEFAULT 'stack';
  ALTER TABLE "__pages_v_cards_v" ADD COLUMN "layout" "enum___pages_v_cards_v_layout" DEFAULT 'grid';
  ALTER TABLE "__pages_v_defs_v_items" ADD COLUMN "icon" "enum___pages_v_defs_v_items_icon";
  ALTER TABLE "__pages_v_defs_v" ADD COLUMN "display" "enum___pages_v_defs_v_display" DEFAULT 'grid';
  ALTER TABLE "__pages_v_steps_v" ADD COLUMN "layout" "enum___pages_v_steps_v_layout" DEFAULT 'stack';
  ALTER TABLE "reusable_sections_cards" ADD COLUMN "layout" "enum_reusable_sections_cards_layout" DEFAULT 'grid';
  ALTER TABLE "reusable_sections_defs_items" ADD COLUMN "icon" "enum_reusable_sections_defs_items_icon";
  ALTER TABLE "reusable_sections_defs" ADD COLUMN "display" "enum_reusable_sections_defs_display" DEFAULT 'grid';
  ALTER TABLE "reusable_sections_steps" ADD COLUMN "layout" "enum_reusable_sections_steps_layout" DEFAULT 'stack';`)
}

export async function down({ db, payload, req }: MigrateDownArgs): Promise<void> {
  await db.execute(sql`
   ALTER TABLE "pages_cards" DROP COLUMN "layout";
  ALTER TABLE "pages_defs_items" DROP COLUMN "icon";
  ALTER TABLE "pages_defs" DROP COLUMN "display";
  ALTER TABLE "pages_steps" DROP COLUMN "layout";
  ALTER TABLE "__pages_v_cards_v" DROP COLUMN "layout";
  ALTER TABLE "__pages_v_defs_v_items" DROP COLUMN "icon";
  ALTER TABLE "__pages_v_defs_v" DROP COLUMN "display";
  ALTER TABLE "__pages_v_steps_v" DROP COLUMN "layout";
  ALTER TABLE "reusable_sections_cards" DROP COLUMN "layout";
  ALTER TABLE "reusable_sections_defs_items" DROP COLUMN "icon";
  ALTER TABLE "reusable_sections_defs" DROP COLUMN "display";
  ALTER TABLE "reusable_sections_steps" DROP COLUMN "layout";
  DROP TYPE "public"."enum_pages_cards_layout";
  DROP TYPE "public"."enum_pages_defs_items_icon";
  DROP TYPE "public"."enum_pages_defs_display";
  DROP TYPE "public"."enum_pages_steps_layout";
  DROP TYPE "public"."enum___pages_v_cards_v_layout";
  DROP TYPE "public"."enum___pages_v_defs_v_items_icon";
  DROP TYPE "public"."enum___pages_v_defs_v_display";
  DROP TYPE "public"."enum___pages_v_steps_v_layout";
  DROP TYPE "public"."enum_reusable_sections_cards_layout";
  DROP TYPE "public"."enum_reusable_sections_defs_items_icon";
  DROP TYPE "public"."enum_reusable_sections_defs_display";
  DROP TYPE "public"."enum_reusable_sections_steps_layout";`)
}
