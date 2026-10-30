# Integration Guide

> **Status note (2026-10-30, ADR-018):** Android uses the FP32 PAD model (ADR-017). SBI/MDS hardware integration is OUT OF SCOPE for the submission: the SBI code is implemented against the spec and unit-tested only, never exercised on a device. The submission demo is camera-only on the host app. Where this document says otherwise, this note wins. Not re-edited line by line.

## MOSIP Face Liveness Detection + PAD
### Version 0.6.0

---

## 1. Overview

This guide explains how to integrate the face liveness detection system into the MOSIP Registration Client. The system plugs into the biometric capture pipeline at the **service layer** that creates the biometric record.

### PAD Pipeline Architecture
The presentation attack detection (PAD) pipeline uses a streamlined, single-model architecture rather than legacy multi-cue heuristics (no moire/FFT, LBP texture, or Gaussian curvature stubs):
1. **Capture Integrity (IAD):** FrameSequenceMonitor (freeze detection via byte-identical content digests, loop-replay detection via pHash Hamming sequence matching with motion verification), device identity validation, and RCAPTURE transaction/nonce freshness binding.
2. **Quality Gate:** Laplacian blur variance, brightness bounds, face size, pose angles (yaw, pitch, roll).
3. **Single-Model Neural PAD:** Qualified MiniFASNet V2-SE ONNX inference (80x80 face crop) yielding class 0 live / class 1 spoof probabilities.
4. **Spatial PAD Scorer:** Sliding window spatial aggregation with quality-weighted score fusion.
5. **Active Challenge-Response:** Randomized micro-challenges (blink, smile, head turn) for uncertain scores.
6. **Same-Face Verification:** SFace deep face embedder (`SFaceEmbedder`) comparing host-stream frames with the final RCAPTURE frame (calibrated cosine distance threshold tau=0.48 on genuine/impostor pairs; real-camera cross-device calibration is tracked under Part 2 X5/X13).

---

## 2. Prerequisites

- **Java 11+** runtime
- **Maven 3.9+** for building
- MOSIP Registration Client with SBI/MDS device support

### Build

```bash
mvn clean package -DskipTests
# Output: target/face-liveness-pad-0.6.0.jar (or newer)
```

---

## 3. Quick Start

### 3.1 Minimal Integration (5 lines)

```java
import liveness.service.LivenessService;
import liveness.service.LivenessService.*;
import liveness.device.MdsHttpDeviceAdapter;
import liveness.policy.LivenessConfig;
import liveness.engine.*;
import liveness.challenge.ChallengeGenerator;
import liveness.flow.RetryRateLimiter;
import liveness.crypto.EcdsaSignerStub;

// 1. Create config (use defaults or load from server)
LivenessConfig config = LivenessConfig.defaults();

// 2. Create components
MiniFasNetScorer padScorer = new MiniFasNetScorer(modelPath);  // null for stub
SameFaceChecker sameFaceChecker = new SameFaceChecker(0.48); // calibrated tau=0.48 (Part 2 X5/X13 calibration caveat)
SFaceEmbedder sfaceEmbedder = new SFaceEmbedder(sfaceModelPath);
ChallengeGenerator challengeGen = new ChallengeGenerator(
        ChallengeGenerator.DEFAULT_POOL, Collections.emptyList());
RetryRateLimiter limiter = new RetryRateLimiter(config.maxRetries());
EcdsaSignerStub signer = new EcdsaSignerStub();

// 3. Create service and wire SFaceEmbedder
LivenessService service = new LivenessService(
        config, padScorer, sameFaceChecker, challengeGen, limiter, signer);
service.setFaceEmbedder(sfaceEmbedder);

// 4. Create device adapter
MdsHttpDeviceAdapter device = new MdsHttpDeviceAdapter("http://127.0.0.1:4501");

// 5. Run liveness check (MUST be called from a background thread)
LivenessResult result = service.performLiveness(
        subjectId, device, machineUserId,
        WorkflowType.RESIDENT_REGISTRATION, callback);

if (result.isSuccess()) {
    Attestation attestation = result.attestation();
    // Proceed with biometric capture
} else {
    // Handle failure: result.verdict(), result.failureReason()
}
```

### 3.2 With UI Callbacks

```java
LivenessCallback callback = new LivenessCallback() {
    @Override
    public void onStateChanged(String state, String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    @Override
    public void onPreviewFrame(byte[] jpegBytes) {
        Platform.runLater(() -> updatePreviewImage(jpegBytes));
    }

    @Override
    public void onChallengePrompt(String action, long timeoutMs) {
        Platform.runLater(() -> showChallengePrompt(action, timeoutMs));
    }

    @Override
    public void onProgress(int framesProcessed, int framesNeeded) {
        Platform.runLater(() -> progressBar.setProgress(
                (double) framesProcessed / framesNeeded));
    }

    @Override
    public void onResult(LivenessService.LivenessResult result) {
        Platform.runLater(() -> showResult(result));
    }

    @Override
    public void onError(String errorCode, String message) {
        Platform.runLater(() -> showError(errorCode, message));
    }
};

// Run on background thread -- NEVER on UI thread
executorService.submit(() ->
    service.performLiveness(subjectId, device, machineUserId,
            WorkflowType.RESIDENT_REGISTRATION, callback));
```

---

## 4. Workflow Types

| Workflow | When to use | Active challenges? |
|----------|------------|-------------------|
| `RESIDENT_REGISTRATION` | Enrolling a new resident | Yes (if uncertain band) |
| `OPERATOR_AUTH` | Operator login | Yes (if uncertain band) |
| `SUPERVISOR_AUTH` | Supervisor login + exemptions | Yes (if uncertain band) |
| `INFANT` | Child registration | No (passive only) |

---

## 5. Configuration

### 5.1 Default Configuration

```java
LivenessConfig config = LivenessConfig.defaults();
// attackThreshold=30, passThreshold=70, maxRetries=3,
// activeEnabled=true, minChallenges=2, challengeTimeoutMs=5000
```

### 5.2 Custom Configuration

```java
LivenessConfig config = LivenessConfig.builder()
    .attackThreshold(25)
    .passThreshold(75)
    .maxRetries(5)
    .activeEnabled(true)
    .minChallenges(2)
    .maxChallenges(3)
    .challengeTimeoutMs(8000)     // 8s for accessibility
    .faceSizeMinPx(80)
    .poseMaxYawDeg(25)
    .poseMaxPitchDeg(25)
    .minQualifyingFrames(5)
    .build();
```

### 5.3 Server-Synced Configuration

```java
ConfigSyncManager syncManager = new ConfigSyncManager(
    "https://config-server/api/liveness",
    serviceConfig,
    300_000L  // sync every 5 minutes
);
syncManager.startSync();
```

---

## 6. Device Adapters

### 6.1 MDS/SBI Device (Production)

```java
// Discover device
MdsHttpDeviceAdapter device = new MdsHttpDeviceAdapter("http://127.0.0.1:4501");
// Device will be opened/closed by LivenessService
```

### 6.2 Mock Device (Testing)

```java
MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
// Available scenarios: BONA_FIDE, PRINT, SCREEN, VIDEO, CUTOUT,
// CURVED_PRINT, MASK_2D, MASK_3D, DEEPFAKE, FRAME_FREEZE,
// FRAME_LOOP, VIRTUAL_CAMERA, DISCONNECT, CORRUPT_FRAME,
// LOW_FPS, BUSY, TIMEOUT, LOW_LIGHT, MULTIPLE_FACES, NO_FACE
```

---

## 7. Result Handling

### 7.1 Success Path

```java
if (result.isSuccess()) {
    Attestation att = result.attestation();
    String imageSha256 = att.imageSha256();
    byte[] signature = att.signature();
    // Include attestation in biometric record
}
```

### 7.2 Failure Handling

```java
switch (result.verdict()) {
    case PASS:         // Success (handled above)
    case ATTACK:       // Presentation attack detected -- do NOT save capture
    case MAX_RETRIES:  // Retry budget exhausted -- prompt operator
    case DEVICE_FAILURE: // Device error -- check hardware
    case PENDING:      // Should not occur in result
}
```

### 7.3 Critical Rule

> **Liveness-failed captures are NEVER saved.** The `LivenessResult` enforces this:
> `attestation` is `null` for any non-PASS verdict.

---

## 8. Threading Model

```
UI Thread                    Background Thread
    │                              │
    │  submit(performLiveness)  ->  │
    │                              ├── device.open()
    │  ← onStateChanged()         ├── frame loop
    │  ← onPreviewFrame()         │   ├── captureFrame()
    │  ← onChallengePrompt()      │   ├── processFrame()
    │  ← onProgress()             │   └── (repeat)
    │                              ├── RCAPTURE
    │  ← onResult()               ├── verification
    │                              └── attestation
```

- **NEVER** call `performLiveness()` on the UI thread
- All `LivenessCallback` methods are called from the background thread
- Use `Platform.runLater()` (JavaFX) or `runOnUiThread()` (Android) in callbacks

---

## 9. Internationalization

```java
// Get localized message for a state
String msg = UiMessages.get("POSITIONING");  // "Position your face in the oval"

// Set locale
UiMessages.setLocale("fr");  // French
UiMessages.setLocale("ar");  // Arabic (RTL)
```

Supported locales: `en` (default), `fr`, `ar`

---

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `DEVICE_FAILURE` on startup | MDS not running or wrong port | Check MDS service, verify port |
| `DEVICE_FAILURE` during capture | Camera disconnected | Reconnect camera, retry |
| `ATTACK` on real face | PAD threshold too aggressive | Lower `attackThreshold` (needs calibration) |
| All captures fail quality | Poor lighting or distance | Check `faceSizeMinPx`, improve lighting |
| Challenge timeout | Subject too slow | Increase `challengeTimeoutMs`, use ACCESSIBILITY profile |

---

## 11. Running Evaluation & Benchmarks (ISO/IEC 30107-3)

### 11.1 Full Test Suite

Run all unit and integration tests (expected count: see TEST_REPORT.md generated block):

```bash
mvn clean test
```

### 11.2 Generating Benchmark Evaluation Report

To execute the ISO/IEC 30107-3 benchmark evaluation harness against dataset manifests:

```bash
mvn test -Dtest=BenchmarkReportRunnerTest
```

This runs `EvaluationHarness` on `datasets/manifests/evaluation_benchmark_manifest.csv` and outputs a comprehensive test report:
- `target/TEST_REPORT.md`
- Synced to `docs/TEST_REPORT.md`

The generated report includes:
- **Per-Species APCER** with 95% Wilson confidence intervals
- **Worst-Species Headline APCER** (ISO/IEC 30107-3 recommendation)
- **BPCER** with 95% Wilson confidence intervals (validating Rule of 30: $N \ge 30$)
- **Operating Points (DET & D-EER)**
- **CEN/TS 18099 Injection Attack Detection Table** (freeze, loop, nonce-swap)
- **Fairness & Demographic Bias Analysis** (DFIC Bias Index across Monk Skin Tone MST 10, gender, and age brackets)

### 11.3 Model Qualification Test

To verify ONNX model input dimensions, channel ordering (`[1, 3, 128, 128]` RGB), output logits, and file size constraints:

```bash
mvn test -Dtest=ModelQualificationTest
```

---

## 12. Android & Flutter Mobile Integration (Milestone 4)

See [`README-M4.md`](file:///Users/k.siddharthareddy/Documents/MOSIP/face-liveness-pad/README-M4.md) for full setup instructions.

### 12.1 Architecture
- **Liveness Frames:** Host CameraX analysis frames (Tier T3).
- **Accepted Biometric Capture:** MOSIP SBI intent `io.sbi.device.rCapture` (Tier T1).
- **Binding:** `CrossStreamBinder` binds CameraX frames to the SBI image via nonce matching + maximum time gap + same-face check.
- **Environment Integrity:** `EnvironmentIntegrityChecker` verifies against root, ADB debugging, Xposed/Frida hooking, and emulators before opening the camera.

### 12.2 Gradle & Android Modules
- **Android Library:** `android/liveness-android/` (minSdk 28, targetSdk 31, Java 11).
- **ONNX Runtime:** `com.microsoft.onnxruntime:onnxruntime-android:1.16.3`.
- **Flutter UI Plugin:** `flutter/liveness_ui/` provides resident, operator, and supervisor capture screens reacting to `ui-contract/events-schema.json`.

### 12.3 Running Android Unit Tests
All Android business logic runs on a standard JVM without requiring an emulator:

```bash
mvn test -Dtest='liveness.android.*Test'
```

### 12.4 Mobile INT8 Latency Benchmark & Trust Anchor Verification
To benchmark the quantized INT8 mobile ONNX model latency ($p95 \le 200\text{ ms}$):

```bash
mvn test -Dtest=Int8ModelBenchmarkTest
```

To ingest production MOSIP Device Provider root CAs into the SBI JWT verification chain:

```java
String pemCertificate = "...PEM DATA...";
SbiJwtVerifier verifier = SbiJwtVerifier.fromPem(pemCertificate);
```

### 12.5 Host App SBI Discovery & Device Fallback
The Android host integration supports dynamic discovery of MOSIP SBI vendor apps with automatic host-camera fallback:
1. **Package Visibility:** `AndroidManifest.xml` queries `<intent><action android:name="io.sbi.device" /></intent>`.
2. **Dynamic Discovery:** `AndroidSbiLauncher.resolveSbiPackages()` inspects available providers at startup. If present, `AndroidDeviceAdapter` routes final capture through `SbiIntentDeviceAdapter` with `CrossStreamBinder` same-face validation.
3. **Standalone Fallback:** When deployed on standalone development devices without a registered SBI vendor APK, the host app engages `CameraOnlyDevAdapter` (`HOST_CAMERA_DEV_ONLY`), allowing full end-to-end testing of the liveness pipeline.
4. **Challenge Calibration:** Active micro-challenges use calibrated mobile parameters (`earBlinkThreshold=0.22`, `minActionFrames=1`, challenge-active yaw threshold 60 deg) for reliable user confirmation on front-facing mobile sensors.



