"""Texture recipes for Level 4 (the office towers). Called by build_textures.build(); add(out, src) puts an entry
(rgb float array RES x RES x 3, normal strength or None, spec image or None) into out for every name below.

A 2000s office building at night: precast and granite facades, brushed-steel lift doors, carpet tiles, terrazzo in
the lobby, fabric cubicle panels, painted drywall and a suspended ceiling. Mostly procedural (deterministic seeds);
the drop ceiling reuses the source pack's tile grid like the other office ceilings."""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

import build_textures as bt

RES = bt.RES
NAMES = ["tower_facade", "tower_facade_dark", "tower_floor_tile", "tower_lobby_tile", "tower_partition", "tower_wall",
         "tower_column", "tower_elevator", "tower_roof", "tower_ceiling"]

YY, XX = np.mgrid[0:RES, 0:RES].astype(np.float32)


def col(rgb):
    return np.array(rgb, np.float32) / 255.0


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r)), np.float32) / 255.0


def speckle(seed, density, size=1):
    rng = np.random.default_rng(seed)
    n = -(-RES // size)
    m = (rng.random((n, n)) < density).astype(np.float32)
    if size > 1:
        m = np.kron(m, np.ones((size, size), np.float32))[:RES, :RES]
    return m


def streaks(seed, vertical=True, strength=1.0):
    """Fine brushed-metal lines: 1-D noise stretched along one axis."""
    rng = np.random.default_rng(seed)
    line = rng.random(RES).astype(np.float32)
    line = np.convolve(np.concatenate([line[-3:], line, line[:3]]), np.ones(3) / 3, mode="same")[3:-3]
    a = np.tile(line[None, :], (RES, 1)) if vertical else np.tile(line[:, None], (1, RES))
    return (a - 0.5) * strength


def facade():
    # light precast concrete panel: fine aggregate, a horizontal reveal at the top and bottom edges, faint rain streaks
    base = col((176, 176, 170))
    n = bt.noise(4101, scale=16, octaves=4)
    a = base * (0.9 + 0.16 * n[..., None])
    a = a + (speckle(4102, 0.05) * 0.06 - speckle(4103, 0.05) * 0.07)[..., None]
    reveal = (YY < 6) | (YY >= RES - 6)
    a[reveal] *= 0.72
    a[(YY >= 6) & (YY < 9)] *= 1.06  # lit lip under the reveal
    rain = np.clip(bt.noise(4104, scale=32, octaves=2) - 0.55, 0, 1) * np.clip(1 - YY / RES, 0.2, 1)
    rain = blur(np.tile(rain.mean(0, keepdims=True), (RES, 1)) * 2.5, 1)
    a *= (1 - 0.12 * rain)[..., None]
    return np.clip(a, 0, 1)


def facade_dark():
    # polished dark granite: black with grey/white crystals and a faint polish sheen
    a = np.zeros((RES, RES, 3), np.float32) + col((52, 54, 58))
    n = bt.noise(4201, scale=24, octaves=4)
    a *= (0.85 + 0.3 * n[..., None])
    a += (speckle(4202, 0.08) * 0.10)[..., None] + (speckle(4203, 0.015) * 0.22)[..., None]
    a -= (speckle(4204, 0.10, 2) * 0.05)[..., None]
    sheen = np.clip(1 - np.abs((XX + YY) / RES - 0.7) * 3, 0, 1) * 0.04
    a += sheen[..., None]
    # panel joint at the edges
    joint = (XX < 3) | (XX >= RES - 3) | (YY < 3) | (YY >= RES - 3)
    a[joint] *= 0.6
    return np.clip(a, 0, 1)


def carpet():
    # 2000s carpet tiles, 2 x 2 per block, laid quarter-turned: loop pile ribs alternate direction per tile
    step = RES // 2
    tx, ty = (XX // step).astype(int), (YY // step).astype(int)
    turned = (tx + ty) % 2 == 1
    rib = np.where(turned, np.sin(XX * np.pi / 3.0), np.sin(YY * np.pi / 3.0)) * 0.5 + 0.5
    n = bt.noise(4301, scale=64, octaves=2)
    fleck = speckle(4302, 0.06)
    base = col((98, 102, 110))
    a = base * (0.84 + 0.12 * rib[..., None] + 0.1 * n[..., None])
    a = a + (fleck * 0.07)[..., None] * col((140, 150, 175))
    a = a - (speckle(4303, 0.05) * 0.05)[..., None]
    # slight tone difference between tiles and a dark seam between them
    tone = np.random.default_rng(4304).normal(0, 0.025, (2, 2)).astype(np.float32)
    a *= (1 + tone[ty, tx])[..., None]
    lx, ly = XX % step, YY % step
    seam = (lx < 1) | (ly < 1)
    a[seam] *= 0.8
    # worn walking paths: a broad lighter blotch
    wear = np.clip(bt.noise(4305, scale=3, octaves=2) - 0.55, 0, 1) * 0.25
    a *= (1 + wear[..., None])
    return np.clip(a, 0, 1)


def terrazzo():
    # polished terrazzo: cream matrix with marble chips of several colours, one tile per block, thin brass-grey joint
    rng = np.random.default_rng(4401)
    a = np.zeros((RES, RES, 3), np.float32) + col((214, 206, 190))
    a *= (0.96 + 0.06 * bt.noise(4402, scale=8)[..., None])
    chips = [((150, 138, 124), 0.05, 4), ((86, 82, 78), 0.03, 3), ((240, 236, 226), 0.06, 3), ((176, 132, 104), 0.02, 4),
             ((120, 132, 128), 0.02, 3), ((60, 58, 58), 0.03, 2)]
    for i, (c, dens, size) in enumerate(chips):
        m = speckle(4410 + i, dens, size)
        m = np.clip(blur(m, 1.0) * 1.8, 0, 1) * 0.9
        a = a * (1 - m[..., None]) + col(c) * m[..., None]
    joint = (XX < 2) | (YY < 2)
    a[joint] = col((150, 146, 138))
    gloss = np.clip(1 - np.hypot(XX / RES - 0.3, YY / RES - 0.25) * 1.6, 0, 1) ** 2 * 0.06
    a += gloss[..., None]
    _ = rng
    return np.clip(a, 0, 1)


def partition():
    # cubicle panel: grey-blue woven fabric with an aluminium trim at top and sides
    weave = (np.sin(XX * np.pi / 2.0) * np.sin(YY * np.pi / 2.0)) * 0.5 + 0.5
    n = bt.noise(4501, scale=32, octaves=3)
    fabric = col((118, 124, 134)) * (0.88 + 0.08 * weave[..., None] + 0.1 * n[..., None])
    fabric = fabric + (speckle(4502, 0.08) * 0.04)[..., None]
    a = fabric
    trim = (YY < 14)
    a[trim] = col((176, 178, 180)) * (0.95 + 0.08 * streaks(4503, vertical=False, strength=1.0)[trim][..., None])
    a[(YY >= 14) & (YY < 17)] *= 0.7
    side = (XX < 8) | (XX >= RES - 8)
    a[side & ~trim] = col((160, 162, 166))
    a[((XX >= 8) & (XX < 10)) | ((XX >= RES - 10) & (XX < RES - 8))] *= 0.75
    return np.clip(a, 0, 1)


def drywall():
    # painted drywall: warm off-white, roller orange-peel, a faint scuffed skirting zone at the bottom
    a = col((222, 219, 210)) * (0.95 + 0.05 * bt.noise(4601, scale=48, octaves=2)[..., None])
    a = a + (bt.noise(4602, scale=8, octaves=3)[..., None] - 0.5) * 0.03
    scuff = np.clip(bt.noise(4603, scale=16, octaves=2) - 0.6, 0, 1) * np.clip((YY - RES * 0.85) / (RES * 0.15), 0, 1)
    a *= (1 - scuff * 0.5)[..., None]
    return np.clip(a, 0, 1)


def column():
    # mullion / column cladding: painted aluminium with brushed lines and a recessed shadow gap at both sides
    a = col((150, 152, 156)) * (1 + streaks(4701, vertical=True, strength=0.12)[..., None])
    a *= (0.95 + 0.06 * bt.noise(4702, scale=8)[..., None])
    gap = (XX < 10) | (XX >= RES - 10)
    a[gap] *= 0.55
    edge = ((XX >= 10) & (XX < 14)) | ((XX >= RES - 14) & (XX < RES - 10))
    a[edge] *= 1.12
    return np.clip(a, 0, 1)


def elevator():
    # brushed stainless steel: fine vertical brushing, soft vertical reflection band, faint fingerprints
    a = col((176, 180, 184)) * (1 + streaks(4801, vertical=True, strength=0.18)[..., None])
    band = np.exp(-((XX / RES - 0.35) / 0.18) ** 2) * 0.12 - np.exp(-((XX / RES - 0.8) / 0.1) ** 2) * 0.08
    a *= (1 + band)[..., None]
    smudge = np.clip(bt.noise(4802, scale=6, octaves=2) - 0.62, 0, 1) * np.clip((YY / RES - 0.35) * 3, 0, 1)
    a *= (1 - 0.18 * smudge)[..., None]
    frame = (XX < 3) | (XX >= RES - 3) | (YY < 3) | (YY >= RES - 3)
    a[frame] *= 0.7
    return np.clip(a, 0, 1)


def roof():
    # ballasted roof: grey gravel over the membrane
    rng = np.random.default_rng(4901)
    a = np.zeros((RES, RES, 3), np.float32) + col((96, 97, 98))
    for i in range(1400):
        cx, cy = rng.integers(0, RES, 2)
        r = rng.uniform(2, 5)
        shade = rng.uniform(0.7, 1.35)
        tone = col((110, 110, 108)) * shade + rng.normal(0, 0.02)
        d = np.hypot(((XX - cx + RES / 2) % RES) - RES / 2, ((YY - cy + RES / 2) % RES) - RES / 2)
        m = d < r
        hl = np.clip(1 - d / r, 0, 1)
        a[m] = (tone * (0.8 + 0.3 * hl[m][..., None]))
    a *= (0.92 + 0.12 * bt.noise(4902, scale=4)[..., None])
    return np.clip(a, 0, 1)


def ceiling(src):
    # suspended ceiling: 2 x 2 tiles of the source pack's tile grid per block, cool white like the light panels
    d = bt.load(src, "diorite")
    tile_px = d.size[0] / 5.0
    crop = d.crop((0, 0, int(round(tile_px * 2)), int(round(tile_px * 2)))).resize((RES, RES), Image.LANCZOS)
    a = bt.tint(bt.arr(crop), (150, 152, 152), (226, 228, 226), contrast=1.0)
    return np.clip(a, 0, 1)


def add(out, src):
    out["tower_facade"] = (facade(), 1.2, bt.spec_map(0.15))
    out["tower_facade_dark"] = (facade_dark(), 0.8, bt.spec_map(0.7, f0=0.05))
    out["tower_floor_tile"] = (carpet(), 2.5, bt.spec_map(0.03))
    out["tower_lobby_tile"] = (terrazzo(), 0.6, bt.spec_map(0.85))
    out["tower_partition"] = (partition(), 1.5, bt.spec_map(0.05))
    out["tower_wall"] = (drywall(), 0.6, bt.spec_map(0.2))
    out["tower_column"] = (column(), 1.0, bt.spec_map(0.55, f0=0.6))
    out["tower_elevator"] = (elevator(), 0.8, bt.spec_map(0.75, f0=0.9))
    out["tower_roof"] = (roof(), 2.5, bt.spec_map(0.05))
    out["tower_ceiling"] = (ceiling(src), 2.5, bt.spec_map(0.1))
    missing = [n for n in NAMES if n not in out]
    assert not missing, missing
