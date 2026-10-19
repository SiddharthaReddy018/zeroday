#!/bin/sh
# Run from the project root: sh tools/build-m4-patch.sh  ->  m4-android-patch.zip
set -e
OUT=m4-android-patch.zip
rm -f "$OUT"
zip -r "$OUT" \
  README-M4.md \
  tools/build-m4-patch.sh \
  src/main/java/liveness/android \
  src/test/java/liveness/android \
  src/main/java/liveness/device/DeviceAdapter.java \
  src/main/java/liveness/device/MockDeviceAdapter.java \
  src/test/java/liveness/device/MockDevicePerceptualHashTest.java \
  src/main/java/liveness/core/FaceFrame.java \
  src/main/java/liveness/flow/LivenessSession.java \
  src/main/java/liveness/service/LivenessService.java \
  android flutter \
  -x '*/build/*' '*/.gradle/*' '*.class'
echo "created $OUT"
