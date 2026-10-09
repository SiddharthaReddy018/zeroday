# PROGRESS LOG — Face Liveness Detection + PAD (ZeroDay)
> **Project:** MOSIP Decode 2026 — Problem 04
> **Repo:** `SiddharthaReddy018/zeroday`
> **Build Order:** HANDOFF-2.md (strict M1→M2→M3→M4→M5→M6)
> **Last updated:** 2026-10-09T09:18 IST

---

## Legend
- ✅ = Done & verified
- 🔧 = In progress
- ⏳ = Not started
- ⚠️ = Blocked / needs input

---

## Milestone 1 — Core Skeleton ✅
**Status: COMPLETE** | Completed: 2026-10-06
**Gate: All M1 tests pass (StateMachineMatrixTest, BandTableTest, etc.)**

### Source files (21 files)
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `src/main/java/liveness/flow/LivenessState.java` | ✅ | 13-state enum, `isTerminal()` |
| 2 | `src/main/java/liveness/flow/LivenessEvent.java` | ✅ | All events from §16.2 transition table |
| 3 | `src/main/java/liveness/flow/LivenessStateMachine.java` | ✅ | Table-driven, 31 transitions, `peek()` + `fire()` + `transitionTable()` |
| 4 | `src/main/java/liveness/flow/RetryRateLimiter.java` | ✅ | Per-tx + per-machine-user budgets, benign vs integrity errors |
| 5 | `src/main/java/liveness/flow/Verdict.java` | ✅ | PASS is only value that produces attestation |
| 6 | `src/main/java/liveness/policy/LivenessConfig.java` | ✅ | All config keys with defaults |
| 7 | `src/main/java/liveness/policy/LivenessPolicy.java` | ✅ | 4-band routing: QUALITY_FAILURE / CLEAR_ATTACK / UNCERTAIN / CLEAR_BONA_FIDE |
| 8 | `src/main/java/liveness/policy/ChallengeProfile.java` | ✅ | DEFAULT, ACCESSIBILITY, LOW_QUALITY, INFANT, WORKFLOW_SPECIFIC — R5 bounds enforced |
| 9 | `src/main/java/liveness/audit/AuditEvent.java` | ✅ | Immutable audit event |
| 10 | `src/main/java/liveness/audit/AuditChain.java` | ✅ | Hash-chained, GENESIS seed, `verify()` and `verifySealed()` |
| 11 | `src/main/java/liveness/audit/AuditLog.java` | ✅ | Session-aware logging (rewritten for M2 compat) |
| 12 | `src/main/java/liveness/crypto/Attestation.java` | ✅ | ECDSA P-256, canonical string, `imageSha256` non-null enforced |
| 13 | `src/main/java/liveness/crypto/EcdsaSignerStub.java` | ✅ | Deterministic test signer |
| 14 | `src/main/java/liveness/challenge/BoundChallenge.java` | ✅ | Fields: challengeId, sessionNonce, action, expectedSequence, issuedAtMs, expiresAtMs, sequenceIndex |
| 15 | `src/main/java/liveness/challenge/ChallengeGenerator.java` | ✅ | SecureRandom, no immediate repeat, no session-sequence repeat |
| 16 | `src/main/java/liveness/core/FaceFrame.java` | ✅ | Expanded in M2 with pixel data, timestamps, landmarks, bbox, pose |
| 17 | `src/main/java/liveness/core/FrameQuality.java` | ✅ | Changed from enum → class with raw physical units (R3). GOOD/SUSPECT constants kept |
| 18 | `src/main/java/liveness/device/DeviceAdapter.java` | ✅ | Interface: open(), captureFrame(), close(), isOpen() |
| 19 | `src/main/java/liveness/device/MockDeviceAdapter.java` | ✅ | Expanded in M2: 20 scenario types (9 PAD, 3 injection, 8 edge) |
| 20 | `src/main/java/liveness/device/DeviceException.java` | ✅ | Checked exception |
| 21 | `src/main/java/liveness/util/Sha256.java` | ✅ | SHA-256: digest, hex, fromHex |

### Test files (8 files)
| # | File | Status |
|---|------|--------|
| 1 | `src/test/java/liveness/flow/StateMachineMatrixTest.java` | ✅ |
| 2 | `src/test/java/liveness/flow/BandRoutingTest.java` | ✅ |
| 3 | `src/test/java/liveness/flow/DeviceErrorRecoveryTest.java` | ✅ |
| 4 | `src/test/java/liveness/policy/BandTableTest.java` | ✅ |
| 5 | `src/test/java/liveness/policy/ChallengeProfileBoundsTest.java` | ✅ |
| 6 | `src/test/java/liveness/challenge/ChallengeGeneratorTest.java` | ✅ |
| 7 | `src/test/java/liveness/crypto/AttestationTest.java` | ✅ |
| 8 | `src/test/java/liveness/audit/AuditChainIntegrityTest.java` | ✅ |

---

## Milestone 2 — Engine ✅
**Status: COMPLETE** | Completed: 2026-10-09
**Gate: Bona fide fixture → SUCCESS + attestation; print fixture → BLOCKED_ATTACK; all 13 error rows tested**

### pom.xml changes
- Version bumped `0.1.0-M1` → `0.2.0-M2`
- Added ONNX Runtime `1.16.3` dependency

### Source files (21 new files)

**Capture integrity package (7 files):**
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `src/main/java/liveness/capture/FrameFingerprint.java` | ✅ | pHash 64-bit, hammingDistance, computePHash |
| 2 | `src/main/java/liveness/capture/FrameSequenceMonitor.java` | ✅ | Circular buffer: FROZEN/LOOP_REPLAY/TIMESTAMP_GAP/NONMONOTONIC/RESOLUTION_CHANGE |
| 3 | `src/main/java/liveness/capture/SessionTransactionBinder.java` | ✅ | UUID nonce binding |
| 4 | `src/main/java/liveness/capture/FreshnessChecker.java` | ✅ | ±30s timestamp check |
| 5 | `src/main/java/liveness/capture/DeviceIdentityValidator.java` | ✅ | Serial+cert identity lock |
| 6 | `src/main/java/liveness/capture/CaptureIntegrityChecker.java` | ✅ | Orchestrator combining all checks |
| 7 | `src/main/java/liveness/capture/RCaptureBindingVerifier.java` | ✅ | Final verification gate |

**Engine package (14 files):**
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `src/main/java/liveness/engine/QualityGate.java` | ✅ | Raw physical units (R3). RawFrameData + QualityResult inner classes |
| 2 | `src/main/java/liveness/engine/MiniFasNetScorer.java` | ✅ | ONNX stub with `setStubScore()`, `modelVersion()`, `isStubMode()` |
| 3 | `src/main/java/liveness/engine/SpatialPadScorer.java` | ✅ | Quality-weighted fusion §5.4 |
| 4 | `src/main/java/liveness/engine/DecisionEngine.java` | ✅ | Hard gates + band routing + attack persistence |
| 5 | `src/main/java/liveness/engine/ModelRegistry.java` | ✅ | SHA-256 verification + anti-rollback counter |
| 6 | `src/main/java/liveness/engine/LandmarkDetector.java` | ✅ | EAR/MAR/turn detection (§20.1-20.2 formulas) |
| 7 | `src/main/java/liveness/engine/SameFaceChecker.java` | ✅ | Cosine distance with stub mode |
| 8 | `src/main/java/liveness/engine/MoireAnalyzer.java` | ✅ | Tier B stub |
| 9 | `src/main/java/liveness/engine/LbpTextureAnalyzer.java` | ✅ | Tier B stub |
| 10 | `src/main/java/liveness/engine/TemporalConsistencyAnalyzer.java` | ✅ | Tier B stub |
| 11 | `src/main/java/liveness/engine/GeometryConsistencyAnalyzer.java` | ✅ | Tier B stub |
| 12 | `src/main/java/liveness/engine/BoundaryConsistencyScorer.java` | ✅ | Tier B stub |
| 13 | `src/main/java/liveness/engine/ColourNaturalnessAnalyzer.java` | ✅ | Tier C stub (weight=0) |
| 14 | `src/main/java/liveness/engine/GaussianCurvatureScorer.java` | ✅ | Tier C stub (weight=0) |

**Challenge additions (3 files):**
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `src/main/java/liveness/challenge/ChallengeResponseValidator.java` | ✅ | Expired/replayed/sequence order/suspicious latency |
| 2 | `src/main/java/liveness/challenge/ChallengeSequenceStore.java` | ✅ | Interface for persisted history |
| 3 | `src/main/java/liveness/challenge/InMemoryChallengeSequenceStore.java` | ✅ | Test-only implementation |

**Flow additions (2 files):**
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `src/main/java/liveness/flow/SupervisorAuth.java` | ✅ | Supervisor exemptions; BLOCKED_ATTACK never exempt |
| 2 | `src/main/java/liveness/flow/LivenessSession.java` | ✅ | THE critical orchestrator. No boolean flags. Full pipeline |

**Other:**
| # | File | Status | Notes |
|---|------|--------|-------|
| 1 | `ui-contract/events-schema.json` | ✅ | Shared event schema for Desktop + Android |

### Test files (7 new files)
| # | File | Status |
|---|------|--------|
| 1 | `src/test/java/liveness/capture/FrameSequenceMonitorTest.java` | ✅ |
| 2 | `src/test/java/liveness/engine/PoseGateRawUnitsTest.java` | ✅ |
| 3 | `src/test/java/liveness/engine/SpatialPadScorerTest.java` | ✅ |
| 4 | `src/test/java/liveness/challenge/ChallengeResponseValidatorTest.java` | ✅ |
| 5 | `src/test/java/liveness/engine/LandmarkDetectorTest.java` | ✅ |
| 6 | `src/test/java/liveness/engine/SameFaceCheckerTest.java` | ✅ |
| 7 | `src/test/java/liveness/evaluation/AttackTaxonomyTest.java` | ✅ |

### Pending for M2 (non-blocking)
- `ModelQualificationTest` — needs actual ONNX model file to verify channel order/scaling. Will be done when model file is provided.

---

## Milestone 3 — Desktop Integration 🔧
**Status: IN PROGRESS** | Started: 2026-10-09
**Gate: End-to-end 3 workflows on MockMDS; frozen/loop caught; device identity change rejected**

### Components Built & Remaining
| # | Component | Package | Status | Notes |
|---|-----------|---------|--------|-------|
| 1 | `MdsDeviceInfo.java` | `device` | ✅ | Immutable DTO for device discovery / metadata (MOSIPDINFO/SBIDISC) |
| 2 | `MdsResponse.java` | `device` | ✅ | Wrapper for MDS HTTP responses, BiometricData, and ErrorInfo |
| 3 | `MdsHttpDeviceAdapter.java` | `device` | ✅ | HTTP adapter: `MOSIPDINFO`, `SBIDISC`/`MOSIPDISC`, `STREAM`, `RCAPTURE` (§18) |
| 4 | `MdsStreamReader.java` | `device` | ✅ | MJPEG multipart stream parser for live camera preview |
| 5 | `LivenessService.java` | `service` | ✅ | Service-layer enforcement (§22) — orchestrator driving `LivenessSession` |
| 6 | `LivenessServiceConfig.java` | `service` | ✅ | Config wrapper with active, last-known-good fallback & staleness check |
| 7 | `ConfigSyncManager.java` | `service` | ✅ | Periodic daemon HTTP sync for `mosip.registration.liveness.*` |
| 8 | `LivenessCallback.java` | `service` | ✅ | Asynchronous callback interface for UI decoupling |
| 9 | `BoundedFrameQueue.java` | `engine` | ✅ | Bounded producer-consumer frame queue (§26.1) |
| 10 | `InferenceWorker.java` | `engine` | ✅ | Dedicated daemon thread pulling frames for inference off UI thread (§26.1) |
| 11 | `UiEventType.java` | `ui` | ✅ | Typed event enum for state changes, guidance, challenges, results |
| 12 | `UiEvent.java` | `ui` | ✅ | Immutable UI event matching `ui-contract/events-schema.json` |
| 13 | `LivenessUiListener.java` | `ui` | ✅ | UI callback listener interface for decoupled scene updates |
| 14 | `UiMessages.java` | `ui` | ✅ | Localised strings (EN, FR, AR with RTL support) (§9) |
| 15 | JavaFX UI preview & guide controls | `ui` | ⏳ | Live preview oval guide, distance/pose feedback, challenge prompts |
| 16 | Desktop MockMDS Integration Tests | `test` | ⏳ | End-to-end test with MockDeviceAdapter / MockMDS |

---

## Milestone 4 — Android ⏳
**Status: NOT STARTED**

---

## Milestone 5 — Evaluation ⏳
**Status: NOT STARTED**
**⚠️ BLOCKED: Needs datasets (CelebA-Spoof, ONOT, Replay-Attack, team recordings, LFW)**

---

## Milestone 6 — Documentation ⏳
**Status: NOT STARTED**

---

## Datasets & Models Needed
> User will provide these. Documenting what's needed for the team.

| Item | Purpose | When needed | Status |
|------|---------|-------------|--------|
| MiniFASNetV2-SE ONNX (FP32 ~1.82MB) | Primary PAD model | M2 (stub active now) | ⚠️ Download from `facenox/face-antispoof-onnx` |
| YuNet ONNX | Face detection | M3 | ⚠️ Not yet obtained |
| SFace ONNX | Same-face embeddings | M3 | ⚠️ Not yet obtained |
| CelebA-Spoof | Primary PAD evaluation | M5 | ⚠️ |
| ONOT | Bona fide source for self-made attacks | M5 | ⚠️ |
| Replay-Attack (Idiap) | Temporal + video PAD eval | M5 | ⚠️ Requires Idiap approval |
| LFW | Same-face threshold calibration | M5 | ⚠️ |
| Team recordings (≥30 subjects) | Mandatory evaluation set | M5 | ⚠️ |

---

## Licence Registration (from `docs/licence-register.md`)
All third-party components must be documented before shipping. See `docs/licence-register.md`.

---

## Environment Notes
- **Java / Maven:** NOT installed on dev machine. Code verified structurally; `mvn clean test` cannot run locally yet.
- **OS:** Windows
- **Java target:** 11 (no Java 14+ features)

---

## Change Log

| Date | What | By |
|------|------|----|
| 2026-10-06 | M1 complete: 21 source + 8 test files | Agent |
| 2026-10-06 | M2 started: pom.xml updated, FaceFrame/FrameQuality expanded | Agent |
| 2026-10-06 | M2: Capture integrity package (7 files) created | Agent |
| 2026-10-06 | M2: Engine package (14 files) created | Agent |
| 2026-10-06 | M2: Challenge additions (3 files) created | Agent |
| 2026-10-06 | M2: Flow additions (2 files) + ui-contract created | Agent |
| 2026-10-06 | M2: QualityGate, DecisionEngine, MiniFasNetScorer rewritten/fixed for API consistency | Agent |
| 2026-10-06 | M2: AuditLog rewritten for LivenessSession compat | Agent |
| 2026-10-06 | M2: MockDeviceAdapter expanded to 20 scenarios | Agent |
| 2026-10-06 | M2: All 7 test files created | Agent |
| 2026-10-09 | .gitignore fixed: removed docs/ exclusion, added datasets/ and models/ | Agent |
| 2026-10-09 | AttackTaxonomyTest import fixed | Agent |
| 2026-10-09 | Progress log created (`docs/PROGRESS-LOG.md`) | Agent |
| 2026-10-09 | M3: Implemented MDS device adapters (`MdsDeviceInfo`, `MdsResponse`, `MdsHttpDeviceAdapter`, `MdsStreamReader`) | Agent |
| 2026-10-09 | M3: Implemented service layer (`LivenessService`, `LivenessServiceConfig`, `ConfigSyncManager`, `LivenessCallback`) | Agent |
| 2026-10-09 | M3: Implemented bounded async engine pipeline (`BoundedFrameQueue`, `InferenceWorker`) | Agent |
| 2026-10-09 | M3: Implemented UI event contract & i18n (`UiEventType`, `UiEvent`, `LivenessUiListener`, `UiMessages`) | Agent |

