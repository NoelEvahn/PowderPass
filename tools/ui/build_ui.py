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
W, H = 512, 288


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
    font = ImageFont.truetype(OSD_FONT, 13)
    small = ImageFont.truetype(OSD_FONT, 11)
    white = (236, 236, 228, 215)
    shadow = (0, 0, 0, 150)

    def text(xy, s, f=font, fill=white):
        draw.text((xy[0] + 1, xy[1] + 1), s, font=f, fill=shadow)
        draw.text(xy, s, font=f, fill=fill)

    # REC with a blinking red dot (on for half the cycle)
    if frame < FRAMES // 2:
        draw.ellipse((22, 20, 32, 30), fill=(220, 30, 30, 230))
    text((38, 17), "REC")
    text((W - 52, 17), "SP")
    # battery
    draw.rectangle((W - 92, 21, W - 66, 31), outline=white)
    draw.rectangle((W - 65, 24, W - 63, 28), fill=white)
    draw.rectangle((W - 90, 23, W - 80, 29), fill=(236, 236, 228, 200))
    text((22, H - 32), "PLAY ▶", small)
    text((W - 168, H - 32), "AM 3:33  JAN.01 1996", small)
    if light:
        text((W // 2 - 22, H - 32), "LIGHT", small, (255, 226, 160, 200))


def vhs_frame(frame: int, light: bool) -> Image.Image:
    rng = np.random.default_rng(1996 + frame * 7 + (100 if light else 0))
    r = radial(W, H)
    rgba = np.zeros((H, W, 4), dtype=np.float64)
    # vignette: corners fall into shadow (much deeper through a flashlight)
    if light:
        vig = np.clip((r - 0.32) / 0.75, 0, 1) ** 1.3 * 0.9
    else:
        vig = np.clip((r - 0.55) / 0.8, 0, 1) ** 1.6 * 0.6
    alpha = vig.copy()
    col = np.zeros((H, W, 3))
    # a flashlight's warm hot spot in the middle (adds a faint glow over what it lights)
    if light:
        spot = np.clip(1 - r / 0.42, 0, 1) ** 2 * 0.1
        col = col * (1 - spot[..., None]) + np.array([255, 236, 190]) * spot[..., None]
        alpha = alpha + spot * (1 - alpha)
    # scanlines: every other row a little darker
    scan = np.zeros((H, W))
    scan[::2, :] = 0.07
    a2 = scan
    col = col * (1 - a2[..., None] / np.maximum(alpha + a2, 1e-6)[..., None] * 0)  # scanlines are black
    alpha = alpha + a2 * (1 - alpha)
    # grain: specks of light and dark
    g = rng.normal(0, 1, (H, W))
    bright = g > 1.45
    dark = g < -1.45
    ga = np.zeros((H, W))
    ga[bright] = rng.uniform(0.07, 0.22, bright.sum())
    ga[dark] = rng.uniform(0.08, 0.24, dark.sum())
    # film grain also comes in soft clumps, not only single specks
    clump = np.clip(np.array(Image.fromarray((rng.uniform(0, 255, (H // 2, W // 2))).astype(np.uint8)).resize((W, H), Image.BILINEAR), dtype=np.float64) / 255 - 0.5, 0, 1) * 0.12
    ga = np.maximum(ga, clump * (g > 0))
    gcol = np.zeros((H, W, 3))
    gcol[bright] = 235
    na = alpha + ga * (1 - alpha)
    col = (col * alpha[..., None] * (1 - ga[..., None]) + gcol * ga[..., None]) / np.maximum(na, 1e-6)[..., None]
    alpha = na
    # a tracking band: a thin bright noisy strip somewhere, now and then
    if frame in (1, 3):
        y0 = int(rng.integers(H // 5, H - H // 5))
        hgt = int(rng.integers(3, 7))
        band = np.zeros((H, W))
        noise = rng.uniform(0, 1, (hgt, W))
        band[y0:y0 + hgt, :] = (noise > 0.55) * rng.uniform(0.08, 0.2)
        col = (col * alpha[..., None] * (1 - band[..., None]) + 250 * band[..., None]) / np.maximum(alpha + band * (1 - alpha), 1e-6)[..., None]
        alpha = alpha + band * (1 - alpha)
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
