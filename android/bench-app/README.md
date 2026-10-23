# Liveness Bench (P4/P5, AUDIT_TODO X6)

Standalone app that times the PAD model, YuNet detector, and (from P5) the per-frame pipeline on a real phone. It compiles the
shared engine sources directly, so the phone runs the same Java the desktop tests run.

Needs: JDK 17 for Gradle, Android SDK platform 34 (`ANDROID_HOME` or `android/local.properties` with `sdk.dir=`),
phone with USB debugging, `adb`.

```
tools/stage_bench_app.sh                      # copies PAD + YuNet models into android/bench-app/src/main/assets
cd android
./gradlew :bench-app:installRelease           # release-like build, debug-signed, not debuggable
adb logcat -s LivenessBench                   # optional: watch results
```

Conditions that make the number worth quoting: phone unplugged, cool, screen on (the app keeps it on), no other
apps, battery saver off. Run each job two or three times with a pause; the JSON records thermal status and
battery temperature before and after, and block medians that show slowdown within a run.

Pull results: `adb pull /sdcard/Android/data/io.oxalpha.liveness.bench/files/bench ./bench-results`

What a result is NOT: synthetic input, so it says nothing about accuracy; one phone, so it says nothing about
other phones.


The "YuNet detector only: ORT CPU" job includes RGB-to-BGR resize/pad, ONNX inference, decoding and NMS.
It uses deterministic synthetic noise frames for latency only, not accuracy. YuNet provides five SFace alignment
points but no eye/mouth contours or head pose, so this detector benchmark does not enable the liveness challenge
flow. The YuNet model is SHA-256 pinned in the detector source and model files are never committed.
