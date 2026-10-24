#!/usr/bin/env bash
# Copies PAD int8, SFace and YuNet into host-app assets (gitignored). Verifies SHA-256 against known values.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${1:-datasets/models}"
DST=android/host-app/src/main/assets
mkdir -p "$DST"
check() {
  f="$1"; want="$2"
  [ -f "$SRC/$f" ] || { echo "MISSING: $SRC/$f" >&2; exit 1; }
  got="$(shasum -a 256 "$SRC/$f" | cut -d' ' -f1)"
  [ "$got" = "$want" ] || { echo "SHA MISMATCH $f: $got" >&2; exit 1; }
  cp "$SRC/$f" "$DST/$f"
}
check MiniFASNetV2-SE-int8.onnx fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9
check face_recognition_sface.onnx 0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79
check face_detection_yunet.onnx 8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4
echo "Staged and verified."
