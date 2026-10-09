# Face Liveness Detection + Presentation Attack Detection
## Master Implementation Plan — MOSIP Decode 2026, Problem 04
### Group: liveness | Artifact: face-liveness-pad | Version: 0.1.0-M1 → 0.6.0
### Single authoritative document. Supersedes all prior drafts (v1 FINAL_IMPLEMENTATION_PLAN.md, v2 FINAL_IMPLEMENTATION_PLAN_v2.md).

**Basis:** `Tier 1/TECHNICAL_DESIGN.md` (all verified facts), `docs/FINAL_IMPLEMENTATION_PLAN.md` (v1 architecture), `FINAL_IMPLEMENTATION_PLAN_v2.md` (v2 gap analysis and corrections).
**Tagging convention from TECHNICAL_DESIGN.md is preserved:** facts are marked VERIFIED or UNVERIFIED. Anything not tagged is a design decision, not a fact claim.

---

## § 0. Pre-flight — Do These First

1. **Team registration** — MOSIP Decode 2026 registration closed 14 Sept 2026. Confirm the team is registered before writing a single line of code. This blocks the entire submission.
2. **Repo hygiene** — The current repo contains paywalled standards PDFs (`Tier 2/ISO-IEC-30107-3-2017-en.pdf`, `ISO_IEC_30107-1_2023(en).pdf`, `iec.19794-5.2005.pdf`, BSI `PD-ISO-IEC-TR-29794-5-2010`), compiled `target/` output, and `.DS_Store`. Either make the repo **private immediately** or strip these files before any judging or sharing.
3. **`datasets/`** — Add `datasets/` to `.gitignore`. No dataset frames or models may be committed (licence breach risk).
4. **Licence register** — Create `docs/licence-register.md`. One row per model, library, dataset. Fill in Apache/MIT/research-only, commercial-use flag, and sha256 of the downloaded file.
5. **ADR file** — Create `docs/decisions.md`. Every non-obvious design choice that follows from this plan gets an ADR entry at the time it is made.

---

## § 1. What Is Being Built

Face liveness detection + PAD for the MOSIP Registration Client on Desktop (Java 11, JavaFX) and Android (Java/Flutter, SDK 31, Android 9+). Three workflows: resident registration, operator authentication, supervisor authentication — online and offline.

**Where the code plugs in:** The BioSDK interface covers quality/match/extract, not liveness (UNVERIFIED — open question §14 item 10). Enforce liveness at the **service layer that creates the biometric record**, behind an adapter. Ship a standalone reference implementation plus a thin integration shim. Do not invent MOSIP class names.

**What MDS/SBI gives us** (VERIFIED from `mosip/registration-client` source):
- Discovery: `MOSIPDINFO` → `/info` for port scan; `SBIDISC` (spec 1.0) or `MOSIPDISC` (spec 0.9.x) for per-device readiness re-check.
- `STREAM` → `/stream` (MJPEG preview, **registration devices only**).
- `RCAPTURE` → `/capture` (final signed image).
- `STREAM` frames stop during `RCAPTURE` — the judged frame and the captured frame can differ. This is the swap-at-capture risk; mitigate with same-face check + time gap + session nonce in `transactionId`.
- `previousHash` is hardcoded `null` in `MosipDeviceSpecification_SBI_1_0_ProviderImpl.getRCaptureRequest()` (VERIFIED). Do not design a chain around it.
- Auth-purpose devices: no STREAM, no preview (DISCRETE mode). Open question for MOSIP (§14 item 7).
- Android: SBI app owns camera UI; STREAM unsupported. Liveness runs on host CameraX frames; final image from SBI `rCapture`; bind with same-face + time gap.

---

## § 2. Threat Model

Two families with separate controls and separate metrics. Mixing them produces misleading numbers.

### 2.1 Presentation Attacks (ISO/IEC 30107-3 scope)

A physical artefact is presented to a real camera. The camera feed is genuine; the face is not.

| PAI Species | Coverage Level | Primary Detection Signal |
|---|---|---|
| Printed photo (flat) | **Primary target** | MiniFASNet + moiré/FFT + LBP |
| Screen display (static/video) | **Primary target** | MiniFASNet + moiré + glare + bezel |
| Video replay | **Primary target** | MiniFASNet + temporal + random challenge |
| Cut-out mask | **Secondary target** | Geometry consistency + boundary |
| Curved print | **Secondary target** | Temporal geometry + yaw asymmetry |
| 2D rigid mask (paper/plastic/latex) | **Secondary target** | LBP texture + active challenges |
| 3D silicone/resin mask | **Experimental resistance** | Multi-cue; no standalone detection claim |

### 2.2 Injection Attacks (CEN/TS 18099 concepts — separate from PAD)

| Attack | Control | Metric |
|---|---|---|
| Frozen frame (static feed) | Frame sequence monitor (pHash + timestamp) | Detection rate |
| Frame loop/replay injection | Duplicate pHash detection across session window | Detection rate |
| Virtual camera / OBS injection | Device identity validation + T1 RCAPTURE mandatory | Detection rate |
| Pre-recorded video injection | Session nonce binding in `transactionId` | Nonce-swap rejection rate |
| ADB frame push (Android) | Environment integrity check (root, USB debug detection) | Detection rate |
| Frame swap between judged and RCAPTURE | Same-face check + time gap + nonce | Rejection rate |

**Do not report injection detection as APCER.** Report in a separate table as detection rates.

### 2.3 Real-Time Synthetic Media (mitigation only, not claimed detection)

Challenge-response binding, latency logging, and temporal consistency analysis raise the cost of real-time deepfake attacks. No RGB-only system can reliably detect high-quality real-time synthesis. Do not claim detection; claim mitigation and document the limit honestly.

---

## § 3. Full Pipeline

```
Frame from device
    │
    ▼
┌─────────────────────────────────────┐
│ 1. CAPTURE INTEGRITY (IAD)          │
│   DeviceIdentityValidator           │
│   FrameSequenceMonitor (pHash)      │
│   FreshnessChecker                  │
│   SessionTransactionBinder          │
│   FAIL → DEVICE_ERROR (terminal)   │
└──────────────┬──────────────────────┘
               │ PASS
               ▼
┌─────────────────────────────────────┐
│ 2. QUALITY GATE                     │
│   Own gate: blur, brightness,       │
│   face size, pose, single face      │
│   Run on RAW frame, before crop     │
│   FAIL → POSITIONING (not attack)  │
└──────────────┬──────────────────────┘
               │ PASS
               ▼
┌─────────────────────────────────────┐
│ 3. SPATIAL PAD (per-frame)          │
│   MiniFASNet V2-SE (ONNX)           │
│   Moiré / FFT energy                │
│   Glare + screen-bezel rectangle   │
│   LBP texture                       │
│   [Tier B: ship if beats baseline] │
└──────────────┬──────────────────────┘
               │ scores
               ▼
┌─────────────────────────────────────┐
│ 4. TEMPORAL + GEOMETRY PAD          │
│   Landmark jitter (N-frame window)  │
│   Optical-flow sanity               │
│   Planar-homography residual        │
│   Yaw asymmetry (during turns)      │
│   [Tier B: ship if beats baseline] │
└──────────────┬──────────────────────┘
               │ scores
               ▼
┌─────────────────────────────────────┐
│ 5. DECISION ENGINE                  │
│   Hard gates first (see § 5.3)      │
│   Quality-weighted fusion           │
│   → CLEAR_BONA_FIDE / UNCERTAIN /  │
│     CLEAR_ATTACK                    │
└────────┬─────┴──────┬───────────────┘
         │             │
  CLEAR_BONA_FIDE   UNCERTAIN
         │             │
         │             ▼
         │   ┌──────────────────────┐
         │   │ 6. ACTIVE CHALLENGE  │
         │   │ BoundChallenge       │
         │   │ nonce+expiry+seq     │
         │   │ PAD re-run during    │
         │   │ BLINK/SMILE/TURN/    │
         │   │ GAZE / compound      │
         │   └──────────┬───────────┘
         └──────┬────────┘
                │
                ▼
   ┌────────────────────────┐
   │ 7. SAME-FACE CHECK     │
   │ Liveness frames vs.    │
   │ RCAPTURE image         │
   │ Cosine distance gate   │
   └────────┬───────────────┘
            │
            ▼
   ┌────────────────────────┐
   │ 8. T1 RCAPTURE BINDING │
   │ Session nonce verified │
   │ JWT signature valid    │
   │ Freshness window       │
   │ T1 device mandatory    │
   │ FINAL PAD CHECK        │
   └────────┬───────────────┘
            │
            ▼
   ┌────────────────────────┐
   │ 9. ATTESTATION + AUDIT │
   │ ECDSA P-256 signed     │
   │ imageSha256 non-null   │
   │ Hash-chained AuditLog  │
   └────────────────────────┘
```

---

## § 4. State Machine (13 states)

Table-driven. All transitions not in the table are rejected with `IllegalStateException`. No state logic outside the transition table. No boolean flags in `LivenessSession`.

```
IDLE
 └─ DEVICE_CHECK
     ├─ DEVICE_ERROR ⛔ terminal
     └─ POSITIONING
         └─ PASSIVE
             ├─ CHALLENGE_PROMPT
             │   └─ CHALLENGE_WAIT
             │       ├─ CHALLENGE_PROMPT  (next in compound sequence)
             │       ├─ FINAL_CAPTURE
             │       └─ RETRY_AVAILABLE
             └─ FINAL_CAPTURE             (CLEAR_BONA_FIDE, no challenge needed)
                 └─ VERIFYING_FINAL
                     ├─ SUCCESS ⛔ terminal
                     ├─ RETRY_AVAILABLE
                     └─ BLOCKED_ATTACK ⛔ terminal

RETRY_AVAILABLE
 ├─ PASSIVE              (retry granted through rate limiter)
 └─ FAILED_MAX_RETRIES ⛔ terminal

BLOCKED_ATTACK ⛔ terminal
FAILED_MAX_RETRIES ⛔ terminal
SUCCESS ⛔ terminal
DEVICE_ERROR ⛔ terminal
```

**`BLOCKED_ATTACK` is terminal for the session, not for the person.** A new session is allowed through `RetryRateLimiter` and `failure_behavior` config — each blocked session counts against the lock budget. Supervisor exemption never applies to a session ended in `BLOCKED_ATTACK` for attack reasons. (This is a confirmed design decision over v1's "no retry ever" which conflicted with the problem statement's retry requirement.)

**Attestation is produced only from `VERIFYING_FINAL → SUCCESS`.** No other path produces an attestation. Liveness-failed captures are never saved.

---

## § 5. Decision Logic

### 5.1 Score Bands (4-band routing)

Two thresholds only: `attackThreshold < passThreshold`. Enforced at config load; loading fails if violated. Score 0–100, higher = more live.

```
QUALITY_FAILURE (OFIQ/quality gate)  →  POSITIONING (guidance; not an attack)
CLEAR_ATTACK (score < attackThreshold,
              held for N consecutive
              quality-OK windows)      →  BLOCKED_ATTACK
UNCERTAIN (attackThreshold ≤ score
           < passThreshold)            →  CHALLENGE_PROMPT
CLEAR_BONA_FIDE (score ≥ passThreshold) →  FINAL_CAPTURE
```

**The attack band requires persistence:** `CLEAR_ATTACK` is only declared after the composite score falls below `attackThreshold` for `attack.persistence_windows` (default 3) consecutive quality-OK windows. A single bad frame does not block. This prevents false positives from transient motion blur or glare.

**Starting thresholds:** `attack=30`, `pass=70`. Calibrate on dev split only; run test split once; never tune post-hoc after seeing test numbers.

### 5.2 Signal Tiers

**Tier A (must ship — needed for every mandatory task):**
- MiniFASNetV2-SE ONNX
- Frame sequence monitor (freeze/loop/timestamp gaps)
- Own quality gate (blur, brightness, face size, pose, single face)
- Landmark-based action detectors (EAR blink, mouth-ratio smile, yaw turn)
- Same-face check (embedding model)

**Tier B (ship only if each signal beats Tier A alone on dev split):**
- Moiré/FFT energy analysis
- Glare detection
- Screen-bezel rectangle detector
- LBP texture analysis
- Temporal consistency (landmark jitter, optical-flow sanity)
- Planar-homography residual during head turns

**Tier C (weight=0 by default; experiment only):**
- Gaussian curvature from MediaPipe face-mesh depth — demoted from v1 (weight 0.15) because face-mesh depth is predicted from 2D; a printed face likely still produces face-like depth. Keep at weight 0 until it beats a baseline on held-out data.
- Boundary flicker
- Colour naturalness — fairness risk (skin-tone dependent); use as a diagnostic only unless data shows no skin-tone gap across Monk Tone scale.

### 5.3 Hard Gates (applied before weighted fusion)

```
captureIntegrityFail        →  DEVICE_ERROR
qualityBelowMinimum         →  POSITIONING (guidance)
hardPadFlag (e.g. strong
  screen-bezel — configurable) →  CLEAR_ATTACK (immediate)
```

### 5.4 Fusion

Quality-weighted average over qualifying frames only:
```
qualifyingFrames = frames where ownQuality >= minQualityThreshold
if qualifyingFrames.size() < minQualifyingFrames → POSITIONING (insufficient data)

composite = Σ(padScore[i] × quality[i]) / Σ(quality[i])
            over qualifyingFrames
```

**Do not weight all frames equally.** A motion-blurred or partially occluded frame contributes less than a sharp well-lit frame.

Fusion weights: calibrate on dev split (logistic regression over Tier A/B scores, class-balanced); freeze; evaluate on test once. Never hardcode weights and claim they are calibrated. Every weight and score is written to the audit chain.

Score direction note: MOSIP issue #1117 expects lower = more bona fide. Map at the attestation boundary: `padDecisionScore = 100 − livenessScore`.

### 5.5 MiniFASNet Notes

- Input: 1×3×80×80 float32 BGR [0,1], 2.7× bbox crop.
- **Verify class order empirically on known samples before trusting any score** — published sources conflict on which class index is "live".
- Test on JPEG and JPEG2000-lossy frames (the real encoding path for MOSIP capture).
- If the model is swapped, prefer depth/pixel-wise-supervised families (CDCN lineage) over binary-only classifiers; they generalise better cross-dataset.

---

## § 6. Modules

```
face-liveness-pad/
├── pom.xml                                    Java 11, JUnit 5.9, ONNX Runtime
├── src/main/java/liveness/
│   ├── capture-integrity/
│   │   ├── CaptureIntegrityChecker.java
│   │   ├── DeviceIdentityValidator.java
│   │   ├── SessionTransactionBinder.java
│   │   ├── FrameSequenceMonitor.java
│   │   ├── FreshnessChecker.java
│   │   └── RCaptureBindingVerifier.java
│   ├── core/
│   │   ├── FaceFrame.java
│   │   └── FrameQuality.java
│   ├── device/
│   │   ├── DeviceAdapter.java
│   │   ├── DeviceException.java
│   │   └── MockDeviceAdapter.java         (Scenario enum — all attack + edge types)
│   ├── policy/
│   │   ├── LivenessPolicy.java            (4-band routing, attackThreshold < passThreshold)
│   │   └── LivenessConfig.java            (mosip.registration.liveness.* namespace)
│   ├── engine/
│   │   ├── SpatialPadScorer.java          (quality-weighted aggregate)
│   │   ├── MiniFasNetScorer.java          (ONNX wrapper)
│   │   ├── MoireAnalyzer.java
│   │   ├── LbpTextureAnalyzer.java
│   │   ├── ColourNaturalnessAnalyzer.java (Tier C; weight=0 default)
│   │   ├── GaussianCurvatureScorer.java   (Tier C; weight=0 default)
│   │   ├── BoundaryConsistencyScorer.java
│   │   ├── TemporalConsistencyAnalyzer.java
│   │   ├── GeometryConsistencyAnalyzer.java
│   │   └── DecisionEngine.java            (hard gates + weighted fusion)
│   ├── challenge/
│   │   ├── BoundChallenge.java
│   │   ├── ChallengeGenerator.java
│   │   ├── ChallengeResponseValidator.java
│   │   ├── ChallengeSequenceStore.java    (interface)
│   │   └── InMemoryChallengeSequenceStore.java  (TEST ONLY; prod uses encrypted DB)
│   ├── flow/
│   │   ├── LivenessState.java             (13 states)
│   │   ├── LivenessStateMachine.java      (table-driven)
│   │   ├── LivenessSession.java           (orchestrator — no boolean flags)
│   │   ├── RetryRateLimiter.java          (persisted in encrypted DB)
│   │   ├── SupervisorAuth.java
│   │   └── Verdict.java
│   ├── crypto/
│   │   ├── Attestation.java               (ECDSA P-256)
│   │   └── EcdsaSignerStub.java           (M1 stub; M2 replaces with key store)
│   ├── audit/
│   │   ├── AuditLog.java
│   │   ├── AuditChain.java
│   │   └── AuditEvent.java
│   └── util/
│       └── Sha256.java
├── src/test/java/liveness/
│   ├── policy/BandTableTest.java
│   ├── flow/StateMachineMatrixTest.java
│   ├── flow/BandRoutingTest.java
│   ├── flow/SupervisorAuthTest.java
│   ├── challenge/ChallengeGeneratorTest.java
│   ├── challenge/ChallengeResponseValidatorTest.java
│   ├── crypto/AttestationTest.java
│   ├── audit/AuditChainIntegrityTest.java
│   ├── capture/FrameSequenceMonitorTest.java
│   └── evaluation/AttackTaxonomyTest.java
├── docs/
│   ├── MASTER_PLAN.md
│   ├── decisions.md                       (ADR — one entry per non-obvious design choice)
│   ├── licence-register.md
│   └── previous_plans/
└── ui-contract/
    └── events-schema.json                 (shared Desktop + Android event schema)
```

**`LivenessSession` fields:**
```
subjectId, sessionId
device:            DeviceAdapter
policy:            LivenessPolicy
spatialPad:        SpatialPadScorer
temporal:          TemporalConsistencyAnalyzer
geometry:          GeometryConsistencyAnalyzer
challengeGen:      ChallengeGenerator
audit:             AuditChain
retryLimiter:      RetryRateLimiter          (persisted)
supervisor:        SupervisorAuth            (nullable; null disables exemptions)
captureIntegrity:  CaptureIntegrityChecker
machine:           LivenessStateMachine
```
`LivenessSession` drives the state machine. No boolean flags. All routing decisions derive from `machine.current()`. `Attestation.sign()` is called only from `VERIFYING_FINAL → SUCCESS`.

### 6.1 CaptureIntegrity

Runs before quality gate. Answers: "Can I trust where this frame came from?"

| Component | What it does |
|---|---|
| `DeviceIdentityValidator` | Verifies SBI cert chain + serial number; rejects mid-session device identity change |
| `SessionTransactionBinder` | Generates per-session nonce on `LivenessSession.open()`. Embeds in `transactionId`. On RCAPTURE response, verifies the returned `transactionId` matches — prevents a swap of a legitimate image from a different session. |
| `FrameSequenceMonitor` | Maintains a bounded circular buffer of `FrameFingerprint` records per session. Detects FROZEN (≥K identical hashes, default K=5), LOOP_REPLAY (same pHash non-consecutively), TIMESTAMP_GAP (delta > 2× expected interval), TIMESTAMP_NONMONOTONIC (timestamp decreases), RESOLUTION_CHANGE. ~1 ms/frame — highest value-per-cost control in the pipeline. |
| `FreshnessChecker` | RCAPTURE response `timestamp` within ±30 s (configurable) of request `captureTime`. Rejects stale or future-dated responses. |
| `RCaptureBindingVerifier` | Final: session nonce matches, JWT validates against device certificate, T1 trust domain confirmed, same-face passes. |

**`FrameFingerprint` struct:**
```
FrameFingerprint {
    pHash:       64-bit perceptual hash of 32×32 grayscale downsample
    timestampMs: device-reported frame time
    sequenceNum: monotonically incrementing counter
    widthPx:     frame resolution width
}
```

### 6.2 Quality Gate

Own lightweight gate (blur via Laplacian variance, brightness mean, face bounding-box size, pose angle, single-face check) ships first. OFIQ (`BSI-OFIQ/OFIQ-Project`, C/C++, JNI/process bridge) wrapped behind the same `QualityGate` interface — include only if the JNI/Android build is stable by M3. If OFIQ goes in, check its luminance-mean and dynamic-range measures for skin-tone bias.

Run on the **raw frame before crop/alignment**.

### 6.3 Active Challenge

**Pool:** BLINK, SMILE, TURN_LEFT, TURN_RIGHT, LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN (coarse gaze only — a challenge pool entry, not a separate subsystem); compound challenges (TURN_LEFT_RETURN, BLINK_TWICE) at ~20%. Fine-grained gaze-target CAPTCHA is future work.

**Selection rules:** `SecureRandom`; no immediate repeat; no repeat of previous session sequence per subject (persisted store — not in-memory); system-selected only; pool filtered by workflow and accessibility profile.

**BoundChallenge fields:**
```
challengeId      UUID
sessionNonce     from SessionTransactionBinder
action           "BLINK" | "TURN_LEFT" | ...
expectedSequence List<String>  (compound)
issuedAtMs       long
expiresAtMs      long
sequenceIndex    int
```

**Validation:** reject replayed or expired responses; check sequence order for compound; log latency plausibility (`SUSPICIOUS_LATENCY`) but never hard-block on latency alone (the 150–800 ms figure is an engineering assumption, not a verified empirical range).

**PAD re-runs during the challenge window** — temporal consistency analyser runs on frames captured while the challenge is in progress.

**EAR threshold (blink):** 0.2 is a starting value; validate on team-recorded fixtures before shipping.

### 6.4 Same-Face Check

**Candidate model:** OpenCV Zoo YuNet detector + SFace recognizer (Apache-2.0 per third-party docs — **confirm licence and sha256 at download**). Alternative: MOSIP match SDK behind an adapter.

Calibrate the cosine-distance threshold on LFW genuine/impostor pairs plus team captures. Cross-device case (Android CameraX vs SBI RCAPTURE image) needs its own calibration — do not reuse desktop threshold.

### 6.5 Attestation

ECDSA P-256 (`SHA256withECDSA`, Java 11). No Ed25519.

Fields:
```
subjectId:         String
sessionId:         String
verdict:           "PASS"  (only value that produces an attestation)
imageSha256:       String  // SHA-256 hex of T1 RCAPTURE image bytes; NEVER null
transcriptSha256:  String  // SHA-256 of challenge sequence + responses
deviceTier:        String  // "T1", "T2", or "T3"
captureTimestamp:  long    // epoch ms from RCAPTURE response
modelVersion:      String  // from ModelRegistry
configVersion:     int
mode:              String  // "STREAM" | "DISCRETE"
pad: {
  decision:        String  // "NO_ATTACK" (only value when verdict=PASS)
  score:           int     // 100 − livenessScore (lower = more bona fide; MOSIP issue #1117)
  mechanism:       List<String>  // ["MiniFASNet", ...]
  challengeEcho:   List<{action, latencyMs}>
}
createdAtEpochMs:  long
signature:         byte[]  // ECDSA P-256 over canonical string
```

**Canonical string for signing:**
```
subjectId + "|" + sessionId + "|" + verdict + "|" + imageSha256
+ "|" + transcriptSha256 + "|" + deviceTier + "|" + captureTimestamp
+ "|" + createdAtEpochMs
```

`imageSha256` is validated non-null before `sign()` is called. `sign()` throws `NullPointerException` if null — intentional, not a bug.

Emit a companion JSON next to the 19794-5 image record. Per ISO/IEC 39794-5 (anticipated PAD result carriage): the attestation payload is designed to accommodate embedding PAD result fields when the standard matures. Server-side verification is future work; say so explicitly.

### 6.6 Audit

Hash-chained `AuditLog`. Every score, weight, band decision, challenge issue/result, retry attempt, supervisor exemption attempt, and state transition is appended.

```
hash[i] = SHA-256( hash[i-1] || entry[i].canonical() )
```

First entry uses `"GENESIS"` as the previous hash. `verify()` checks `hashes.size() == log.size()`. `verifySealed()` additionally checks the log has not grown since `seal()` was called.

Each `AuditLog.Entry` contains: `(timeMs, channel, event, fields)`. `canonical()` produces a deterministic string for hashing.

No biometric data (frames, templates) in logs — scores and event codes only. Persisted in encrypted local DB (not in-memory). `verify()` and `verifySealed()` must be callable offline.

### 6.7 ModelRegistry

Loads model files; verifies sha256 and signature at load time; refuses unsigned or tampered models; maintains anti-rollback counter; records `modelVersion` in every attestation. Supports offline update via a signed bundle format. This is a Bonus task from the problem statement.

### 6.8 RetryRateLimiter and ChallengeSequenceStore

Both persisted in the encrypted local DB, not in-memory. Restarting the app must not reset lockout. Sliding window per `transactionId` and per `machineId+userId`.

---

## § 7. Configuration

Two namespaces: `mosip.registration.liveness.<key>` and `mosip.face_authentication.liveness.<key>`. Per-workflow overrides: `<workflow>.<key>` for resident, guardian, operator, supervisor — within server-set bounds.

| Key | Default | Local override? |
|---|---|---|
| `enabled` | true | No |
| `passive.threshold` | 70 | No (server floor) |
| `attack.threshold` | 30 | No |
| `attack.persistence_windows` | 3 | No |
| `active.enabled` | true | No |
| `active.min_challenges` | 2 | Per-workflow, within bounds |
| `active.max_challenges` | 3 | Per-workflow, within bounds |
| `active.challenge_types` | all | Per-workflow |
| `active.challenge_timeout_ms` | 5000 | Per-workflow |
| `max_retries` | 3 | Per-workflow, within bounds |
| `failure_behavior` | `LOCK_MINUTES(1)` | No. Values: BLOCK, LOCK_MINUTES(n), ESCALATE_SUPERVISOR, GUIDE_TO_RECOVERY |
| `quality.blur_min` | engineering default | Per-workflow |
| `quality.brightness_min` | engineering default | Per-workflow |
| `quality.face_size_min_px` | engineering default | Per-workflow |
| `agegroup.INFANT` | passive-only + operator attestation | Open MOSIP decision (§14 item 9) |
| `mode.preference` | auto (capability discovery) | No |
| `config.version` | integer | Signed; last-known-good on failure |
| `minQualifyingFrames` | 5 | No |
| `frameFreezePHashWindowSize` | 5 | No |
| `rcaptureFreshnessWindowMs` | 30000 | No |

Nothing may accept a failed liveness result under any `failure_behavior`.

---

## § 8. Error Codes

Errors are a separate `ErrorCode` dimension, not additional states. Fail closed: no error path may reach SUCCESS.

| Problem-statement error | State after | Counts against retry budget? | UI message key |
|---|---|---|---|
| Device unavailable | DEVICE_ERROR | No | `err.device.unavailable` |
| Device connection failure | DEVICE_ERROR | No | `err.device.connection` |
| Camera unavailable | DEVICE_ERROR | No | `err.camera.unavailable` |
| Face not detected | POSITIONING (timeout → RETRY_AVAILABLE) | On timeout | `guide.face.none` |
| Multiple faces | POSITIONING | No | `guide.face.multiple` |
| Poor face quality | POSITIONING | No | `guide.quality.*` |
| Liveness below threshold | CHALLENGE_PROMPT | No | `status.liveness.checking` |
| PAD failure | BLOCKED_ATTACK | Yes (lock budget) | generic `err.verify.generic` |
| Active challenge failure | RETRY_AVAILABLE | Yes | `err.liveness.retry` |
| Challenge timeout | RETRY_AVAILABLE | Yes | `err.liveness.retry` |
| Maximum retries exceeded | FAILED_MAX_RETRIES | n/a | `err.retry.max` + recovery guidance |
| Invalid/incomplete frame | drop frame; N consecutive → DEVICE_ERROR | No | `err.frame.invalid` |
| Engine error (FAILURE_TO_COMPUTE) | RETRY_AVAILABLE (uncounted up to k, then DEVICE_ERROR) | No | `err.engine` |

---

## § 9. UI/UX Deliverable

This is a mandatory deliverable (problem statement deliverable 2, 14 items). Not optional.

**Screens:** resident face capture, operator authentication, supervisor authentication — each on Desktop (JavaFX) and Android (Flutter).

**Per screen states:** live preview with oval guide, distance/light feedback, liveness status, capture progress, challenge prompt, success, retry, max-retry, device error.

**State → message key mapping:**

| State | EN default |
|---|---|
| POSITIONING | "Position your face in the oval" |
| PASSIVE | "Checking face liveness…" |
| CHALLENGE_PROMPT | "Please blink" / "Please smile" / "Please turn your head to the left" / "Please look left" |
| CHALLENGE_WAIT | "Please continue" / "Hold still" |
| SUCCESS | "Good, face captured successfully" |
| RETRY_AVAILABLE | "We could not verify face liveness. Please try again." + remaining attempts |
| BLOCKED_ATTACK | "Face verification could not be completed. Please try again." (generic; detail to audit only) |
| FAILED_MAX_RETRIES | "Verification could not be completed." + recovery path (Biometrics Correction / supervisor) |
| DEVICE_ERROR | Device/camera guidance + reconnect instruction |

**Rules:** status never indicated by colour alone; language-independent icons for challenges; offline indicator visible but behaviour identical online/offline; no model internals shown to users; supervisor-only diagnostics mode.

**Localisation:** EN/FR/AR minimum; AR requires RTL layout support.

**Deliverables from UX milestone:** wireframes for 3 screens × 8 states; `ui-contract` JSON event schema shared by Desktop and Android; localisation key file; accessibility notes (alternative challenge pool for users who cannot perform certain actions, longer timeouts, infant/guardian flow).

**`ui-contract` event schema (shared):** events flow from the liveness engine to the UI layer. The UI never calls into the engine directly.

---

## § 10. Timing Budgets

| Step | Target |
|---|---|
| Per frame, Desktop | ≤ 100 ms |
| Per frame, Android (5 fps min) | ≤ 200 ms on low-end device |
| Passive decision (collected frames) | ≤ 2 s |
| Per challenge timeout | 5 s (configurable) |
| Active phase (2 challenges) | ~15 s target |
| RCAPTURE device timeout | < 4 s (MDS device spec) |
| Model load, Desktop | ≤ 5 s (one-time) |
| Model load, Android | ≤ 8 s (one-time) |

Note: the < 4 s figure is the **device capture time**, not the liveness session duration. These are different things.

---

## § 11. Security and Privacy

- **ModelRegistry**: sha256 + signature verified at load; anti-rollback counter; version in every attestation; offline signed bundle update format; refuse unsigned.
- **Persisted state** (encrypted local DB): retry/lockout counters (sliding window per transactionId and machineId+userId), challenge-sequence history, config version, last-known-good config. In-memory implementations are test-only.
- **No frame retention** beyond the session; zeroize buffers after use.
- **Logs**: scores and event codes only — no images, no biometric templates.
- **Diagnostic mode**: supervisor-only, off by default.
- **Anonymised metrics**: avg processing time, challenge completion time, retry rate, failure rate. No subject identifiers.
- **STRIDE** table: as in `TECHNICAL_DESIGN.md §10`.

---

## § 12. Evaluation Design

### 12.1 PAD Metrics (ISO/IEC 30107-3)

| Metric | Definition |
|---|---|
| APCER (per species s) | Proportion of attack presentations of species s classified as bona fide |
| BPCER | Proportion of bona fide presentations classified as attack |
| APNRR / BPNRR | No-response rates |
| PS-PD | Processing duration |
| D-EER | Detection Equal Error Rate (APCER = BPCER operating point) |
| DET curve | Over the full threshold range |

**Never report ACER.** State evaluation level (PAD subsystem), type (application-focused), attacker (biometric impostor). Wilson confidence intervals. Rule of 30 for bona fide counts (need ≥ 30 subjects for a meaningful BPCER). Written rationale for the bona fide population (mixed lighting, mixed skin tones, field registration conditions).

Reference bar: iBeta Level 1 (print/screen/video, everyday materials, 0% penetration, BPCER ≤ 15%). Aspirational for the demo; do not claim certification.

### 12.2 IAD Metrics (CEN/TS 18099 concepts — separate table)

Freeze detection rate, loop detection rate, nonce-swap rejection rate, false IAD rate. Not APCER values.

### 12.3 Protocol (no tuning on test set)

1. Split by **subject**: train/dev/test. Use dataset's own split files where they exist.
2. Calibrate thresholds and fusion weights on **dev only**; freeze; run test split **once**.
3. **Cross-dataset run**: calibrate on one source, test on another; report the drop.
4. Vary artefact intensity within each species (print DPI, screen type, distance, lighting).
5. Degrade inputs with JPEG and JPEG2000-lossy encoding.

### 12.4 Fairness

Bias Index (from DFIC paper): Σ over categories of (max − min) of BPCER/APCER across groups. Group by Monk Skin Tone scale (not Fitzpatrick), lighting condition, glasses, face coverings. Flag any signal whose BPCER swings > ±5 pp across conditions. Gini coefficient as optional stricter measure.

### 12.5 Mock Device (replaces v1 § 8)

**Real-content scenarios** (BONA_FIDE, PRINT, SCREEN, VIDEO, CUTOUT, CURVED_PRINT, MASK_2D): replay **real image/video fixtures** — dataset clips where licence allows, team-recorded otherwise. **Never encode the scenario label in the frame object** — the scorer must not be able to read the answer. Each scenario call returns frames with variable artefact intensity (different DPI, screen type, lighting). This tests generalisation, not memorisation.

**Synthetic-metadata scenarios** (injection): FRAME_FREEZE, FRAME_LOOP, VIRTUAL_CAMERA (resolution/timestamp change). Synthetic metadata is acceptable here because the test is whether the sequence monitor detects it, not whether the PAD model does.

**Device/edge:** DISCONNECT, corrupt frame, low fps, Busy, timeout, LOW_LIGHT, MULTIPLE_FACES, NO_FACE.

**Scripted RCAPTURE outcomes**: signed JWT with controllable quality, stale timestamp, wrong `transactionId`.

**MASK_3D and DEEPFAKE**: include only if real fixtures exist; otherwise leave out and state this explicitly.

---

## § 13. Build Schedule (30 working days)

Confirm the actual submission deadline — documents say "evaluated in November" but that has not been verified.

| Phase | Days | Deliverables | Gate |
|---|---|---|---|
| **P0 Setup** | 1–2 | Repo hygiene (§0), `ui-contract` schema, config schema, ADR file, licence register, dataset requests sent, models downloaded + sha256 recorded, MiniFASNet class order verified on known samples | Models load; score known live/spoof samples in the correct direction |
| **S1 Vertical slice** | 3–8 | `DeviceAdapter` + real-fixture mock → own quality gate → MiniFASNet → decision logic (§5) → 13-state machine + error table → attestation (ECDSA P-256) → audit chain → minimal console/JavaFX status display | Bona fide fixture → SUCCESS + attestation; print/screen fixture → not SUCCESS; all 13 error rows tested; runs offline |
| **M2 Active + engine depth** | 9–14 | Landmarks, EAR/smile/yaw detectors, challenge generator/validator, persisted limiter + sequence store, Tier B signals evaluated one by one on dev split, fusion calibration, `ModelRegistry`, same-face check with calibrated threshold | Each Tier B signal kept or dropped with evidence; challenge unpredictability tests pass; no two consecutive identical challenge types |
| **UX** | 12–16 (parallel with M2) | Wireframes for 3 screens × 8 states, message keys EN/FR/AR, `ui-contract` JSON schema, accessibility notes | Review against §9 checklist |
| **M3 Desktop** | 15–19 | `mds-http` adapter (verified verbs: `MOSIPDINFO`, `SBIDISC`/`MOSIPDISC`, `STREAM`, `RCAPTURE`), service-layer enforcement, JavaFX screens for all 3 workflows, nonce binding + freshness + same-face, config sync + last-known-good | End-to-end three workflows on MockMDS; frozen/loop frames caught before PAD; device identity change rejected |
| **M4 Android** | 20–24 | CameraX adapter, SBI intent flow (`io.sbi.device`, `.Info`, `.rCapture`), Flutter platform channel + screens, int8 model, environment integrity check | p95 ≤ 200 ms on reference low-end device; all S1 scenarios pass |
| **M5 Evaluation** | 25–28 | Eval harness, per-species APCER/BPCER with CIs, DET/D-EER, injection table, fairness (Bias Index), latency percentiles, soak test, interoperability matrix, anonymised metrics | Report generated from frozen thresholds; test split run once |
| **M6 Docs** | 29–30 | Technical design doc (16 problem-statement deliverable items, sequence diagrams, STRIDE, ISO mapping), UX doc, test report, integration guide, demo script, open-questions list | §2 traceability table fully green |

**Cut order if time is short** (drop from top): fine-grained gaze, compound challenges, Tier C signals, OFIQ (keep own gate), Tier B signals that did not help, Android polish, interoperability matrix. **Never cut:** state machine, service-layer enforcement, attestation/audit, per-species evaluation, UI/UX deliverable, honest coverage statement.

---

## § 14. Open Questions

Send these to MOSIP before M3.

1. Is `transactionId` uniqueness enforced by the client or the caller?
2. Published device-certificate PKI for L1 devices, or self-signed per device?
3. `previousHash` hardcoded `null` — known issue or intentional?
4. Is OFIQ bundled with the BioSDK plugin JAR or a separate runtime dependency?
5. Guardian policy when the guardian is the suspected attacker?
6. Is there a minimum face size from the device spec we can use as a quality gate input?
7. Frame source for liveness on auth-purpose devices (no STREAM, no preview — DISCRETE mode)?
8. Is Android frame access expected given the SBI app owns the camera UI?
9. Infant/guardian and disability policy — passive-only with operator attestation?
10. Is this a patch against the real registration client or a standalone reference + shim?
11. Is operator biometric login in scope on Android?
12. May a `BLOCKED_ATTACK` session be retried through the rate limiter? What failure behaviour does MOSIP expect?
13. Building on another team's public contest repo — permitted or not?
14. Submission deadline and evaluation scoring criteria.

---

## § 15. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Restricted datasets not granted | High | Medium | CelebA-Spoof + ONOT re-captures + own recordings; state it in the report |
| Own recordings too few for Rule of 30 | Medium | High | Start recording in P0; report CIs and state the limit |
| MiniFASNet generalises poorly to MOSIP cameras | Medium | High | Calibrate on dev split from target-like cameras; narrow attack band; Tier B |
| False blocks of real users (BPCER) | Medium | High | Persistence windows; quality-first routing; retry via rate limiter |
| OFIQ on Android/JNI fails | Medium | Low | Own gate behind same interface |
| Same-face threshold unreliable cross-device | Medium | Medium | Calibrate per device pairing; log; do not hide |
| Integration with real client undefined | High | Medium | Standalone reference + shim; escalate to MOSIP |
| Schedule slip | High | High | Vertical slice first; cut order above |
| Dataset licence breach | Low | High | `datasets/` gitignored; private repo; no frames committed |

---

## § 15.5 Honest Coverage Statement

| Threat | Treatment | What We Claim |
|---|---|---|
| Printed photo | **Primary detection target** | Targeted detection with APCER measured across artefact intensities (DPI, paper type) |
| Screen display | **Primary detection target** | Targeted detection |
| Video replay | **Primary detection target** | Targeted detection |
| Cut-out mask | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| Curved print | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| 2D rigid mask (paper/plastic/latex) | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| 3D silicone/resin mask | **Experimental resistance** | Multi-cue analysis (geometry, boundary); no standalone detection claim |
| Real-time deepfake | **Mitigation only** | Challenge-response binding + latency + temporal analysis; raises cost, does not guarantee detection |
| Injection attacks | **Architectural control (IAD)** | CaptureIntegrity module per CEN/TS 18099 concepts; not a cryptographic guarantee against a fully compromised device |
| Frozen frame | **Detection** | High-reliability via frame sequence monitor |
| Frame loop replay | **Detection** | Reliable for non-adaptive replay; adaptive attacker avoiding pHash matching is not covered |

This table must be reproduced verbatim in the M6 final technical design document and the evaluation report. Accuracy in the coverage statement is non-negotiable.

---

## § 16. Immutable Constraints

These do not change under any circumstance:

- Java 11 target throughout. No Java 14+ features.
- ECDSA P-256 only (`SHA256withECDSA`). No Ed25519 (not available in Java 11 without Bouncy Castle).
- `previousHash` in RCAPTURE is always `null`. Do not design a chain around it (VERIFIED from source).
- Config namespaces: `mosip.registration.liveness.*` and `mosip.face_authentication.liveness.*`.
- Liveness-failed capture is **never saved** under any condition.
- Attestation is produced **only from `VERIFYING_FINAL → SUCCESS`**.
- Quality checks run on the **raw frame before crop/alignment**.
- No network call needed for inference. Fully offline.
- No model of foundation-model size (>100 MB). ONNX model budget ≤ 5 MB for the primary PAD model.
- Per ISO/IEC 39794-5 (anticipated PAD result carriage): the attestation payload is designed to accommodate embedding PAD result fields when the standard matures. Do not block on this now; implement the field structure to allow it later.
- Fact tagging convention: VERIFIED or UNVERIFIED on every factual claim.
- Never fabricate model URLs or weights.
- Do not mine or copy `BestTeam-MosipDecode/face-liveness-pad` until explicitly confirmed permitted.

---

## § 17. Datasets and Models

All datasets and models in one place. Rejected options are included with reasons.

### 17.1 Primary PAD Datasets

| Dataset | Access Link | Access Type | Verified? | What it validates | What it cannot validate | Status |
|---|---|---|---|---|---|---|
| **CelebA-Spoof** (625k images, 10k+ subjects, 10 spoof types, 43 attributes) | https://github.com/ZhangYuanhan-AI/CelebA-Spoof · Agreement: https://mmlab.ie.cuhk.edu.hk/projects/CelebA/CelebA_Spoof.html | Non-commercial, no redistribution. Download via repo links. | ✅ VERIFIED | Single-frame PAD, cross-dataset generalisation, attribute-wise analysis (lighting, environment) | Video temporal cues, active challenges, 3D masks | **USE — first dataset to download** |
| **Replay-Attack (Idiap)** (1,300 clips, 50 subjects, print/phone/tablet/video, train/devel/test split) | https://www.idiap.ch/en/dataset/replayattack · Files: https://zenodo.org/records/4593128 | Restricted — EULA; academic/professional email from same org as signatory | ✅ VERIFIED | Print + screen replay, video, calibration protocol (has official train/devel/test split) | 3D masks, modern phones/screens | **REQUEST — needed for temporal + video PAD eval** |
| **WMCA (Idiap)** (1,941 clips, 72 identities, colour/depth/IR/thermal) | https://www.idiap.ch/en/dataset/wmca · Files: https://zenodo.org/records/4580313 | Restricted — EULA; signatory must hold permanent position | ✅ VERIFIED | Multi-channel, mask/cut-out experiments. Request the RGB-only package for our pipeline. | Requires EULA signatory with permanent institutional position | **REQUEST — for secondary target validation** |
| **3DMAD (Idiap)** (76,500 frames, 17 persons, Kinect) | https://www.idiap.ch/en/dataset/3dmad · Files: https://zenodo.org/records/4068477 | Restricted — EULA | ✅ VERIFIED | 3D mask experiments | Small, low resolution, only 17 subjects | **REQUEST — for experimental 3D mask work** |
| **OULU-NPU** (4,950 videos, 55 subjects, 6 phones) | https://sites.google.com/site/aboraji/oulu-npu | Request | ❌ UNVERIFIED | Cross-phone, cross-environment generalisation | — | **REQUEST — useful for cross-device eval** |
| **SiW** (4,478 videos, 165 subjects) | http://cvlab.cse.msu.edu/siw-spoof-in-the-wild-database.html | Request | ❌ UNVERIFIED | Varied attacks, pose/expression variation, different distances | — | **REQUEST — secondary** |
| **CASIA-FASD** (600 clips) | http://www.cbsr.ia.ac.cn/english/FaceAntiSpoofDatabases.asp | Request | ❌ UNVERIFIED | Classic print/cut/replay 3-protocol benchmark | Old, low-quality capture equipment | **REQUEST — lower priority; old benchmark** |
| **MSU-MFSD** (280 videos) | Contact MSU researchers | Request | ❌ UNVERIFIED | Print + replay (laptop + Android) | Very small | **REQUEST — low priority** |

### 17.2 Deepfake / Digital Attack Datasets

These are for mitigation experiments only, not PAD claims.

| Dataset | Link | Access | Verified? | Use | Status |
|---|---|---|---|---|---|
| **FaceForensics++** | https://github.com/ondyari/FaceForensics | Request form | ❌ UNVERIFIED | Digital deepfake experiments for mitigation section of eval | **REQUEST — for deepfake mitigation eval only** |
| **Celeb-DF** | https://github.com/yuezunli/celeb-deepfakeforensics | Request | ❌ UNVERIFIED | Higher-quality deepfake evaluation | **REQUEST — secondary deepfake** |
| **UniAttackData+** (ICCV 2025, 2,875 subjects, 679k forged videos, 54 attack methods) | ICCV 2025 workshop challenge — contact Ajian Liu / Jun Wan | Institutional affiliation required | ❌ UNVERIFIED | Unified physical + digital attack evaluation | Requires institutional application | **REQUEST — aspirational; use if access granted** |

### 17.3 Bona Fide, Quality, and Fairness Datasets

| Dataset | Link | Access | Verified? | Use | Status |
|---|---|---|---|---|---|
| **ONOT** (synthetic ICAO faces, ~13k images, 3 subsets, 5 ethnic groups) | https://miatbiolab.csr.unibo.it/icao-synthetic-dataset/ | Free, CC BY-NC 4.0 | ✅ VERIFIED | Bona fide source only — print and screen-replay these yourself to make labelled attacks with no consent problem. Not an attack dataset. | **USE NOW — no consent issues** |
| **TONO** | https://miatbiolab.csr.unibo.it/tono-synthetic-dataset/ | Request | ❌ UNVERIFIED | Companion synthetic set for ICAO checks | **REQUEST** |
| **DFIC** (58,633 images + 2,706 videos, 1,016 subjects, 26 requirements annotated) | https://github.com/visteam-isr-uc/DFIC | Request — research access | ❌ UNVERIFIED | Quality gate + fairness evaluation (best real-photo set if access granted) | **REQUEST** |
| **CelebA** | http://mmlab.ie.cuhk.edu.hk/projects/CelebA.html | Non-commercial | ✅ VERIFIED | Attribute-wise quality gate checks | **USE** |
| **LFW** | http://vis-www.cs.umass.edu/lfw/ | Open research use | ✅ VERIFIED | Genuine/impostor pairs to calibrate same-face threshold | **USE** |
| **Team-recorded set** | n/a | Team produces | n/a | **Mandatory** — ≥30 consenting subjects, all challenge actions, good/low light, glasses, face coverings, print/screen/video attacks of ONOT faces and volunteers; Monk Tone tagged | **RECORD IN P0** |

### 17.4 Models and Tools

| Item | Link | Licence | Verified? | Notes |
|---|---|---|---|---|
| **MiniFASNet / Silent-Face-Anti-Spoofing** (PyTorch source) | https://github.com/minivision-ai/Silent-Face-Anti-Spoofing | Apache-2.0 | ✅ VERIFIED | Original architecture and weights |
| **MiniFASNetV2-SE ONNX export** (~1.8 MB, INT8 ~600 KB) | https://github.com/suriAI/face-antispoof-onnx | Apache-2.0 per repo | ✅ VERIFIED (repo exists; weights not independently re-verified) | Candidate ONNX for Desktop + Android. **Verify class order empirically before trusting score direction.** |
| **OFIQ** (reference ISO/IEC 29794-5, C/C++) | https://github.com/BSI-OFIQ/OFIQ-Project · https://bsi.bund.de/dok/OFIQ-e | Check `LICENSE.md` and all dependency licences | ✅ VERIFIED (repo exists) | Optional quality gate behind `QualityGate` interface; models are a separate download; JNI/Android build is a risk |
| **OpenCV Zoo YuNet (detector) + SFace (recognizer)** | https://github.com/opencv/opencv_zoo | Apache-2.0 per model — **confirm per model file at download** | ✅ VERIFIED (repo exists) | Candidate same-face embedder |
| **Landmark model** | Not chosen yet | Must be permissive + ONNX/TFLite export | ❌ UNVERIFIED | Record in licence register at download time |
| **ISO/IEC 29794-5:2025 free preview** | https://webstore.ansi.org/preview-pages/ISO/preview_ISO+IEC+29794-5-2025.pdf | Preview only | ✅ VERIFIED (from TECHNICAL_DESIGN.md) | Reference for quality field alignment |
| **iBeta ISO 30107-3 methodology** | https://www.ibeta.com/iso-30107-3-presentation-attack-detection-confirmation-letters/ | Public | ✅ VERIFIED | Reference bar for evaluation |
| **NIST BioCTS** | nist.gov | Public | ❌ UNVERIFIED | 19794-5 conformance check of generated records |
| **EDC/pAUC quality eval tool** | https://github.com/dasec/quality-assessment-evaluation | Check licence | ❌ UNVERIFIED | Optional quality evaluation |
| **MOSIP repos** | https://github.com/mosip/registration-client · https://github.com/mosip/android-registration-client · https://github.com/mosip/mosip-mock-services | MOSIP licence | ✅ VERIFIED | Integration reference; extend MockMDS, do not rewrite |

### 17.5 Rejected Datasets and Models — with Reasons

| Item | Why Rejected |
|---|---|
| **FoundPAD / foundation model LoRA PAD** (WACV 2025, github.com/gurayozgur/FoundPAD) | Foundation models are 300 MB–1 GB+. Target is offline Android deployment; model budget is ≤ 5 MB. LoRA fine-tuning requires GPU training environment and labelled data — neither available in the hackathon timeline. MiniFASNet already covers primary attack targets adequately. |
| **CLIP-based PAD / Class-Free Prompt Learning (CCPE, CFPL, arXiv:2504.04470)** | Requires CLIP (~400 MB) and a text tokeniser. Zero-shot generalisation is appealing but untested on MOSIP-specific capture conditions. No verified ONNX export with this training exists for Java inference. Wrong tool for this project's constraints. |
| **rPPG (remote photoplethysmography) as a primary liveness signal** | (1) Video replay of a real person contains the original person's pulse signal — rPPG cannot distinguish live from replay in this case. (2) Signal quality depends on skin tone and ambient lighting — fairness risk confirmed by literature. (3) CVPR 2025 research confirms rPPG remains unreliable in-the-wild without heavy preprocessing (Kalman filtering, wavelet scattering). (4) Privacy concern: rPPG extracts physiological data — regulatory risk in biometric systems. Kept as future work only; not implemented. |
| **Gaussian curvature from MediaPipe face-mesh depth as a scored signal** | MediaPipe face-mesh depth is predicted from 2D RGB; a printed face likely still produces face-like depth because the prediction is appearance-based, not geometric. The v1 weight of 0.15 was unjustified. Demoted to Tier C weight=0 until it beats a baseline on held-out data. |
| **Colour naturalness score as a scored signal** | Skin-tone dependent — fairness risk across Monk Skin Tone scale. Cannot be weighted without demonstrating no skin-tone gap across diverse populations. Kept as a diagnostic only. |
| **CDCN / CDCN++ as a replacement for MiniFASNet** | Useful as a model-selection criterion (prefer depth-auxiliary supervision) but CDCN is heavier than MiniFASNet and has potential ONNX export complications with custom Central Difference Convolution ops. No pre-trained ONNX with a verified permissive licence found. Use as a replacement only if MiniFASNet proves inadequate and a clean ONNX export is confirmed. |
| **ONOT as an attack dataset** | ONOT contains only synthetic bona fide ICAO faces. It has no attack samples. Use it only as the source material for creating your own print/screen/replay attacks by physically printing and re-photographing the faces. Do not treat ONOT images as an attack dataset directly. |

### 17.6 Download Priority and Data Handling

**Download immediately (no EULA/signature needed):**
- ONOT (CC BY-NC 4.0)
- CelebA-Spoof stratified subset — all 10 spoof types, both environments, all 4 lighting conditions, a few thousand images with the official train/test split files
- CelebA and LFW subsets
- MiniFASNetV2-SE ONNX, YuNet, SFace (verify sha256; record in licence register)

**Request today (slow approval, may be refused):**
- Replay-Attack, WMCA, 3DMAD (Idiap EULA — requires academic/professional signatory with permanent position; verify who on your team qualifies)
- OULU-NPU, SiW, FaceForensics++, Celeb-DF

**Record yourselves in P0 (mandatory, cannot substitute):**
- ≥ 30 consenting subjects for the Rule of 30 on bona fide BPCER
- Each challenge action (blink, smile, turn left/right, look left/right/up/down)
- Bona fide in good light, low light, glasses, face coverings
- Print attacks: ONOT faces and volunteers at 300 dpi, 600 dpi, 1200 dpi on matte and glossy paper
- Screen attacks: ONOT faces and volunteers on phone, tablet, laptop displays
- Video replay attacks of the above
- Tag: Monk Skin Tone, lighting condition, glasses, coverings, source dataset
- Store consent records

**Manifest format for handing data to the agent:**
```
path, label, species, subject_id, split, lighting, skin_tone, glasses, source
```
`label` ∈ {bona_fide, attack}; `species` ∈ {print, screen, video, cutout, curved_print, mask_2d, mask_3d, deepfake}; `split` ∈ {train, dev, test}.

Never commit dataset files to the repo. Add `datasets/` to `.gitignore` immediately.

---

## § 18. Device and Platform Specifics (VERIFIED)

### 18.1 Face Device Characteristics

| | Registration devices | Auth devices |
|---|---|---|
| Resolution | 1080p (PhilSys: 1080px min, 110° FoV) | 720p+ |
| Field of View | ≥ 90° (PhilSys: 110°) | Not specified |
| Encoding | JPEG2000 lossless | JPEG2000 lossy, up to 15:1 |
| ICAO frontal compliance | Required, full frontal ±5° | Not mandated |
| Live preview | Yes (STREAM, `/stream`) | **No preview at all** |
| Capture time target | < 4 s | < 4 s |
| Liveness frame source | STREAM frames | **Open question — DISCRETE captures only** |

Auth-purpose devices have no STREAM and no preview — the only option is DISCRETE mode (sequential single captures). This is an open question for MOSIP (§14 item 7).

### 18.2 RCAPTURE Response Fields (VERIFIED from `SbiRCaptureResponseDataDTO`)

```
digitalId, bioType, deviceCode, deviceServiceVersion,
bioSubType, purpose, env,
bioValue,       ← opaque bytes; likely ISO/IEC 19794-5 wrapping JPEG2000 (UNVERIFIED)
bioExtract,     ← second field alongside bioValue, presumably an extracted template (UNVERIFIED)
transactionId, timestamp, requestedScore, qualityScore
```

**No liveness or PAD field anywhere in this DTO** (VERIFIED — reconfirmed directly against real DTOs). Liveness is entirely client-side; never trust a device's own claim of liveness.

Trust domain config keys (VERIFIED — configurable, not hardcoded):
```
mosip.registration.mdm.trust.domain.rcapture   = "DEVICE"
mosip.registration.mdm.trust.domain.digitalId  = "FTM"
mosip.registration.mdm.trust.domain.deviceinfo = "DEVICE"
```

### 18.3 `isDeviceAvailable()` OR-Logic Bug (VERIFIED — do not replicate)

The spec_1_0 provider's `isDeviceAvailable()` has three separate `if` statements where the clearly intended logic is a single AND. A device can be reported "available" on the strength of any one condition alone (right spec version, OR Ready status, OR right certification). Do not assume the real client's availability check is airtight when designing mock-device or error-handling logic.

### 18.4 Verified MOSIP Config Key Values

```
mosip.registration.face_threshold                   = 9
mosip.face_authentication.quality_score              = 30
mosip.registration.mdm.RCAPTURE.connection.timeout   = 40000
mosip.registration.mdm.MOSIPDINFO.connection.timeout = 5000
mosip.registration.mdm.MOSIPDISC.connection.timeout  = 5000
mosip.registration.mdm.connection.timeout            = 10000
mosip.registration.capture_time_out                  = 10000
mosip.registration.audit_log_deletion_configured_days = 10
mosip.registration.agegroup-config   (JSON: INFANT <5 = face-only + guardian auth required)
```

These are the existing MOSIP values the liveness config must be compatible with, not replace.

### 18.5 BioSDK Plugin Packaging (VERIFIED pattern from MOSIP docs)

Mirror this packaging for the liveness-engine plugin point:
```
face_sdk.dll
face_sdk_jni.dll
<vendor>.jar
config.properties
```

Config keys:
```
mosip.<modality>.provider
mosip.biometric.sdk.providers.<modality>.<vendor>.classname
mosip.biometric.sdk.providers.<modality>.<vendor>.version
mosip.biometric.sdk.providers.<modality>.<vendor>.args
mosip.biometric.sdk.providers.<modality>.<vendor>.threshold
```

### 18.6 Android-Specific

- **Android Match SDK** compares resident's captured biometrics against operator's onboarded templates, offline — a reusable candidate for the same-face consistency check instead of a new model. Exact API **UNVERIFIED**.
- **Supervisor local override attack surface** (VERIFIED, §2.8): supervisors can override permitted config keys locally on Android; app restarts to apply. Block local overrides of `enabled`, liveness thresholds, `min_challenges`, and `failure_behavior`, or floor-bound them server-side and audit every override.
- **Flutter fast path** (evaluate, not default): `flutter_face_liveness` (pub.dev, MIT) bundles ML Kit landmarks + a TFLite MiniFASNet variant with 7 challenge types. Only adopt if its dependency footprint and model sizing fit; otherwise use the ONNX path so Desktop and Android share one implementation.
- **ML Kit** (`leftEyeOpenProbability`, `smilingProbability`, `headEulerAngleX/Y/Z`) as a UX convenience layer — proprietary Google SDK, offline-capable. Not the default path.

### 18.7 MockMDS Notes (VERIFIED)

- MockMDS is **Java 21**. Ships separate registration and auth profiles. Captures are file-based.
- **Extend MockMDS** rather than building from scratch — this satisfies the bonus task "provide a mock L0/L1 device for testing." Android reference: `mosip/android-camera-mds`.
- Existing behavior NOT to replicate: if no capture clears the quality threshold, the real client saves the highest-scoring image anyway. A liveness-failed capture must **never** be saved. Call this divergence out explicitly so reviewers don't read it as an oversight.
- Existing recovery path: **"Biometrics Correction"** via Additional Info Request. Reuse this as the liveness-failure recovery flow rather than inventing a new one.

---

## § 19. Biometric Record Format and ISO 19794-5 Specifics

### 19.1 ISO/IEC 19794-5:2005 Record Structure (VERIFIED — 2005 first edition)

```
General header:         17 bytes
Per representation:
  Common header:        19 bytes
  Quality blocks:       N × 5 bytes
  Facial-information:   17 bytes (expression + pose angle)
  Landmark blocks:      M × 8 bytes
  Image-information:    11 bytes
  Image data:           JPEG / JPEG2000 / PNG
  Byte order:           big-endian throughout
```

**Pose encoding (clause 5.5.8):** yaw/pitch/roll stored as one byte each, Tait-Bryan angles relative to frontal (0,0,0). Encoding: `BY = Y/2 + 1` for 0≤Y≤180, `181 + Y/2` for −180≤Y<0 (2° resolution; `0` = "not specified"). Encoding order: Roll, then Pitch, then Yaw. Positive yaw = face looking to its left; positive pitch = looking down; positive roll = tilting toward its right shoulder. Companion 3-byte Pose Angle Uncertainty (UY, UP, UR): value = uncertainty+1, range 1–181, `0` = unspecified.

**Pose constraint (clause 7.2.2, Frontal, inherited by Full Frontal and Token):** "Rotation of the head shall be less than +/− 5 degrees from frontal in every direction — roll, pitch and yaw." Use ±5° on all three axes as the ISO/IEC 19794-5 figure. If a looser roll tolerance is wanted, cite its actual source (ICAO 9303 or ISO/IEC 39794-5 Annex D) — not "19794-5."

**Full Frontal geometry (clause 8.3):** crown-to-chin portion **≤ 80% of the image's vertical length** (mandatory). Informative Annex: 70–80% best practice for adults/teens; ~50% acceptable for children under 11.

**Compression guidance (informative Annex A.3/A.4):** significant degradation = >2% worse matching than uncompressed. Minimum Token image ≈ 9 KB average. Region-of-interest: inner region ≈ 50:1, outer ≈ 200:1. Relevant to JPEG2000-lossy frame testing requirement.

**Four Face Image Types:** Basic → Frontal → Full Frontal / Token Frontal (each inherits requirements from the last).

**Note on editions:** the problem statement cites the 2011 edition; what has been read is the 2005 first edition. Use 2005 numbers as the verified baseline; confirm against 2011 if the difference matters.

### 19.2 Moving to ISO/IEC 39794-5

MOSIP's own direction (from GitHub issue `mosip/android-registration-client#1117`): MOSIP is moving PAD-result carriage to **ISO/IEC 39794-5** (the extensible successor), not 19794-5, because 19794-5 has no field for a liveness result. The proposed SBI capture request gains a `pad: {required, method, challenge}` object. PAD decision is a tri-state enum `ATTACK / NO_ATTACK / FAILURE_TO_COMPUTE`; score 0–100, **lower = more bona fide**. Accept policy checks decision + score vs. vendor's declared ISO/IEC 30107-3 operating point + mechanism vendor allow-list + challenge match + capture freshness — not score alone.

Design `LivenessAttestation` so its fields map 1:1 onto this shape, so a future migration to real 39794-5 embedding is a formatting change, not a redesign.

---

## § 20. Detection Algorithms — Precise Formulas

### 20.1 Blink Detection — Two Formulas (Cross-Check Both)

**EAR (Eye Aspect Ratio):**
```
EAR = (||p2−p6|| + ||p3−p5||) / (2 × ||p1−p4||)
```
over the six standard eye-corner/lid landmarks. Closed ≈ 0; open when EAR ≥ 0.2 (starting threshold — validate against actual sample frames before use). Require minimum consecutive low-EAR frames to count as a genuine blink, not a single dropped frame.

**ω (ISO/IEC 29794-5 standard eye-openness metric):**
```
ω = min(DL, DR) / IED
```
where DL, DR = left and right eyelid gaps from eyelid landmarks; IED = inter-eye distance. This is the *standard's own* metric — cross-check with EAR; two independent formulas agreeing is a stronger liveness signal than one alone.

### 20.2 Smile Detection

**MAR (Mouth Aspect Ratio)** vs. neutral baseline, plus cheek landmark displacement.

**Standard's mouth-open formula (Elatfi thesis, implementable):**
```
1 − min(1, A/B) ≥ 0.5
```
where A = measured mouth opening, B = lower-lip thickness. Alternative or cross-check to MAR.

### 20.3 Illumination Uniformity (Elatfi thesis, grounded in ISO/ICAO)

Split the face into four regions; per colour channel, compute min/max luminance ratio across the four regions. The ratio-of-ratios must be ≥ 0.5.

### 20.4 Skin-Tone Naturalness (CIELAB L*a*b*)

```
normalized a* > 86/184 (≈ 0.467)  AND  b* > 107/202 (≈ 0.530)
```
Flag over/under-saturated skin tone. **Fairness risk** — keep as a diagnostic, not a scored signal, unless skin-tone neutrality is confirmed on the Monk scale.

### 20.5 Eye Visibility Zone (EVZ)

A defined region around each eye used to detect occlusion (thick glasses, hair, flash glare). Use this named concept rather than inventing an ad hoc occlusion check. Sourced from Elatfi thesis and OFIQ's own design document.

### 20.6 Planar-Homography 3D Cue (During Head-Turn Challenge)

Fit a planar homography to landmark motion during a TURN challenge. Low residual = flat surface (print or screen). High residual = 3D face geometry. This is a heuristic — validate against fixtures before trusting it as a hard signal. Cannot distinguish a real 3D face from a 3D mask.

### 20.7 Quality Gate Ordering Rule (VERIFIED — Kabbani et al., arXiv:2501.07179)

Any distortion/quality check that depends on the raw optical geometry (e.g., radial/fish-eye distortion, which ISO/IEC 19794-5 mandates must be absent and ISO/IEC 39794-5 caps at ≤7% magnification distortion) **must run before face detection/alignment/cropping, not after**. Cropping discards the peripheral geometry the distortion shows up in. Apply this rule generally: run the quality gate on the frame as captured, before any crop/align step feeding the PAD model.

Radial distortion detection itself is not in scope as a mandatory task — but the ordering rule applies regardless.

---

## § 21. Trust Tiers and Acquisition Modes

### 21.1 Trust Tiers

- **T1** — Signed device output: SBI RCAPTURE JWT from an L0/L1 device. Highest trust.
- **T2** — MDS STREAM frames: registration devices only. No per-frame signature.
- **T3** — Host camera (webcam / CameraX): no device-level trust signal.

**The final accepted image must be T1.** T2/T3 frames may be used for passive scoring and challenge validation during the live session, but the record persisted and hashed into the attestation must come from an RCAPTURE call. Every verdict records which tier(s) contributed — honest statement of injection-attack limits.

### 21.2 Acquisition Modes

Chosen by **capability discovery**, not hardcoded per platform:
- **STREAM** — MDS STREAM (Desktop registration devices) or host camera (any live-preview source).
- **DISCRETE** — Sequential single captures; the only option for auth-purpose devices (no STREAM, no preview) and the Android SBI path (SBI app owns camera UI during capture).

Android specifically: liveness scoring runs on **host CameraX** frames. Final accepted image comes from SBI `rCapture`. The two are bound with same-face check + maximum time gap, since they are not guaranteed to be the same frame stream.

### 21.3 Gaze Challenge — Coarse vs. Fine-Grained

**Coarse gaze direction** (which quadrant/direction, using iris-position landmarks already computed for challenges) is low-risk and effectively covered by the existing "look direction" challenge type. Implement as one more challenge-pool entry.

**Fine-grained gaze tracking** (following a moving point precisely, CAPTCHA-style) is substantially higher risk: consumer-camera gaze estimation degrades sharply with viewing distance, off-axis camera placement, and glasses reflections. Light/distance control cannot be assumed in field registration. Log as future work requiring its own calibration study, not a mandatory-task dependency.

---

## § 22. Security — STRIDE Table

Enforcement lives in the **service layer that creates the biometric record**, not the UI. A UI bug can never accept a failed liveness result.

| Threat (STRIDE) | Example | Control |
|---|---|---|
| Spoofing | Presentation attack (print/replay) | Passive PAD + active challenge |
| Spoofing | Injected/virtual-camera frames | Trust-tier model (§21.1); final image must be T1 |
| Tampering | Model file swapped/corrupted | Signed models, sha256 verified at load, anti-rollback |
| Tampering | Config tampered to weaken thresholds | Signed config version; blocked/floor-bounded local overrides |
| Repudiation | Operator denies a bypass happened | Attestation + hash-chained audit log, every transition audited |
| Information disclosure | Captured face frames retained | No frame/biometric retention beyond session; zeroize buffers; sanitized logs |
| Denial of service | Repeated attack attempts | RetryRateLimiter, per-transactionId and per-machine+user sliding window |
| Elevation of privilege | UI-layer bypass of a failed verdict | Enforcement in the service layer, not the UI |

**Personas considered:** external spoofer, bribed operator, malicious insider (config tamper/override abuse), virtual-camera injector (T3), model tamperer, log leakage.

`SupervisorAuth` exemptions (age/disability policy) require supervisor auth, are audited, and **never apply to ATTACK band sessions**.

---

## § 23. Sequence Diagrams

### 23.1 Resident Registration — Passive Pass

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

### 23.2 Active Challenge Path

```
engine-onnx → SM: score in [attackThreshold, passThreshold)
SM → ChallengeGenerator: next challenge (SecureRandom, no repeat, no session-sequence repeat)
ChallengeGenerator → Operator UI: "Please blink"
loop until action confirmed or timeout
  engine-onnx: EAR / ω cross-check / MAR / yaw detector + re-run passive PAD
end
alt action confirmed, min challenges met
  SM → FINAL_CAPTURE
else timeout / failed action
  SM → RETRY_AVAILABLE (counts against retry budget)
else hard PAD flag mid-challenge
  SM → BLOCKED_ATTACK (terminal)
end
```

### 23.3 Attack Blocked

```
engine-onnx → SM: score < attackThreshold (held for attack.persistence_windows consecutive quality-OK frames)
SM → AuditLog: full detail (score, PAI-species guess, frame hashes)
SM → Operator UI: generic message only — "Face verification could not be completed. Please try again."
Note: no detail exposed to UI; no retry within this session;
      recovery = Biometrics Correction flow (existing MOSIP path, do not invent a new one)
```

### 23.4 Device Error Mid-Stream

```
DeviceAdapter -x SM: STREAM disconnects
SM: → DEVICE_ERROR
SM → Operator UI: "Device unavailable, reconnecting..."
SM → DeviceAdapter: MOSIPDISC retry
alt device found again
  DeviceAdapter → SM: Ready
  SM: → DEVICE_CHECK → POSITIONING
else still not found
  SM → Operator UI: guide to manual retry / alternate device
end
```

---

## § 24. Evaluation — Extended Details

### 24.1 ISO/IEC 30107-3 Mandatory Evaluation Elements

Evaluation type (clause 6): **application-focused** (attack set chosen for the MOSIP registration scenario). Attacker type: **biometric impostor**. Evaluation level (clause 7): **PAD subsystem** (our engine makes an explicit PAD decision/score the evaluator can read; not a data-capture or full-system evaluation).

Mandatory metrics beyond APCER/BPCER:
- **APNRR / BPNRR** — presentations that trigger no response (device timeout etc.)
- **APAR** — attack presentations from which a sample of sufficient quality is acquired
- **PS-PD** — PAD subsystem processing duration (maps directly to performance budget)
- **DET curve** — express error rates as functions of decision threshold, not one fixed point
- **D-EER** — operating point where APCER = BPCER

For a PAD subsystem returning a score, the standard says to express both error rates as functions of the decision threshold (DET curve), not one fixed operating point.

**ACER is not conformant** — the standard explicitly forbids reporting a combined/averaged metric. Do not report it.

Report APCER **per PAI species** across multiple artefact intensities within each species (print: 300/600/1200 dpi; screen: phone/tablet/laptop; video: varying quality). A score achieving good APCER only at one resolution has not been validated.

Bona fide sample size: **Rule of 30** from ISO/IEC 19795-1:2006 — you need ≥ 30 errors to be ~90% confident the true error rate is within ±30% of the observed one. Do not claim BPCER from a handful of test subjects. Include a written rationale for the bona-fide population (mixed lighting, mixed skin tones, field registration conditions).

### 24.2 iBeta Reference Points (for calibrating ambition)

| Level | Duration | Attacker expertise | Artefact cost | Penetration allowed | BPCER cap |
|---|---|---|---|---|---|
| Level 1 | ~8 h/species | None | ≤ $30, everyday materials | **0%** | ≤ 15% |
| Level 2 | 2–4 days/species | Moderate | ≤ $300, 3D-printed/resin/latex | 1% | ≤ 15% |
| Level 3 | ~7 days/species | Significant | Hyper-realistic masks | 5% | ≤ 10% |

Level 1 (print/screen/video, everyday materials) is the realistic aspirational bar for the demo harness. Level 2/3 (masks) is out of scope — state this plainly. iBeta tests against 6 attack species, ~150 attack + 50 genuine presentations at Level 1. A passing result means *conformance with ISO/IEC 30107-3 testing and reporting requirements*, **not product certification**.

### 24.3 Quality Gate Validation (EDC/pAUC)

Do not just check that OFIQ produces plausible numbers — check that it actually predicts recognition error. Use **Error-versus-Discard-Characteristic (EDC) curves** and **partial-Area-Under-Curve (pAUC)** rankings (Schlett, Rathgeb, Tapia, Busch, arXiv:2303.13294). Concrete requirements: **stepwise, not linear, curve interpolation**; **normalize quality scores to a [0,100] integer range** (OFIQ already does this); prefer higher pAUC discard-fraction limits for more stable rankings. Free evaluation tool: `github.com/dasec/quality-assessment-evaluation`. This is a good-to-have validation step, not a mandatory dependency.

**OFIQ known weaknesses** (from DFIC paper Table VI benchmark): blur (EER 0.243) and varied background (EER 0.227) are weak points vs. newer deep-learning methods. Supplementary classical check for blur: **Laplacian variance** (cheap, fast); alternative: **DCT-based focus assessment** (Sang, Lei, Li, CAS). Do not present OFIQ as uniformly best-in-class.

### 24.4 Fairness Methodology

**Bias Index** (DFIC paper, Gonçalves et al., 2026): for each demographic category c ∈ {Gender, Origin, Age}:
```
Bias Index = Σ_c (metric_max_c − metric_min_c)
```
Lower is more fair. Use this formula directly — it is a published, defensible metric to cite in the submission.

**If more rigour is needed:** Gini Coefficient (Dörsch, Schlett, Munch, Rathgeb, Busch, arXiv:2408.11392) — simplest of four statistically careful alternatives from the same group that developed OFIQ.

**Specific bias to check** (Kabbani, Raja, Ramachandra, Busch, arXiv:2501.07898): OFIQ's **luminance mean** and **dynamic range** measures show lighter skin tones systematically scoring higher. Given MOSIP's population, check these two dimensions directly. If the bias appears in testing, downweight those specific measures in the quality gate's pass/fail logic rather than excluding OFIQ altogether.

**Skin-tone scale:** use **Monk Skin Tone (10-tone, Google/Dr. Ellis Monk)**, not the Fitzpatrick scale. Fitzpatrick is a poor predictor of apparent skin tone for computer-vision fairness work.

**ISO/IEC 19795-10** (biometric system performance variation across demographic groups) — under development; cite as the direction this kind of testing is headed.

### 24.5 Component Gap — What This Submission Uniquely Provides

None of the general-purpose open-source tools (MiniFASNet, OFIQ, flutter_face_liveness, DJL) provide:
- MOSIP SBI/MDS device integration with verified HTTP verbs
- Trust-tier modelling for injection resistance
- A formal 13-state liveness state machine
- Signed attestation records
- MOSIP-shaped config-key surfaces
- Per-workflow policy (resident/operator/supervisor/infant)
- ISO/IEC 30107-3-aligned per-PAI-species metric reporting
- An extended MockMDS with scripted attack scenarios

That combination — not any single algorithm — is the actual deliverable.

### 24.6 Evaluated and Rejected Quality Model

**FaceQgen** (Hernandez-Ortega, Fierrez, Serna, Morales, arXiv:2201.00770) — GAN-based unified quality score. **Rejected** because its training data is 114/130 male and entirely Caucasian; the authors' own paper acknowledges the model "will not perform well when restoring images of females or non-white people." Given MOSIP's population, this is a disqualifying property. Worth stating in the submission as evidence of a real evaluation process.

---

## § 25. Standards Status

| Standard | Status | Source of facts used |
|---|---|---|
| ISO/IEC 30107-1:2023 | Full text read | Framework terms/definitions |
| ISO/IEC 30107-3:2017 (1st ed.) | Full text read (42 pp.) | APCER/BPCER metrics, mandatory clauses, Annex A taxonomy |
| ISO/IEC 30107-3:2023 (2nd ed.) | **Not obtained** — 1st edition used | Cite 2017 edition; 2nd ed. main change is harmonized terminology |
| ISO/IEC 30107-4:2024 | **Not obtained** — cited by name only | Mobile PAD testing profile; directly relevant to Android half |
| ISO/IEC 19794-5:2005 (1st ed.) | Full text read (56 pp.) | Record structure, pose encoding, Full Frontal geometry, compression |
| ISO/IEC 19794-5:2011 (2nd ed.) | **Not obtained** — 2005 edition used as proxy | The problem statement cites 2011; confirm diff if it ever matters |
| ISO/IEC 29794-5:2025 | Free ANSI preview read (clause structure, Introduction) | OFIQ is its reference implementation; Aadhaar named as driver |
| ISO/IEC TR 29794-5:2010 | Full text read (BSI PD) | Purely taxonomy/concepts; no numeric thresholds |
| ISO/IEC 39794-5:2019 | **Not obtained** (paywalled, CHF 227) | Cited via MOSIP issue #1117, ICAO eMRTD profile (obtained separately), Busch deck |
| ISO/IEC 39794-5 eMRTD profile (ICAO, v1.00, Aug 2023) | Full text obtained (free) | Angle blocks OPTIONAL; gender/image format changes vs 19794-5 |
| ISO/IEC TR 49794:2022 | **Not obtained** (USD 226) | 19794→39794 transition guide |
| CEN/TS 18099 | UNVERIFIED / thin | Injection-attack detection; separate from PAD |
| ISO/IEC 25456 | Not published yet | Injection-attack standard in development |
| ICAO Doc 9303 Part 3 (consolidated) | Full text obtained (free, 78 pp.) | Photo submission/scanning; defers to ISO/IEC 39794-5 on head rotation |

---

## § 26. Architectural Betterments and Production Refinements

Based on systems engineering reviews, the following "Group A" betterments are **required additions or validation refinements** to ensure the system is production-grade, highly performant, and secure at the runtime and evaluation levels.

### 26.1 Bounded Asynchronous Inference Pipeline (Producer-Consumer)
**Requirement:** Heavy ONNX inference must not run on the UI/Camera thread. 
- **Design:** The `DeviceAdapter` pushes frames into a `BoundedFrameQueue`. A dedicated `InferenceWorker` thread pulls the latest frame, dropping stale ones, and processes them at whatever FPS the hardware can sustain.
- **Goal:** The UI preview remains perfectly fluid; memory does not grow unbounded.

### 26.2 Inference Result Freshness
**Requirement:** The liveness decision itself must be fresh relative to the final `RCAPTURE`.
- **Design:** Track `frameTimestamp`, `inferenceTimestamp`, and `decisionTimestamp`. Enforce a maximum `decisionAge`. 
- **Goal:** Prevents an attacker from showing a live face, achieving a LIVE decision, swapping the scene, and triggering a late `RCAPTURE`.

### 26.3 Component-Level Performance Instrumentation & Telemetry
**Requirement:** Do not rely on simple averages. Instrument component-level measurements (decode, face detect, OFIQ, MiniFASNet, temporal fusion).
- **Design:** Implement a lightweight, in-memory `MetricsRegistry` inside `liveness-core` to track **p50, p90, p95, p99, and max** latencies.
- **Goal:** Enables a live "Diagnostic Mode" overlay and identifies actual bottlenecks (not guesses).

### 26.4 Frame Ownership and Memory Hygiene
**Requirement:** Captured face frames must not be retained beyond their required processing lifetime.
- **Design:** Every frame/buffer must have explicit ownership and release semantics. Sensitive buffers should be explicitly released and overwritten where practical. Avoid unnecessary copies of full-resolution frames.
- **Goal:** Minimize retained biometric data and prevent buffer leaks.

### 26.5 Capability-Aware Challenge Selection (Quality-Aware)
**Requirement:** Active challenges must not be purely random if environmental conditions render them unreliable.
- **Design:** Make the `ChallengeGenerator` **capability-aware**. If the quality gate reports poor visibility, filter out unobservable challenges. (e.g., exclude "Blink" if eyes are poorly illuminated).
- **Goal:** Reduces false-rejection rate (BPCER) in poor real-world environments while maintaining unpredictability within the physically capable pool.

### 26.6 Semantic Score Types (Code Safety)
**Requirement:** Avoid passing generic numeric primitive values everywhere.
- **Design:** Use explicit internal type concepts (`LivenessScore`, `PadRiskScore`, `QualityScore`, `IntegrityScore`).
- **Goal:** Prevents accidental comparison or logic bugs between values that have different directionality (e.g., higher-is-better vs. lower-is-better).

### 26.7 Explicit Final Transaction Commit Gate (Coherence Invariant)
**Requirement:** Make the final coherence check an explicit commit gate in the service layer.
- **Design:** The invariant is: `Liveness Decision → RCAPTURE → Transaction Binding → Freshness → SameFace → Final PAD → COMMIT → Attest`.
- **Goal:** No successful transaction can exist until the final image has independently passed this entire verification pipeline.

### 26.8 Challenge Timeout Policy
**Requirement:** Challenge timing must support validated workflow/accessibility policies.
- **Design:** Introduce a policy abstraction such as `ChallengeProfile` (e.g., `DEFAULT`, `ACCESSIBILITY`, `LOW_QUALITY`, `INFANT`, `WORKFLOW_SPECIFIC`). The effective timeout remains bounded by signed/configured policy.
- **Goal:** Improves accessibility without weakening security.

### 26.9 Hardware-Aware Model / Execution-Provider Benchmarking
**Requirement:** Do not blindly assume INT8 or a specific hardware accelerator is always best.
- **Design:** Benchmark the applicable CPU, INT8 CPU, and supported accelerator paths on representative target devices. Select the appropriate configuration at the session or device-profile level.
- **Goal:** Choose the configuration that provides acceptable security, latency, memory use, and stability.

### 26.10 Memory Stability and Sustained Thermal Testing
**Requirement:** The system must not degrade over long registration shifts.
- **Design:** The deployment test must include a **Memory Stability Test** (starting test target: 10,000+ continuous frames, verifying no monotonic memory growth) and a **Sustained Thermal Test** (starting test target: 30 minutes of continuous workload on Android, measuring thermal throttling and battery drain).

### 26.11 Model Qualification Harness
**Requirement:** Never blindly trust model repository documentation for critical security logic.
- **Design:** Every production model candidate must pass an automated empirical check verifying its input shape, input normalization, class ordering (live vs spoof index), and score direction against known live/spoof samples before deployment.

### 26.12 Cross-Device and Unseen Attack Material Evaluation
**Requirement:** Evaluate generalization under unseen devices, materials, lighting, replay conditions, and other acquisition-domain shifts.
- **Design:** Evaluation must hold out entirely unseen conditions (e.g., new paper, new screen, new replay device, new print quality, new lighting). It must also measure domain shift performance (Desktop vs Android).

### 26.13 Hard-Negative Regression Set
**Requirement:** Prevent regressions on edge cases as the system evolves.
- **Design:** Maintain a development-only evaluation dataset of difficult cases (e.g., real face + glare, real face + low light, high-quality prints, low-moiré screen replays). This is an evaluation artifact, not a runtime component, used strictly to benchmark improvements.

### 26.14 Replay / Fingerprint Adaptation Testing
**Requirement:** Determine exactly how robust the frame-sequence (pHash) monitor is against adaptive replays.
- **Design:** Do not add a new detector, but strictly evaluate the existing pHash check against: exact replay, re-encoded replay, resized replay, brightness-adjusted replay, and cropped/reframed replay. Measure freeze detection, loop detection, and false-IAD rates.

### 26.15 End-to-End User Journey Metrics
**Requirement:** PAD model metrics alone aren't enough to judge system usability.
- **Design:** Beyond just PAD latencies, measure the total duration of the end-to-end journey: `T_capture`, `T_passive`, `T_challenge`, `T_rcapture`, `T_final_verification`, and `T_total`. Additionally, track operational outcomes: retry rate, abort rate, and device error rate.

---

## Appendix A — 26-Item ISO/IEC 19794-5 / ICAO Quality Requirement Checklist

Source: DFIC paper (Gonçalves et al., 2026), Table I. Use this to validate that OFIQ's output actually covers what the standard cares about. An item present here but missing from OFIQ's documented measures needs a supplementary classical check.

| # | Requirement | # | Requirement |
|---|---|---|---|
| 1 | Eyes Closed | 14 | Frames Too Heavy |
| 2 | Non-Neutral Expression* | 15 | Shadows Behind Head |
| 3 | Mouth Open | 16 | Shadows Across Face |
| 4 | Rotated Shoulders* | 17 | Flash Reflection on Skin |
| 5 | Roll/Pitch/Yaw | 18 | Unnatural Skin Tone |
| 6 | Looking Away | 19 | Red Eyes |
| 7 | Hair Across Eyes | 20 | Too Dark/Light |
| 8 | Head Coverings | 21 | Blurred |
| 9 | Veil Over Face | 22 | Varied Background |
| 10 | Other Faces or Toys/Objects | 23 | Pixelation |
| 11 | Dark Tinted Lenses | 24 | Washed Out |
| 12 | Frame Covering the Eyes | 25 | Ink Marked/Creased |
| 13 | Flash Reflection on Lenses | 26 | Posterization* |

\* Added by DFIC paper beyond the BioLab 23-item base.

## Appendix B — 21-Item ISO/IEC 39794-5 / ONOT Quality Checklist

Source: ONOT paper (arXiv:2404.11236), from a commercial ICAO SDK grounded in ISO/IEC 39794-5. This is the standard MOSIP itself is moving toward. Use alongside Appendix A — the two overlap but aren't identical.

**Geometric:** (1) Unique and valid face, (2) Face fully included in frame, (3) Eye distance, (4) Horizontal/vertical position, (5) Head width/height ratio.

**Photographic:** (6) Correctly focused, (7) Sharpness, (8) Face saturation, (9) Image colour conformance, (10) Shadows over face, (11) Dark-lensed glasses/glare, (12) Cluttered background.

**Pose/attributes:** (13) Gaze direction, (14) Mouth expression, (15) Shoulder position, (16) Both eyes visible and open, (17) Eye colour, (18) Eyes occluded by glasses/hair, (19) Presence of glasses, (20) Glasses frames too heavy, (21) Hat/cap on head.

Geometric items (3–5) and gaze direction (13) are additions vs. the 19794-5 list. Artifact items (pixelation, posterization, ink marks) from the 19794-5 list are absent — those came from that standard's scanned-document heritage. Use both lists when validating OFIQ coverage.

## Appendix C — Key Papers and Sources

| Source | What it gives this plan |
|---|---|
| `mosip/registration-client` Java source (package `io.mosip.registration.mdm`) | All VERIFIED facts in §18 (discovery verbs, previousHash, DTO fields, trust-domain keys, OR-logic bug) |
| ISO/IEC 30107-3:2017 (full text, read directly) | APCER/BPCER definitions, mandatory clause list, Annex A taxonomy, APAR/PS-PD/D-EER metrics |
| ISO/IEC 19794-5:2005 (full text, read directly) | Record structure, pose encoding (Tait-Bryan, 2° resolution), Full Frontal ≤80%, compression figures |
| OFIQ design document (Merkle, Rathgeb, et al., secunet/BSI, arXiv:2211.08030, read in full) | Standard hierarchy (39794-5 sets bar; 29794-5 measures distance); EAR and ω formulas; OFIQ confirmed as 29794-5 reference implementation |
| Elatfi Master's thesis (Univ. Bologna, Oct 2024, read in full) | Illumination/skin-tone/mouth-open formulas; EVZ concept; YOLOv8/Dlib/face-parsing.PyTorch components |
| DFIC paper (Gonçalves et al., 2026, arXiv:2602.10985) | 26-requirement checklist; Bias Index formula; DFIC dataset; OFIQ blur/background weakness; 29794-5:2025 publication confirmed |
| Kabbani et al., arXiv:2501.07898 | Luminance mean + dynamic range OFIQ bias for lighter skin tones; Monk scale recommendation |
| Kabbani et al., arXiv:2501.07179 (radial distortion study) | Pipeline ordering rule: quality gate before crop/align |
| Schlett, Rathgeb et al., arXiv:2303.13294 | EDC/pAUC methodology; stepwise interpolation; free tool |
| Dörsch et al., arXiv:2408.11392 | Gini Coefficient and three additional fairness measures |
| iBeta methodology page (read directly, 2026-07-07) | Level 1/2/3 definitions, penetration limits, BPCER caps, 6 attack species |
| ONOT dataset page (read directly, CC BY-NC 4.0) | 13,289 images total (255+125+55 identities); five ethnicity groups; safe for print/screen attacks |
| Hernandez-Ortega et al., arXiv:2201.00770 (FaceQgen) | Rejected quality model (training data 114/130 male, entirely Caucasian — authors' own acknowledgement) |
| PhilSys "Annex C" procurement spec | 1080px min / 110° FoV registration device; exception-image spec for residents who can't provide normal capture |
| `mosip/android-registration-client#1117` | MOSIP's own 39794-5 PAD-result carriage design (ATTACK/NO_ATTACK/FAILURE_TO_COMPUTE, lower score = more bona fide) |
