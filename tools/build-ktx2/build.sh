#!/usr/bin/env bash
# build.sh — Encode the four KTX2 / Basis Universal artifacts for the Moon renderer.
#
#   Albedo (2K/8K)  ETC1S, sRGB transfer fn, mip chain
#   Normal (2K/8K)  UASTC mode 2 + RDO + zstd L18, linear transfer fn, mip chain
#
# Inputs (under .cache/, populated by T104 + T105):
#   lroc_color_poles_{2k,8k}.tif   (NASA SVS albedo source)
#   moon_normal_{2k,8k}.png        (baked by ../bake-normal-map/bake.py)
#
# Outputs (under .cache/):
#   moon_albedo_{2k,8k}.ktx2
#   moon_normal_{2k,8k}.ktx2
#
# Prerequisites:
#   ./install-toktx.sh      (vendors toktx into bin/<os>-<arch>/)
#   Python 3.10+ with Pillow (used by tif2png.py to convert the source TIFFs to PNG, since
#   toktx itself does not read TIFF).

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
CACHE="${SCRIPT_DIR}/.cache"
OS_LOWER="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
TOKTX="${SCRIPT_DIR}/bin/${OS_LOWER}-${ARCH}/toktx"

if [ ! -x "$TOKTX" ]; then
  echo "error: toktx not vendored at $TOKTX" >&2
  echo "       run ./install-toktx.sh first." >&2
  exit 1
fi
if [ ! -d "$CACHE" ]; then
  echo "error: cache dir missing at $CACHE — run T104 (download SVS TIFFs) and T105 (bake.py) first." >&2
  exit 2
fi

echo "Using $(basename "$TOKTX"):"
"$TOKTX" --version 2>&1 | head -1

# 1. Convert albedo TIFFs -> 8-bit PNG (toktx reads PNG/JPG/Netpbm only).
echo
echo "[1/2] Converting albedo TIFFs -> 8-bit PNG ..."
python3 "${SCRIPT_DIR}/tif2png.py" --in-dir "$CACHE" --out-dir "$CACHE" --tiers 2k 8k

# 2. Encode KTX2 / Basis Universal.
echo
echo "[2/2] Encoding KTX2 ..."
for tier in 2k 8k; do
  ALBEDO_IN="${CACHE}/moon_albedo_${tier}.png"
  ALBEDO_OUT="${CACHE}/moon_albedo_${tier}.ktx2"
  NORMAL_IN="${CACHE}/moon_normal_${tier}.png"
  NORMAL_OUT="${CACHE}/moon_normal_${tier}.ktx2"

  for f in "$ALBEDO_IN" "$NORMAL_IN"; do
    [ -f "$f" ] || { echo "error: missing input $f" >&2; exit 3; }
  done

  rm -f "$ALBEDO_OUT" "$NORMAL_OUT"

  echo "  $(basename "$ALBEDO_OUT")  <-  $(basename "$ALBEDO_IN")  (ETC1S, sRGB)"
  "$TOKTX" --t2 --bcmp --genmipmap --assign_oetf srgb \
    "$ALBEDO_OUT" "$ALBEDO_IN"

  echo "  $(basename "$NORMAL_OUT")  <-  $(basename "$NORMAL_IN")  (UASTC L2 + RDO + zstd L18, linear)"
  "$TOKTX" --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap --assign_oetf linear \
    "$NORMAL_OUT" "$NORMAL_IN"
done

echo
echo "done. Outputs:"
ls -la "${CACHE}"/*.ktx2 2>&1
