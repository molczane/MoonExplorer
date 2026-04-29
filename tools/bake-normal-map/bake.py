#!/usr/bin/env python3
"""bake.py — Bake a tangent-space normal map from an equirectangular lunar elevation map.

Input:  ldem_16_uint.tif (5760x2880, 16-bit unsigned, 0.5 m / integer per NASA SVS CGI Moon Kit)
Output: moon_normal_<tier>.png for each requested tier (default 2k + 8k)

Tangent-space convention (matches MoonRenderer's UvSphere):
  T = dP/du  (east)
  B = dP/dv  (south, since v=0 is the north pole)
  N = radial outward
  R = (nx + 1) / 2,  G = (ny + 1) / 2,  B = (nz + 1) / 2  (OpenGL / Filament default)

Equirectangular distortion: a u-pixel spans (2*pi*R*cos(phi))/W meters, a v-pixel spans (pi*R)/H.
Near the poles cos(phi) -> 0, so we clamp it (POLE_CLAMP); polar normals are noise at this LDEM
resolution either way.

Usage:
  python3 bake.py --input ldem_16_uint.tif --out-dir tools/build-ktx2/.cache
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# Mean lunar radius in meters (IAU).
MOON_RADIUS_M = 1_737_400.0
# LDEM_*_uint encoding: 0.5 meters per integer count per NASA SVS CGI Moon Kit.
LDEM_SCALE_M = 0.5
# Polar clamp on cos(latitude). At the poles a u-pixel spans no real distance, so the gradient
# blows up; clamping ~1e-3 caps the slope while keeping mid-latitude values exact.
POLE_CLAMP = 1e-3


def load_ldem(path: Path) -> np.ndarray:
    """Load the LDEM TIFF as a (H, W) float32 elevation array (meters).

    The integer-to-meter offset is irrelevant for normal-map baking — gradients cancel any constant
    offset. Anything that uses absolute heights (e.g. future displacement) must consult the active
    LDEM encoding at https://svs.gsfc.nasa.gov/4720.
    """
    img = Image.open(path)
    arr = np.asarray(img, dtype=np.float32)
    if arr.ndim != 2:
        raise SystemExit(f"Expected single-channel LDEM, got shape {arr.shape}")
    return arr * LDEM_SCALE_M


def bake_normal_map(height_m: np.ndarray) -> np.ndarray:
    """Return an (H, W, 3) float32 normal map in [0, 1], packed as (R, G, B) = (nx, ny, nz)*0.5+0.5.

    Convention (T, B, N) = (east, south, radial). The bumped tangent-space normal is
    normalize(-dh/du_m, -dh/dv_m, 1).
    """
    H, W = height_m.shape

    # Latitude per row: v=0 is the north pole, v=H-1 is the south pole.
    v = np.arange(H, dtype=np.float64)
    lat = np.pi / 2.0 - np.pi * (v + 0.5) / H
    cos_lat = np.maximum(np.cos(lat), POLE_CLAMP)
    du_m = (2.0 * np.pi * MOON_RADIUS_M * cos_lat / W).astype(np.float32)  # shape (H,)
    dv_m = np.float32(np.pi * MOON_RADIUS_M / H)

    # Central differences. u wraps periodically (longitude); v clamps at the poles.
    h_left = np.roll(height_m, 1, axis=1)
    h_right = np.roll(height_m, -1, axis=1)
    dh_du_m = ((h_right - h_left) * 0.5) / du_m[:, None]

    h_north = np.empty_like(height_m)
    h_south = np.empty_like(height_m)
    h_north[1:, :] = height_m[:-1, :]
    h_north[0, :] = height_m[0, :]
    h_south[:-1, :] = height_m[1:, :]
    h_south[-1, :] = height_m[-1, :]
    dh_dv_m = ((h_south - h_north) * 0.5) / dv_m

    nx = -dh_du_m
    ny = -dh_dv_m
    nz = np.ones_like(nx)
    inv_len = 1.0 / np.sqrt(nx * nx + ny * ny + nz * nz)
    nx *= inv_len
    ny *= inv_len
    nz *= inv_len

    rgb = np.stack(
        [nx * 0.5 + 0.5, ny * 0.5 + 0.5, nz * 0.5 + 0.5],
        axis=-1,
    )
    return rgb.astype(np.float32)


def to_uint8(rgb_float: np.ndarray) -> np.ndarray:
    return np.clip(np.round(rgb_float * 255.0), 0, 255).astype(np.uint8)


def parse_tier(spec: str) -> tuple[str, int, int]:
    name, dims = spec.split(":", 1)
    w_s, h_s = dims.lower().split("x")
    return name, int(w_s), int(h_s)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Bake a tangent-space normal map from a lunar LDEM.")
    p.add_argument("--input", type=Path, required=True, help="Path to ldem_16_uint.tif")
    p.add_argument("--out-dir", type=Path, required=True, help="Output directory for moon_normal_<tier>.png")
    p.add_argument(
        "--tiers",
        nargs="+",
        default=["2k:2048x1024", "8k:8192x4096"],
        help='Output tiers as <name>:<W>x<H> pairs (default: "2k:2048x1024 8k:8192x4096")',
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    if not args.input.is_file():
        print(f"error: {args.input} not found", file=sys.stderr)
        return 1
    args.out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading {args.input} ...")
    height_m = load_ldem(args.input)
    print(f"  shape = {height_m.shape}, range [{height_m.min():.1f}, {height_m.max():.1f}] m")

    print("Baking normal at native LDEM resolution ...")
    rgb_native = bake_normal_map(height_m)
    base = Image.fromarray(to_uint8(rgb_native), mode="RGB")
    print(f"  baked at {base.size}")

    for spec in args.tiers:
        try:
            name, tw, th = parse_tier(spec)
        except ValueError:
            print(f"error: bad tier spec {spec!r}; expected <name>:<W>x<H>", file=sys.stderr)
            return 2
        out = args.out_dir / f"moon_normal_{name}.png"
        print(f"  resampling -> {tw}x{th}  ->  {out}")
        base.resize((tw, th), resample=Image.Resampling.BICUBIC).save(out, format="PNG", optimize=True)

    print("done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
