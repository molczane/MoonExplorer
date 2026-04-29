#!/usr/bin/env python3
"""tif2png.py — Convert NASA SVS albedo TIFFs to 8-bit PNG for toktx ingestion.

toktx (KTX-Software) reads .png/.jpg/.pam/.pgm/.ppm only — TIFF is not supported. This helper
converts the lroc_color_poles_<tier>.tif TIFFs that T104 downloaded into 8-bit RGB PNGs that
build.sh feeds to toktx.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--in-dir", type=Path, required=True, help="Directory holding the source TIFFs")
    p.add_argument("--out-dir", type=Path, required=True, help="Directory to write PNG outputs")
    p.add_argument(
        "--tiers",
        nargs="+",
        default=["2k", "8k"],
        help="Tier suffixes (default: 2k 8k). Sources read as lroc_color_poles_<tier>.tif",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    for tier in args.tiers:
        src = args.in_dir / f"lroc_color_poles_{tier}.tif"
        dst = args.out_dir / f"moon_albedo_{tier}.png"
        if not src.is_file():
            print(f"error: {src} not found", file=sys.stderr)
            return 1
        with Image.open(src) as im:
            if im.mode != "RGB":
                im = im.convert("RGB")
            im.save(dst, format="PNG", optimize=True, compress_level=6)
            print(f"  {dst.name}: {im.size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
