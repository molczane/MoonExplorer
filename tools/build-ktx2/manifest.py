#!/usr/bin/env python3
"""manifest.py — Generate manifest.json describing the HD (8 K) KTX2 tier.

Schema matches `AssetManifest` (T110) — `Json { ignoreUnknownKeys = true }`-tolerant:

    {
      "version": "2026-04-29-1",
      "albedo": { "url", "sha256", "sizeBytes", "width", "height" },
      "normal": { "url", "sha256", "sizeBytes", "width", "height" }
    }

URLs follow ADR-0010's pattern with an `<owner>/MoonExplorer` placeholder. Search-replace
the placeholder before T109 (the GH Releases upload).

Usage:
  python3 manifest.py --in-dir .cache --out .cache/manifest.json
  python3 manifest.py --in-dir .cache --out .cache/manifest.json --owner ernest-molczan --release-tag assets-v1
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from datetime import date
from pathlib import Path

KTX2_MAGIC = b"\xabKTX 20\xbb\r\n\x1a\n"
# 9 uint32s after the 12-byte magic: vkFormat, typeSize, pixelWidth, pixelHeight,
# pixelDepth, layerCount, faceCount, levelCount, supercompression.
KTX2_HEADER_FMT = "<IIIIIIIII"


def parse_ktx2_dims(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        magic = f.read(12)
        if magic != KTX2_MAGIC:
            raise SystemExit(f"error: not a KTX2 file (bad magic): {path}")
        fields = struct.unpack(KTX2_HEADER_FMT, f.read(struct.calcsize(KTX2_HEADER_FMT)))
        _vk, _typesize, w, h, _d, _layers, _faces, _levels, _supercomp = fields
        return w, h


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def make_entry(path: Path, base_url: str) -> dict:
    w, h = parse_ktx2_dims(path)
    return {
        "url": f"{base_url}/{path.name}",
        "sha256": sha256_of(path),
        "sizeBytes": path.stat().st_size,
        "width": w,
        "height": h,
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--in-dir", type=Path, required=True, help="Directory containing the 8 K KTX2 files")
    p.add_argument("--out", type=Path, required=True, help="Path to write manifest.json")
    p.add_argument("--owner", default="<owner>", help="GitHub owner (placeholder default per ADR-0010)")
    p.add_argument("--repo", default="MoonExplorer", help="GitHub repo name (default: MoonExplorer)")
    p.add_argument("--release-tag", default="assets-v1", help="GH Release tag (ADR-0010: assets-v<N>)")
    p.add_argument(
        "--version",
        default=None,
        help="Manifest version string (default: today's date + '-1', e.g. 2026-04-29-1)",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    if not args.in_dir.is_dir():
        print(f"error: {args.in_dir} is not a directory", file=sys.stderr)
        return 1

    albedo = args.in_dir / "moon_albedo_8k.ktx2"
    normal = args.in_dir / "moon_normal_8k.ktx2"
    for f in (albedo, normal):
        if not f.is_file():
            print(f"error: missing input {f} — run build.sh first", file=sys.stderr)
            return 2

    base_url = f"https://github.com/{args.owner}/{args.repo}/releases/download/{args.release_tag}"
    version = args.version or f"{date.today().isoformat()}-1"

    manifest = {
        "version": version,
        "albedo": make_entry(albedo, base_url),
        "normal": make_entry(normal, base_url),
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(f"wrote {args.out} (version {version})")
    print(json.dumps(manifest, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
