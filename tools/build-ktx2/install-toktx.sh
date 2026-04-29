#!/usr/bin/env bash
# install-toktx.sh — Vendor `toktx` from KTX-Software's Mac .pkg into tools/build-ktx2/bin/.
#
# Mirrors the matc download-on-demand pattern (tools/matc/<ver>/<os>/). KTX-Software is not
# published to Homebrew; the official Mac artifact is a distribution .pkg installer that
# requires admin to install globally. This script extracts only the toktx binary and the
# libktx dylib from the .pkg payload using pkgutil --expand + cpio (no admin).
#
# Usage:
#   ./install-toktx.sh                      # default pin (v4.4.2)
#   KTX_VERSION=4.4.2 ./install-toktx.sh
#
# Output:
#   tools/build-ktx2/bin/<os>-<arch>/toktx          (gitignored)
#   tools/build-ktx2/bin/<os>-<arch>/libktx*.dylib  (gitignored, found via @executable_path rpath)

set -euo pipefail

VERSION="${KTX_VERSION:-4.4.2}"
OS_NAME="$(uname -s)"
ARCH="$(uname -m)"

case "$OS_NAME" in
  Darwin)
    OS_LOWER="darwin"
    case "$ARCH" in
      arm64)  PKG_NAME="KTX-Software-${VERSION}-Darwin-arm64.pkg" ;;
      x86_64) PKG_NAME="KTX-Software-${VERSION}-Darwin-x86_64.pkg" ;;
      *)      echo "error: unsupported arch on Darwin: $ARCH" >&2; exit 1 ;;
    esac
    ;;
  *)
    echo "error: unsupported OS: $OS_NAME (Linux/Windows install paths not implemented)" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
BIN_DIR="${SCRIPT_DIR}/bin/${OS_LOWER}-${ARCH}"
TARGET_BIN="${BIN_DIR}/toktx"

if [ -x "$TARGET_BIN" ]; then
  echo "toktx already installed at $TARGET_BIN"
  "$TARGET_BIN" --version 2>&1 | head -1 || true
  exit 0
fi

mkdir -p "$BIN_DIR"

WORK_DIR="$(mktemp -d -t ktx-install.XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

URL="https://github.com/KhronosGroup/KTX-Software/releases/download/v${VERSION}/${PKG_NAME}"
echo "Downloading $URL"
curl --fail --location --progress-bar -o "${WORK_DIR}/${PKG_NAME}" "$URL"

echo "Expanding distribution .pkg payload (no admin)…"
pkgutil --expand "${WORK_DIR}/${PKG_NAME}" "${WORK_DIR}/expanded"

# The Mac distribution .pkg ships four sub-pkgs (library, tools, jni, dev). Each has its own
# Payload (gzipped cpio). Extract them all so toktx (in tools) and libktx (in library) are both
# present.
while IFS= read -r payload; do
  subpkg="$(basename "$(dirname "$payload")")"
  out="${WORK_DIR}/payload-${subpkg}"
  mkdir -p "$out"
  (cd "$out" && cat "$payload" | gunzip -dc | cpio -i 2>/dev/null)
done < <(find "${WORK_DIR}/expanded" -name Payload -type f)

TOKTX_SRC="$(find "${WORK_DIR}" -path '*/usr/local/bin/toktx' -type f | head -1)"
[ -n "$TOKTX_SRC" ] || { echo "error: toktx not found in any sub-pkg payload" >&2; exit 3; }

cp "$TOKTX_SRC" "$TARGET_BIN"
chmod +x "$TARGET_BIN"

# toktx depends on @rpath/libktx.4.dylib with rpath @executable_path → place dylibs alongside the
# binary. Preserve symlinks so the loader resolves the unversioned soname.
while IFS= read -r dylib; do
  cp -P "$dylib" "${BIN_DIR}/$(basename "$dylib")"
done < <(find "${WORK_DIR}" -path '*/usr/local/lib/libktx*.dylib')

echo
echo "Installed: $TARGET_BIN"
ls -la "$BIN_DIR"
echo
"$TARGET_BIN" --version 2>&1 | head -3 || true
