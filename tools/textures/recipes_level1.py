"""Texture recipes for Level 1 (Habitable Zone parking garage). Called by build_textures.build(); add(out, src) must put an entry
(rgb float array RES x RES x 3, normal strength or None, spec image or None) into out for every name below.

PLACEHOLDER: flat noisy colour from custom-blocks.json until the real recipes are written."""
from __future__ import annotations

import json
import os

import numpy as np

import build_textures as bt

NAMES = ["garage_wall", "garage_wall_stained", "garage_floor", "garage_floor_oil", "garage_floor_cracked", "garage_line", "garage_ceiling", "garage_column", "garage_hazard", "garage_mass"]


def add(out, src):
    with open(bt.DEFS, encoding="utf-8") as f:
        defs = json.load(f)["blocks"]
    for i, name in enumerate(NAMES):
        c = defs[name]["color"]
        rgb = np.array([int(c[k:k + 2], 16) for k in (1, 3, 5)], np.float32) / 255.0
        a = rgb * (0.92 + 0.16 * bt.noise(900 + i, scale=8)[..., None])
        out[name] = (a, 1.5, bt.spec_map(0.1))
