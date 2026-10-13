#!/usr/bin/env python3
"""Build the custom block textures, models and the note_block blockstate for the Backrooms resource pack.

    python3 tools/textures/build_textures.py --src <extracted texture pack root>

Reads src/main/resources/custom-blocks.json (shared with the plugin). Writes:
  pack/java/assets/backrooms/textures/block/<name>.png (+ _n.png / _s.png LabPBR maps for shader users)
  pack/java/assets/backrooms/models/block/<id>.json
  pack/java/assets/minecraft/blockstates/note_block.json   every one of the 1150 note block states: the custom
                                                           states get our models, all others the vanilla model
Then run tools/audio/build_pack.py to zip the pack.

Source images come from the server owner's "Backrooms PBR" pack (by Pro_fessional, packsmc.com); see
pack/MANIFEST.md. Textures with no suitable source (pool tiles, light panels) are drawn procedurally here.
Deterministic: fixed seeds, no timestamps.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFS = os.path.join(ROOT, "src", "main", "resources", "custom-blocks.json")
OUT = os.path.join(ROOT, "pack", "java", "assets")
TEX = os.path.join(OUT, "backrooms", "textures", "block")
MODELS = os.path.join(OUT, "backrooms", "models", "block")
STATES = os.path.join(OUT, "minecraft", "blockstates")
RES = 256

INSTRUMENTS = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
               "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper",
               "dragon", "wither_skeleton", "piglin", "custom_head"]


# ------------------------------------------------------------------------------------- image helpers
def load(src, name):
    p = os.path.join(src, "assets", "minecraft", "textures", "block", name + ".png")
    if not os.path.exists(p):
        sys.exit(f"missing source texture {p}")
    return Image.open(p).convert("RGBA")


def arr(img):
    return np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def fit(im, size=RES):
    """Square crop (top-left, keeps tiling) and high quality resize."""
    w, h = im.size
    s = min(w, h)
    return im.crop((0, 0, s, s)).resize((size, size), Image.LANCZOS)


def lum(a):
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def tint(a, dark, light, contrast=1.0, gamma=1.0):
    """Recolour by luminance: darkest -> dark, lightest -> light (colours as 0-255 tuples)."""
    l = lum(a)
    lo, hi = np.percentile(l, 1), np.percentile(l, 99.5)
    t = np.clip((l - lo) / max(hi - lo, 1e-4), 0, 1) ** gamma
    t = 0.5 + (t - 0.5) * contrast
    d, li = np.array(dark) / 255.0, np.array(light) / 255.0
    return d + (li - d) * t[..., None]


def noise(seed, size=RES, scale=8, octaves=4):
    """Tileable value noise in [0,1]."""
    rng = np.random.default_rng(seed)
    out = np.zeros((size, size), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = scale * (2 ** o)
        g = rng.random((n, n)).astype(np.float32)
        x = np.linspace(0, n, size, endpoint=False)
        i0 = np.floor(x).astype(int) % n
        i1 = (i0 + 1) % n
        f = x - np.floor(x)
        f = f * f * (3 - 2 * f)
        rows = g[i0][:, i0] * (1 - f)[None, :] + g[i0][:, i1] * f[None, :]
        rows2 = g[i1][:, i0] * (1 - f)[None, :] + g[i1][:, i1] * f[None, :]
        layer = rows * (1 - f)[:, None] + rows2 * f[:, None]
        out += layer * amp
        tot += amp
        amp *= 0.5
    return out / tot


def stain(a, seed, color, strength=0.55, top=True):
    """A water stain: a soft irregular blotch with a slightly darker tide line; from the top edge when {@code top}
    (wall stains run down from the ceiling), otherwise a round patch in the middle (ceiling / floor)."""
    n = noise(seed, scale=3, octaves=3)
    yy, xx = np.mgrid[0:RES, 0:RES] / RES
    if top:
        shape = np.clip(1 - yy * 1.6, 0, 1)
    else:
        shape = np.clip(1 - np.hypot(xx - 0.5, yy - 0.5) * 2.0, 0, 1)
    m = np.clip((shape * 0.8 + n * 0.5 - 0.55) * 3.0, 0, 1)
    m = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), np.float32) / 255
    rim = np.clip(1 - np.abs(m - 0.15) * 9, 0, 1)
    c = np.array(color) / 255.0
    out = a * (1 - m[..., None] * strength) + c * (m[..., None] * strength)
    return out * (1 - rim[..., None] * 0.12)


def grain(a, seed, amount=0.03):
    n = noise(seed, scale=32, octaves=2) - 0.5
    return a + n[..., None] * amount


def normal_map(a, strength=2.0, invert=False):
    """LabPBR _n: RGB normal (tangent space), alpha = height. Derived from luminance (tileable gradients)."""
    h = lum(a)
    if invert:
        h = 1 - h
    dx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * strength
    dy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * strength
    nz = np.ones_like(h)
    ln = np.sqrt(dx * dx + dy * dy + nz * nz)
    n = np.stack([-dx / ln, dy / ln, nz / ln], -1) * 0.5 + 0.5
    rgba = np.concatenate([n, h[..., None]], -1)
    return Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def spec_map(smooth, f0=0.04, emissive=None):
    """LabPBR _s: R perceptual smoothness, G F0 (0-229 dielectric), B porosity, A emission (255 = none)."""
    s = np.full((RES, RES), smooth, np.float32) if np.isscalar(smooth) else smooth
    r = np.clip(s, 0, 1)
    g = np.full_like(r, f0)
    b = np.zeros_like(r)
    a = np.full_like(r, 1.0) if emissive is None else np.clip(emissive * (254 / 255), 0, 254 / 255)
    rgba = np.stack([r, g, b, a], -1)
    return Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


# ------------------------------------------------------------------------------------- procedural
def tiles(n, base, grout, seed, var=0.035, grout_px=3, gloss=True, dirt=0.0):
    """n x n ceramic tiles per block with grout, per-tile tint variation, soft edge shading and a glaze sheen."""
    rng = np.random.default_rng(seed)
    a = np.zeros((RES, RES, 3), np.float32)
    step = RES // n
    yy, xx = np.mgrid[0:RES, 0:RES]
    lx, ly = xx % step, yy % step
    tx, ty = xx // step, yy // step
    # mostly brightness variation between tiles, only a trace of colour (real glazes differ by firing batch)
    tint_tile = rng.normal(0, var * 0.15, (n, n, 3)).astype(np.float32) + rng.normal(0, var, (n, n, 1)).astype(np.float32)
    b = np.array(base) / 255.0
    a[:] = b + tint_tile[ty, tx]
    # bevel: slightly darker towards the edges, highlight upper-left
    ex = np.minimum(lx, step - 1 - lx) / step
    ey = np.minimum(ly, step - 1 - ly) / step
    edge = np.clip(np.minimum(ex, ey) * 10, 0, 1)
    a *= (0.9 + 0.1 * edge)[..., None]
    if gloss:
        hl = np.clip(1 - np.hypot(lx / step - 0.3, ly / step - 0.3) * 2.2, 0, 1) ** 2 * 0.06
        a += hl[..., None]
    g = np.array(grout) / 255.0
    gm = (lx < grout_px // 2 + 1) | (lx >= step - grout_px // 2) | (ly < grout_px // 2 + 1) | (ly >= step - grout_px // 2)
    gn = noise(seed + 1, scale=16, octaves=3)
    gcol = g * (0.92 + 0.12 * gn[..., None])
    if dirt > 0:
        gcol = gcol * (1 - dirt * (0.3 + 0.7 * noise(seed + 2, scale=4)[..., None]))
    a = np.where(gm[..., None], gcol, a)
    return a


def troffer(seed, lit=True, cool=False):
    """A recessed fluorescent troffer running along X: tile margins, a metal frame with end caps, a prismatic
    diffuser and the faint shapes of two tubes behind it. One block = one 1 m section; rows join end to end."""
    a = np.zeros((RES, RES, 3), np.float32)
    yy, xx = np.mgrid[0:RES, 0:RES].astype(np.float32)
    ceiling = np.array((214, 207, 178) if not cool else (220, 220, 214)) / 255.0
    a[:] = ceiling * (0.95 + 0.05 * noise(seed, scale=8)[..., None])
    y0, y1 = 64, 192   # troffer band (half a block wide)
    band = (yy >= y0) & (yy < y1)
    frame = np.array((176, 174, 166)) / 255.0
    a[band] = frame
    inner = (yy >= y0 + 10) & (yy < y1 - 10) & (xx >= 6) & (xx < RES - 6)
    if lit:
        dif = np.array((255, 247, 222) if not cool else (244, 249, 255)) / 255.0
    else:
        dif = np.array((168, 165, 152) if not cool else (170, 172, 172)) / 255.0
    # prismatic diffuser: a fine diamond lattice
    prism = ((np.sin(xx * 0.9 + yy * 0.9) * np.sin(xx * 0.9 - yy * 0.9)) * 0.5 + 0.5)
    shade = 0.93 + 0.07 * prism
    # two tubes behind the diffuser, brighter bands
    tube = np.exp(-((yy - 105) / 9) ** 2) + np.exp(-((yy - 151) / 9) ** 2)
    glow = (0.9 + 0.12 * tube) if lit else (0.95 + 0.05 * tube)
    a[inner] = (dif * (shade * glow)[..., None])[inner]
    # inner lip shadow
    lip = band & ~inner
    a[lip] *= 0.85
    # end caps at both x edges so rows read as separate 1 m sections
    cap = band & ((xx < 6) | (xx >= RES - 6))
    a[cap] = frame * 0.8
    return np.clip(a, 0, 1)


# ------------------------------------------------------------------------------------- recipes
def build(src):
    out = {}  # name -> (rgb array, normal strength or None, spec image or None)
    L0_DARK, L0_LIGHT = (150, 132, 66), (220, 204, 124)

    wall = tint(arr(fit(load(src, "warped_planks"))), L0_DARK, L0_LIGHT, contrast=0.62, gamma=0.9)
    wall = grain(wall, 11, 0.025)
    out["l0_wall"] = (wall, 1.2, spec_map(0.25))
    wall_b = tint(arr(fit(load(src, "mangrove_planks"))), (146, 128, 64), (214, 197, 118), contrast=0.55)
    out["l0_wall_b"] = (grain(wall_b, 12, 0.03), 1.0, spec_map(0.25))
    out["l0_wall_stain"] = (stain(wall, 13, (124, 98, 52), 0.42), 1.2, spec_map(0.2))

    carpet = tint(arr(fit(load(src, "podzol_top"))), (104, 90, 46), (180, 160, 96), contrast=0.9)
    out["l0_carpet"] = (carpet, 3.0, spec_map(0.05))
    wet = carpet * 0.62 + np.array((0.04, 0.03, 0.0))
    wet = stain(wet, 21, (70, 56, 30), 0.35, top=False)
    out["l0_carpet_wet"] = (wet, 2.0, spec_map(0.55 * noise(22, scale=6) + 0.2))

    # drop ceiling: 2 x 2 tiles of the source grid per block (about 50 cm tiles)
    d = load(src, "diorite")
    tile_px = d.size[0] / 5.0
    crop = d.crop((0, 0, int(round(tile_px * 2)), int(round(tile_px * 2)))).resize((RES, RES), Image.LANCZOS)
    ceil = arr(crop)
    out["l0_ceiling"] = (tint(ceil, (150, 142, 116), (224, 217, 190), contrast=1.0), 2.5, spec_map(0.1))
    out["l0_ceiling_stain"] = (stain(out["l0_ceiling"][0], 31, (150, 118, 66), 0.55, top=False), 2.5, spec_map(0.1))
    out["office_ceiling"] = (tint(ceil, (150, 150, 146), (228, 228, 222)), 2.5, spec_map(0.1))

    yy = np.mgrid[0:RES, 0:RES][0]
    band = ((yy >= 74) & (yy < 182)).astype(np.float32)
    out["light_panel"] = (troffer(41, True), None, spec_map(0.5, emissive=band))
    out["light_panel_off"] = (troffer(41, False), None, spec_map(0.5))
    out["light_panel_cool"] = (troffer(42, True, cool=True), None, spec_map(0.5, emissive=band))

    out["pool_tile"] = (tiles(4, (236, 239, 236), (196, 200, 196), 51), 1.5, spec_map(0.85))
    out["pool_tile_blue"] = (tiles(4, (150, 206, 220), (190, 214, 216), 52, var=0.03), 1.5, spec_map(0.85))
    out["pool_tile_grime"] = (stain(tiles(4, (224, 226, 218), (150, 150, 132), 53, dirt=0.5), 54, (150, 140, 100), 0.3, top=False),
                              1.5, spec_map(0.7))

    out["office_wall"] = (tint(arr(fit(load(src, "stripes"))), (188, 182, 168), (236, 232, 222), contrast=0.9), 0.8, spec_map(0.2))
    out["office_wall_b"] = (arr(fit(load(src, "birch_planks"))) * 0.94, 0.8, spec_map(0.2))
    out["office_carpet"] = (arr(fit(load(src, "podzol_top"))) * 0.92, 3.0, spec_map(0.05))
    out["office_carpet_b"] = (arr(fit(load(src, "stone"))), 3.0, spec_map(0.05))
    # per-level recipe modules (tools/textures/recipes_*.py) add their own textures
    import recipes_level1
    import recipes_level4
    recipes_level1.add(out, src)
    recipes_level4.add(out, src)
    return out


# ------------------------------------------------------------------------------------- writers
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, sort_keys=True)
        f.write("\n")


def save_png(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.environ.get("BACKROOMS_TEXTURE_SRC"), help="extracted source texture pack")
    ap.add_argument("--no-pbr", action="store_true", help="skip the LabPBR _n/_s maps")
    ap.add_argument("--normals", action="store_true", help="also write _n normal maps (bigger pack)")
    a = ap.parse_args()
    if not a.src:
        sys.exit("--src is required (the extracted Backrooms PBR texture pack)")
    with open(DEFS, encoding="utf-8") as f:
        defs = json.load(f)["blocks"]

    textures = build(a.src)
    needed = {t for d in defs.values() for t in d["textures"].values()}
    missing = needed - set(textures)
    if missing:
        sys.exit(f"no recipe for textures: {sorted(missing)}")

    if os.path.isdir(TEX):
        for f in os.listdir(TEX):
            os.remove(os.path.join(TEX, f))
    total = 0
    for name in sorted(needed):
        rgb, nstr, spec = textures[name]
        p = os.path.join(TEX, name + ".png")
        save_png(img(rgb), p)
        total += os.path.getsize(p)
        if not a.no_pbr:
            if nstr is not None and a.normals:
                pn = os.path.join(TEX, name + "_n.png")
                save_png(normal_map(rgb, nstr), pn)
                total += os.path.getsize(pn)
            if spec is not None:
                ps = os.path.join(TEX, name + "_s.png")
                save_png(spec, ps)
                total += os.path.getsize(ps)

    # models: one cube per custom block
    if os.path.isdir(MODELS):
        for f in os.listdir(MODELS):
            os.remove(os.path.join(MODELS, f))
    taken = {}
    for bid, d in sorted(defs.items()):
        key = (d["instrument"], d["note"])
        if key in taken:
            sys.exit(f"{bid} and {taken[key]} share note block state {key}")
        if d["instrument"] not in INSTRUMENTS or not 0 <= d["note"] <= 24:
            sys.exit(f"{bid}: invalid note block state {key}")
        taken[key] = bid
        t = d["textures"]
        if d.get("emissive"):
            # a light panel: every face unshaded and drawn at full brightness (block model light_emission), so it
            # glows in vanilla too, not only with shaders
            faces = {f: {"texture": "#all", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}
            model = {"parent": "minecraft:block/block", "textures": {"all": f"backrooms:block/{t['all']}",
                                                                   "particle": f"backrooms:block/{t['all']}"},
                     "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False,
                                   "light_emission": int(d["emissive"]), "faces": faces}]}
        elif "all" in t:
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"backrooms:block/{t['all']}"}}
        else:
            model = {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "side": f"backrooms:block/{t['side']}", "top": f"backrooms:block/{t['top']}",
                "bottom": f"backrooms:block/{t['bottom']}"}}
        write_json(os.path.join(MODELS, bid + ".json"), model)

    # blockstate: every state explicit, so nothing is ambiguous and vanilla note blocks keep their look
    variants = {}
    for inst in INSTRUMENTS:
        for note in range(25):
            for powered in ("false", "true"):
                key = f"instrument={inst},note={note},powered={powered}"
                bid = taken.get((inst, note)) if powered == "true" else None
                variants[key] = {"model": f"backrooms:block/{bid}" if bid else "minecraft:block/note_block"}
    write_json(os.path.join(STATES, "note_block.json"), {"variants": variants})
    print(f"{len(needed)} textures ({total / 1e6:.2f} MB with PBR maps), {len(defs)} block models, "
          f"{len(variants)} note block states ({len(taken)} custom)")


if __name__ == "__main__":
    main()
