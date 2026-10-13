# Textures I would like you to find

Your pack covered Level 0 and the Office well:
- **Level 0:** chevron wallpaper, carpet and ceiling tiles.
- **Office:** striped and floral papers, two carpets and the same tiles.

Some textures had no source in your pack, so they are drawn procedurally for now; they work, but real photographs would look much better. Each request below says which file to replace.

**Format:** square, tileable (seamless on all four edges), 256 x 256 or 512 x 512 PNG. `_n`/`_s` LabPBR maps are welcome but optional. Please also tell me where each one comes from and its licence, so the pack can be published.

**Priority 1 (biggest visual win):**
1. **Poolrooms wall/floor tile:** small square white ceramic tiles, about 4 x 4 tiles per texture, light grey grout, slightly glossy. Replaces `pool_tile`.
2. **Poolrooms coloured tile:** the same tiles in pale aqua or light blue, for pool linings. Replaces `pool_tile_blue`.
3. **Fluorescent troffer (2 x 4 ft prismatic panel):** a photo looking straight up at one lit panel, with frame. Replaces `light_panel` (the off version can be derived).

**Priority 2:**

4. **Grimy pool tile:** the same white tiles with dirty or yellowed grout and limescale. Replaces `pool_tile_grime`.
5. **Level 0 wallpaper, true mono-yellow:** the classic Level 0 yellow paper with a faint vertical pattern. Yours is cream and pink, recoloured by the script. A natively yellow photo would beat the recolour. Replaces `l0_wall`.
6. **Damp Level 0 carpet:** worn mustard or olive commercial carpet, close-up. Replaces `l0_carpet`.

**Optional (Habitable Zone, the parking garage):** this level still uses vanilla blocks on purpose, because you said it already works. If you want it textured:
7. bare cast concrete wall, with board marks;
8. a concrete floor with oil stains;
9. a painted parking line (yellow on concrete);
10. a concrete column with a yellow and black hazard band.

Put new files into the extracted pack folder under the same names, or send them to me, and re-run:

```
python3 tools/textures/build_textures.py --src <folder>
python3 tools/audio/build_pack.py
```
