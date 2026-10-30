# Evaluator Demonstration Script

> **Status note (2026-10-30, ADR-018):** Android uses the FP32 PAD model (ADR-017). SBI/MDS hardware integration is OUT OF SCOPE for the submission: the SBI code is implemented against the spec and unit-tested only, never exercised on a device. The submission demo is camera-only on the host app. Where this document says otherwise, this note wins. Not re-edited line by line.

## MOSIP Face Liveness Detection + Presentation Attack Detection (ZeroDay)
### Competition: MOSIP Decode 2026 -- Problem 04
### Artifact: `face-liveness-pad` | Version: 0.6.0 | Date: October 2026

---

## 1. Quick Overview

This guide provides step-by-step instructions for judges, evaluators, and engineers to verify the **MOSIP Face Liveness Detection and Presentation Attack Detection (PAD)** implementation.

The system enforces ISO/IEC 30107-3 compliance, CEN/TS 18099 injection attack detection (IAD), and strict fail-closed state machine transitions across Desktop (MDS HTTP) and Mobile (Android CameraX + MOSIP SBI Intent Bridge).

### Evaluation Highlights
- **Automated Test Suite:** Complete automated unit, integration, and contract tests (count: see TEST_REPORT.md generated block) pass reliably.
- **ISO/IEC 30107-3 Evaluation Harness:** Subject-disjoint benchmark with Wilson 95% confidence intervals, zero ACER reporting, and Monk Skin Tone fairness analysis.
- **Mobile Model Latency:** The shipped Android model is FP32 (ADR-017). On a Nothing A001 phone (synthetic frames, fixed-box detector, unplugged) FP32 PAD inference measured mean 6.96-7.18 ms, p95 9.05-9.31 ms over 4 lockstep runs; capture + preprocess + PAD per-frame p95 was 14.64-14.95 ms against the $\le 200\text{ ms}$ target. Face detector, SFace, signing, camera copy and UI are not included. The INT8 desktop-JVM benchmark below (mean 10.86 ms, p95 14.51 ms) is for the unevaluated alternative model.
- **Zero Foundation Model Footprint:** Model budget $\le 5\text{ MB}$ (shipped MiniFASNetV2-SE FP32 is 1.82 MB; the unevaluated INT8 alternative is 612 KB), completely offline-capable.
- **Cryptographic Attestation:** ECDSA P-256 digital signature over canonical attestation token with non-null `imageSha256` binding, `auditHeadHash` sealing, and tamper-evident SHA-256 audit chaining.

---

## 2. Prerequisites & Environment Setup

- **Java Development Kit (JDK):** OpenJDK 11+ (Targeted and compiled with `--release 11`)
- **Build Tool:** Apache Maven 3.9+
- **Platform:** macOS (Apple Silicon / Intel), Linux (x86_64 / aarch64), or Windows
- **ONNX Runtime:** Pre-configured via Maven (`com.microsoft.onnxruntime:onnxruntime:1.16.3`)

Verify your environment:
```bash
java -version
mvn -version
```

---

## 3. Step 1: Run the Full Test Suite

Execute the entire test suite covering all 6 milestones (Core State Machine, Engine & Capture Integrity, Desktop Service Layer, Android & SBI Intent Bridge, ISO Evaluation Harness, and Benchmarks):

```bash
cd /Users/k.siddharthareddy/Documents/MOSIP/face-liveness-pad
mvn clean test
```

### Expected Output:
```
[INFO] Results:
[INFO] Tests run: <N from your run>, Failures: 0, Errors: 0, Skipped: <S>
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 4. Step 2: Run the Mobile INT8 Inference Benchmark ($p95 \le 200\text{ ms}$)

Validate the inference speed of the quantized `MiniFASNetV2-SE-int8.onnx` (612 KB) alternative model running directly via ONNX Runtime. This is NOT the shipped Android model (ADR-017); it is kept as a benchmark of the unevaluated alternative:

```bash
mvn test -Dtest=Int8ModelBenchmarkTest
```

### Observed Results (Desktop JVM, Synthetic Input):
```
=== INT8 Model Benchmark Results ===
Iterations: 300
Mean: 10.86 ms
p50:  9.46 ms
p95:  14.51 ms
p99:  39.26 ms
Max:  103.73 ms
Target <= 200ms: true
JSON: {"n":300,"meanMs":10.86,"p50Ms":9.46,"p95Ms":14.51,"p99Ms":39.26,"maxMs":103.73}
=====================================
```
*Note: Measured on desktop JVM (synthetic input). Physical phone latency on target mobile hardware is not measured [Part 2 X6].*

---

## 5. Step 3: Run the ISO/IEC 30107-3 Evaluation Harness

Execute the automated evaluation runner across ground-truth dataset manifests:

```bash
mvn test -Dtest=BenchmarkReportRunnerTest
```

This runs the complete ISO/IEC 30107-3 compliant benchmark harness and generates an updated evaluation report at `docs/TEST_REPORT.md` and `target/TEST_REPORT.md`:

### Key ISO Verification Points:
1. **Zero ACER:** Verifies that Attack Presentation Classification Error Rate (APCER) and Bona Fide Presentation Classification Error Rate (BPCER) are never averaged into ACER (enforced via `EvalReportContractTest`).
2. **Per-Species APCER Breakdown:** Reports exact rates for Print, Screen, Video, Cutout, Curved Print, 2D Mask, 3D Mask, and Deepfake presentations with 95% Wilson confidence intervals.
3. **Worst-Species Headline APCER:** System-level security metric highlights the most penetrating attack species (marked `[EXPLORATORY]` when $N < 30$).
4. **Rule of 30 Compliance:** Validates that bona fide evaluation is conducted on $N=40 \ge 30$ distinct subjects on the benchmark manifest test split.
5. **Separate IAD Table:** Injection attacks (freeze, loop, nonce-swap) are isolated in a CEN/TS 18099 table rather than mixed into APCER.
6. **Fairness & Demographic Parity:** Generates the Monk Skin Tone (MST 10) Bias Index, age groups (0-20, 20-30, 30-40, 40-50, 50+), and gender breakdown.

To view the generated report:
```bash
cat docs/TEST_REPORT.md
```

---

## 6. Step 4: Verify Capture Integrity & Injection Defenses (CEN/TS 18099)

Run targeted unit and integration tests proving that hardware injection attacks, frozen video feeds, and transaction tampering are blocked before reaching inference:

```bash
mvn test -Dtest='liveness.capture.*Test,liveness.audit.*Test'
```

### Verified Protections:
1. **Frame Freeze Detection:** `FrameSequenceMonitor` tracks SplitMix64 perceptual hashes (`pHash`) and SHA-256 content digests; identical frames across sliding windows immediately trigger `Verdict.DEVICE_FAILURE`.
2. **Video Loop Detection:** Per ADR-015/ADR-016, loop detection needs intra-segment motion and intermediate-frame difference, and by default a loop suspicion is a soft signal: it is logged and forces an active challenge (it cannot pass without one) instead of ending the session as an attack. In synthetic probes it catches large-motion loops, mostly misses low-motion loops (about 3 px/frame or less) and can fire on periodic legitimate motion. Real-camera tuning is not done (Part 2 X13); do not quote detection or false-alarm rates from the synthetic runs as real-world figures.
3. **Session Nonce Binding:** RCAPTURE responses must match the cryptographically generated `sessionNonce` bound inside the MOSIP `transactionId`. Replayed or pre-recorded tokens are rejected.
4. **Tamper-Evident Audit Log:** `AuditChain` uses genesis-seeded SHA-256 rolling hashes; modifying or dropping any prior event record causes `AuditChain.verify()` and `Attestation.verifyAuditLog()` to fail.

---

## 7. Step 5: Verify Android Mobile & SBI Intent Bridge (Milestone 4)

Run all Android platform integration tests (executed on standard JVM without needing an emulator):

```bash
mvn test -Dtest='liveness.android.*Test'
```

### Test Suite Covers:
1. **`SbiIntentBridgeTest`:** Validates intent URI construction (`io.sbi.device.rCapture`, `io.sbi.device.Info`), bundle arguments, timeout handling, MOSIP DTO mapping, and multi-package allowlist filtering.
2. **`CameraXDeviceAdapterTest`:** Validates non-blocking drop-oldest frame queues, YUV-to-RGB conversion, and luma variance tracking.
3. **`EnvironmentIntegrityCheckerTest`:** Validates detection of `su` root binaries, ADB debugging flags, Xposed/Frida hooking frameworks, and emulators across production and development profiles.
4. **`SbiJwtVerifierTest`:** Validates JWS signature verification using PEM-encoded trust certificates (`SbiJwtVerifier.fromPem()`), multi-hop certificate chain validation, broken issuer link rejection, payload extraction, and timestamp freshness.
5. **`TestGridEmbedderTest` & `SFaceEmbedderTest`:** Validates face embedding generation and production-grade interface enforcement.
6. **`Iso19794FaceTest` & `SbiCaptureFinalIntegrationTest`:** Validates ISO/IEC 19794-5:2005 facial biometric record parsing and fail-closed JPEG 2000 handling.
7. **`LivenessPlatformChannelTest`:** Validates event serialization across Flutter `MethodChannel` and `EventChannel` against `ui-contract/events-schema.json`.

---

## 8. Step 6: End-to-End Workflow Verification

Run the comprehensive service integration suite validating the 3 MOSIP workflows (Resident Registration, Operator Authentication, Supervisor Authentication) and edge conditions:

```bash
mvn test -Dtest=LivenessServiceIntegrationTest
```

| Scenario | Workflow | Presentation / Error | Expected Result | State Machine Path |
|---|---|---|---|---|
| 1 | Resident Registration | Live bona fide face | `Verdict.PASS` | `IDLE -> POSITIONING -> PASSIVE -> FINAL_CAPTURE -> SUCCESS` |
| 2 | Resident Registration | 2D Print Attack | `Verdict.ATTACK` | `PASSIVE -> BLOCKED_ATTACK` (held over persistence window) |
| 3 | Resident Registration | Video Screen Replay | `Verdict.ATTACK` | `PASSIVE -> BLOCKED_ATTACK` |
| 4 | Resident Registration | Uncertain PAD Score | Triggers Active Challenge | `PASSIVE -> CHALLENGE_PROMPT -> CHALLENGE_WAIT -> FINAL_CAPTURE -> SUCCESS` |
| 5 | Resident Registration | Camera Disconnect | `Verdict.DEVICE_FAILURE` | `DEVICE_CHECK -> DEVICE_ERROR` |
| 6 | Operator Auth | Live Operator Face | `Verdict.PASS` | Streamlined passive verification |
| 7 | Supervisor Override | Supervisor Token | `Verdict.ATTACK` | Not wired in production; test-only. `SupervisorAuth` exists but `BLOCKED_ATTACK` is never exempt. See Part 2 X15. |
| 8 | Infant Registration | Resident `< 5` years | `Verdict.PASS` (Passive) | Active challenge waived via `ChallengeProfile.INFANT` |

---

## 9. Step 7: Packaging Standalone Release Artifact

To compile and package the clean standalone JAR for deployment into the MOSIP Registration Client:

```bash
mvn clean package -DskipTests
```

The resulting library is generated at:
```
target/face-liveness-pad-0.6.0.jar
```
This JAR contains all classes, configuration defaults, localized UI strings (`UiMessages`), and the event contract schema, ready to drop into MOSIP client classpaths.
