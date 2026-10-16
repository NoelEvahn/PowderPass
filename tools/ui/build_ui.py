"""Build the Backrooms pack's screen assets: the title font, the VHS camera overlays and the item definitions.

    python3 tools/ui/build_ui.py && python3 tools/audio/build_pack.py

Writes under pack/java/assets/backrooms/:
  font/title.json + textures/font/the_backrooms.png   "THE BACKROOMS" as one glyph (U+E000) of a bitmap font
  textures/misc/vhs_<n>.png                         camcorder overlay frames (grain, scanlines, vignette, OSD)
  textures/misc/vhs_light_<n>.png                   the same seen through a flashlight beam (dark tunnel, warm centre)
  items/vhs.json                                    the invisible head item that carries the overlay (empty model)
  items/flashlight.json, models/item/flashlight.json, textures/item/flashlight.png

Camera overlays are drawn stretched over the whole screen with nearest filtering, so they are made at a low
resolution on purpose: the grain comes out a few screen pixels wide, like tape, and the OSD letters blocky.
Deterministic (fixed seeds).
"""
from __future__ import annotations

import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, "pack", "java", "assets", "backrooms")
FONT_DIR = "/usr/share/fonts"
TITLE_FONT = os.path.join(FONT_DIR, "opentype/inter/InterDisplay-Light.otf")
OSD_FONT = os.path.join(FONT_DIR, "truetype/dejavu/DejaVuSansMono-Bold.ttf")
FRAMES = 4
W, H = 2048, 1152


def out(*parts):
    p = os.path.join(A, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------------------------------------- title font
def title():
    """THE BACKROOMS in a light, widely tracked display face, white (the text colour tints it)."""
    text = "THE BACKROOMS"
    size = 120
    font = ImageFont.truetype(TITLE_FONT, size)
    tracking = 34
    widths = [font.getlength(c) for c in text]
    total = int(sum(widths) + tracking * (len(text) - 1)) + 40
    img = Image.new("RGBA", (total, 160), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = 20
    for c, w in zip(text, widths):
        d.text((x, 10), c, font=font, fill=(255, 255, 255, 255))
        x += w + tracking
    img = img.crop(img.getbbox())
    # pad to a fixed height so the glyph's height/ascent below stay meaningful
    pad = Image.new("RGBA", (img.width + 8, 128), (0, 0, 0, 0))
    pad.paste(img, (4, (128 - img.height) // 2))
    pad.save(out("textures", "font", "the_backrooms.png"))
    write_json(out("font", "title.json"), {"providers": [
        {"type": "bitmap", "file": "backrooms:font/the_backrooms.png", "height": 10, "ascent": 8, "chars": [""]},
        {"type": "reference", "id": "minecraft:default"},
    ]})


# ---------------------------------------------------------------------------------------------- VHS overlay
def radial(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float64)
    nx = (x - w / 2) / (w / 2)
    ny = (y - h / 2) / (h / 2)
    return np.sqrt(nx * nx * 1.0 + ny * ny * 1.15)


def osd(draw: ImageDraw.ImageDraw, frame: int, light: bool):
    """Small camcorder text, inset from the curved edges (sizes relative to the frame, so it reads small)."""
    k = W / 512
    font = ImageFont.truetype(OSD_FONT, int(8 * k))
    small = ImageFont.truetype(OSD_FONT, int(7 * k))
    white = (236, 236, 228, 175)
    shadow = (0, 0, 0, 110)
    m = int(30 * k)

    def text(xy, s, f=font, fill=white):
        draw.text((xy[0] + k * 0.6, xy[1] + k * 0.6), s, font=f, fill=shadow)
        draw.text(xy, s, font=f, fill=fill)

    y = m
    if frame < FRAMES // 2:
        r = 3 * k
        draw.ellipse((m, y + 2 * k, m + 2 * r, y + 2 * k + 2 * r), fill=(220, 30, 30, 200))
    text((m + 9 * k, y), "REC")
    text((W - m - 14 * k, y), "SP")
    bx = W - m - 40 * k
    draw.rectangle((bx, y + 2 * k, bx + 16 * k, y + 8 * k), outline=white, width=max(1, int(k * 0.8)))
    draw.rectangle((bx + 16 * k, y + 4 * k, bx + 17.5 * k, y + 6 * k), fill=white)
    draw.rectangle((bx + 1.5 * k, y + 3.5 * k, bx + 9 * k, y + 6.5 * k), fill=white)
    yb = H - m - 8 * k
    text((m, yb), "PLAY \u25b6", small)
    stamp = "AM 3:33  JAN.01 1996"
    text((W - m - draw.textlength(stamp, font=small), yb), stamp, small)
    if light:
        text((W // 2 - 12 * k, yb), "LIGHT", small, (255, 226, 160, 170))


def vhs_frame(frame: int, light: bool) -> Image.Image:
    rng = np.random.default_rng(1996 + frame * 7 + (100 if light else 0))
    y, x = np.mgrid[0:H, 0:W].astype(np.float64)
    nx = (x - W / 2) / (W / 2)
    ny = (y - H / 2) / (H / 2)
    r = np.sqrt(nx * nx + ny * ny * 1.15)
    # curved CRT screen: the picture sits in a rounded, slightly bulging tube; outside it the bezel is black,
    # with a soft falloff into the curve (superellipse edge)
    edge = (np.abs(nx) ** 7 + np.abs(ny) ** 7) ** (1 / 7)
    bezel = np.clip((edge - 0.955) / 0.04, 0, 1)
    shade = np.clip((edge - 0.80) / 0.17, 0, 1) ** 2 * 0.45   # darkening as the glass curves away
    if light:
        vig = np.clip((r - 0.35) / 0.8, 0, 1) ** 1.4 * 0.8
    else:
        vig = np.clip((r - 0.6) / 0.9, 0, 1) ** 1.8 * 0.3
    alpha = np.maximum.reduce([vig, shade, bezel])
    col = np.zeros((H, W, 3))
    if light:
        spot = np.clip(1 - r / 0.42, 0, 1) ** 2 * 0.06
        col = col * (1 - spot[..., None]) + np.array([255, 236, 190]) * spot[..., None]
        alpha = alpha + spot * (1 - alpha)
    # faint scanlines (every 4th row at this resolution: same pitch as before, much lighter)
    scan = np.zeros((H, W))
    scan[(np.arange(H) % 4) < 1, :] = 0.035
    alpha = alpha + scan * (1 - alpha)
    # fine, sparse grain: single specks, low opacity
    g = rng.normal(0, 1, (H, W))
    bright = g > 2.6
    dark = g < -2.6
    ga = np.zeros((H, W))
    ga[bright] = rng.uniform(0.04, 0.10, bright.sum())
    ga[dark] = rng.uniform(0.04, 0.10, dark.sum())
    gcol = np.zeros((H, W, 3))
    gcol[bright] = 235
    na = alpha + ga * (1 - alpha)
    col = (col * alpha[..., None] * (1 - ga[..., None]) + gcol * ga[..., None]) / np.maximum(na, 1e-6)[..., None]
    alpha = na
    if frame in (1, 3):
        y0 = int(rng.integers(H // 5, H - H // 5))
        hgt = int(rng.integers(4, 10))
        band = np.zeros((H, W))
        band[y0:y0 + hgt, :] = (rng.uniform(0, 1, (hgt, W)) > 0.7) * 0.06
        col = (col * alpha[..., None] * (1 - band[..., None]) + 250 * band[..., None]) / np.maximum(alpha + band * (1 - alpha), 1e-6)[..., None]
        alpha = alpha + band * (1 - alpha)
    rgba = np.zeros((H, W, 4))
    rgba[..., :3] = np.clip(col, 0, 255)
    rgba[..., 3] = np.clip(alpha, 0, 1) * 255
    img = Image.fromarray(rgba.astype(np.uint8), "RGBA")
    osd(ImageDraw.Draw(img), frame, light)
    return img


def overlays():
    for light in (False, True):
        for f in range(FRAMES):
            name = f"vhs_light_{f}.png" if light else f"vhs_{f}.png"
            vhs_frame(f, light).save(out("textures", "misc", name), optimize=True)


# ---------------------------------------------------------------------------------------------- items
def items():
    write_json(out("items", "vhs.json"), {"model": {"type": "minecraft:empty"}})
    write_json(out("items", "flashlight.json"), {"model": {"type": "minecraft:model", "model": "backrooms:item/flashlight"}})
    write_json(out("models", "item", "flashlight.json"), {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "backrooms:item/flashlight"},
    })
    # 16x16 pixel art: a dark rubber torch body, a chrome head, a warm lens, a switch
    px = np.zeros((16, 16, 4), dtype=np.uint8)
    body, body_hi = (44, 44, 48, 255), (70, 70, 76, 255)
    chrome, chrome_hi = (150, 152, 158, 255), (205, 208, 214, 255)
    lens = (255, 226, 140, 255)
    for i in range(3, 10):          # body: a diagonal from bottom-left
        x, y = i, 15 - i
        for dx, dy in ((0, 0), (1, 0), (0, -1)):
            px[y + dy, x + dx] = body
        px[y - 1, x + 1] = body_hi
    for i in range(10, 13):         # head
        x, y = i, 15 - i
        for dx, dy in ((0, 0), (1, 0), (0, -1), (1, -1), (-1, 0)):
            if 0 <= y + dy < 16 and 0 <= x + dx < 16:
                px[y + dy, x + dx] = chrome
        px[y - 1, x] = chrome_hi
    px[2, 13], px[1, 13], px[2, 14], px[1, 14] = lens, lens, lens, (255, 245, 200, 255)
    px[9, 6] = (190, 40, 40, 255)   # switch
    Image.fromarray(px, "RGBA").save(out("textures", "item", "flashlight.png"))


if __name__ == "__main__":
    title()
    overlays()
    items()
    for d, _, fs in os.walk(A):
        for f in fs:
            if d.endswith(("misc", "font")) or f in ("vhs.json", "flashlight.json", "flashlight.png"):
                p = os.path.join(d, f)
                print(f"{os.path.relpath(p, ROOT):70s} {os.path.getsize(p) / 1e3:8.1f} kB")
