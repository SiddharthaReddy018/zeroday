# MOSIP Face Liveness & Presentation Attack Detection (PAD)

**MOSIP Decode 2026 — Problem 04: Face Liveness + PAD**

A prototype Face Liveness and Presentation Attack Detection engine for the MOSIP biometric ecosystem (Registration Client & Android SBI), designed with ISO/IEC 30107-3 and CEN/TS 18099 in mind. It is not certified or compliance-tested.

**How it works:** a passive PAD model scores every frame that passes the quality gate. If the passive score is clearly bona fide, the session proceeds without an active challenge. The active challenge (blink, turn, look) runs when the passive score is uncertain, or when a replay loop is suspected. The smile detector is implemented but smile is not issued as a challenge.

**Host app:** the Android host app is DEV ONLY. It uses the host camera only. SBI/MDS hardware integration is out of scope (code implemented against the spec and unit-tested, never exercised on a device). It forces an active challenge on every session.

---

## Repository Layout & Clone Structure

Two GitHub repositories. Names on your disk may differ from the GitHub names.

| GitHub repo | Purpose | Notes |
|---|---|---|
| `SiddharthaReddy018/zeroday` | Code, tests, `reports/`, and the deliverable docs in `docs/` | This repository. `main` is the submitted code. |
| `SiddharthaReddy018/zerodayjunk-` | Working copies of project docs (HANDOFF files, MASTER_PLAN, PROGRESS-LOG, bench notes) | Not needed to build or run. The deliverable docs are copied into `docs/` here. |

```text
zeroday/
|-- src/main/java/        # Core engine, audit, crypto, device adapters (shared by desktop JVM and the Android host app)
|-- src/test/java/        # Unit and integration tests
|-- android/
|   |-- liveness-android/ # Android library module (CameraX, ONNX Runtime, SBI glue)
|   |-- host-app/         # DEV host app (camera only); compiles ../../src/main/java directly
|   `-- bench-app/        # Latency bench app
|-- flutter/liveness_ui/  # Flutter reference UI (screens not run on a device)
|-- ui-contract/          # JSON schema for engine-UI events
|-- reports/              # TEST_REPORT.md, architecture, bench results, design notes
|-- docs/                 # TECHNICAL_DESIGN, UX_GUIDE, INTEGRATION_GUIDE, DEMO_SCRIPT, decisions (ADRs), AUDIT_TODO, licence-register
|-- datasets/             # NOT in git: datasets/models/ holds the ONNX files for desktop tests
|-- pom.xml               # Maven project (Java 11, Surefire, JaCoCo)
`-- README-M4.md          # Milestone 4 Android integration notes
```

Git-ignored and created locally: `datasets/models/` (desktop tests) and `android/host-app/src/main/assets/` (host app: `MiniFASNetV2-SE.onnx`, `face_detection_yunet.onnx`, `face_recognition_sface.onnx`), plus `android/local.properties`.

---

## Architectural Highlights

1. **Lightweight Neural PAD Scorer:**
   - Uses the FP32 model `MiniFASNetV2-SE.onnx` (1.82 MB; not stored in git, see "Model and dataset files"). The INT8 file is an unevaluated alternative and is not shipped (ADR-017).
   - Latency (synthetic frames, not accuracy): on the Nothing A001 with FP32, PAD inference mean about 7 ms and per-frame p95 about 15 ms in a lockstep bench (ADR-017). The real face detector, SFace embedding, signing and UI are not included in those numbers. The older desktop-JVM INT8 figures are desktop only and say nothing about the phone.
   - Single-model decision architecture with strict `[0, 100]` probability score mapping and calibrated threshold bands (`PASS`, `UNCERTAIN`, `ATTACK`).

2. **SBI & MDS Device Adapters:**
   - **Android SBI Intent Adapter:** Spec-compliant (`io.sbi.device.Info`, `<appId>.rCapture`) with transaction ID nonce-binding, package allowlisting, and purpose validation.
   - **MDS HTTP Device Adapter:** Pure-Java standard `HttpURLConnection` client for desktop/remote MDS endpoints, supporting `/stream` and `/rCapture` with JWT verification.
   - **Multi-Hop JWT Verifier:** Enforces full X.509 certification path validation (`x5c`), intermediate CAs, `basicConstraints`, `keyCertSign`, leaf `digitalSignature`/`nonRepudiation`, and validity time windows against trusted root anchors.

3. **Cross-Stream Face Binding (`CrossStreamBinder`):**
   - Binds host camera live frames (T3) to peripheral SBI/MDS final captures (T1).
   - Integrates `SFaceEmbedder` (112×112 BGR planar, 128-D L2-normalized deep embeddings) with `SameFaceChecker` (calibrated cosine distance threshold: 0.48).
   - Normalizes timestamps across seconds/millis/micros/nanos/ISO-8601 and tolerates bounded clock skew (2000 ms).

4. **Cryptographic Attestation & Audit Trail:**
   - ECDSA P-256 digital signature over pipe-delimited canonical strings binding subject, session, image SHA-256, transcript hash, device tier, model version, and config version.
   - Supports both platform key stores (`AndroidKeyStoreSigner`) and custom signers via the `Signer` interface.
   - SHA-256 hash-chained audit log with terminal head sealing (`AuditSeal`), tamper detection, and export/import capabilities.

5. **Host Environment Integrity Checking:**
   - Heuristic root, Magisk, SU binary, Frida, Xposed, debugger, and emulator detection.
   - Profile-aware: `PRODUCTION` (disallows ADB and test-keys) vs `DEVELOPMENT` (allows ADB when configured, while still enforcing core integrity).

6. **ISO/IEC 30107-3 & Demographic Fairness Evaluation:**
   - Full evaluation harness computing APCER (per-species and worst-species), BPCER, and DET curves with 95% Wilson confidence intervals.
   - Fairness analyzer computing Monk Skin Tone, lighting, gender, and age category biases, adhering strictly to the **Rule of 30** (cells with <30 subjects are marked exploratory and excluded from conclusive bias deltas).
   - CEN/TS 18099 Injection Attack Detection (IAD) metric reporting (freeze, loop, nonce-swap).

---

## Build & Test Instructions

### Prerequisites
- JDK 11 or higher (built and verified with JDK 21, `--release 11`)
- Apache Maven 3.8+
- Android parts only: Android SDK with `ANDROID_HOME` set, `adb` on PATH, and a JDK for Gradle (Gradle wrapper in `android/`)
- Flutter reference UI only: Flutter SDK (version Not verified)

### Model and dataset files (not in git)
Model files, datasets and the `models/` and `datasets/` folders are git-ignored, so a fresh clone does not contain them.
- Put the ONNX models in `datasets/models/`: `MiniFASNetV2-SE.onnx`, `MiniFASNetV2-SE-int8.onnx`, `face_detection_yunet.onnx`, `face_recognition_sface.onnx`.
- The MiniFASNetV2-SE models come from `facenox/face-antispoof-onnx` (Apache-2.0). Expected SHA-256: FP32 `af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6`, INT8 `fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9`. Sources for the YuNet and SFace files: Not verified.
- The host app also reads copies of the models from `android/host-app/src/main/assets/` (git-ignored). Copy the same files there before `installDebug`.
- Datasets (for example ONOT, CC BY-NC 4.0) are not redistributed. `datasets/download_datasets.sh` is a local helper with hard-coded paths and has not been verified on another machine.
- Without these files, tests that need them are skipped, not failed. An earlier run without them skipped 24 tests (not re-run). With them present, the last run was 651 tests, 0 skipped.

### Running the Java Test Suite
```bash
# Run all unit, contract, and integration tests
mvn test

# Run tests with JaCoCo code coverage report generation
mvn test jacoco:report
```
At the last run the suite executed **651 tests with 0 failures, 0 errors and 0 skipped** (re-run `mvn test` to confirm). Code coverage reports are emitted to `target/site/jacoco/index.html`.

### Testing Sub-Modules
```bash
# Evaluation harness and fairness metrics only
mvn test -Dtest='liveness.evaluation.*Test'

# Android SBI, CameraX, and platform channel tests only
mvn test -Dtest='liveness.android.*Test'

# Crypto and attestation tests only
mvn test -Dtest='liveness.crypto.*Test,liveness.audit.*Test'
```

### Flutter Reference UI
```bash
cd flutter/liveness_ui
flutter pub get
flutter test
```

### Android Library Build
```bash
cd android
./gradlew :liveness-android:testDebugUnitTest
```

---

## Run the host app (DEV ONLY)
The host app (`io.oxalpha.liveness.host`, Gradle module `:host-app`) is a development harness. It reads the phone camera directly. It always uses the host camera only (camera-only adapter). SBI/MDS hardware integration is out of scope; see Limitations. It forces an active challenge on every session. Live observations so far come from one phone (Nothing A001, Android 15) and one person.

Prerequisites: Android SDK with `ANDROID_HOME` set, `adb` on PATH, and a phone with USB debugging enabled.
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
./gradlew :host-app:installDebug
```
Then open the app from the phone launcher, allow camera access if asked, tap Start and face the camera. Behaviour, known limits and what was not verified on a device: see "Limitations" below and the live observations section of `reports/TEST_REPORT.md`.

These commands use the standard Android Gradle plugin task names. They were not re-run when this section was written.

## Android Bench App (device run)
Device used so far: Nothing A001 (MT6878, Android 15), one phone only. Release build.
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
./gradlew :bench-app:installRelease
```
Bench outputs are written on the phone under
`/sdcard/Android/data/io.oxalpha.liveness.bench/files/bench/`.
`adb run-as` does not work on the release build; pull files with `adb pull`.

## Test Results
- Java suite: see "Running the Java Test Suite" above. Re-run `mvn test` to confirm the count on your machine.
- Device bench results, thermal readings and the relative blur-gate findings are in `reports/bench-results/` and `reports/design-notes/`.
- The relative blur gate is additive, default off, and not enabled by the app. Its constants
  (window 30, drop 2.0, y shift 1.5) are candidates, not fitted values.

## Limitations (Not verified)
- Only one phone, one lighting setup family and a few distances were measured. Other devices: not tested.
- Device observations are from one phone and one person; they are not accuracy figures.
- The host app forces a challenge on every session. The library default is passive-first, so host app runs do not show the passive-only path.
- SBI/MDS hardware integration is out of scope for the submission. `SbiIntentBridge`, `SbiJwtVerifier`, `CrossStreamBinder` and `AndroidDeviceAdapter` are implemented against the spec and unit-tested only, never exercised on a device. Signature checking is disabled in development paths. The host app does not use them.
- JavaFX desktop UI: not delivered. Flutter screens: not run on a device.
- The challenge logic does not defeat a video that contains the requested action; it only raises the cost. Video replay is not defended.
- Earlier presentation attack trials on the phone were run by the developer; results are not logged in this repository and no detection rates are claimed.
- Cold-start DEVICE_FAILURE on the first Start after launch was seen in several captures; cause not verified. A later change tolerates one large first frame gap.
- Turn direction was verified from yaw captures and unit tests. One on-device turn challenge was confirmed in the 2026-10-30 release run (N=3 runs, one phone, one person; see `reports/TEST_REPORT.md`).
- Blur gate margin is thin on the test phone. Blur limit is 100 (Laplacian variance). Frames that passed in one good capture measured about 101 to 156. In two other captures live frames measured a median of about 63 to 73, every frame was rejected, and the session stayed on PENDING (POSITIONING). Cause (lens, angle, distance, focus, light) is not verified. Keep the lens clean and hold steady in good light when demonstrating. The threshold was not loosened because no attack data supports a lower value.
- 2026-10-30 release build (FP32, commit e59a582), N=3 live runs on the Nothing A001: 1 SUCCESS, 2 BLOCKED_ATTACK; cause of the two blocks not established. Informal developer trial, one phone, one person; not a detection rate. Presentation-attack trials were not re-run after the FP32 change.
- The host app still widens the timestamp-gap limit to about 4 s (dev workaround, ADR-018); this weakens the freeze/injection check.
- Attack accuracy on real presentation attacks for the relative blur gate: not measured.
- Detector concurrent cost, signing integration, UI mapping of reason strings, and the
  `:liveness-android` compile in the full app: not verified in the bench sessions.
- Relative gate design holes: no reset after LOST or BLUR; the first 30 fed frames are blocked;
  a session that starts blurred is not caught; honest users changing light can be rejected;
  sessions with no samples bypass the check.
- ISO/IEC 30107-3 and CEN/TS 18099 wording in this README describes the metrics the harness computes.
  It is not a certification claim.

---

## References & Further Reading
- [README-M4.md](README-M4.md): Milestone 4 Android integration details, benchmark protocols, and wiring recipes.
- `ui-contract/events-schema.json`: Strict JSON schema contract for UI platform channel events.
- MOSIP Biometric Device Specification 1.1.5 & Android SBI Draft 0.9.
