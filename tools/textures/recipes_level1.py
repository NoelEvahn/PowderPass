"""Texture recipes for Level 1 (Habitable Zone parking garage). Called by build_textures.build(); add(out, src) puts an
entry (rgb float array RES x RES x 3, normal strength or None, spec image or None) into out for every name below.

Everything is procedural concrete, drawn at 256 px per block (one block = 1 m) and tileable on every edge:
  garage_wall           board-formed concrete: 8 horizontal form boards per metre with grain imprint, seams, tie holes
  garage_wall_stained   the same wall with water runs, efflorescence and a dirty splash zone
  garage_floor          power-troweled slab: mottling, trowel swirls, fine aggregate, tyre scuffs
  garage_floor_oil      the slab with an engine oil stain (dark core, brown halo, a few drips)
  garage_floor_cracked  the slab broken up: crack network, spalled pits showing aggregate
  garage_line           worn yellow traffic paint over the slab
  garage_ceiling        slab soffit: plywood formwork joint and grain, exhaust soot
  garage_column         smooth formed column concrete with darker arrises (the face edges read as chamfers)
  garage_hazard         yellow/black 45 degree hazard band, chipped
  garage_mass           darker, rougher fill concrete (also used for patched repairs in the floor)
Only the stone source image is used (luminance only, as fine aggregate detail); everything else is drawn here.
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import build_textures as bt

NAMES = ["garage_wall", "garage_wall_stained", "garage_floor", "garage_floor_oil", "garage_floor_cracked", "garage_line",
         "garage_ceiling", "garage_column", "garage_hazard", "garage_mass"]

RES = bt.RES


# ------------------------------------------------------------------------------------- helpers
def blur(m, r):
    """Tileable gaussian blur of a 2-D float map (FFT convolution, which wraps around the edges by construction)."""
    f = np.fft.fftfreq(RES)
    g = np.exp(-2 * (np.pi * r) ** 2 * (f[:, None] ** 2 + f[None, :] ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(m) * g)).astype(np.float32)


def wrapped(draw_fn, mode="F", fill=0.0):
    """Draw with PIL on a 3x3 canvas so shapes that cross an edge reappear on the opposite edge, then fold."""
    big = Image.new(mode, (RES * 3, RES * 3), fill)
    d = ImageDraw.Draw(big)
    for ox in (0, RES, 2 * RES):
        for oy in (0, RES, 2 * RES):
            draw_fn(d, ox, oy)
    a = np.asarray(big, np.float32)
    out = np.zeros((RES, RES), np.float32)
    for i in range(3):
        for j in range(3):
            out = np.maximum(out, a[i * RES:(i + 1) * RES, j * RES:(j + 1) * RES])
    return out


def col(rgb):
    return np.array(rgb, np.float32) / 255.0


def mix(a, b, t):
    t = t[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + b * t


def stretched_noise(seed, sx, sy, octaves=3):
    """Tileable noise stretched along x (sx > sy) or y: sample a square noise with different periods per axis."""
    rng = np.random.default_rng(seed)
    out = np.zeros((RES, RES), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        nx, ny = sx * 2 ** o, sy * 2 ** o
        g = rng.random((ny, nx)).astype(np.float32)
        x = np.linspace(0, nx, RES, endpoint=False)
        y = np.linspace(0, ny, RES, endpoint=False)
        x0 = np.floor(x).astype(int) % nx
        y0 = np.floor(y).astype(int) % ny
        fx = x - np.floor(x)
        fy = y - np.floor(y)
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        a = g[y0][:, x0] * (1 - fx)[None, :] + g[y0][:, (x0 + 1) % nx] * fx[None, :]
        b = g[(y0 + 1) % ny][:, x0] * (1 - fx)[None, :] + g[(y0 + 1) % ny][:, (x0 + 1) % nx] * fx[None, :]
        out += (a * (1 - fy)[:, None] + b * fy[:, None]) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def pores(seed, density=0.004, rmax=1.6):
    """Small dark air voids (bug holes) typical of formed concrete: returns a darkness map in [0,1]."""
    rng = np.random.default_rng(seed)
    n = int(RES * RES * density)
    pts = rng.random((n, 3))

    def draw(d, ox, oy):
        for x, y, r in pts:
            rr = 0.6 + r * rmax
            d.ellipse((ox + x * RES - rr, oy + y * RES - rr, ox + x * RES + rr, oy + y * RES + rr), fill=1.0)

    return blur(wrapped(draw), 0.6)


def aggregate(src, seed):
    """Fine aggregate speckle: the source pack's stone image (luminance, high-passed), else procedural."""
    p = None
    if src:
        cand = os.path.join(src, "assets", "minecraft", "textures", "block", "stone.png")
        if os.path.exists(cand):
            p = cand
    if p:
        a = bt.lum(bt.arr(bt.fit(Image.open(p).convert("RGBA"))))
        a = a - blur(a, 6)
        return np.clip(a / (np.std(a) * 3 + 1e-4), -1, 1)
    n = bt.noise(seed, scale=64, octaves=2)
    return np.clip((n - 0.5) * 4, -1, 1)


def cracks(seed, count, length, width=1.4, branch=0.35):
    """Random-walk hairline cracks with occasional branches; returns a [0,1] mask (1 = crack)."""
    rng = np.random.default_rng(seed)
    paths = []

    def walk(x, y, ang, steps, w):
        pts = [(x, y)]
        for _ in range(steps):
            ang += rng.normal(0, 0.45)
            step = rng.uniform(5, 10)
            x += np.cos(ang) * step
            y += np.sin(ang) * step
            pts.append((x, y))
            if rng.random() < branch * 0.15 and w > 0.8:
                walk(x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.6, 1.2), int(steps * 0.4), w * 0.7)
        paths.append((pts, w))

    for _ in range(count):
        walk(rng.uniform(0, RES), rng.uniform(0, RES), rng.uniform(0, 2 * np.pi), length, width)

    def draw(d, ox, oy):
        for pts, w in paths:
            d.line([(ox + x, oy + y) for x, y in pts], fill=1.0, width=max(1, int(round(w))))

    m = wrapped(draw)
    return np.clip(blur(m, 0.5) * 1.3, 0, 1)


def concrete(base, seed, mottle=0.07, grain=0.035, warm=0.0):
    """Base concrete colour with broad cloudy mottling, mid-scale variation and grain."""
    b = col(base)
    n1 = bt.noise(seed, scale=3, octaves=4) - 0.5
    n2 = bt.noise(seed + 1, scale=12, octaves=3) - 0.5
    n3 = bt.noise(seed + 2, scale=48, octaves=2) - 0.5
    l = 1 + n1 * mottle * 2 + n2 * mottle + n3 * grain * 2
    a = b[None, None, :] * l[..., None]
    if warm:
        a = a + np.stack([n1, n1 * 0.6, -n1 * 0.4], -1) * warm
    return a


# ------------------------------------------------------------------------------------- recipes
def wall(src, seed=1101):
    a = concrete((158, 157, 151), seed, mottle=0.05)
    yy, xx = np.mgrid[0:RES, 0:RES]
    board_h = RES // 8
    board = yy // board_h
    rng = np.random.default_rng(seed)
    # each form board leaves its own tone and a grain imprint (long noise along x)
    tone = rng.normal(0, 0.035, 8).astype(np.float32)
    grain = stretched_noise(seed + 5, 2, 24, 3) - 0.5
    a = a * (1 + tone[board][..., None] + grain[..., None] * 0.07)
    # board seams: a thin dark groove with a lighter lip (cement paste squeezed out) just under it
    ly = yy % board_h
    seam = np.exp(-((ly - 0) / 1.1) ** 2) + np.exp(-((ly - board_h) / 1.1) ** 2)
    lip = np.exp(-((ly - 2.5) / 1.2) ** 2)
    wav = (bt.noise(seed + 6, scale=16, octaves=2) - 0.5) * 0.6
    a = a * (1 - (seam * (0.22 + wav * 0.2))[..., None]) + (lip * 0.04)[..., None]
    # tie holes: two per metre, plugged with darker mortar, with a soft ring
    def ties(d, ox, oy):
        for cx, cy in ((64, 128), (192, 128)):
            d.ellipse((ox + cx - 7, oy + cy - 7, ox + cx + 7, oy + cy + 7), fill=1.0)

    tie = blur(wrapped(ties), 1.2)
    ring = np.clip(blur(wrapped(ties), 4) - tie, 0, 1)
    a = a * (1 - tie[..., None] * 0.38) - (ring * 0.05)[..., None]
    a = a * (1 - pores(seed + 7, 0.0009, 1.2)[..., None] * 0.22)
    return np.clip(a, 0, 1)


def wall_stained(base, seed=1201):
    a = base.copy()
    # water runs: vertical streaks that tile top to bottom
    runs = stretched_noise(seed, 18, 2, 3)
    runs = np.clip((runs - 0.52) * 3.2, 0, 1) ** 1.5
    a = mix(a, a * col((178, 165, 140)) * 1.0, runs * 0.55)
    # efflorescence: chalky white bleeding out of the board seams
    yy = np.mgrid[0:RES, 0:RES][0]
    ly = yy % (RES // 8)
    eff = np.exp(-((ly - 3) / 3.0) ** 2) * np.clip((bt.noise(seed + 2, scale=10, octaves=3) - 0.55) * 4, 0, 1)
    a = mix(a, col((214, 212, 204)), eff * 0.5)
    # broad grime
    g = np.clip((bt.noise(seed + 3, scale=4, octaves=4) - 0.4) * 1.6, 0, 1)
    a = a * (1 - g[..., None] * 0.18) * col((250, 246, 236))
    return np.clip(a, 0, 1)


def floor(src, seed=1301):
    a = concrete((146, 146, 141), seed, mottle=0.06, warm=0.01)
    ag = aggregate(src, seed + 9)
    a = a * (1 + ag[..., None] * 0.035)
    # power-trowel swirls: faint overlapping arcs, slightly glossier and lighter
    rng = np.random.default_rng(seed)
    arcs = []
    for _ in range(26):
        cx, cy, r = rng.uniform(0, RES), rng.uniform(0, RES), rng.uniform(40, 110)
        start = rng.uniform(0, 360)
        arcs.append((cx, cy, r, start, start + rng.uniform(40, 140)))

    def draw(d, ox, oy):
        for cx, cy, r, s, e in arcs:
            d.arc((ox + cx - r, oy + cy - r, ox + cx + r, oy + cy + r), s, e, fill=1.0, width=2)

    sw = blur(wrapped(draw), 1.6)
    a = a + sw[..., None] * 0.03
    # tyre scuffs: long soft dark streaks
    sc = stretched_noise(seed + 4, 1, 10, 3)
    sc = np.clip((sc - 0.62) * 3.0, 0, 1)
    a = a * (1 - sc[..., None] * 0.12)
    a = a * (1 - pores(seed + 5, 0.0012, 1.0)[..., None] * 0.25)
    return np.clip(a, 0, 1), np.clip(0.35 + sw * 0.25 - sc * 0.1, 0, 1)


def floor_oil(base, seed=1401):
    yy, xx = np.mgrid[0:RES, 0:RES] / RES
    n = bt.noise(seed, scale=4, octaves=4)
    # an irregular blot centred in the block (oil drips land in one place), plus satellite drops
    r = np.hypot(xx - 0.5, (yy - 0.5) * 1.15)
    blot = np.clip((0.34 - r + (n - 0.5) * 0.3) * 5, 0, 1)
    rng = np.random.default_rng(seed)

    def drops(d, ox, oy):
        for _ in range(5):
            x, y, s = rng.uniform(0.15, 0.85) * RES, rng.uniform(0.15, 0.85) * RES, rng.uniform(3, 9)
            d.ellipse((ox + x - s, oy + y - s, ox + x + s, oy + y + s), fill=1.0)

    blot = np.maximum(blot, blur(wrapped(drops), 2.5) * 0.6)
    blot = blur(blot, 4.0) * (0.75 + 0.5 * bt.noise(seed + 7, scale=16, octaves=3)[..., None][..., 0])
    core = np.clip((blot - 0.55) * 2.5, 0, 1)
    halo = np.clip(blot * 1.6, 0, 1) - core
    a = base * (1 - halo[..., None] * 0.35) * mix(np.ones(3, np.float32), col((205, 190, 160)), halo * 0.6)
    a = mix(a, col((52, 50, 46)) + (n[..., None] - 0.5) * 0.06, core * 0.7)
    # a faint sheen on the fresh middle of the stain
    a = a + (np.clip((n - 0.5) * 3, 0, 1) * core * 0.04)[..., None]
    gloss = np.clip(0.35 + core * 0.45, 0, 1)
    return np.clip(a, 0, 1), gloss


def floor_cracked(base, src, seed=1501):
    a = base * 0.94
    cr = cracks(seed, 3, 22, width=1.2)
    # spalled pits: shallow broken patches exposing aggregate
    n = bt.noise(seed + 1, scale=5, octaves=4)
    spall = np.clip((n - 0.6) * 5, 0, 1)
    ag = aggregate(src, seed + 3)
    pit = col((112, 110, 104)) * (1 + ag[..., None] * 0.18)
    a = mix(a, pit, spall * 0.85)
    edge = np.clip(blur(spall, 1.5) - spall, 0, 1)
    a = a * (1 - edge[..., None] * 0.6)
    a = a * (1 - cr[..., None] * 0.45)
    # dirt collects around the cracks
    d = blur(cr, 3.5)
    a = a * (1 - np.clip(d * 2.2, 0, 1)[..., None] * 0.14)
    return np.clip(a, 0, 1)


def line(base, seed=1601):
    paint = col((214, 168, 40))
    n = bt.noise(seed, scale=4, octaves=3)
    wear = np.clip((n - 0.58) * 3.0, 0, 1)              # rubbed through to the slab
    rub = stretched_noise(seed + 2, 1, 12, 3)
    rub = np.clip((rub - 0.58) * 2.5, 0, 1)           # dark tyre rubbing
    fine = bt.noise(seed + 3, scale=48, octaves=2) - 0.5
    a = paint * (1 + fine[..., None] * 0.08)
    a = mix(a, base, wear * 0.8)
    a = a * (1 - rub[..., None] * 0.22)
    # the paint is thin: the slab's pores show through as dark specks
    a = a * (1 - pores(seed + 4, 0.0008, 0.8)[..., None] * 0.25)
    return np.clip(a, 0, 1)


def ceiling(src, seed=1701):
    a = concrete((122, 122, 118), seed, mottle=0.06)
    # plywood formwork: grain imprint along x and one panel joint per metre
    grain = stretched_noise(seed + 2, 3, 20, 3) - 0.5
    a = a * (1 + grain[..., None] * 0.05)
    yy, xx = np.mgrid[0:RES, 0:RES]
    joint = np.exp(-((yy - 0) / 1.2) ** 2) + np.exp(-((yy - RES) / 1.2) ** 2)
    fin = np.exp(-((yy - 2.2) / 1.0) ** 2)
    a = a * (1 - joint[..., None] * 0.25) + (fin * 0.035)[..., None]
    # a nail-head row along the joint
    def nails(d, ox, oy):
        for x in range(16, RES, 64):
            d.ellipse((ox + x - 1.5, oy + 6 - 1.5, ox + x + 1.5, oy + 6 + 1.5), fill=1.0)

    a = a * (1 - blur(wrapped(nails), 0.5)[..., None] * 0.3)
    # exhaust soot: broad dark clouds
    soot = np.clip((bt.noise(seed + 4, scale=3, octaves=4) - 0.45) * 2.0, 0, 1)
    a = a * (1 - soot[..., None] * 0.2)
    a = a * (1 - pores(seed + 6, 0.0008, 1.0)[..., None] * 0.2)
    return np.clip(a, 0, 1)


def column(src, seed=1801):
    a = concrete((164, 163, 158), seed, mottle=0.045)
    # steel-form smoothness: very faint vertical streaks
    st = stretched_noise(seed + 2, 24, 2, 3) - 0.5
    a = a * (1 + st[..., None] * 0.04)
    # arrises: each face darkens toward its vertical edges, which on a column reads as a chamfered corner
    xx = np.mgrid[0:RES, 0:RES][1]
    e = np.minimum(xx, RES - 1 - xx).astype(np.float32)
    arris = np.exp(-(e / 5.0) ** 2) * 0.22 + np.exp(-(e / 16.0) ** 2) * 0.06
    a = a * (1 - arris[..., None])
    # chips along the arris
    chips = np.clip((bt.noise(seed + 4, scale=32, octaves=2) - 0.68) * 6, 0, 1) * (e < 6)
    a = a * (1 - chips[..., None] * 0.3)
    # scuffs (vehicles, trolleys)
    sc = np.clip((stretched_noise(seed + 5, 6, 1, 3) - 0.66) * 4, 0, 1)
    a = a * (1 - sc[..., None] * 0.12)
    a = a * (1 - pores(seed + 6, 0.0007, 1.0)[..., None] * 0.2)
    return np.clip(a, 0, 1)


def hazard(seed=1901):
    yy, xx = np.mgrid[0:RES, 0:RES]
    period = 64                                   # divides 256, so the diagonal tiles in both directions
    t = ((xx + yy) % period) / period
    stripe = (t < 0.5).astype(np.float32)
    # soften the stripe edge a touch (paint, not vector art)
    stripe = blur(stripe, 0.7)
    yellow, black = col((222, 176, 34)), col((28, 27, 25))
    fine = bt.noise(seed, scale=40, octaves=2) - 0.5
    a = mix(black, yellow, stripe) * (1 + fine[..., None] * 0.08)
    # chipped paint: bare grey concrete showing through, and grime
    concrete_ = concrete((150, 150, 145), seed + 3, mottle=0.05)
    chips = np.clip((bt.noise(seed + 1, scale=14, octaves=3) - 0.68) * 6, 0, 1)
    a = mix(a, concrete_, chips * 0.9)
    grime = np.clip((bt.noise(seed + 2, scale=4, octaves=4) - 0.45) * 1.5, 0, 1)
    a = a * (1 - grime[..., None] * 0.22)
    return np.clip(a, 0, 1)


def mass(src, seed=2001):
    a = concrete((104, 104, 100), seed, mottle=0.09)
    ag = aggregate(src, seed + 4)
    a = a * (1 + ag[..., None] * 0.07)
    a = a * (1 - pores(seed + 2, 0.0025, 1.4)[..., None] * 0.25)
    return np.clip(a, 0, 1)


def add(out, src):
    w = wall(src)
    out["garage_wall"] = (w, 1.4, bt.spec_map(0.12))
    out["garage_wall_stained"] = (wall_stained(w), 1.4, bt.spec_map(0.15))
    f, fgloss = floor(src)
    out["garage_floor"] = (f, 1.0, bt.spec_map(fgloss))
    oil, ogloss = floor_oil(f)
    out["garage_floor_oil"] = (oil, 1.0, bt.spec_map(np.maximum(fgloss, ogloss)))
    out["garage_floor_cracked"] = (floor_cracked(f, src), 2.0, bt.spec_map(0.25))
    out["garage_line"] = (line(f), 1.0, bt.spec_map(0.4))
    out["garage_ceiling"] = (ceiling(src), 1.5, bt.spec_map(0.08))
    out["garage_column"] = (column(src), 1.2, bt.spec_map(0.2))
    out["garage_hazard"] = (hazard(), 1.0, bt.spec_map(0.35))
    out["garage_mass"] = (mass(src), 2.0, bt.spec_map(0.08))


if __name__ == "__main__":
    # preview: python3 tools/textures/recipes_level1.py <src> <out.png>
    import sys

    o = {}
    add(o, sys.argv[1] if len(sys.argv) > 1 else None)
    sheet = Image.new("RGB", (RES * 5 * 2, RES * 2 * 2))
    for i, n in enumerate(NAMES):
        im = bt.img(o[n][0])
        tile = Image.new("RGB", (RES * 2, RES * 2))
        for dx in (0, RES):
            for dy in (0, RES):
                tile.paste(im, (dx, dy))
        sheet.paste(tile, ((i % 5) * RES * 2, (i // 5) * RES * 2))
        a = o[n][0]
        print(n, "#%02x%02x%02x" % tuple(int(round(v * 255)) for v in a.reshape(-1, 3).mean(0)))
    sheet.save(sys.argv[2] if len(sys.argv) > 2 else "level1_preview.png")
