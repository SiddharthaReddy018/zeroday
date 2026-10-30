# Audit Remediation -- Part 2 Comprehensive Implementation Guide

This document tracks items from Part 2 of the codebase audit (X1 - X15). It details:
1. Automated validations completed in the offline software environment.
2. Step-by-step instructions, commands, hardware prerequisites, and acceptance criteria for the user to execute the remaining physical and field activities.

---

## Part 2 Item Inventory & Execution Guide

---

### [COMPLETED & VERIFIED IN DOCKER - Part 2 X1] Full Offline/Online Network & Coverage Certification
- **Status:** Complete & Certified
- **Isolated Container Verification Completed:**
  - Installed and configured lightweight container runtime (Colima + Docker; total disk footprint 2.1 GB, well below 7 GB limit).
  - Executed Maven test suite in full network isolation container (`docker run --rm --network none ... maven:3.9-eclipse-temurin-11 mvn test -o`).
  - Total tests executed: 476 tests across 56 test classes.
  - Test Results: 473 passed, 0 failures, 3 skipped via `assumeTrue` (awaiting external multi-gigabyte research datasets).
  - JaCoCo Instruction Coverage: **81.90%** (20,611 / 25,166 instructions).
  - JaCoCo Branch Coverage: **69.05%** (1,653 / 2,394 branches).
  - Resolved Java 11 `Instant.parse` strict UTC offset incompatibility in `CrossStreamBinder` under containerized Temurin-11 runtime.
- **Reproducibility Command:**
  ```bash
  docker run --rm --network none \
    -v ~/.colima_build/code:/workspace \
    -v ~/.m2:/root/.m2 \
    -w /workspace \
    maven:3.9-eclipse-temurin-11 \
    mvn test -o
  ```

---

### [PARTIALLY COMPLETED - Part 2 X2] Model-Dependent End-to-End Field Qualification
- **Automated Baseline Completed:**
  - Acquired and verified cryptographic SHA-256 hashes of production ONNX models in `datasets/models/`:
    - `MiniFASNetV2-SE.onnx` (`af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6`)
    - `MiniFASNetV2-SE-int8.onnx` (`fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9`)
    - `face_recognition_sface.onnx` (`0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79`)
    - `face_detection_yunet.onnx` (`8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`)
  - Automated tests passing:
    - `ModelQualificationTest` (4/4 passed): input shape `[-1, 3, 128, 128]`, output shape `[-1, 2]`, softmax distribution validated.
    - `SFaceEmbedderTest` (2/2 passed): 128D embedding generated with unit L2 norm ($1.0 \pm 1e-4$).
    - `Int8ModelBenchmarkTest` (1/1 passed): INT8 quantized inference validated.
- **User Action Steps on Target Devices:**
  1. Copy ONNX models to physical Android device test assets directory.
  2. Configure ONNX Runtime mobile package:
     `implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.3'`
  3. Enable hardware acceleration delegate (NNAPI / Qualcomm QNN / MediaTek NeuroPilot) in `OrtSession.SessionOptions`:
     ```java
     SessionOptions options = new SessionOptions();
     options.addNnapi(); // Or Qualcomm QNN / MediaTek delegate
     ```
  4. Run on ARM64-v8a hardware and benchmark inference latency across 100 consecutive frames.
- **Acceptance Criteria:**
  - Hardware accelerated inference completes in <= 25 ms on target mid-range mobile chipset.
  - Classification outputs match desktop FP32 outputs within tolerance ($|y_{desktop} - y_{device}| < 1e-3$).

---

### [NOT MEASURED - Part 2 X4] Android / Flutter Production Build & Engine Hash Verification
- **Prerequisites:**
  - Android Studio Hedgehog / Iguana (or SDK CLI tools), Android SDK 34, NDK r26.
  - Flutter SDK 3.24+ (Dart 3.5+).
- **User Action Steps:**
  1. Build the Android AAR library:
     ```bash
     cd android
     ./gradlew :liveness-android:assembleRelease
     ```
  2. Verify ProGuard/R8 shrinking rules (`consumer-rules.pro`): ensure cryptographic signers (`liveness.crypto.*`), ONNX bindings, and native JNI entry points are retained.
  3. Build Flutter demo application:
     ```bash
     cd ../flutter/liveness_ui
     flutter pub get
     flutter build apk --release
     ```
  4. Compute and record release hashes:
     ```bash
     sha256sum android/liveness-android/build/outputs/aar/liveness-android-release.aar
     sha256sum flutter/liveness_ui/build/app/outputs/flutter-apk/app-release.apk
     ```
- **Acceptance Criteria:**
  - AAR and APK build cleanly without unresolved symbols or missing ProGuard keep rules.
  - APK installs on target Android device (`adb install app-release.apk`) and launches without crash.

---

### [NOT MEASURED - Part 2 X5] SFace Calibration on Real Cross-Stream Mobile Pairs
- **Scope note (2026-10-30, ADR-018):** the SBI part of this item is OUT OF SCOPE (no SBI device in the submission). Any camera-only part stays NOT MEASURED.
- **Prerequisites:**
  - Target Android mobile handset with front camera.
  - Physical MOSIP L0/L1 SBI biometric face capture device.
- **User Action Steps:**
  1. Collect genuine pairs: Capture 100+ live sessions where a user faces the front camera (CameraX) and simultaneously undergoes capture on the SBI device (`rCapture`).
  2. Collect impostor pairs: Cross-pair images of different individuals.
  3. Store pairs in a structured dataset directory (`genuine/` and `impostor/`).
  4. Run the calibration script:
     ```bash
     python3 tools/calibrate_same_face.py \
         --dataset path/to/cross_stream_pairs/ \
         --out sface_cross_stream_calibration.json
     ```
  5. Check resulting ROC / DET curve and Equal Error Rate (EER).
  6. If the measured optimal threshold differs from 0.48, update `LivenessConfig.sameFaceThreshold` with calibration documentation.
- **Acceptance Criteria:**
  - False Match Rate (FMR) <= 0.1% at selected threshold.
  - False Non-Match Rate (FNMR) <= 1.0% under nominal indoor illumination.

---

### [NOT MEASURED - Part 2 X6] Physical Phone Latency Benchmarks
- **Prerequisites:**
  - Low-end target Android smartphones (e.g., Quad-core ARM Cortex-A53 / Cortex-A55, 2 GB RAM, Android 11+).
- **User Action Steps:**
  1. Install test benchmark APK on device.
  2. Put phone in normal mode (ambient 25 deg C, full battery).
  3. Execute 50 consecutive capture sessions and record per-frame latencies:
     - Frame pre-processing (RGB conversion, crop, resize)
     - Perceptual hash computation (pHash)
     - MiniFASNet PAD model inference
     - SFace embedding generation
  4. Repeat under thermal stress: run CPU stress test for 10 minutes to induce thermal throttling, or enable aggressive battery saver mode.
  5. Aggregate latencies (mean, p50, p90, p95, max).
- **Acceptance Criteria:**
  - Under normal conditions: frame latency <= 100 ms (>= 10 FPS preview pipeline).
  - Under thermal throttling: frame latency <= 200 ms (>= 5 FPS minimum operational requirement).

---

### [COMPLETED SOFTWARE VALIDATION - Part 2 X7] Clock Skew & NTP Verification Under Device Drift
- **Automated Baseline Completed:**
  - Implemented `FreshnessCheckerClockSkewTest.java` (9 unit and integration tests passing).
  - Validated forward drift, backward drift, exact $\pm 30$s window boundaries, boundary violations, extreme drift (hours, days, epoch), and custom configurable freshness windows.
  - Validated `CaptureIntegrityChecker.verifyRCapture` integration: timestamp drift beyond freshness window immediately triggers `BINDING_FAILURE` and aborts session.
- **User Action Steps for Physical Device Testing:**
  1. Connect physical test handset via ADB:
     ```bash
     adb shell "su -c 'date -s @$(($(date +%s) + 45))'" # Advance clock by 45s
     ```
  2. Initiate capture session via mobile application.
  3. Verify that the session rejects the capture with `INTEGRITY_FAILURE` / `BINDING_FAILURE`.
  4. Reset device clock to network time:
     ```bash
     adb shell "su -c 'settings put global auto_time 1'"
     ```
- **Acceptance Criteria:**
  - Any capture with $|t_{device} - t_{server}| > rcaptureFreshnessWindowMs$ fails closed.

---

### [NOT MEASURED - Part 2 X8] Android KeyStore Hardware Attestation on Physical Device
- **Prerequisites:**
  - Physical Android 11+ device equipped with hardware-backed Keystore (StrongBox or TEE).
- **User Action Steps:**
  1. In the Android app, initialize `AndroidKeyStoreSigner`:
     ```java
     AndroidKeyStoreSigner signer = new AndroidKeyStoreSigner("mosip_attestation_key");
     signer.ensureKey();
     ```
  2. Extract the key attestation certificate chain:
     ```java
     KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
     ks.load(null);
     Certificate[] chain = ks.getCertificateChain("mosip_attestation_key");
     ```
  3. Verify hardware backing properties in ASN.1 attestation extension (OID `1.3.6.1.4.1.11129.2.1.17`):
     - `attestationSecurityLevel` == `KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT` or `KM_SECURITY_LEVEL_STRONGBOX`.
     - Root certificate matches Google Hardware Attestation Root CA public key.
- **Acceptance Criteria:**
  - Key generation succeeds with hardware security level.
  - Attestation signature verification passes against genuine certificate chain.

---

### [NOT MEASURED - Part 2 X9] Physical Penetration Testing on Rooted/Frida Devices
- **Prerequisites:**
  - Rooted Android test handset (Magisk v26+ or KernelSU).
  - Frida CLI installed on host machine (`pip install frida-tools`).
- **User Action Steps:**
  1. Push and start frida-server on device:
     ```bash
     adb push frida-server /data/local/tmp/
     adb shell "su -c 'chmod 755 /data/local/tmp/frida-server && /data/local/tmp/frida-server &'"
     ```
  2. Launch app and verify that `EnvironmentIntegrityChecker` flags the environment:
     - Detects `su` binary in `/system/xbin/`, `/system/bin/`, `/sbin/`.
     - Detects Frida listening port (27042) or named pipe.
     - Detects installed Magisk/Zygisk manager apps.
  3. Attempt Frida script hooking `Attestation.sign()` or `LivenessStateMachine.fire()`:
     - Verify app aborts immediately with `INTEGRITY_COMPROMISED`.
- **Acceptance Criteria:**
  - App fails closed on rooted/tampered environment. No valid attestation can be produced on compromised device.

---

### [PARTIALLY COMPLETED - Part 2 X10] Production Face Localisation Model Replacement
- **Automated Baseline Completed:**
  - Acquired `face_detection_yunet.onnx` (`8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`) from OpenCV Zoo.
  - Integrated in offline evaluation tools (`tools/detect_face_boxes.py`, `tools/calibrate_same_face.py`).
- **User Action Steps on Mobile Pipeline:**
  1. Package `face_detection_yunet.onnx` into the Android project assets folder.
  2. In `liveness.android.CameraXDeviceAdapter`, replace heuristic bounding box logic with YuNet ONNX inference:
     - Input size: 320x320 or 512x512.
     - Extract bounding box `[x, y, w, h]` and 5 facial landmarks (right eye, left eye, nose tip, right mouth corner, left mouth corner).
  3. Pass detected face box directly into `PadPreprocessor.extractFaceCrop(frame, bbox)`.
- **Acceptance Criteria:**
  - Face detection achieves >= 99% acquisition rate on frontal faces with pitch/yaw within $\pm 20$ degrees.
  - Detector execution time <= 15 ms per frame on target hardware.

---

### [OUT OF SCOPE - Part 2 X11] Real SBI / MDS Hardware & JPEG 2000 Native Decoder Integration
- **Scope note (2026-10-30, ADR-018):** SBI/MDS hardware integration is out of scope for the submission. Code exists and is unit-tested only.
- **Prerequisites:**
  - MOSIP-certified L0 or L1 biometric capture device (USB OTG or BLE).
  - Device vendor MDS (MOSIP Device Service) Android APK.
- **User Action Steps:**
  1. Install vendor MDS APK on Android device and grant USB permissions.
  2. Connect scanner and query device info:
     - Verify device info endpoint responds with signed device certificate.
  3. Trigger capture flow via `SbiIntentBridge` (`MOSIP_BIOMETRIC_CAPTURE` intent):
     - Receive signed JWT containing ISO/IEC 19794-5 biometric data block.
  4. If vendor biometric block uses JPEG 2000 (`image/jp2` format):
     - Ensure native `libopenjp2.so` is bundled in `jniLibs/arm64-v8a/` and `jniLibs/armeabi-v7a/`.
     - Verify `Iso19794Face.extractImageData()` successfully decodes JP2 bytes into RGB bitmap.
- **Acceptance Criteria:**
  - End-to-end SBI capture produces valid `RCAPTURE` token verified by `SbiJwtVerifier`.
  - JP2 decompression succeeds without memory leaks or crash.

---

### [NOT MEASURED - Part 2 X12] Evidence Plan Without a 30-Subject Live Recording
- **Constraint:** the team cannot recruit 30+ live subjects. Do not fake it; make claims that match the evidence.
- **Plan (in order of value):**
  1. Use public anti-spoofing / replay datasets for bona fide and attack samples (for example CASIA-FASD, Replay-Attack, MSU-MFSD, OULU-NPU, CelebA-Spoof). Check each licence or request form and the hackathon rules first (X14). Keep the manifest format and fix the dev/test split before looking at results.
  2. Use a dataset with skin-tone labels for fairness, with its licence checked.
  3. Optional small self-recording by 5 to 10 consenting teammates on the real phone pipeline: report as "N subjects, EXPLORATORY"; use it for demo realism and for X13, not for headline rates.
  4. Print, screen replay and cutout attacks of a few volunteers on the phone pipeline: report N, number of subjects and Wilson CIs.
  5. Keep the Limitations section in TEST_REPORT.md accurate.
- **Acceptance Criteria:**
  - Every number carries its N, source and caveat.
  - No claim implies a live BPCER, conclusive fairness, or coverage of species that were not tested.

---

### [NOT MEASURED - Part 2 X13] Real-Camera Frame Freeze & Loop Replay Tuning
- **Why it matters:** loop detection is a soft signal (ADR-016) because synthetic probes show false alarms on periodic or large legitimate motion and misses on low-motion loops. Thresholds are not validated on any real camera.
- **Prerequisites:** a phone on a tripod, a few teammates, a tablet or virtual-camera setup for replay.
- **User Action Steps:**
  1. Record 50 sessions of live subjects sitting very still, and 50 with natural movement (nodding, small sway). Count `FROZEN` and `LOOP_REPLAY` events per session.
  2. Record 50 replay sessions of a looping head-motion clip at several motion sizes (small, medium, large). Count detections.
  3. Tune on a dev recording set only, then check on a separate set. Parameters in `LivenessConfig`:
     - `freezeHammingThreshold` (default 2), `freezeConsecutiveFrames` (default 5)
     - `loopHammingThreshold` (default 6), `loopSequenceLengthK` (default 5), `loopMinSegmentMotionBits` (default 3)
     - `frameFreezePHashWindowSize` / loop window (default 15, must be at least 2K+1)
     - `loopReplayHardFail` (default false; switch to true only if false alarms are acceptably low)
- **Acceptance Criteria:**
  - False `FROZEN` or `LOOP_REPLAY` rate on legitimate sessions is measured with Wilson CIs and is acceptably low for the chosen hard/soft setting.
  - Detection rate per loop motion size is reported, including the sizes that are missed.

---

### [NOT MEASURED - Part 2 X14] Formal Legal & License Conformance Clearance
- **User Action Steps:**
  1. Review `docs/licence-register.md`.
  2. Confirm open-source licenses:
     - `MiniFASNetV2-SE`: Apache-2.0
     - `YuNet`, `SFace`: Apache-2.0 (OpenCV Zoo)
     - `ONNX Runtime`: MIT
     - `JUnit 5`: EPL-2.0
  3. Confirm that research-only datasets (CelebA-Spoof, OULU-NPU, ONOT) are strictly quarantined to evaluation harnesses and never bundled into shipping Android production releases.
  4. Submit compliance package to institutional legal counsel for formal clearance.
- **Acceptance Criteria:**
  - Formal sign-off on open-source software license register.

---

### [NOT MEASURED - Part 2 X15] Independent Third-Party Laboratory Review
- **Prerequisites:**
  - Accredited independent biometric evaluation laboratory (e.g., iBeta, FIDO Alliance accredited test lab, BSI).
- **User Action Steps:**
  1. Deliver compiled production release artifacts (`.aar` / `.apk`) and documentation package.
  2. Provide laboratory with calibration documentation (`TEST_REPORT.md`, `INTEGRATION_GUIDE.md`).
  3. Commission formal ISO/IEC 30107-3 Level 1 and Level 2 Presentation Attack Detection (PAD) evaluation.
- **Acceptance Criteria:**
  - Formal ISO/IEC 30107-3 Level 1 / Level 2 Conformance Certificate issued by accredited testing facility.
