#!/usr/bin/env python3
"""
Equirectangular → cubemap baker for the ESO Brunier Milky Way Panorama.
T701 + T702 / 07-celestial-background.

Source asset: ESO press release 0932 — "Milky Way Panorama" by Serge Brunier.
Released under CC BY 4.0; attribution required as "ESO/S. Brunier" (verbatim).
  https://www.eso.org/public/images/eso0932a/
  https://cdn.eso.org/images/original/eso0932a.tif  (6000×3000, 28 MB)

The TIFF is *not* committed to the repo (28 MB binary). Re-running the bake:

    cd tools/bake-stars-cubemap
    curl -fsSL -o /tmp/eso0932a.tif https://cdn.eso.org/images/original/eso0932a.tif
    python3 bake_eso.py /tmp/eso0932a.tif

Output: 6 PNG files at FACE_SIZE × FACE_SIZE bundled at
`shared/src/commonMain/composeResources/files/stars/{px,nx,py,ny,pz,nz}.png`
in Filament's cubemap face order (+X, -X, +Y, -Y, +Z, -Z). Replaces whatever
PNGs are at those paths (the procedural placeholder from `generate_placeholder.py`
on first run; subsequent re-bakes overwrite the previous ESO PNGs).

Math: each cube face's pixels map to a ray direction; the ray is converted to
spherical (lat, lon); the equirectangular is sampled bilinearly. Standard
OpenGL-cubemap face-axis convention. Numpy-vectorized over each face for
speed (~1 second per face on a modern Mac).
"""

import os
import sys
import numpy as np
from PIL import Image

FACE_SIZE = 1024
OUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..",
    "shared", "src", "commonMain", "composeResources", "files", "stars",
)
# Filament's cubemap face order: [+X, -X, +Y, -Y, +Z, -Z].
FACES = ["px", "nx", "py", "ny", "pz", "nz"]


def equirect_to_cubemap_face(equirect: np.ndarray, face_idx: int, face_size: int) -> np.ndarray:
    """
    Sample one cubemap face from a 2:1 equirectangular image. Returns an
    (face_size, face_size, 3) uint8 array.

    Face direction conventions (standard OpenGL cubemap, per
    GL_TEXTURE_CUBE_MAP_*_AXIS spec):

        +X (face 0): looking right;  s = -Z, t = -Y
        -X (face 1): looking left;   s = +Z, t = -Y
        +Y (face 2): looking up;     s = +X, t = +Z
        -Y (face 3): looking down;   s = +X, t = -Z
        +Z (face 4): looking forward;s = +X, t = -Y
        -Z (face 5): looking back;   s = -X, t = -Y

    For each pixel (s, t) in the face's 2D coords (range [-1, +1] with the
    +0.5 pixel-centre offset), the unnormalized direction is
    `face_normal + s·s_axis + t·t_axis`. Normalize, then convert to (lat,
    lon) and look up in the equirectangular.
    """
    H, W = equirect.shape[:2]

    # Pixel-centre coords in [-1, +1].
    coord = (np.arange(face_size) + 0.5) / face_size * 2.0 - 1.0
    s, t = np.meshgrid(coord, coord)  # both shape (face_size, face_size)

    if face_idx == 0:    # +X
        dx, dy, dz = np.ones_like(s), -t, -s
    elif face_idx == 1:  # -X
        dx, dy, dz = -np.ones_like(s), -t, s
    elif face_idx == 2:  # +Y
        dx, dy, dz = s, np.ones_like(s), t
    elif face_idx == 3:  # -Y
        dx, dy, dz = s, -np.ones_like(s), -t
    elif face_idx == 4:  # +Z
        dx, dy, dz = s, -t, np.ones_like(s)
    elif face_idx == 5:  # -Z
        dx, dy, dz = -s, -t, -np.ones_like(s)
    else:
        raise ValueError(f"face_idx out of range: {face_idx}")

    # Normalize the direction vectors.
    mag = np.sqrt(dx * dx + dy * dy + dz * dz)
    dx /= mag
    dy /= mag
    dz /= mag

    # Spherical coords. lat ∈ [-π/2, +π/2], lon ∈ [-π, +π].
    lat = np.arcsin(np.clip(dy, -1.0, 1.0))
    lon = np.arctan2(dx, dz)

    # Equirectangular pixel coords (sub-pixel accurate).
    # lon = 0 maps to image centre x; lon = +π/-π wraps at image edges.
    # lat = 0 maps to image centre y; lat = +π/2 (north) at top (y=0).
    ex = (lon + np.pi) / (2 * np.pi) * W
    ey = (np.pi / 2 - lat) / np.pi * H

    # Wrap horizontally (longitude is periodic), clamp vertically (poles are unique).
    ex = np.mod(ex, W)
    ey = np.clip(ey, 0.0, H - 1.0001)

    # Bilinear sampling.
    x0 = np.floor(ex).astype(int) % W
    x1 = (x0 + 1) % W
    y0 = np.floor(ey).astype(int)
    y1 = np.minimum(y0 + 1, H - 1)
    fx = (ex - np.floor(ex))[..., np.newaxis]  # broadcast over channels
    fy = (ey - np.floor(ey))[..., np.newaxis]

    # eq is uint8; promote to float for the lerp, then cast back.
    eq = equirect.astype(np.float32)
    p00 = eq[y0, x0]
    p01 = eq[y0, x1]
    p10 = eq[y1, x0]
    p11 = eq[y1, x1]

    top = p00 * (1.0 - fx) + p01 * fx
    bot = p10 * (1.0 - fx) + p11 * fx
    pixels = top * (1.0 - fy) + bot * fy

    return np.clip(pixels, 0.0, 255.0).astype(np.uint8)


def main():
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <equirectangular_image>", file=sys.stderr)
        sys.exit(2)

    src_path = sys.argv[1]
    print(f"Loading {src_path} …")
    src = Image.open(src_path).convert("RGB")
    print(f"  source: {src.size[0]}×{src.size[1]} {src.mode}")
    if src.size[0] != 2 * src.size[1]:
        print(f"  WARN: source is not 2:1 aspect ratio; output will be distorted")
    equirect = np.array(src)

    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Baking 6 cubemap faces at {FACE_SIZE}×{FACE_SIZE} → {OUT_DIR}")
    for i, face in enumerate(FACES):
        pixels = equirect_to_cubemap_face(equirect, i, FACE_SIZE)
        out_path = os.path.join(OUT_DIR, f"{face}.png")
        Image.fromarray(pixels).save(out_path, "PNG", optimize=True)
        size_kb = os.path.getsize(out_path) // 1024
        print(f"  {face}.png  {size_kb} KB")
    total_kb = sum(
        os.path.getsize(os.path.join(OUT_DIR, f"{f}.png")) for f in FACES
    ) // 1024
    print(f"Total: {total_kb} KB across 6 faces")


if __name__ == "__main__":
    main()
