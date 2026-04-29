#!/usr/bin/env python3
"""
Procedural starfield cubemap generator — T702 placeholder.

Generates 6 face PNGs that ship in `shared/src/commonMain/composeResources/files/stars/`
as the visual stand-in until the real ESO Milky Way Panorama (T701) is baked in. Each
face is FACE_SIZE × FACE_SIZE black with ~STARS_PER_FACE random stars — most dim, a few
bright. Seams at cubemap face boundaries are visible (clearly procedural, the user can
tell at a glance the real bake hasn't landed yet); matching seams properly would require
an equirectangular bake which is out of scope for the placeholder.

Reproducibility: deterministic via a fixed RNG seed per face (seeds 1–6 for px / nx / py
/ ny / pz / nz). Re-running this script overwrites the existing PNGs with byte-identical
output.

Usage:
    cd tools/bake-stars-cubemap
    python3 generate_placeholder.py
"""

import os
import random
from PIL import Image, ImageDraw

FACE_SIZE = 512
STARS_PER_FACE = 700
OUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..",
    "shared", "src", "commonMain", "composeResources", "files", "stars",
)
# Filament cubemap face order: [+X, -X, +Y, -Y, +Z, -Z].
FACES = ["px", "nx", "py", "ny", "pz", "nz"]


def make_face(seed: int) -> Image.Image:
    rng = random.Random(seed)
    img = Image.new("RGBA", (FACE_SIZE, FACE_SIZE), (0, 0, 0, 255))
    draw = ImageDraw.Draw(img)
    for _ in range(STARS_PER_FACE):
        x = rng.randint(0, FACE_SIZE - 1)
        y = rng.randint(0, FACE_SIZE - 1)
        # Power-law brightness distribution: most stars dim, a few bright.
        base = int(255 * (rng.random() ** 4))
        # Slight per-channel jitter so the field doesn't read as uniformly grey.
        r = max(0, min(255, base + rng.randint(-20, 20)))
        g = max(0, min(255, base + rng.randint(-10, 10)))
        b = max(0, min(255, base + rng.randint(-5, 25)))
        # ~5% of bright stars get a 2-px halo so the eye picks them up.
        if rng.random() < 0.05 and base > 100:
            draw.ellipse((x - 1, y - 1, x + 1, y + 1), fill=(r, g, b, 255))
        else:
            img.putpixel((x, y), (r, g, b, 255))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Writing 6 face PNGs to {OUT_DIR}")
    for i, face in enumerate(FACES):
        img = make_face(seed=i + 1)
        path = os.path.join(OUT_DIR, f"{face}.png")
        img.save(path, "PNG", optimize=True)
        print(f"  {face}.png  {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
