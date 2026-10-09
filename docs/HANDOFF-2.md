# Agent Handoff — Face Liveness Detection + PAD
## MOSIP Decode 2026, Problem 04

**Date:** 4 Oct 2026 (Rev 3 — adds Section 15 research/source-check addendum, completes the state machine, updates diagrams)
**This document is your single entry point.** Read it fully before writing any code.

---

## 0. What You Are Receiving

You are receiving this handoff document plus a set of companion files. Together they contain everything you need to build the project from scratch. You do not need to read any ISO PDFs, research papers, or prior planning documents — all actionable content has been extracted into `MASTER_PLAN.md` **and into Section 15 of this file** (three later research papers plus a repo/doc check). Section 15 separates MUST / SHOULD / FYI items; only MUST items are binding.

### Files You Must Read (in order)

| # | File | What it is | How to use it |
|---|------|-----------|---------------|
| 1 | **This file (`HANDOFF.md`)** | Context, instructions, rules, build order, Section 15 addendum, complete state table (16.2) | Read first. Defines what you must do and how. |
| 2 | **`MASTER_PLAN.md`** | Complete architecture, algorithms, formulas, state machine, threat model, evaluation design, datasets, and config | Your **authoritative design spec**. Every section number (S1-S26, Appendix A/B/C) is a reference you will use repeatedly. Do not deviate from it without explicit instruction. Section 15 refines it (conflicts: 15.7). |

### Files You May Use As-Is (existing correct code)

These 7 files compile and are architecturally correct. Use them as your starting foundation:

| File | What it is |
|------|-----------|
| `pom.xml` | Maven build — Java 11, JUnit 5.9.3, UTF-8. Add dependencies here as needed (ONNX Runtime, etc). |
| `src/main/java/liveness/device/DeviceAdapter.java` | Interface: `open()`, `captureFrame()`, `close()`, `isOpen()` |
| `src/main/java/liveness/device/MockDeviceAdapter.java` | Scriptable test device with `Scenario` enum. Expand the enum as you add attack types. |
| `src/main/java/liveness/device/DeviceException.java` | Checked exception for device failures. |
| `src/main/java/liveness/core/FaceFrame.java` | Immutable frame metadata value object. Expand with pixel data, timestamps, landmarks in later milestones. |
| `src/main/java/liveness/core/FrameQuality.java` | Quality hint enum (`GOOD`, `SUSPECT`). Replace with OFIQ dimensions later. |
| `src/main/java/liveness/util/Sha256.java` | SHA-256 utility (digest, hex, fromHex). Pure Java, no dependencies. |

### Files You Will Receive Later

| File | When |
|------|------|
| Datasets (CelebA-Spoof, ONOT, Replay-Attack, team recordings) | Before Milestone 5 (Evaluation). Provided as a directory with a manifest CSV. |
| MiniFASNetV2-SE ONNX model file | Before Milestone 2 (Engine). You download it yourself from the URL in S17.4 of `MASTER_PLAN.md` (the repo has moved to `facenox/face-antispoof-onnx`; see 15.2 R1 for its real input spec). |

---

## 1. What You Are Building

A **face liveness detection + presentation attack detection (PAD)** system for the MOSIP Registration Client. It runs on:
- **Desktop:** Java 11, JavaFX
- **Android:** Java/Flutter, SDK 31, Android 9+

Three workflows: **resident registration**, **operator authentication**, **supervisor authentication** — both online and offline.

The system plugs into MOSIP's biometric capture pipeline at the **service layer** that creates the biometric record. It does NOT plug into the BioSDK quality/match/extract interface.

**The deliverable is not a single algorithm.** It is a complete system: 13-state machine + capture integrity + quality gate + passive PAD + active challenges + same-face verification + signed attestation + hash-chained audit log + UI/UX for 3 workflows x 8 states + evaluation harness.

---

## 2. Immutable Constraints (Do Not Violate)

These are non-negotiable. Copied from `MASTER_PLAN.md` S16 for emphasis:

1. **Java 11** target throughout. No Java 14+ features.
2. **ECDSA P-256 only** (`SHA256withECDSA`). No Ed25519.
3. **`previousHash`** in RCAPTURE is always `null`. Do not design a chain around it.
4. **Config namespaces:** `mosip.registration.liveness.*` and `mosip.face_authentication.liveness.*`.
5. **Liveness-failed capture is NEVER saved** under any condition.
6. **Attestation** is produced **only from `VERIFYING_FINAL -> SUCCESS`**.
7. **Quality checks run on the raw frame before crop/alignment.**
8. **Fully offline** — no network call for inference.
9. **ONNX model budget <= 5 MB** for the primary PAD model.
10. **Never fabricate** model URLs, weights, or dataset links.
11. **Do not mine or copy** `BestTeam-MosipDecode/face-liveness-pad`.

---

## 3. Build Order (Strict — Do Not Skip Ahead)

Build in this exact order. Each milestone must compile and pass its own tests before proceeding.

### Milestone 1 — Core (Days 1-2)

**Goal:** The skeleton compiles, the state machine works, policy routing is correct, mock device scenarios pass.

Build these (see `MASTER_PLAN.md` S4, S5, S6, S7, S8 for exact specs):

| Component | Package | Key spec reference |
|-----------|---------|-------------------|
| `LivenessState.java` (13-state enum) | `flow` | S4 — exact state graph |
| `LivenessStateMachine.java` (table-driven) | `flow` | S4 — all transitions; reject anything not in the table with `IllegalStateException` |
| `LivenessConfig.java` | `policy` | S7 — all config keys with defaults |
| `LivenessPolicy.java` (4-band routing) | `policy` | S5.1 — `attackThreshold < passThreshold` enforced at load |
| `Verdict.java` | `flow` | S6.5 — PASS is the only value that produces an attestation |
| `AuditEvent.java`, `AuditChain.java`, `AuditLog.java` | `audit` | S6.6 — hash-chained, `GENESIS` seed, `verify()` and `verifySealed()` |
| `Attestation.java` + `EcdsaSignerStub.java` | `crypto` | S6.5 — ECDSA P-256, canonical string for signing, `imageSha256` non-null check |
| `RetryRateLimiter.java` | `flow` | S6.8 — sliding window per `transactionId` and `machineId+userId` |
| `BoundChallenge.java` | `challenge` | S6.3 — fields: `challengeId`, `sessionNonce`, `action`, `expectedSequence`, `issuedAtMs`, `expiresAtMs`, `sequenceIndex` |
| `ChallengeGenerator.java` | `challenge` | S6.3 — `SecureRandom`, no immediate repeat, no session-sequence repeat |

**Tests to write (all must pass):**
- `StateMachineMatrixTest` — every valid transition succeeds; every invalid transition throws `IllegalStateException`
- `BandTableTest` — score routing: `< attackThreshold` -> CLEAR_ATTACK, `[attack, pass)` -> UNCERTAIN, `>= passThreshold` -> CLEAR_BONA_FIDE
- `BandRoutingTest` — persistence windows: attack not declared until N consecutive quality-OK windows
- `ChallengeGeneratorTest` — no immediate repeat; pool filtering works
- `AttestationTest` — ECDSA signs and verifies; `imageSha256` null -> NPE
- `AuditChainIntegrityTest` — append entries, `verify()` passes; tamper one entry, `verify()` fails

**Rev 3 additions:** `LivenessStateMachine` is built from the complete transition table in 16.2 (R2); `StateMachineMatrixTest` is generated from it. `ChallengeProfile` enum with bounds validation (R5, `ChallengeProfileBoundsTest`).

**Gate:** `mvn clean test` passes with 0 failures.

### Milestone 2 — Engine (Days 3-8)

**Goal:** Real PAD scoring with MiniFASNet ONNX, quality gate, landmarks, decision engine.

Build these (see `MASTER_PLAN.md` S3, S5, S6.1-6.4, S20 for specs):

| Component | Key details |
|-----------|------------|
| `MiniFasNetScorer.java` | ONNX Runtime wrapper. **Input shape, channel order, crop factor and scaling are defined by the exact ONNX file you ship (15.2 R1).** The old 80x80 BGR / 2.7x crop spec applies to the original Minivision weights; the S17.4 export documents 128x128 RGB, 1.5x square crop. **Verify class order and scaling empirically (`ModelQualificationTest`).** |
| Quality gate (own) | Laplacian blur, brightness mean, face bbox size, pose angle, single-face check. Runs on **raw frame before crop**. |
| `SpatialPadScorer.java` | Quality-weighted aggregate over qualifying frames (S5.4 formula) |
| `DecisionEngine.java` | Hard gates first (S5.3), then weighted fusion (S5.4) |
| `CaptureIntegrityChecker.java` + sub-components | S6.1 — `FrameSequenceMonitor` (pHash), `FreshnessChecker`, `SessionTransactionBinder`, `DeviceIdentityValidator` |
| `FrameFingerprint` | S6.1 — `pHash` (64-bit, 32x32 grayscale), `timestampMs`, `sequenceNum`, `widthPx` |
| Landmark-based action detectors | EAR blink (S20.1), MAR smile (S20.2), yaw turn |
| `ChallengeResponseValidator.java` | Reject replayed/expired; check sequence order for compound challenges |
| Same-face check | S6.4 — cosine distance gate; calibrate threshold on LFW |
| `LivenessSession.java` | S6 — orchestrator; no boolean flags; drives `LivenessStateMachine` |
| `ModelRegistry.java` | S6.7 — sha256 + signature at load; anti-rollback counter |

**Add to `pom.xml`:**
```xml
<dependency>
  <groupId>com.microsoft.onnxruntime</groupId>
  <artifactId>onnxruntime</artifactId>
  <version>1.16.3</version>
</dependency>
```

**Rev 3 additions:** `ModelQualificationTest` passes before any threshold work (R1). The stream quality gate works in raw units, never mapped scores, never a cut-off of 50, and is calibrated separately from the final-image gate (R3; `PoseGateRawUnitsTest`). Seeds and optional improvements: 15.3 (S1-S4), 15.4. ONNX Runtime `1.16.3` below is a baseline, not a requirement — see 15.6.

**Gate:** Bona fide fixture -> SUCCESS + attestation; print fixture -> BLOCKED_ATTACK; all 13 error rows from S8 tested.

### Milestone 3 — Desktop Integration (Days 9-14)

**Goal:** End-to-end on Desktop with JavaFX UI for all 3 workflows.

| Component | Key details |
|-----------|------------|
| `MdsHttpDeviceAdapter` | HTTP adapter using verified verbs: `MOSIPDINFO`, `SBIDISC`/`MOSIPDISC`, `STREAM`, `RCAPTURE` (S18) |
| Service-layer enforcement | Liveness enforcement lives in the service layer, NOT the UI (S22) |
| JavaFX screens | 3 workflows x 8 states (S9) — live preview with oval guide, challenge prompts, success/failure |
| `ui-contract` event schema | JSON events from engine -> UI; UI never calls engine directly |
| Nonce binding + freshness + same-face | Full RCAPTURE verification pipeline (S6.1, S26.7) |
| Config sync | Server config with `last-known-good` fallback |

**Rev 3 additions (SHOULD):** guidance keys `guide.face.too_close`, `guide.face.too_far`, `guide.pose.operator_adjust_camera` (S4); operator-selectable `ChallengeProfile` per workflow, never inferred from the face (R4, R5); optional aggregate telemetry (S7).

**Gate:** End-to-end three workflows on MockMDS; frozen/loop frames caught; device identity change rejected.

### Milestone 4 — Android (Days 15-19)

**Goal:** Android deployment with CameraX, Flutter screens, INT8 model.

| Component | Key details |
|-----------|------------|
| CameraX adapter | Host camera frames for liveness scoring |
| SBI intent flow | `io.sbi.device`, `.Info`, `.rCapture` intents |
| Flutter platform channel + screens | Same 3x8 state screens |
| INT8 model variant | Quantize and benchmark (S26.9); only ship if regression is acceptable |
| Environment integrity check | Root detection, USB debug detection |

**Rev 3 additions:** use `onnxruntime-android` (AAR); calibrate gate and same-face thresholds separately for Android (R3); keep profile behaviour identical to desktop.

**Gate:** p95 <= 200 ms on reference low-end device; all M1 scenarios pass.

### Milestone 5 — Evaluation (Days 20-24)

**Goal:** ISO/IEC 30107-3 compliant evaluation report.

| Component | Key details |
|-----------|------------|
| Evaluation harness | Per-species APCER/BPCER with Wilson CIs, DET curves, D-EER |
| IAD metrics table | Freeze/loop/nonce-swap detection rates (separate from APCER) |
| Fairness analysis | Bias Index (S24.4 formula), Monk Skin Tone scale, flag +/-5pp BPCER swings |
| Latency percentiles | p50/p95/p99 per component (S26.3) |
| Memory soak test | S26.10 — 10,000+ frames, verify no monotonic growth |
| Hard-negative regression | S26.13 |

**Rev 3 additions:** report contract per R6 (`EvalReportContractTest`): per-species rows, worst-species headline, no ACER, no pooled-only report. Fairness per R7 (>= 30 subjects per cell, CIs, no p-values); extras in S6. Optional manifest columns in 15.5.

**Datasets needed for this milestone (provided separately):**
- CelebA-Spoof (primary PAD)
- ONOT (bona fide source for self-made attacks)
- Replay-Attack (temporal + video PAD)
- Team-recorded set (>=30 subjects, Rule of 30)
- LFW (same-face calibration)

**Dataset manifest format:**
```
path, label, species, subject_id, split, lighting, skin_tone, glasses, source
```
`label` in {bona_fide, attack}; `species` in {print, screen, video, cutout, curved_print, mask_2d, mask_3d, deepfake}; `split` in {train, dev, test}.

**Gate:** Report generated from frozen thresholds; test split run once; ACER never reported.

### Milestone 6 — Documentation (Days 25-27)

**Goal:** Final technical design document, UX doc, test report, integration guide, demo script.

**Rev 3 additions:** limitations section includes the relevant items of 15.7; every `[PLACEHOLDER]` is replaced by a calibrated value or labelled uncalibrated.

**Gate:** Honest coverage statement (S15.5) reproduced verbatim in the report.

---

## 4. Key Architecture Decisions (Resolved — Do Not Re-Debate)

These decisions were made after extensive review. They are final.

| Decision | Rationale |
|----------|-----------|
| **MiniFASNetV2-SE** as primary PAD model | <=1.8 MB ONNX, Apache-2.0, covers primary attack targets. Lighter alternatives don't exist with verified licences. |
| **ECDSA P-256**, not Ed25519 | Ed25519 is not available in Java 11 without Bouncy Castle. |
| **Table-driven state machine** (13 states) | No boolean flags in `LivenessSession`. All routing from `machine.current()`. Invalid transitions throw `IllegalStateException`. |
| **4-band score routing** (QUALITY_FAILURE / CLEAR_ATTACK / UNCERTAIN / CLEAR_BONA_FIDE) | Two thresholds only: `attackThreshold < passThreshold`. Enforced at config load. |
| **Attack persistence windows** (default 3) | A single bad frame does not block. CLEAR_ATTACK only after N consecutive quality-OK windows below threshold. |
| **Quality gate before crop** | Radial distortion and optical artifacts exist in the raw frame geometry. Cropping discards the evidence. (Kabbani et al., arXiv:2501.07179) |
| **`BLOCKED_ATTACK` is terminal per session, not per person** | New session via `RetryRateLimiter`. Supervisor exemption never applies to ATTACK band sessions. |
| **Score direction: lower = more bona fide at the MOSIP boundary** | Internal: higher = more live (0-100). Map at attestation: `padDecisionScore = 100 - livenessScore`. (MOSIP issue #1117) |
| **FaceQgen rejected** | Training data: 114/130 male, entirely Caucasian. Authors acknowledge bias. Disqualifying for MOSIP's population. |
| **rPPG rejected as primary signal** | Video replay contains the original person's pulse; fairness risk (skin-tone dependent); privacy concern (physiological data extraction). |
| **No foundation models** | FoundPAD/CLIP: 300 MB-1 GB+, violates the <=5 MB model budget and offline Android constraint. |
| **Frame ownership model, not DirectByteBuffer mandate** | Deterministic erasure of all copies (GPU, native, ONNX runtime) cannot be guaranteed. Require explicit ownership + release + best-effort overwrite. |
| **ChallengeProfile abstraction, not hardcoded demographic multipliers** | Timeout policies (`DEFAULT`, `ACCESSIBILITY`, `LOW_QUALITY`, `INFANT`, `WORKFLOW_SPECIFIC`) must be validated, not assumed. Profiles relax time/pool/retries only, never security thresholds (R5). |
| **(Rev 3) Quality gate on raw units** | Mapped 0-100 scores saturate; raw units discriminate (R3). |
| **(Rev 3) No demographic thresholds or inference** | Fairness and discrimination risk; fix the measure instead (R4). |
| **(Rev 3) Per-species reporting only** | Pooled results hide weak species (R6). |

---

## 5. Critical Formulas (From MASTER_PLAN.md S20)

These are the exact formulas to implement. Do not substitute alternatives.

### Blink Detection (EAR)
```
EAR = (||p2-p6|| + ||p3-p5||) / (2 x ||p1-p4||)
```
Starting threshold: 0.2 (validate before shipping). Require minimum consecutive low-EAR frames.

### Blink Cross-Check (omega — ISO/IEC 29794-5)
```
omega = min(DL, DR) / IED
```
Two independent formulas agreeing is stronger than one alone.

### Quality-Weighted Fusion (S5.4)
```
composite = SUM(padScore[i] x quality[i]) / SUM(quality[i])
             over qualifyingFrames where ownQuality >= minQualityThreshold
```

### Score Mapping at MOSIP Boundary
```
padDecisionScore = 100 - livenessScore
```
(lower = more bona fide per MOSIP issue #1117)

### Audit Chain Hash
```
hash[i] = SHA-256( hash[i-1] || entry[i].canonical() )
```
First entry uses `"GENESIS"` as previous hash.

---

## 6. What NOT To Do

1. **Do not invent MOSIP class names.** Use the adapter pattern; ship standalone + thin integration shim.
2. **Do not report ACER.** ISO/IEC 30107-3 explicitly forbids combined/averaged metrics.
3. **Do not trust model repo docs for class order.** Verify empirically on known live/spoof samples (S26.11).
4. **Do not weight all frames equally in fusion.** Blurred/occluded frames contribute less.
5. **Do not run inference on the UI thread.** Use the bounded async pipeline (S26.1).
6. **Do not put biometric data in logs.** Scores and event codes only.
7. **Do not save liveness-failed captures.** Under any condition, any workflow, any `failure_behavior`.
8. **Do not use Fitzpatrick skin-tone scale.** Use Monk Skin Tone (10-tone).
9. **Do not claim to detect 3D silicone masks.** State "experimental resistance" (S15.5).
10. **Do not claim to detect real-time deepfakes.** State "mitigation only" (S15.5).
11. **Do not hardcode the 80x80 BGR model spec** — read it from the shipped model file (R1).
12. **Do not use mapped 0-100 quality scores or a cut-off of 50 for gating** (R3); **do not add demographic thresholds or infer demographics** (R4); **do not let a profile loosen PAD thresholds** (R5).
13. **Do not publish pooled-only results or ACER** (R6), and **do not rely on p-values or cells under 30 subjects** (R7).
14. **Do not turn FYI items (15.6) into requirements.**

---

## 7. Project Directory Structure

The target structure is defined in `MASTER_PLAN.md` S6. Here it is for quick reference:

```
face-liveness-pad/
  pom.xml
  src/main/java/liveness/
    capture-integrity/    (S6.1)
    core/                 (FaceFrame, FrameQuality)
    device/               (DeviceAdapter, MockDeviceAdapter, DeviceException)
    policy/               (LivenessPolicy, LivenessConfig)
    engine/               (MiniFasNetScorer, DecisionEngine, Tier A/B/C scorers)
    challenge/            (BoundChallenge, ChallengeGenerator, ChallengeResponseValidator)
    flow/                 (LivenessState, LivenessStateMachine, LivenessSession, RetryRateLimiter, Verdict)
    crypto/               (Attestation, EcdsaSignerStub)
    audit/                (AuditLog, AuditChain, AuditEvent)
    util/                 (Sha256)
  src/test/java/liveness/
    policy/BandTableTest.java
    flow/StateMachineMatrixTest.java
    flow/BandRoutingTest.java
    challenge/ChallengeGeneratorTest.java
    crypto/AttestationTest.java
    audit/AuditChainIntegrityTest.java
    capture/FrameSequenceMonitorTest.java
    evaluation/AttackTaxonomyTest.java
  docs/
    MASTER_PLAN.md
    HANDOFF.md
    decisions.md           (ADR — one entry per non-obvious design choice)
    licence-register.md
  ui-contract/
    events-schema.json     (shared Desktop + Android event schema)
```

---

## 8. Existing MOSIP Integration Points (From MASTER_PLAN.md S18)

These are VERIFIED facts from reading MOSIP's actual source code. They are correct.

### MDS/SBI HTTP Verbs
- `MOSIPDINFO` -> `/info` — port scan for device discovery
- `SBIDISC` (spec 1.0) or `MOSIPDISC` (spec 0.9.x) -> per-device readiness
- `STREAM` -> `/stream` — MJPEG preview (registration devices only)
- `RCAPTURE` -> `/capture` — final signed image (JWT)

### RCAPTURE Response Fields
```
digitalId, bioType, deviceCode, deviceServiceVersion,
bioSubType, purpose, env,
bioValue, bioExtract,
transactionId, timestamp, requestedScore, qualityScore
```
**No liveness or PAD field in this DTO.** Liveness is entirely client-side.

### Trust Domain Config Keys
```
mosip.registration.mdm.trust.domain.rcapture   = "DEVICE"
mosip.registration.mdm.trust.domain.digitalId  = "FTM"
mosip.registration.mdm.trust.domain.deviceinfo = "DEVICE"
```

### Key MOSIP Config Values (Do Not Override)
```
mosip.registration.face_threshold                   = 9
mosip.face_authentication.quality_score              = 30
mosip.registration.mdm.RCAPTURE.connection.timeout   = 40000
mosip.registration.capture_time_out                  = 10000
mosip.registration.agegroup-config  (JSON: INFANT <5 = face-only + guardian auth)
```

### Known Bug: isDeviceAvailable() OR-Logic
The spec_1_0 provider uses three separate `if` statements where AND was intended. A device can be "available" on any one condition alone. Do not replicate this bug.

---

## 9. UI/UX Requirements (Mandatory Deliverable)

This is deliverable #2 from the problem statement. Not optional.

### State -> Message Mapping

| State | EN Default |
|-------|-----------|
| POSITIONING | "Position your face in the oval" |
| PASSIVE | "Checking face liveness..." |
| CHALLENGE_PROMPT | "Please blink" / "Please smile" / "Please turn your head to the left" / "Please look left" |
| CHALLENGE_WAIT | "Please continue" / "Hold still" |
| SUCCESS | "Good, face captured successfully" |
| RETRY_AVAILABLE | "We could not verify face liveness. Please try again." + remaining attempts |
| BLOCKED_ATTACK | "Face verification could not be completed. Please try again." (generic; detail to audit only) |
| FAILED_MAX_RETRIES | "Verification could not be completed." + recovery path |
| DEVICE_ERROR | Device/camera guidance + reconnect instruction |

### Rules
- Status never indicated by colour alone
- Language-independent icons for challenges
- No model internals shown to users
- Supervisor-only diagnostics mode
- Localisation: EN/FR/AR minimum; AR requires RTL

---

## 10. Evaluation Protocol (Non-Negotiable)

See `MASTER_PLAN.md` S12 and S24 for full details. Key rules:

1. **Split by subject** (not by image). Use dataset's own split files.
2. **Calibrate on dev only.** Freeze thresholds. Run test split **once**.
3. **Report APCER per PAI species** across multiple artefact intensities.
4. **Never report ACER.**
5. **Rule of 30** — need >=30 bona fide subjects for meaningful BPCER.
6. **Wilson confidence intervals** on all rates.
7. **IAD metrics in a separate table** — not APCER values.
8. **Fairness:** Bias Index formula, Monk Skin Tone scale, flag +/-5pp BPCER swings.
9. **Honest coverage statement** (S15.5) reproduced verbatim in final report.
10. **(Rev 3)** Per-species rows, worst-species headline, image-level vs video-level separate (R6).
11. **(Rev 3)** Fairness: >= 30 subjects per cell else exploratory; effect sizes + CIs, not p-values (R7).

---

## 11. Datasets and Models — Quick Reference

Full details with access links, verification status, and rejection reasons are in `MASTER_PLAN.md` S17. Here is the priority summary:

### Use Immediately (No EULA)
| Dataset/Model | Purpose |
|---------------|---------|
| ONOT | Bona fide source; print/screen these yourself to make labelled attacks |
| CelebA-Spoof | Primary single-frame PAD training/evaluation |
| CelebA | Attribute-wise quality gate checks |
| LFW | Same-face threshold calibration |
| MiniFASNetV2-SE ONNX | Primary PAD model (FP32 ~1.82 MB, INT8 ~600 KB; Apache-2.0; I/O spec in 15.2 R1) |
| YuNet + SFace | Face detection + same-face embedder |

### Request (Slow Approval)
| Dataset | Purpose |
|---------|---------|
| Replay-Attack (Idiap) | Temporal + video PAD eval |
| WMCA (Idiap) | Mask/cut-out experiments |
| 3DMAD (Idiap) | Experimental 3D mask work |
| OULU-NPU | Cross-device eval |

### Record Yourselves (Mandatory — Cannot Substitute)
- >=30 consenting subjects
- All challenge actions (blink, smile, turns, looks)
- Good/low light, glasses, face coverings
- Print attacks at 300/600/1200 dpi on matte and glossy
- Screen attacks on phone/tablet/laptop
- Video replay attacks
- Tag: Monk Skin Tone, lighting, glasses, coverings, source

### Rejected (With Reasons)
| Item | Why |
|------|-----|
| FoundPAD / foundation model LoRA | 300 MB-1 GB+; violates <=5 MB model budget |
| CLIP-based PAD | ~400 MB CLIP; no verified ONNX for Java |
| rPPG as primary liveness | Replay contains original pulse; skin-tone bias; privacy risk |
| Gaussian curvature from MediaPipe depth | 2D-predicted depth; a print likely produces face-like depth |
| Colour naturalness as scored signal | Skin-tone dependent; fairness risk |
| FaceQgen | Training: 114/130 male, entirely Caucasian |
| CDCN/CDCN++ | Heavier; no verified permissive ONNX export |

---

## 12. Open Questions for MOSIP (Unresolved)

These are listed in `MASTER_PLAN.md` S14. They affect design decisions but do not block M1-M2. Make reasonable assumptions documented in `docs/decisions.md`. If MOSIP answers, update accordingly.

Key ones:
1. Is this a patch against the real registration client or standalone + shim? -> **Assume standalone + shim.**
2. Frame source for auth-purpose devices (no STREAM, DISCRETE only)? -> **Assume DISCRETE mode; document the limitation.**
3. Infant/guardian policy? -> **Assume passive-only + operator attestation.** (U26 supports this: adult-trained quality models over-reject young children.)
4. May `BLOCKED_ATTACK` sessions retry via rate limiter? -> **Assume yes, through `RetryRateLimiter`.**

---

## 13. What Success Looks Like

When you are done, the project must satisfy:

1. **`mvn clean test`** passes with 0 failures at every milestone.
2. **Bona fide fixture -> SUCCESS** with a valid ECDSA-signed attestation containing a non-null `imageSha256`.
3. **Print/screen/video fixture -> not SUCCESS** (either `BLOCKED_ATTACK` or `RETRY_AVAILABLE`).
4. **All 13 error scenarios from S8** are tested and produce the correct state transition.
5. **Frozen/loop/replayed frames** are caught by `FrameSequenceMonitor` before reaching PAD.
6. **Device identity change mid-session** is rejected.
7. **Invalid state transitions** throw `IllegalStateException`.
8. **Audit chain** is tamper-evident: modifying any entry causes `verify()` to fail.
9. **No boolean flags** in `LivenessSession`. All routing from `machine.current()`.
10. **Honest coverage statement** (S15.5) is accurate and reproduced verbatim.
11. **(Rev 3)** `StateMachineMatrixTest` covers the full 16.2 table; `ModelQualificationTest`, `ChallengeProfileBoundsTest`, `PoseGateRawUnitsTest`, `EvalReportContractTest` pass.
12. **(Rev 3)** No shipped threshold is an unlabelled `[PLACEHOLDER]`.

---

## 14. Final Checklist Before You Start Coding

- [ ] Read `MASTER_PLAN.md` S0-S9 (architecture + state machine + modules + config + errors + UI)
- [ ] Read `MASTER_PLAN.md` S16 (immutable constraints)
- [ ] Read `MASTER_PLAN.md` S5 (decision logic — 4-band routing, fusion formula)
- [ ] Read `MASTER_PLAN.md` S6 (module specs — every component's exact fields and behavior)
- [ ] Read `MASTER_PLAN.md` S26 (production refinements — async pipeline, freshness, telemetry, memory hygiene)
- [ ] Read **Section 15** (MUST items R1-R7 first), then the state table in **16.2**
- [ ] Verify the existing 6 Java files compile: `mvn clean compile`
- [ ] Begin Milestone 1: `LivenessState` enum -> `LivenessStateMachine` -> `LivenessConfig` -> `LivenessPolicy` -> tests

---

## 15. Research and Source-Check Addendum (Rev 3)

Three research papers plus a check of the actual repos/docs were reviewed after `MASTER_PLAN.md` was frozen. **You will not receive the papers.** This section adds to `MASTER_PLAN.md`; if the two conflict, `MASTER_PLAN.md` wins unless 15.7 says otherwise.

**Not every good finding is a requirement.** Items are sorted into three levels:

| Level | Meaning |
|---|---|
| **MUST (R1-R7)** | Binding. Small in number on purpose. Tested or reported. |
| **SHOULD (S1-S8)** | Cheap and sensible. Do it unless dev-set evidence shows no gain; if skipped, note it in `docs/decisions.md`. |
| **FYI (15.6)** | Context and numbers. **Do not implement** unless the owner asks. |

**Evidence tags:** `[PAPER]` stated in a paper · `[REPO]` stated in a project's own docs (Oct 2026) · `[WEB]` secondary source · `[DERIVED]` reviewer's inference · `[PLACEHOLDER]` starting number that must be replaced by dev-split calibration (never ship as if calibrated).

### 15.1 What Was Reviewed

| ID | Source | Trust |
|----|--------|-------|
| **U26** | Utcke et al., "Demographic Variability of Face Image Quality Measures in Operational Data," IEEE Access 2026. OFIQ (ISO/IEC 29794-5 reference implementation) on >109k passport-chip images and >172k live border-gate webcam probes. | High for direction. Data is not public. Border-control setting; authors warn it may not transfer to mobile. |
| **G26** | Grd et al., "Evaluating the Unified Physical and Deepfake Attack Detection in Face Biometric Systems," MIPRO 2026. One binary real-vs-attack CNN on CelebA-Spoof + Celeb-DF. | Moderate for method; authors themselves say closed-set, RGB-only, no augmentation, no cross-dataset test. |
| **C19** | Corsetti et al., "Face Image Analysis in Mobile Biometric Accessibility Evaluations," 2019 (AMBER project). 48 participants (31 control incl. 8 older adults; 17 accessibility). | Directional only (groups far below 30). |
| **Source check** | Docs of the PAD model repo named in S17.4 (README, ARCHITECTURE, DATA_PREPARATION, LIMITATIONS), OFIQ repo, ONNX Runtime on Maven Central, secondary sources on ACER. | Read directly. **Not read:** the PAD repo's inference source code (site blocked automated access), ISO/IEC 30107-3:2023, ISO/IEC 30107-4. |

### 15.2 MUST

**R1 - The PAD model's I/O contract comes from the model file, not from the plan.** `[REPO]` The plan (S5.5) and Milestone 2 describe a `1x3x80x80` BGR `[0,1]` input with a 2.7x crop; that matches the *original* Minivision weights. The export named in S17.4 is documented differently: **128x128 RGB** input; **square crop at 1.5x the detected face box**; padding with `BORDER_REFLECT_101` when the crop leaves the image; `LANCZOS4` when upscaling, `INTER_AREA` when downscaling; files: FP32 ~1.82 MB, INT8 ~600 KB; Apache-2.0. The repo now lives at `github.com/facenox/face-antispoof-onnx` (the `suriAI/` URL in the plan may redirect — verify).
- Read the input shape from the ONNX at load. Implement the preprocessing documented for the exact file you ship.
- `ModelQualificationTest` (S26.11) must prove, on known live/spoof crops and **before any threshold work**: channel order, value scaling (not stated in the docs read), class order, score direction.
- Record model sha256 + preprocessing spec in `ModelRegistry` and `docs/licence-register.md`. If you ever switch to the 80x80 weights, change the spec with the file.

**R2 - Use the complete state-transition table (Section 16.2).** The simplified graph in `MASTER_PLAN.md` S4 omits transitions that S5.1, S8 and S23 require (e.g. PASSIVE -> BLOCKED_ATTACK on CLEAR_ATTACK; CHALLENGE_WAIT -> BLOCKED_ATTACK; PASSIVE -> POSITIONING). Implement the table; `StateMachineMatrixTest` is generated from it.

**R3 - Stream quality gate uses raw physical units, never saturating 0-100 mapped scores, never a default cut-off of 50.** `[PAPER]` In U26, pose measures stay near 100 even at 25 degrees; some measures cluster near one value or are bimodal, so a mapped-score threshold does not discriminate. Use degrees, pixels, Laplacian variance, clipped-pixel fractions. Calibrate **the liveness-stream gate and the final RCAPTURE image gate separately** (U26: live capture scores far below reference capture; they are different distributions). Map to 0-100 only for UI/audit display.

**R4 - No demographic thresholds, no demographic inference in the engine.** `[PAPER+DERIVED]` U26 finds clear gender/age/skin-tone effects in quality measures but advises global thresholds plus drift monitoring rather than per-group thresholds (fairness and discrimination risk). The engine never estimates or consumes age, gender, skin tone, ethnicity or disability. The only age-related input is MOSIP's existing `agegroup-config` (INFANT), supplied as configuration. If a measure is biased, fix or drop the measure; do not add group-specific cut-offs. Demographics appear only in offline evaluation.

**R5 - `ChallengeProfile` may relax time/pool/retries, never security.** `[DERIVED from C19]` ACCESSIBILITY/LOW_QUALITY/INFANT profiles may change `challenge_timeout_ms`, `max_retries`, `max_challenges`, compound-challenge enablement and challenge pool. They may **not** change `attackThreshold`, `passThreshold`, persistence windows, capture-integrity rules, or same-face thresholds. Enforce in config validation (`ChallengeProfileBoundsTest`). C19 supplies **no** timeout values; choose them in the pilot. Profiles are selected by the operator or MOSIP config, never inferred from the face.

**R6 - Evaluation reports are per species; pooled-only reports are forbidden.** `[PAPER]` In G26 only ~1.5% of attack samples (1,128 of 74,585) are deepfake videos, so its headline AUC 0.999 is dominated by physical attacks, and deepfake-only slices were far worse (BPCER up to 0.26 after unified training). Report APCER per PAI species with Wilson CIs and **headline the worst species** (ISO's system-level recommendation is the maximum APCER over species `[WEB]`), plus BPCER. Report image-level and video-level results separately. Do not report ACER in the ISO-style table (see 15.7 item 1). A deepfake replayed on a screen is tagged `species=screen, content_origin=deepfake`; a digitally injected deepfake is an injection attack for CaptureIntegrity, not for PAD.

**R7 - Fairness claims need enough subjects and effect sizes.** Keep the S12.4 stratified BPCER/APCER and +/-5 pp flag. In addition: a cell with fewer than **30 bona fide subjects** is labelled *exploratory* (Wilson CI, no conclusions); report rates/differences with CIs, **not ANOVA p-values** (with N in the thousands everything is "significant" — U26 found p < 0.05 everywhere); decide the Monk-band merge rule before seeing results. Use age bins 0-20, 20-30, 30-40, 40-50, 50+ (comparable with U26).

### 15.3 SHOULD

- **S1 - Per-person neutral baseline for blink/smile.** Compute median EAR/MAR over the first `challenge.baseline_frames` qualifying frames and accept the action as a relative change from baseline *in addition to* the absolute floor (EAR 0.2). Reason: U26 shows expression/landmark measures shift with age. The relative factor is `[PLACEHOLDER]` — tune on fixtures. Expression is only used to verify the requested action; never feed it into PAD fusion.
- **S2 - Discriminative-power screen.** Before a measure may reject anyone, take p5/p50/p95 on the dev bona fide set. If p5 is already at the "good" end, make it monitor-only; if bimodal, treat as a binary flag.
- **S3 - Exposure health over bare mean brightness; resolution-normalised sharpness.** Prefer clipped-shadow/highlight fractions and in-face contrast; compute Laplacian variance on a fixed-size face ROI resampled from the raw frame. (U26: luminance-type measures are the skin-tone-sensitive ones.) Always keep a Monk-stratified rejection table for whichever exposure measure you ship.
- **S4 - "Too close" upper bound and operator-first guidance.** Add `quality.face_height_max_ratio` (start from S19.1 Full Frontal <= 80%) and guide keys `guide.face.too_close`, `guide.face.too_far`, `guide.pose.operator_adjust_camera`. Pose/position problems are POSITIONING, never an attack. `[C19 hypothesis, weak]`
- **S5 - Accessibility handling defaults.** Keep `max_retries` for ACCESSIBILITY at least equal to DEFAULT and never charge device/quality failures to the budget (S8); disable compound challenges and let the operator drop head-turn challenges for limited neck mobility; order the pool so the least demanding action comes first; use calm, actionable failure wording (Section 9 of this file). C19 saw users improve with practice and older adults tire, which supports retries and short sessions.
- **S6 - Fairness extras.** Subgroup discard-rate table per quality threshold; best/worst intersectional row (gender x age x Monk band — U26's female 0-20 vs male 40-50 gap was ~23 UQS points in UC1); per-group same-face FNMR; two annotators for Monk tone (record disagreement rate); optional per-group error-vs-discard curves for the quality gate.
- **S7 - Optional aggregated telemetry** (maps to the problem statement's "anonymised operational metrics"): percentiles of gate measures, rejection counts by reason code, retry/failure rates per site / device bucket / `config.version`. No subject identifiers, no images, no demographics.
- **S8 - Training hygiene if you ever train anything:** subject-disjoint splits; no frames of one video across splits; class-balanced sampling; JPEG/JPEG2000-lossy augmentation. (G26 describes an 80:20 split without saying it is subject-disjoint, used no augmentation.)

### 15.4 Seed Values from the PAD Model Docs `[REPO]` (calibrate; do not treat as validated)

| Fact | Use as |
|---|---|
| Source face should be at least 64x64 px; upscaling small blurry faces destroys the artifacts | Seed `quality.face_size_min_px` >= 64 (measured on the raw face box) |
| Works best frontal, about +/-30 degrees yaw/pitch; profile views lose accuracy | Seed stream-gate `quality.pose_max_yaw_deg`/`pitch_deg` = 30 for *PAD scoring frames*. This is **not** the +/-5 degree ICAO figure, which applies to the final image |
| Turn challenges push yaw beyond that comfort zone | `[DERIVED]` During TURN, down-weight or skip PAD scoring for frames beyond the limit; rely on challenge validation + geometry cues. Validate empirically |
| Authors suggest requiring 3-5 consecutive "real" frames in video | Matches the plan's persistence windows. Do not shorten them |
| Author-reported test on 70k+ CelebA-Spoof samples: 98.20% accuracy, real-class 97.6%, spoof 98.7% (INT8 same) | About 2.4% of real faces misclassified *per frame* in-domain, so never decide on a single frame. In-domain numbers are optimistic; the cross-dataset/own-camera runs in S12 are what count |
| Not trained on 3D silicone masks/prosthetics; poor in low light/backlight | Keep the S15.5 coverage statement; feed backlight/low-light into the quality gate |
| Default threshold balanced for FPR < 2% | Your `attackThreshold`/`passThreshold` come from dev calibration, not this |

### 15.5 Config, Tests, Manifest

**Config keys added or seeded** (extend S7; same namespaces; server-signed bounds still apply):

| Key | Default | Notes |
|---|---|---|
| `quality.face_size_min_px` | seed 64 `[REPO]` | existing key; calibrate |
| `quality.pose_max_yaw_deg`, `quality.pose_max_pitch_deg` | seed 30 `[REPO]` | stream gate, raw degrees (R3) |
| `quality.face_height_max_ratio` | from S19.1, then calibrate | S4 |
| `quality.final.*` | calibrated separately | final RCAPTURE image (R3) |
| `challenge.profile` | `DEFAULT` | per-workflow; operator/MOSIP-set |
| `profile.<NAME>.challenge_timeout_ms`, `.max_retries`, `.max_challenges`, `.allow_compound`, `.challenge_types` | set in pilot | must pass R5 bounds |
| `challenge.baseline_frames` | = `minQualifyingFrames` | only if S1 is built |

**Tests added:**

| Milestone | Test | Proves |
|---|---|---|
| M1 | `StateMachineMatrixTest` (extended) | Every row of the 16.2 table passes; every other (state, event) pair throws `IllegalStateException` |
| M1 | `ChallengeProfileBoundsTest` | R5: a profile cannot loosen thresholds/windows; timeouts/retries stay inside server bounds |
| M1 | `DeviceErrorRecoveryTest` | 16.2 DEVICE_ERROR recovery: benign reasons are free and bounded, integrity failures count toward lock |
| M2 | `ModelQualificationTest` | R1: channel order, scaling, class order, score direction on known live/spoof crops |
| M2 | `PoseGateRawUnitsTest` | R3: a ~25 degree-yaw fixture is judged on degrees (stream gate), not on a mapped score |
| M5 | `EvalReportContractTest` | R6: per-species APCER rows with CIs, BPCER row, worst-species headline, no ACER, IAD in a separate table, refuses pooled-only |

**Manifest (backward-compatible).** Keep the M5 columns. The loader must accept manifests without these optional trailing columns: `gender, age_group, capture_device, content_origin`. `age_group` in {0-20, 20-30, 30-40, 40-50, 50+}; `content_origin` in {real, deepfake}. Used for offline evaluation only (R4).

### 15.6 FYI (context; not requirements)

- **U26 headline numbers.** Mean UQS UC1 / UC3: female 61.11 / 41.62 vs male 71.95 / 51.41. UQS rises with age and levels off around 25; very young children score markedly lower (authors blame adult-dominated training data — consistent with the INFANT assumption). Luminance/contrast/dynamic-range measures are lower for the darkest tone class in reference images, much less so in live capture. UQS itself varies little by tone. Quality-driven discarding lowered false non-match rate less steeply for females and the youngest group. Many passport-image outliers were simply low resolution (country-specific capture standards).
- **U26 setting.** MagFace-derived utility score; percentile thresholds at 5/10/15% discard; 5th-percentile UC3 UQS about 13 overall. Use these only to appreciate the *shape* of the problem.
- **C19.** ICAO frontal-pose compliance: control 91-99% across sessions vs accessibility group 61-77% (n = 17); older adults dropped between sessions (tiredness suspected); more negative expressions in older/accessibility groups (stress suspected). Study design worth borrowing if you collect data: enrolment + verification, second session about a week later. If you include older adults or people with accessibility needs, label them as exploratory cohorts and obtain consent (special-category data).
- **G26 recipe (Tier C idea, weight 0, after M5 only).** ImageNet-pretrained backbone, 224x224, Adam, BCE-with-logits, batch 32; head only 15 epochs at 1e-3, then last 40 layers 20 epochs at 1e-5; threshold at EER on validation. MobileNetV3 matched or beat heavier backbones in the unified setting (ACER 0.0074 vs 0.0113 EfficientNetV2S vs 0.0080 ConvNeXtV2). It would graduate only if it beats the primary on dev **and** cross-dataset and exports under 5 MB; model size and variant are unstated in G26.
- **OFIQ.** MIT, C/C++, has a mobile build guide. Third-party Python wrappers exist; one says the model/config download is about 400 MB `[WEB]`. That footprint is why OFIQ stays optional (S6.2) and off the Android build. Wrappers can be used **offline** to run OFIQ on the dev set for comparison. Its UQS is a utility score, not a liveness score; never use it as a PAD signal.
- **ONNX Runtime.** `1.16.3` in M2 is a known-compatible baseline, not a requirement. Newer 1.26/1.27 builds are on Maven Central; the Android artifact is `com.microsoft.onnxruntime:onnxruntime-android` (AAR), distinct from the desktop `onnxruntime`. Pin whichever version passes the full test suite and runs on Java 11 (desktop).
- **Related unified-detection work named by G26** (UniFAD, JFSFDB, MS-UFAD, SHIELD, FA3-CLIP): unverified leads; UniFAD reportedly found naive pooling of attack types can hurt generalisation, which is another reason to keep per-species reporting. Not added to S17.
- **Rejected:** commercial SDKs used in C19 (VeriLook, Preface) and the non-public operational datasets; vendor brightness/eye-contrast scales are not normative thresholds.

### 15.7 Conflicts and Open Items

1. **ACER.** `MASTER_PLAN.md` S24.1 says never report ACER. Two secondary sources state averaging APCER/BPCER is deprecated in ISO/IEC 30107-3:2017 for industry evaluations and still used in research benchmarks for comparability `[WEB]`; G26 reports ACER and calls it ISO-compliant citing the 2023 edition, which nobody on this project has read. **Keep the plan's rule** (do not report ACER in the ISO-style results; if you add it for literature comparison, label it "non-ISO, for comparison only"). Close this by reading the 2023 edition if obtainable.
2. **DEVICE_ERROR. RESOLVED.** S4 calls it terminal, S23.4 describes reconnecting. Decision (16.2): terminal for the session, recovery via a new session, with benign vs integrity reasons treated differently. Log in `docs/decisions.md`.
3. **Plan vs model spec** (R1): the plan's 80x80 text is superseded for the shipped file.
4. **Former ASSUMPTION rows (17, 21, retry path) are now DECIDED** in 16.2 using the problem statement; record them in `docs/decisions.md`. The retry path (RETRY_AVAILABLE -> PASSIVE, which can fall back to POSITIONING by row 9) needs no change: it matches the problem statement's resident workflow (retry, subject to the configured retry limit; clear retry indication; guide to recovery when the maximum is reached).
5. **Domain transfer.** U26 = supervised border gates, C19 = tiny mobile pilot, G26 = celebrity datasets. None matches MOSIP registration; your own dev/test numbers are what count.

---

## 16. Reference Diagrams

ASCII diagrams: pipeline, complete state machine (table + map), success sequence, component view, and failure-path sequences (challenge, attack blocked, device error/recovery), PAD preprocessing, and evaluation data flow. These reflect `MASTER_PLAN.md`; Rev 3 marks Tier A/B stages, completes the state transitions, and adds the component view.

### 16.1 System Pipeline (9 Stages)

```
Frame from device
    │
    ▼
┌───────────────────────────────────────┐
│ 1. CAPTURE INTEGRITY (IAD)            │
│   DeviceIdentityValidator             │
│   FrameSequenceMonitor (pHash)        │
│   FreshnessChecker                    │
│   SessionTransactionBinder            │
│   FAIL → DEVICE_ERROR (session ends)  │
└───────────────────────────────────────┘
               │ PASS
               ▼
┌───────────────────────────────────────┐
│ 2. QUALITY GATE  [Tier A]             │
│   Runs on RAW frame, BEFORE crop      │
│   Raw units: blur, exposure/clipping, │
│   face px + ratio, pose in degrees,   │
│   single face (no 0-100 cut-offs)     │
│   FAIL → POSITIONING (not an attack)  │
└───────────────────────────────────────┘
               │ PASS
               ▼
┌───────────────────────────────────────┐
│ 3. SPATIAL PAD (per-frame)            │
│   [Tier A] MiniFASNetV2-SE (ONNX)     │
│     crop/size/scaling = model file    │
│   [Tier B: keep only if it beats A]   │
│     Moiré/FFT · glare+bezel · LBP     │
└───────────────────────────────────────┘
               │ scores
               ▼
┌───────────────────────────────────────┐
│ 4. TEMPORAL + GEOMETRY PAD [Tier B]   │
│   Landmark jitter (N-frame window)    │
│   Optical-flow sanity                 │
│   Planar-homography residual (turns)  │
│   Yaw asymmetry (during turns)        │
│   Keep only if it beats Tier A alone  │
└───────────────────────────────────────┘
               │ scores
               ▼
┌───────────────────────────────────────┐
│ 5. DECISION ENGINE                    │
│   Hard gates first (S5.3)             │
│   Quality-weighted fusion (S5.4)      │
│   Band needs N consecutive windows    │
│   CLEAR_ATTACK → BLOCKED_ATTACK       │
│   CLEAR_BONA_FIDE / UNCERTAIN ↓       │
└────────┬─────┴──────┬─────────────────┘
         │            │
  CLEAR_BONA_FIDE  UNCERTAIN
         │            │
         │            ▼
         │   ┌──────────────────────┐
         │   │ 6. ACTIVE CHALLENGE  │
         │   │ BoundChallenge       │
         │   │ nonce+expiry+seq     │
         │   │ PAD re-runs during   │
         │   │ the challenge window │
         │   └──────────┬───────────┘
         └──────┬───────┘
                │
                ▼
   ┌────────────────────────┐
   │ 7. SAME-FACE CHECK     │
   │ Liveness frames vs.    │
   │ RCAPTURE image         │
   │ Cosine distance gate   │
   └────────┬───────────────┘
            ▼
   ┌────────────────────────┐
   │ 8. T1 RCAPTURE BINDING │
   │ Session nonce verified │
   │ JWT signature valid    │
   │ Freshness window       │
   │ T1 device mandatory    │
   │ FINAL PAD CHECK        │
   └────────┬───────────────┘
            ▼
   ┌────────────────────────┐
   │ 9. ATTESTATION + AUDIT │
   │ ECDSA P-256 signed     │
   │ imageSha256 non-null   │
   │ Hash-chained AuditLog  │
   └────────────────────────┘
```

### 16.2 State Machine (13 States) — Complete Transition Table

**This table is authoritative for implementation (Rev 3, R2).** `MASTER_PLAN.md` S4 shows a simplified graph; every transition below is required by the plan section cited. Anything not in the table throws `IllegalStateException`. Rows marked **DECIDED** were silent in the plan and were resolved against the problem statement — record each in `docs/decisions.md`.

Terminal states (per **session**; a new session is allowed through `RetryRateLimiter`): `SUCCESS`, `BLOCKED_ATTACK`, `FAILED_MAX_RETRIES`, `DEVICE_ERROR`.

| # | From | Event / condition | To | Source |
|---|---|---|---|---|
| 1 | IDLE | start capture | DEVICE_CHECK | S23.1 |
| 2 | DEVICE_CHECK | device ready | POSITIONING | S4 |
| 3 | DEVICE_CHECK | device/camera unavailable, connection failure | DEVICE_ERROR (reason `DEVICE_UNAVAILABLE`) | S8 |
| 4 | POSITIONING | enough qualifying frames (quality gate passes) | PASSIVE | S4, S5.4 |
| 5 | POSITIONING | face not detected / positioning timeout | RETRY_AVAILABLE | S8 |
| 6 | PASSIVE | band CLEAR_BONA_FIDE | FINAL_CAPTURE | S4 |
| 7 | PASSIVE | band UNCERTAIN | CHALLENGE_PROMPT | S5.1 |
| 8 | PASSIVE | band CLEAR_ATTACK (persisted N windows) or hard gate | BLOCKED_ATTACK | S5.1, S5.3, S23.3 |
| 9 | PASSIVE | quality drops below minimum | POSITIONING | S5.3, S8 |
| 10 | PASSIVE | liveness engine error | RETRY_AVAILABLE | S8 |
| 11 | CHALLENGE_PROMPT | prompt issued | CHALLENGE_WAIT | S4 |
| 12 | CHALLENGE_WAIT | action confirmed, more challenges required | CHALLENGE_PROMPT | S4 |
| 13 | CHALLENGE_WAIT | action confirmed, minimum challenges met | FINAL_CAPTURE | S23.2 |
| 14 | CHALLENGE_WAIT | timeout or wrong/failed action | RETRY_AVAILABLE | S8, S23.2 |
| 15 | CHALLENGE_WAIT | PAD hard flag / CLEAR_ATTACK during challenge | BLOCKED_ATTACK | S23.2 |
| 16 | FINAL_CAPTURE | RCAPTURE returned | VERIFYING_FINAL | S4 |
| 17 | FINAL_CAPTURE | RCAPTURE failure / timeout | DEVICE_ERROR (reason `DEVICE_UNAVAILABLE`) | S8. **DECIDED** (problem statement: handle device communication failures) |
| 18 | VERIFYING_FINAL | all commit checks pass (S26.7) | SUCCESS | S4, S26.7 |
| 19 | VERIFYING_FINAL | same-face mismatch / final quality fail | RETRY_AVAILABLE | S4 |
| 20 | VERIFYING_FINAL | final PAD check CLEAR_ATTACK | BLOCKED_ATTACK | S4 |
| 21 | VERIFYING_FINAL | JWT / nonce / freshness / device-tier binding fails | DEVICE_ERROR (reason `INTEGRITY_FAILURE`) | S3 stage 1/8. **DECIDED:** treated like stage-1 IAD; counts toward the lock budget |
| 22 | RETRY_AVAILABLE | retry granted by `RetryRateLimiter` | PASSIVE | S4 |
| 23 | RETRY_AVAILABLE | budget exhausted / rate-limited | FAILED_MAX_RETRIES | S4 |
| 24 | any non-terminal (POSITIONING, PASSIVE, CHALLENGE_PROMPT, CHALLENGE_WAIT) | device disconnects mid-stream | DEVICE_ERROR (reason `DEVICE_UNAVAILABLE`) | S23.4 |
| 25 | any non-terminal (same set) | IAD check fails mid-stream (frozen/looped frames, device identity change) | DEVICE_ERROR (reason `INTEGRITY_FAILURE`) | S3 stage 1 |

**DEVICE_ERROR recovery (DECIDED).** The problem statement requires handling device failures and connection/disconnection scenarios, supporting retry, and guiding the user to a recovery process when attempts run out; it must never strand an operator or resident. So: `DEVICE_ERROR` is terminal **for the session**, and recovery is always a **new session** that starts at DEVICE_CHECK (MOSIPDISC rediscovery per S23.4). The transition carries a reason code and `RetryRateLimiter` reads it:
- `DEVICE_UNAVAILABLE` (disconnect, camera unavailable, RCAPTURE timeout): benign. The new session is free: it consumes **no** retry budget (S8). Reconnect attempts are bounded by `device.max_reconnect_attempts` `[PLACEHOLDER]`; when exhausted, show the guidance to use another device or the manual route (S23.4).
- `INTEGRITY_FAILURE` (frozen/looped frames, device identity change, bad JWT/nonce/freshness): security-relevant. The full detail goes to the audit log; the UI shows only the generic message "Face verification could not be completed. Please try again."; the new session **counts toward the lock budget**, and supervisor exemption never applies. This stops "pull the cable" from being used to reset attack attempts.
No boolean flags: the reason is part of the transition event, not session state. Test: `DeviceErrorRecoveryTest` (benign disconnect -> new session allowed and budget unchanged; integrity failure -> counted toward lock).

Notes: the active phase counts attempts against the retry budget only for challenge failures (row 14, 19); device errors and POSITIONING problems never count (S8). `BLOCKED_ATTACK` always shows the generic UI message only (S23.3). Attestation is produced only on row 18.

State map (numbers = table rows; sinks are listed once to avoid crossing lines; the table remains authoritative):

```
 IDLE ─1─▶ DEVICE_CHECK ─2─▶ POSITIONING ─4─▶ PASSIVE
                                  ▲                │ │
                                  └───────9────────┘ │   (9 = quality lost)
                                                      │
                  ┌───────── 6 CLEAR_BONA_FIDE ───────┤
                  │                                   │ 7 UNCERTAIN
                  │                                   ▼
                  │                          CHALLENGE_PROMPT ◀──┐
                  │                                   │ 11       │ 12 (more challenges needed)
                  │                                   ▼          │
                  │                           CHALLENGE_WAIT ────┘
                  │                                   │ 13 (minimum challenges met)
                  │                                   ▼
                  └────────────────────────▶ FINAL_CAPTURE ─16─▶ VERIFYING_FINAL ─18─▶ SUCCESS ⛔

 SINKS
 [R] RETRY_AVAILABLE  ◀─ 5 POSITIONING (no face) · 10 PASSIVE (engine error)
                         · 14 CHALLENGE_WAIT (timeout/failed) · 19 VERIFYING_FINAL (same-face/quality)
        ├─22─▶ PASSIVE (granted by RetryRateLimiter)
        └─23─▶ FAILED_MAX_RETRIES ⛔
 [B] BLOCKED_ATTACK ⛔ ◀─ 8 PASSIVE (CLEAR_ATTACK) · 15 CHALLENGE_WAIT (hard flag) · 20 VERIFYING_FINAL
 [D] DEVICE_ERROR ⛔   ◀─ 3 DEVICE_CHECK · 17 FINAL_CAPTURE · 21 VERIFYING_FINAL (INTEGRITY)
                         · 24 any live state (disconnect) · 25 any live state (IAD fail, INTEGRITY)
     recovery = NEW session at DEVICE_CHECK (benign reasons free; INTEGRITY counts toward lock)
```

### 16.3 Happy Path Sequence Diagram

```
Operator UI → LivenessStateMachine: start capture
SM → DeviceAdapter: MOSIPDINFO / MOSIPDISC
DeviceAdapter → SM: device Ready
SM → DeviceAdapter: STREAM (or host camera)
loop per frame
  DeviceAdapter → engine-onnx: frame
  engine-onnx → engine-onnx: quality gate (raw frame, before crop)
  engine-onnx → engine-onnx: passive PAD score
end
engine-onnx → SM: score >= passThreshold
SM → DeviceAdapter: RCAPTURE (T1)
DeviceAdapter → SM: signed image + qualityScore (JWT, transactionId)
SM → CaptureIntegrity: Verify JWT, transactionId matches session nonce, timestamp is fresh
CaptureIntegrity → SM: PASS
SM → engine-onnx: Same-face check + Final PAD on final image
engine-onnx → SM: PASS
SM → Attestation/Audit: COMMIT + build LivenessAttestation (imageSha256, tier=T1, verdict=SUCCESS)
Attestation → Operator UI: SUCCESS, proceed
```

### 16.4 Component View and Platform Differences

```
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│ UI LAYER  (JavaFX on Desktop · Flutter on Android)                                         │
├────────────────────────────────────────────────────────────────────────────────────────────┤
│ 3 screens (resident / operator / supervisor) x 8 states                                    │
│ Renders ui-contract events only. NEVER calls the engine directly.                          │
│ Sends intents only: start · retry · cancel                                                 │
└────────────────────────────────────────────────────────────────────────────────────────────┘
        ▲ events (JSON, ui-contract/events-schema.json)       │ intents
        │                                                     ▼
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│ SERVICE LAYER  (all enforcement lives here — S22; a UI bug cannot accept a failed result)  │
├────────────────────────────────────────────────────────────────────────────────────────────┤
│ LivenessSession  ──drives──▶  LivenessStateMachine (table, 13 states, no boolean flags)    │
│   uses: LivenessPolicy/Config · DecisionEngine · quality gate · same-face check            │
│         ChallengeGenerator/Validator · CaptureIntegrityChecker · RetryRateLimiter          │
│   on VERIFYING_FINAL → SUCCESS only:  Attestation (ECDSA P-256) + AuditChain               │
└────────────────────────────────────────────────────────────────────────────────────────────┘
        ▲ frames / RCAPTURE         │ load + verify            │ read / write
        │                           ▼                          ▼
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│ INFRASTRUCTURE                                                                             │
├────────────────────────────────────────────────────────────────────────────────────────────┤
│ DeviceAdapter : T1 RCAPTURE (signed) · T2 MDS STREAM · T3 host camera · Mock (tests)       │
│ ModelRegistry : sha256 + signature at load, anti-rollback, model version in attestation    │
│ Config        : signed, versioned, last-known-good fallback; floor-bounded local overrides │
│ Encrypted DB  : retry/lock counters, challenge-sequence history (in-memory = tests only)   │
│ ONNX Runtime  : fully offline inference, bounded async pipeline (S26.1), not on UI thread  │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

| Aspect | Desktop (Java) | Android |
|---|---|---|
| UI | JavaFX | Flutter |
| Liveness frame source | T2 MDS STREAM, or host camera (T3) | Host CameraX (T3) |
| Final accepted image | `RCAPTURE` via MDS (T1) | SBI `rCapture` (T1); bound to the liveness frames by same-face check + maximum time gap (S21.2) |
| ONNX Runtime artifact | `onnxruntime` | `onnxruntime-android` (AAR) |
| Model file | FP32 baseline | FP32 or INT8 — choose by benchmark, re-run qualification (R1) |
| Thresholds | Desktop calibration | **Own calibration** (do not reuse desktop numbers) |

Failure-path sequences (active challenge, attack blocked, device error mid-stream) are in `MASTER_PLAN.md` S23.2-S23.4; they are not repeated here.

### 16.5 Active Challenge Path (rows 7, 11-15)

```
PASSIVE → DecisionEngine: band = UNCERTAIN                         (row 7)
DecisionEngine → LivenessStateMachine: CHALLENGE_PROMPT
SM → ChallengeGenerator: next challenge (SecureRandom, no repeat, not same as last session's sequence)
ChallengeGenerator → UI event: "Please blink" (BoundChallenge: nonce + expiry + sequence no.)
SM → CHALLENGE_WAIT                                                (row 11)
loop each frame until confirmed, timeout, or hard flag
  quality gate → blink/smile/turn/gaze detector (with per-person baseline if S1) 
  PAD re-runs on the same frames  (turn frames beyond ~30 deg: down-weight, 15.4)
end
alt action confirmed AND challenges < minimum                      (row 12)
  SM → CHALLENGE_PROMPT (next challenge)
else action confirmed AND minimum met                              (row 13)
  SM → FINAL_CAPTURE
else timeout / wrong action                                        (row 14)
  SM → RETRY_AVAILABLE  (counts against retry budget)
else PAD hard flag / CLEAR_ATTACK                                  (row 15)
  SM → BLOCKED_ATTACK (terminal for the session)
end
```

### 16.6 Attack Blocked (row 8 / 15 / 20)

```
DecisionEngine → SM: score < attackThreshold for N consecutive quality-OK windows (or a hard gate fires)
SM → AuditLog: full detail (score, PAI-species guess, frame hashes, reason, tiers involved)
SM → UI event: GENERIC message only — "Face verification could not be completed. Please try again."
SM → BLOCKED_ATTACK (terminal for the session; no retry inside it)
RetryRateLimiter: this blocked session counts against the lock budget (per transactionId and per machine+user window)
SupervisorAuth: exemption NEVER applies to a session ended in BLOCKED_ATTACK
Recovery: applicable MOSIP recovery process (Biometrics Correction); do not invent a new flow
No face frames or biometric data are saved from a blocked session
```

### 16.7 Device Error and Recovery (rows 3, 17, 21, 24, 25)

```
DeviceAdapter -x SM: STREAM disconnects / camera unavailable / RCAPTURE timeout    (rows 3, 17, 24)
SM → DEVICE_ERROR  reason = DEVICE_UNAVAILABLE
SM → UI event: "Device unavailable, reconnecting..."
SM → RetryRateLimiter: no budget charged
loop up to device.max_reconnect_attempts
  new session: SM → DeviceAdapter: MOSIPDISC (rediscover)
  alt device ready
    DeviceAdapter → SM: Ready   → DEVICE_CHECK → POSITIONING (normal flow)
  end
end
alt attempts exhausted
  SM → UI event: guide to alternate device / manual route (S23.4); audit the outcome
end

--- security-relevant variant ---
CaptureIntegrity → SM: frozen/looped frames, device identity change, bad JWT/nonce/freshness (rows 21, 25)
SM → AuditLog: full detail
SM → UI event: generic message only
SM → DEVICE_ERROR  reason = INTEGRITY_FAILURE
RetryRateLimiter: the NEXT session counts toward the lock budget; supervisor exemption not applicable
```

### 16.8 PAD Frame Preprocessing and Scoring (R1, R3)

```
Raw frame (from DeviceAdapter, tier T2/T3)
   │
   ▼
Quality gate on the RAW frame, raw units (R3)  ── FAIL ──▶ POSITIONING + guidance key
   │ PASS
   ▼
Face box from landmarks/detector
   │
   ▼
Crop per the SHIPPED MODEL FILE (R1). For the S17.4 export [REPO]:
   square crop = 1.5 x longer box side, centred on the face
   outside the image → pad with BORDER_REFLECT_101
   resize to 128x128: LANCZOS4 if upscaling, INTER_AREA if downscaling
   colour order / value scaling / class order → whatever ModelQualificationTest proved
   │
   ▼
ONNX Runtime (offline) → logits → score (direction verified)
   │
   ▼
Quality-weighted aggregate over the N-frame window (never one frame) → DecisionEngine band
```

### 16.9 Evaluation Harness Data Flow (Section 10, R6, R7)

```
manifest.csv (+ optional columns, 15.5)
   │  subject-disjoint train / dev / test
   ▼
Dev split ──▶ calibrate thresholds (attack/pass, quality gate stream vs final) ──▶ freeze config.version
   │
   ▼
Test split run ONCE with frozen config
   │
   ├─▶ Per-species APCER rows + Wilson CIs → worst-species headline   (no ACER, no pooled-only)
   ├─▶ BPCER (+ per Monk band / gender / age bin / lighting / glasses; +/-5 pp flag; >= 30 subjects or "exploratory")
   ├─▶ IAD metrics (separate table)
   ├─▶ Same-face FNMR / FMR at frozen threshold
   ├─▶ Latency p50/p95 on reference low-end device
   └─▶ Image-level and video-level results reported separately
   ▼
Report + honest coverage statement (S15.5) + limitations (15.7)
```
