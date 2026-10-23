#!/usr/bin/env bash
# Copies the PAD models and YuNet detector into bench assets (gitignored; model files are never committed).
# Usage: tools/stage_bench_app.sh [models_dir]      default: datasets/models
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${1:-datasets/models}"
DST=android/bench-app/src/main/assets
mkdir -p "$DST"
for f in MiniFASNetV2-SE-int8.onnx MiniFASNetV2-SE.onnx face_detection_yunet.onnx; do
  if [ ! -f "$SRC/$f" ]; then echo "MISSING: $SRC/$f" >&2; exit 1; fi
  cp "$SRC/$f" "$DST/$f"
done
echo "Staged. SHA-256 of what the phone will run (record these next to the results):"
( cd "$DST" && { command -v sha256sum >/dev/null && sha256sum *.onnx || shasum -a 256 *.onnx; } )
