# Technical Design Document

> **Status note (2026-10-30, ADR-018):** Android uses the FP32 PAD model (ADR-017). SBI/MDS hardware integration is OUT OF SCOPE for the submission: the SBI code is implemented against the spec and unit-tested only, never exercised on a device. The submission demo is camera-only on the host app. Where this document says otherwise, this note wins. Not re-edited line by line.

## MOSIP Face Liveness Detection + PAD System
### Version 0.6.0 | October 2026

---

## 1. Executive Summary

This document describes the technical design of the face liveness detection and presentation attack detection (PAD) system for the MOSIP Registration Client. The system prevents spoofing attacks during face biometric capture using a multi-layered pipeline: passive PAD scoring, active challenge-response, capture integrity checks, ISO/IEC 30107-3 evaluation harness, and cryptographic attestation.

**Key metrics:**
- 94 source files, 37 test classes
- automated test suite (count and pass rate: see TEST_REPORT.md generated block)
- 13-state machine with table-driven transitions
- Native ONNX Runtime inference, MiniFASNetV2-SE FP32 on Android (ADR-017; INT8 is an unevaluated alternative). Phone PAD_INFER, Nothing A001, unplugged run3, synthetic frames: mean 6.96-7.18 ms, p95 9.05-9.31 ms. The earlier desktop JVM figure (mean 7.19 ms, p95 7.80 ms) is not a phone number and did not record which model produced it
- ISO/IEC 30107-3 compliant evaluation harness with Wilson 95% CIs and Monk scale fairness analysis
- Android CameraX + MOSIP SBI Intent bridge + Flutter UI platform channel
- ECDSA P-256 signed attestation
- Hash-chained audit log
- 3 deployment workflows + infant variant

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌────────────────────────────────────────────────────────┐
│             UI Layer (JavaFX / Flutter)                │
│   LivenessUiListener ← UiEvent ← UiMessages (i18n)    │
│   Flutter MethodChannel / EventChannel                 │
└────────────────────┬───────────────────────────────────┘
                     │ LivenessCallback
┌────────────────────▼───────────────────────────────────┐
│                  Service Layer                          │
│   LivenessService → LivenessSession → StateMachine     │
│   LivenessServiceConfig ← ConfigSyncManager            │
└────────────────────┬───────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────────┐
│                  Engine Layer                            │
│   MiniFasNetScorer (ONNX) ← InferenceWorker            │
│   SpatialPadScorer · DecisionEngine · QualityGate      │
│   SameFaceChecker · LandmarkDetector · PoseGate        │
└────────────────────┬───────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────────┐
│              Capture Integrity Layer                     │
│   FrameSequenceMonitor · DeviceIdentityValidator        │
│   FreshnessChecker · SessionTransactionBinder           │
│   CaptureIntegrityChecker · EnvironmentIntegrityChecker │
└────────────────────┬───────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────────┐
│                  Device Layer                            │
│   DeviceAdapter (interface)                             │
│   ├── MdsHttpDeviceAdapter (MOSIP Desktop SBI)          │
│   ├── AndroidDeviceAdapter (CameraX T3 + SBI T1)        │
│   ├── CameraXDeviceAdapter (T3 Host Camera Analysis)    │
│   ├── SbiIntentDeviceAdapter (T1 MOSIP Mobile SBI)      │
│   ├── MockDeviceAdapter (20 scenarios)                  │
│   MdsStreamReader · MdsDeviceInfo · MdsResponse         │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Package Structure (15 packages, 92 source files)

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `liveness.core` | Immutable value types | `FaceFrame`, `FrameQuality` |
| `liveness.device` | Device abstraction + MDS | `DeviceAdapter`, `MdsHttpDeviceAdapter`, `MockDeviceAdapter` |
| `liveness.android` | Android CameraX + SBI + Flutter | `CameraXDeviceAdapter`, `SbiIntentBridge`, `CrossStreamBinder`, `EnvironmentIntegrityChecker`, `LivenessPlatformChannel`, `Iso19794Face` |
| `liveness.capture` | Capture integrity (IAD) | `CaptureIntegrityChecker`, `FrameSequenceMonitor`, `FreshnessChecker` |
| `liveness.engine` | Scoring + detection | `MiniFasNetScorer`, `DecisionEngine`, `QualityGate`, `SameFaceChecker` |
| `liveness.challenge` | Active challenge system | `ChallengeGenerator`, `ChallengeResponseValidator`, `BoundChallenge` |
| `liveness.flow` | State machine + session | `LivenessStateMachine`, `LivenessSession`, `RetryRateLimiter` |
| `liveness.policy` | Config + band routing | `LivenessConfig`, `LivenessPolicy`, `BandTable` |
| `liveness.crypto` | Attestation + signing | `Attestation`, `EcdsaSignerStub` |
| `liveness.audit` | Hash-chained audit log | `AuditChain`, `AuditEvent`, `AuditLog` |
| `liveness.service` | Service-layer orchestrator | `LivenessService`, `LivenessServiceConfig`, `ConfigSyncManager` |
| `liveness.ui` | UI event contract + i18n | `UiEvent`, `UiMessages`, `LivenessUiListener` |
| `liveness.util` | Utilities | `Sha256` |
| `liveness.evaluation` | ISO/IEC 30107-3 Evaluation | `EvaluationHarness`, `PadMetrics`, `FairnessAnalyzer`, `ManifestLoader`, `EvaluationReport`, `IadMetrics`, `WilsonConfidenceInterval` |
| `liveness.score` | Score types | Score abstractions |
| `liveness.config` | Config types | Config abstractions |

---

## 3. State Machine Design

### 3.1 The 13-State Machine

```
IDLE → DEVICE_CHECK → POSITIONING → PASSIVE → CHALLENGE_PROMPT →
CHALLENGE_WAIT → FINAL_CAPTURE → VERIFYING_FINAL → SUCCESS
                                                 → BLOCKED_ATTACK
                                                 → FAILED_MAX_RETRIES
                                                 → DEVICE_ERROR
                                                 → RETRY_AVAILABLE → PASSIVE (loop)
```

### 3.2 State Descriptions

| State | Purpose | Terminal? |
|-------|---------|-----------|
| `IDLE` | Initial state | No |
| `DEVICE_CHECK` | Device availability verification | No |
| `POSITIONING` | Face positioning guidance (quality gate) | No |
| `PASSIVE` | Passive PAD scoring (spatial scorer) | No |
| `CHALLENGE_PROMPT` | Active challenge display | No |
| `CHALLENGE_WAIT` | Awaiting challenge response | No |
| `FINAL_CAPTURE` | RCAPTURE trigger | No |
| `VERIFYING_FINAL` | Final verification (binding, same-face, final PAD) | No |
| `RETRY_AVAILABLE` | Retry decision point | No |
| `SUCCESS` | Liveness verified — attestation produced | **Yes** |
| `BLOCKED_ATTACK` | Attack detected — session terminated | **Yes** |
| `FAILED_MAX_RETRIES` | Retry budget exhausted | **Yes** |
| `DEVICE_ERROR` | Device failure — terminal | **Yes** |

### 3.3 Key Design Decisions

- **No boolean flags in `LivenessSession`** — all routing derives from `machine.current()`
- **Table-driven transitions** — invalid transitions throw `IllegalStateException`
- **Attestation only from `VERIFYING_FINAL → SUCCESS`** — no other path produces an attestation
- **Liveness-failed captures are NEVER saved** (constraint #5)
- **`BLOCKED_ATTACK` is terminal per session, not per person** — new session via `RetryRateLimiter`

---

## 4. PAD Pipeline

### 4.1 Pipeline Order (per frame)

1. **Capture Integrity (IAD)** — frame freeze, loop detection, device identity
2. **Quality Gate** — blur, brightness, face size, pose, face count (raw frame, before crop)
3. **Spatial PAD** — per-frame MiniFASNet scoring → sliding window composite
4. **Decision Engine** — 4-band routing with attack persistence windows

### 4.2 Score Bands

| Band | Condition | Action |
|------|-----------|--------|
| `CLEAR_BONA_FIDE` | score ≥ passThreshold (70) | → `FINAL_CAPTURE` |
| `UNCERTAIN` | attackThreshold < score < passThreshold | → `CHALLENGE_PROMPT` (if active enabled) |
| `CLEAR_ATTACK` | score ≤ attackThreshold (30) | → `BLOCKED_ATTACK` (after N persistence windows) |
| `QUALITY_FAILURE` | frame quality too low | → `POSITIONING` |

### 4.3 Attack Persistence

A single low-scoring frame does not trigger block. Attack is confirmed only after `attackPersistenceWindows` (default: 3) consecutive quality-OK windows score below `attackThreshold`.

---

## 5. Capture Integrity (IAD)

### 5.1 Injection Attack Detection

| Check | Detects | Method |
|-------|---------|--------|
| **Frame freeze** | Static image injection | pHash sliding window — identical hashes across N consecutive frames |
| **Frame loop** | Video loop injection | Sequence cycle detection in pHash history |
| **Device identity** | Virtual camera substitution | Device serial + certificate pinning per session |
| **Transaction binding** | RCAPTURE replay | Session nonce bound to transaction ID |
| **Freshness** | Stale capture replay | Timestamp window validation |

### 5.2 Frame Sequence Monitor

Uses perceptual hash (pHash) and timestamp analysis over a sliding window to detect:
- Frozen frames (identical pHash for `windowSize` consecutive frames)
- Looped sequences (repeating pHash patterns)
- Temporal anomalies (timestamp gaps, backwards timestamps)

---

## 6. Cryptographic Attestation

### 6.1 Attestation Structure

Produced **only** on `VERIFYING_FINAL → SUCCESS`:
- Subject ID, Session ID
- Image SHA-256, Transcript SHA-256
- Timestamp, Model version, Config version
- Capture mode (`STREAM`)
- PAD result (attack type, decision score, mechanisms, challenge echoes)
- ECDSA P-256 signature

### 6.2 Score Direction

- **Internal:** higher = more live (0-100)
- **MOSIP boundary:** `padDecisionScore = 100 - livenessScore` (lower = more bona fide)

### 6.3 Audit Chain

SHA-256 hash-chained append-only log. Each `AuditEvent` links to the previous via `previousHash`. Chain can be sealed and integrity-verified.

---

## 7. Active Challenge System

### 7.1 Challenge Pool

8 actions: `BLINK`, `SMILE`, `TURN_LEFT`, `TURN_RIGHT`, `LOOK_LEFT`, `LOOK_RIGHT`, `LOOK_UP`, `LOOK_DOWN`

### 7.2 Selection Rules

- `SecureRandom` selection
- No immediate repeat of same action
- No repeat of previous session's sequence
- System-selected only (never user-chosen)
- Pool filtered by workflow and accessibility profile

### 7.3 Detection

Landmark-based detectors:
- **Blink:** Eye Aspect Ratio (EAR) threshold
- **Smile:** Mouth Aspect Ratio (MAR) threshold
- **Head turns:** Yaw/pitch angle thresholds

---

## 8. Workflows

| Workflow | Active Challenges | Supervisor Auth | Notes |
|----------|-------------------|-----------------|-------|
| `RESIDENT_REGISTRATION` | Yes (if uncertain) | No | Standard flow |
| `OPERATOR_AUTH` | Yes (if uncertain) | No | Same pipeline |
| `SUPERVISOR_AUTH` | Yes (if uncertain) | Yes | SupervisorAuth instance created |
| `INFANT` | No (passive only) | No | Challenges skipped |

### 8.1 Supervisor Exemption Rules

- Supervisor **must** authenticate before exemption
- Exemption **NEVER** applies to `BLOCKED_ATTACK` sessions (§4, §22)
- All exemption attempts are audited

---

## 9. Configuration

All config keys under `mosip.registration.liveness.*`:

| Key | Default | Description |
|-----|---------|-------------|
| `attackThreshold` | 30 | Score below → attack |
| `passThreshold` | 70 | Score above → bona fide |
| `attackPersistenceWindows` | 3 | Consecutive attack windows to confirm |
| `activeEnabled` | true | Enable active challenges |
| `minChallenges` | 2 | Minimum challenges in uncertain band |
| `maxChallenges` | 3 | Maximum challenges |
| `challengeTimeoutMs` | 5000 | Per-challenge timeout |
| `maxRetries` | 3 | Retry budget per session |
| `faceSizeMinPx` | 64 | Minimum face bounding box |
| `poseMaxYawDeg` | 30 | Maximum yaw angle |
| `poseMaxPitchDeg` | 30 | Maximum pitch angle |
| `minQualifyingFrames` | 5 | Frames for composite score |

**Invariant:** `attackThreshold < passThreshold` (enforced at config load)

---

## 10. Internationalization

UI messages support 3 locales with RTL:
- English (EN)
- French (FR)
- Arabic (AR) — RTL

Messages keyed by state name, loaded from `UiMessages`.

---

## 11. Test Coverage

### 11.1 Test Suite Summary (333 tests, 36 test classes)

| Category | Tests | Coverage |
|----------|-------|----------|
| State machine matrix | 41 | All 13 states × valid/invalid transitions |
| Device error recovery | 13 | Disconnect, reconnect, integrity failure paths |
| Band routing | 6 | All 4 bands + edge cases |
| Supervisor auth | 14 | Auth, exemption, revocation, ATTACK never exempt |
| Challenge generation | 12 | Pool selection, no-repeat, SecureRandom |
| Challenge validation | 5 | Response matching, timeout |
| Attestation + crypto | 10 | ECDSA sign/verify, canonical format |
| Audit chain | 14 | Append, seal, integrity verification |
| Frame sequence monitor | 7 | Freeze, loop, temporal anomaly detection |
| Spatial PAD scorer | 4 | Composite scoring, window accumulation |
| Same-face checker | 4 | Cosine distance, threshold |
| Landmark detector | 5 | EAR, MAR, turn detection |
| Pose gate | 4 | Raw unit evaluation |
| Policy/config | 24 | Band table, challenge profiles, config bounds |
| Model qualification | 3 | MiniFASNet ONNX dimensions ([1,3,128,128]), size budgets, output logits |
| Attack taxonomy | 2 | Species enumeration |
| Manifest loader & parsing | 8 | Standard & extended demographic CSV formats, comments, malformed rows |
| Wilson confidence intervals | 8 | 95% Wilson binomial score intervals, edge boundary handling |
| PAD metrics computation | 9 | Per-species APCER, BPCER, DET curve, D-EER calculation |
| Fairness & bias analysis | 6 | DFIC Bias Index, Monk Skin Tone (MST 10), Rule of 30 exploratory flags |
| Evaluation report contract | 10 | ISO/IEC 30107-3 reporting rules, per-species rows, zero ACER, IAD separation |
| Evaluation harness lifecycle | 5 | Split filtering, threshold evaluation, Rule of 30 enforcement |
| Benchmark runner E2E | 1 | Full end-to-end evaluation run against ONOT benchmark manifest |
| **Android SBI Intent Bridge** | **29** | **Intent creation, response parsing, timeouts, error mapping** |
| **Android Platform Channel** | **16** | **MethodChannel/EventChannel contract against `events-schema.json`** |
| **Environment Integrity** | **14** | **Root, ADB debugging, su binaries, hook detection** |
| **CameraX Device Adapter** | **13** | **Frame queue drop-oldest, YUV conversion, thread safety** |
| **SBI JWT Verifier** | **10** | **JWS signature verification and tampering detection** |
| **ISO/IEC 19794-5 Face Parser** | **6** | **Record structure and face data parsing** |
| **Android Pipeline Integration**| **5** | **Full end-to-end simulated session (CameraX + SBI rCapture)** |
| **Latency Benchmark** | **5** | **Execution timing percentiles on mobile workloads** |
| **Mock Perceptual Hash** | **4** | **SplitMix64 perceptual hashing on mock frames** |
| **Integration (E2E Desktop)** | **16** | **All workflows, attacks, device errors, callbacks** |
| **Total** | **333** | **36 test classes, 100% pass rate** |

### 11.2 Integration Test Scenarios

| Scenario | Expected | Verified |
|----------|----------|----------|
| Bona fide → SUCCESS | `Verdict.PASS` + attestation + valid ECDSA signature | ✅ |
| Print attack → not SUCCESS | `Verdict.ATTACK`, no attestation | ✅ |
| Screen attack → not SUCCESS | Not PASS, no attestation | ✅ |
| Video attack → not SUCCESS | Not PASS, no attestation | ✅ |
| Device disconnect | `Verdict.DEVICE_FAILURE` | ✅ |
| Device busy | `Verdict.DEVICE_FAILURE` | ✅ |
| Device timeout | `Verdict.DEVICE_FAILURE` | ✅ |
| Operator auth workflow | `Verdict.PASS` | ✅ |
| Supervisor auth workflow | `Verdict.PASS` | ✅ |
| Infant workflow | `Verdict.PASS` (passive only) | ✅ |
| Failed liveness → no attestation | `attestation == null` | ✅ |
| Callback receives events | State changes received | ✅ |
| Uncertain score → challenge path | CHALLENGE_PROMPT reached | ✅ |
| Same-face mismatch | Not PASS | ✅ |
| Corrupt frame | Not PASS | ✅ |

---

## 12. Implementation Status & Limitations

### 12.1 Completed Milestones & Components
- **M1 Core Skeleton:** 13-state machine, table-driven transitions, 4-band routing, audit log, ECDSA attestation.
- **M2 Engine Depth:** Landmark detectors (EAR, MAR, yaw), capture integrity (pHash sequence monitor, freshness, nonce binding), quality gate.
- **M3 Desktop Integration:** MDS device adapters (STREAM, RCAPTURE, DISCOVERY), service layer, bounded async inference queue.
- **M4 Android Implementation:** CameraX adapter (`CameraXDeviceAdapter`), MOSIP SBI intent bridge (`SbiIntentBridge`, `SbiIntentDeviceAdapter`), cross-stream binder (`CrossStreamBinder`), environment integrity checker (`EnvironmentIntegrityChecker`), Flutter platform channel (`LivenessPlatformChannel`), ISO 19794-5 record parser, and Flutter UI package (`flutter/liveness_ui`).
- **M4 Operational Verification (ADR-009, ADR-012):** INT8 ONNX desktop latency benchmark (`Int8ModelBenchmarkTest`: mean 10.86 ms, p95 14.51 ms), real neural face embedder (`SFaceEmbedder` with OpenCV Zoo SFace at calibrated threshold 0.48), safe fail-closed JPEG 2000 decoder (`IsoFaceRgbDecoder`), PEM certificate trust anchor ingestion (`SbiJwtVerifier.fromPem`), and `AndroidKeyStoreSigner.ensureKey()`.
- **M5 Evaluation Framework:** ISO/IEC 30107-3 aligned evaluation harness (`liveness.evaluation`), Wilson 95% CIs, worst-species headline APCER (marked `[EXPLORATORY]` when $N < 30$), BPCER with Rule of 30 validation ($N=40 \ge 30$ bona fide subjects on test split), CEN/TS 18099 IAD metrics table, and Monk scale fairness analyzer.
- **M6 Documentation & Evaluator Tools:** Final technical design, multi-modal UI/UX guide (`UX_GUIDE.md`), evaluator walkthrough (`DEMO_SCRIPT.md`), ISO/IEC 30107-3 test report (`TEST_REPORT.md`), and decisions register (`decisions.md` ADR-001 to ADR-017).
- **PAD Model Integration:** Android ships the FP32 `MiniFASNetV2-SE` (1.82MB; ADR-017). The INT8 variant (612KB, dynamically quantized) is qualified for I/O only, has never been evaluated for accuracy, and is not shipped. Both run via ONNX Runtime (`1.16.3`), with 128x128 RGB inputs verified empirically via `ModelQualificationTest`.
- **Datasets:** Unpacked ONOT synthetic ICAO dataset (179 subjects, 5,811 images) and generated subject-disjoint manifests.

### 12.2 Remaining Gaps / Future Work

| Area | Current Status | Notes |
|------|---------------|-------|
| Same-face | SFace neural embedder active (`SFaceEmbedder`) | Calibrated at threshold $\tau = 0.48$ (FAR 4/100, FRR 1/50, ADR-012); cross-stream mobile pair collection tracked in `AUDIT_TODO.md` [Part 2 X5] |
| Face detection | Landmark detector with pose gate active | Production neural detector (YuNet / SCRFD) integration tracked in [Part 2 X10] |
| JavaFX UI | Event schema (`ui-contract`) defined | Desktop GUI widget bindings (Flutter mobile UI complete) |

---

## 13. Dependencies

| Dependency | Version | Licence | Purpose |
|-----------|---------|---------|---------|
| Java SE | 11 | Oracle/OpenJDK | Runtime (`--release 11`) |
| JUnit 5 | 5.9.3 | EPL-2.0 | Testing (JUnit Jupiter + Params) |
| ONNX Runtime | 1.16.3 | MIT | Native PAD model inference (`com.microsoft.onnxruntime`) |

---

## 14. Honest Coverage Statement (§15.5)

| Threat | Treatment | What We Claim |
|---|---|---|
| Printed photo (flat) | **Primary target** | Targeted detection with APCER measured across artefact intensities |
| Screen display (static/video) | **Primary target** | Targeted detection |
| Video replay | **Primary target** | Targeted detection |
| Cut-out mask | **Secondary target** | Increased resistance; detection under favourable conditions |
| Curved print | **Secondary target** | Increased resistance; detection under favourable conditions |
| 2D rigid mask | **Secondary target** | Increased resistance; detection under favourable conditions |
| 3D silicone/resin mask | **Experimental resistance** | Multi-cue analysis; no standalone detection claim |
| Real-time deepfake | **Mitigation only** | Challenge-response binding + latency + frame sequence monitoring; no detection guarantee |
| Injection attacks | **Architectural control (IAD)** | CaptureIntegrity module per CEN/TS 18099 concepts |
| Frozen frame | **Detection** | Detected for identical pHash sequence; unmeasured in offline still benchmark |
| Frame loop replay | **Detection** | Detected for repeated sequence window; unmeasured in offline still benchmark |

