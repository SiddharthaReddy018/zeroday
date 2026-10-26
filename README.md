# MOSIP Face Liveness & Presentation Attack Detection (PAD)

**MOSIP Decode 2026 — Problem 04: Face Liveness + PAD**

A prototype Face Liveness and Presentation Attack Detection engine for the MOSIP biometric ecosystem (Registration Client & Android SBI), designed with ISO/IEC 30107-3 and CEN/TS 18099 in mind. It is not certified or compliance-tested.

**How it works:** a passive PAD model scores every frame that passes the quality gate. If the passive score is clearly bona fide, the session proceeds without an active challenge. The active challenge (blink, turn, look) runs when the passive score is uncertain, or when a replay loop is suspected. The smile detector is implemented but smile is not issued as a challenge.

**Host app:** the Android host app is DEV ONLY. It uses the host camera. If an SBI app is installed it detects it and switches to an SBI path with signature checking disabled; no real SBI or L0/L1 device was tested. It forces an active challenge on every session.

---

## Repository Layout & Clone Structure

Per project organization standards, code and extended documentation are split across two dedicated repositories:

```text
MOSIP/
├── face-liveness-pad/               # Code repository (zeroday / main)
│   ├── src/main/java/               # Core engine, audit, crypto, device & Android bridge
│   ├── src/test/java/               # Unit & integration test suites (651 tests at last run)
│   ├── android/liveness-android/    # Android library module (CameraX, ONNX Runtime AAR, SBI glue)
│   ├── flutter/liveness_ui/         # Flutter reference UI (screens not run on device) (cross-platform client)
│   ├── ui-contract/                 # JSON schema contracts for engine-UI platform channel
│   ├── pom.xml                      # Maven project definition (Java 11, Surefire, JaCoCo)
│   ├── README-M4.md                 # Milestone 4 Android integration & benchmark report
│   └── reports/                    # Test report, architecture, design notes, bench results
```

> **Note:** The `docs/` folder in `face-liveness-pad` contains localized working copies and is strictly excluded from git tracking (`.gitignore`) to maintain repository hygiene.

---

## Architectural Highlights

1. **Lightweight Neural PAD Scorer:**
   - Uses the optimized INT8 quantized model (`MiniFASNetV2-SE-int8.onnx`, 612 KB; not stored in git, see "Model and dataset files").
   - Low-latency edge execution: **Mean: 8.80 ms, p50: 8.63 ms, p95: 11.34 ms, p99: 12.60 ms** (far exceeding the MOSIP 200 ms target by >17x).
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
The host app (`io.oxalpha.liveness.host`, Gradle module `:host-app`) is a development harness. It reads the phone camera directly. If an SBI app is installed it detects it and uses an SBI path with signature checking disabled; no real SBI or L0/L1 device was tested. It forces an active challenge on every session. Live observations so far come from one phone (Nothing A001, Android 15) and one person.

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
- SBI signature verification is disabled in the development paths (SbiJwtVerifier.disabledForDevelopment). No real SBI or L0/L1 device was connected.
- Presentation attack trials on the phone were run by the developer after the last engine fixes. Results are not logged in this repository and no detection rates are claimed.
  Can be integrated in (if necessary ).
- Cold-start DEVICE_FAILURE on the first Start after launch was seen in several captures; cause not verified. A later change tolerates one large first frame gap.
- Turn direction was verified from yaw captures only (own right gives negative yaw, own left positive) and unit tests. No confirmed on-device challenge turn.
- Blur gate margin is thin on the test phone. Blur limit is 100 (Laplacian variance). Frames that passed in one good capture measured about 101 to 156. In two other captures live frames measured a median of about 63 to 73, every frame was rejected, and the session stayed on PENDING (POSITIONING). Cause (lens, angle, distance, focus, light) is not verified. Keep the lens clean and hold steady in good light when demonstrating. The threshold was not loosened because no attack data supports a lower value.
- The latest clean release build was not re-verified on device after the last fixes; a live PASS was last observed on a debug build.
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
