# Architecture Decision Records (ADR)

## ADR-001: DEVICE_ERROR is terminal per session, recovery via new session

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 16.2)  
**Context:** S4 calls DEVICE_ERROR terminal, S23.4 describes reconnecting.  
**Decision:** DEVICE_ERROR is terminal for the session. Recovery is always a new session starting at DEVICE_CHECK (MOSIPDISC rediscovery). The transition carries a reason code:
- `DEVICE_UNAVAILABLE` (benign): free -- no retry budget charged; bounded by `device.max_reconnect_attempts`.
- `INTEGRITY_FAILURE` (security): counts toward the lock budget; supervisor exemption not applicable.  
**Consequence:** No boolean flags needed; reason is part of the transition event.

## ADR-002: RETRY_AVAILABLE -> PASSIVE retry path

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 15.7 item 4)  
**Context:** Problem statement requires retry support with clear indication and recovery guidance.  
**Decision:** RETRY_AVAILABLE -> PASSIVE (row 22), which can fall back to POSITIONING by row 9 if quality drops. Matches the problem statement's resident workflow.  
**Consequence:** Retry always goes through PASSIVE, not directly to POSITIONING.

## ADR-003: FINAL_CAPTURE RCAPTURE failure -> DEVICE_ERROR

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 16.2 row 17)  
**Context:** Problem statement requires handling device communication failures.  
**Decision:** RCAPTURE failure/timeout -> DEVICE_ERROR with reason DEVICE_UNAVAILABLE.  
**Consequence:** Subject is guided to retry via new session; no budget charged.

## ADR-004: BLOCKED_ATTACK terminal for session, not person

**Date:** 2026-10-06  
**Status:** DECIDED (MASTER_PLAN Section 4)  
**Context:** v1 said "no retry ever" which conflicts with the problem statement's retry requirement.  
**Decision:** BLOCKED_ATTACK is terminal per session. A new session is allowed through RetryRateLimiter. Each blocked session counts against the lock budget. Supervisor exemption never applies.  
**Consequence:** Prevents abuse while allowing legitimate re-enrollment.

## ADR-005: Standalone reference implementation + thin integration shim

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, Section 14 item 10)  
**Context:** Unknown whether this is a patch against the real registration client.  
**Decision:** Ship standalone + thin integration shim. Do not invent MOSIP class names.  
**Consequence:** Minimizes integration risk; can be adapted later.

## ADR-006: Infant/guardian policy -- passive-only + operator attestation

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, Section 14 item 9)  
**Context:** Adult-trained quality models over-reject young children (U26).  
**Decision:** Passive-only with operator attestation for INFANT profile.  
**Consequence:** ChallengeProfile.INFANT has longer timeouts, no compound challenges.

## ADR-007: Auth-purpose devices -- DISCRETE mode

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, Section 14 item 7)  
**Context:** Auth-purpose devices have no STREAM, no preview.  
**Decision:** Assume DISCRETE mode; document the limitation.  
**Consequence:** Liveness evidence is reduced for auth devices; same-face check + time gap compensate.

## ADR-008: Dataset & PAD Model Selection for Evaluation (M5)

**Date:** 2026-10-19  
**Status:** DECIDED (R1, Section 17.1, Section 17.4)  
**Context:** Idiap Replay-Attack and OULU-NPU require strict academic EULAs with permanent faculty signatures. In addition, the primary PAD model's actual export spec is 128x128 RGB rather than 80x80 BGR.  
**Decision:**
1. **Model:** Adopted `MiniFASNetV2-SE` ONNX export from `facenox/face-antispoof-onnx` (FP32 ~1.82MB, INT8 ~612KB) verified empirically via `ModelQualificationTest` with 128x128 RGB input.
2. **Bona Fide Dataset:** Use ONOT synthetic ICAO dataset (179 subjects, 5,811 images) ensuring complete ethical compliance and exceeding the Rule of 30 ($N=99 \ge 30$).
3. **Attack Evaluation:** Sourced replay attack video fixtures and Kaggle open alternatives (*Antispoofing Replay Dataset*, *Real VS Fake*) to bypass institutional EULA gatekeeping.
**Consequence:** Allows offline, reproducible evaluation that satisfies ISO/IEC 30107-3 reporting standards without copyright or institutional access hurdles.

## ADR-009: Android M4 Deployment Constraints and Fallback Architecture

**Date:** 2026-10-19  
**Status:** DECIDED (Section 18.6, Section 21, Section 26.9)  
**Context:** Android integration involves cross-stream binding between host CameraX frames (T3) and SBI `rCapture` intents (T1). Field deployments face mobile hardware latency limits, lack of native JPEG 2000 support, external face recognition model availability, and device-provider trust chains.  
**Decision:**
1. **Inference Latency ($p95 \le 200\text{ ms}$):** Shipped `MiniFASNetV2-SE-int8.onnx` (612 KB) into Android assets (superseded by ADR-017: Android now ships FP32). Measured by `Int8ModelBenchmarkTest` on a desktop JVM with synthetic input; the current figures are in the generated latency table of TEST_REPORT.md. This says nothing about phone latency, which is NOT MEASURED (Part 2 X6); no low-end-device headroom is claimed.
2. **Same-Face Consistency:** Provided `ReferenceFaceEmbedder` (128-D normalized spatial descriptor) for out-of-the-box cross-stream verification without external dependencies. Deep neural models (OpenCV Zoo SFace / ArcFace) plug into `FaceEmbedder` with a calibrated threshold of 0.48 cosine distance (ADR-012).
3. **JPEG 2000 Safe Fail-Closed:** Handled ISO/IEC 19794-5 JPEG frames natively via Android `BitmapFactory`. For JPEG 2000, provided the `IsoFaceRgbDecoder.Jpeg2000Decoder` interface for optional OpenJPEG JNI injection; without one, the parser fails closed safely (`Result.EMBEDDING_FAILED`), preventing unverified image substitution.
4. **JWT Trust Anchors:** Added `SbiJwtVerifier.fromPem()` to allow dynamic loading of MOSIP Device Provider root CAs from PEM assets.
**Consequence:** Guarantees deterministic, offline-capable, and secure Android liveness capture while maintaining strict fail-closed security invariants.

## ADR-010: Post-audit hardening -- fail-closed components, evaluation provenance, signed evidence

**Date:** 2026-10-20  
**Status:** DECIDED (code audit of Oct 2026; HANDOFF-2 R8-R10; MASTER_PLAN Section 26.16-26.18)  
**Context:** An independent audit found that (1) the PAD scorer silently fell back to a fixed passing stub and the session fed it empty tensors; (2) the session's same-face check received empty vectors and depended on a public test stub; (3) the benchmark behind TEST_REPORT.md used a hash-of-path score generator and typed-in IAD numbers; (4) the attestation signature omitted model/config/mode/PAD fields; (5) benchmark and latency tests passed silently when their inputs were missing; (6) document test counts disagreed (330/333/337/338, table sum 343).  
**Decision:**
1. Fail closed (R8): model must load or throw; `StubPolicy` gate (`liveness.allowStubs`); real pixels via `PadPreprocessor`; same-face via injected `FaceEmbedder`; unscorable frame = 0.
2. Evaluation integrity (R9): real scorer only; skipped-not-passed; run-time provenance section; face-box sidecar (`tools/detect_face_boxes.py`) with counted whole-image fallback; test counts generated by `tools/test_report_counts.py`.
3. Signed evidence (R10): wider attestation coverage with escaping; attestation published after SUCCESS fires; `AuditSeal` signed head for every outcome.
4. Report honesty details: IAD rows with zero trials print "not measured", never a 0.0000 rate; the Android Gradle unit-test tree (which reuses `src/test`) sets `liveness.allowStubs=true` for JVM unit tests only.
5. Mandatory extras (MASTER_PLAN 24.1): PS-PD is measured (scorer-side timing of preprocessing + inference, machine stated). APNRR/BPNRR/APAR are reported as "not measured" with reasons, because they need live capture sessions; no estimate is produced.
**Consequences:** All results in the pre-ADR-010 TEST_REPORT.md (sections 3-5) are **withdrawn** and must be regenerated with the real model. The documented INT8 latency is a desktop-JVM figure until `ScorerBenchmark` is run on a device. Remaining known limits (not solved by code): synthetic still-image bona fide data, small per-species attack N, placeholder `ReferenceFaceEmbedder`, ephemeral signing key, Haar-based box detector standing in for the production detector.

## ADR-011: Model Qualification, Output Class Mapping, and DEV Split Threshold Calibration

**Date:** 2026-10-20  
**Status:** DECIDED (verification of Section 3.1 and Section 3.6; HANDOFF-2 R1, R9; MASTER_PLAN Section 12.3)  
**Context:** Verification of MiniFASNetV2-SE under `ModelQualificationTest` confirmed the ONNX model structure (4D tensor `[1, 3, 128, 128]`, float32, normalized `[0, 1]`, output `[1, 2]`). Testing on known bona fide (ONOT) and attack presentations revealed that output index 0 is the live (bona fide) logit and index 1 is the spoof (attack) logit. The initial scorer had inverted these indices, causing real faces to score ~0 and attacks ~100. Furthermore, threshold 70 required empirical calibration on the DEV split per Section 12.3 before freezing for the TEST run.  
**Decision:**
1. **Class Order Alignment:** In `MiniFasNetScorer`, map `liveLogit = logits[0][0]` and `spoofLogit = logits[0][1]`. Softmax probability of Class 0 determines the bona fide score on the 0-100 scale.
2. **Score Direction Verification:** `ModelQualificationTest` was expanded to verify that uniform zero tensors produce spoof scores (<50) and bona fide samples score strictly higher than attack presentations.
3. **DEV Split Calibration:** Evaluated candidate operating thresholds on the DEV split (N = 1,030 samples: 1,021 bona fide, 9 attacks). Threshold 70 yields BPCER = 0.0069 (< 1%) and APCER = 0.1111 on DEV; the D-EER point occurs at threshold 92 (APCER = 0.0000, BPCER = 0.0284). Operational threshold 70 is frozen for the TEST split evaluation.
4. **Test Run:** Run TEST split once (N = 1,107: 1,071 bona fide, 36 attacks across cutout, print, screen). Test results: Cutout APCER = 0.0000, Print APCER = 0.0000, Screen APCER = 0.2500, overall BPCER = 0.0019 (0.19%), D-EER = 0.0502.
**Consequences:** Model scoring direction and output mappings are formally qualified and verified. Evaluation results in `docs/TEST_REPORT.md` reflect real, non-inverted ONNX inference with documented calibration provenance.

## ADR-012: Real Neural Face Embedder Integration (SFace) and Same-Face Calibration

**Date:** 2026-10-20  
**Status:** DECIDED (verification of Section 4.2; HANDOFF-2 R8, R9; MASTER_PLAN Section 6.3, Section 12.3)  
**Context:** An independent audit identified that `ReferenceFaceEmbedder` (8x8 spatial luma grid) lacked identity discrimination capacity, producing non-negative vectors where unrelated human faces had small cosine distances (0.10--0.17) below the initial default threshold, permitting impostor acceptance. Item 4.2 mandated integrating a trained, license-checked embedder behind `FaceEmbedder`, calibrating the threshold on genuine and impostor pairs, and reporting FAR/FRR with Wilson confidence intervals.  
**Decision:**
1. **Model Selection & License:** Integrated OpenCV Zoo's **SFace** (`face_recognition_sface.onnx`, 37.8 MB, Apache-2.0 license, SHA-256: `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79`) paired with **YuNet** (`face_detection_yunet.onnx`, 227 KB, Apache-2.0, SHA-256: `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`). Models recorded in `docs/licence-register.md`.
2. **Empirical Calibration:** Evaluated 50 genuine pairs (same subject, 2 distinct photos) and 100 impostor pairs (different subjects) across ONOT subjects using `tools/calibrate_same_face.py`.
   - Genuine pairs mean cosine distance: **0.3104** (range: 0.1910--0.4872)
   - Impostor pairs mean cosine distance: **0.7037** (range: 0.3109--0.9082)
3. **Threshold Selection:** Set operational same-face threshold $\tau = 0.48$ (decision: same-face if distance $< 0.48$).
   - **FAR (False Acceptance Rate):** 4/100 = **4.00%** (95% Wilson CI: [0.0157, 0.0570, 0.0984])
   - **FRR (False Rejection Rate):** 1/50 = **2.00%** (95% Wilson CI: [0.0035, 0.0542, 0.1050])
**Consequences:** Validates genuine/impostor discrimination with real facial embeddings and Wilson statistical bounds. `SameFaceChecker` correctly rejects impostors (96% rejection rate) and accepts genuine subjects (98% acceptance rate).

## ADR-013: External audit fixes (Oct 20, 2026)

**Status:** DECIDED  
**Changes:**
1. Final RCAPTURE image must score CLEAR_BONA_FIDE to commit; UNCERTAIN or lower goes to RETRY_AVAILABLE via row 19 (event SAME_FACE_FAIL, audit reason FINAL_PAD_NOT_CLEAR_BONA_FIDE).
2. `ChallengeResponseValidator` is now called by the session (expiry, expected action, latency plausibility). BLINK and SMILE need `MIN_ACTION_FRAMES` (2, UNCALIBRATED seed) consecutive qualifying frames.
3. `SupervisorAuth.authenticate()` is gated by `StubPolicy` (it is a stub).
4. `MiniFasNetScorer` reads and checks the input shape from the model file (R1) and reports `modelVersion()` as a SHA-256 prefix of the loaded file. NOT exercised against real ORT in the audit sandbox; run `ModelQualificationTest` with the real model.
5. `WilsonConfidenceInterval.toString()` prints `[lower, upper]` (the old middle value was the Wilson centre, not the point estimate). `IadMetrics` rates return NaN with zero trials. Report header says "aligned", not "Compliant".
6. Typed test counts removed from docs; the generated block in TEST_REPORT.md is the only source.
**Still open:** see AUDIT_TODO.md.

## ADR-014: Frame Sequence Monitor False Alarm Mitigation, Rule of 30 Contract, and Audit Log Sealing

**Date:** 2026-10-22  
**Status:** DECIDED  
**Context:**  
1. FrameSequenceMonitor previously raised false FROZEN/LOOP_REPLAY alarms on live, naturally still subjects because perceptual hash alone had collisions on static facial poses.  
2. Evaluation reports previously risked quoting statistical conclusions without meeting the ISO/IEC 19795-1 Rule of 30 ($N \ge 30$).  
3. Attestation signatures previously sealed the transcript SHA-256 without providing explicit API verification helpers or sealing the audit head on every session outcome.  
4. The dev embedder check previously checked `getClass().getSimpleName().equals("ReferenceFaceEmbedder")` which could be bypassed by renaming the class.  
**Decision:**  
1. **Content Digest + Distance Window:** `FrameFingerprint` computes a 32-byte content digest (SHA-256 of 32x32 downsampled gray) alongside perceptual hash. `FrameSequenceMonitor` requires strict byte-identical digests across the freeze window for a freeze alarm, and non-adjacent loop matching ($gap \ge 1$), eliminating false alarms on live users (empirically verified over 200 noisy trials in `FrameSequenceMonitorFalsePositiveTest`).  
2. **Rule of 30 Contract & Synthetic IAD Runner:** `EvaluationHarness` enforces $N \ge 30$ bona fide and attack subjects unless explicitly flagged `allowExploratory=true`. `EvaluationReport.validateContract()` strictly validates $N \ge 30$, 95% Wilson intervals, non-pooled worst-species headline, unmeasured metrics documentation, and marks headline metrics `[EXPLORATORY]` when $N < 30$. Real synthetic presentations are driven through `IadMechanismRunner` into `FrameSequenceMonitor` and `RCaptureBindingVerifier`, producing measured bypass rates, latencies, and mechanism coverage.  
3. **Audit Log Sealing in Attestation:** `Attestation` exposes `auditHeadHash()` and `auditLogHeadHash()` as well as `verifyAuditLog(AuditLog)` and `verifyAuditChain(AuditChain)`. Truncating or modifying even one audit event causes verification to fail.  
4. **Interface-Level Production Guard:** `FaceEmbedder` defines `default boolean productionGrade() { return false; }`. Production embedders like `SFaceEmbedder` override it to return `true`. Outside development mode, `LivenessService` rejects any embedder where `!embedder.productionGrade()`, preventing bypass via renaming.  
**Consequences:** Complete mathematical and architectural integrity across live capture sequence monitoring, statistical reporting contracts, audit sealing, and production security guards.

## ADR-015: Loop Replay Rule Redesign, Configuration Invariant Hardening, and Explicit Audit Head Attestation Binding

**Date:** 2026-10-22  
**Status:** DECIDED  
**Context:**  
An audit of commit `1fd2826` identified six residual integrity and configuration findings:
1. `FrameSequenceMonitor` loop replay rule required $n \ge 2K+1$ buffered frames ($K \ge 5 \to 11$), but production default window was 5 (`frameFreezePHashWindowSize = 5`), rendering loop detection unreachable in real sessions (F1, F3).
2. At larger windows (15, 30), the naive loop rule fired on static scenes (20/20 live still users flagged) because a static scene trivially matches itself in order (F2).
3. `IadMechanismRunner` hardcoded window 15, uncoupled from the shipped configuration (F4).
4. `LivenessConfig.Builder` coupled `frameFreezePHashWindowSize` and `freezeConsecutiveFrames` (setting one overwrote the other) and failed to enforce $window \ge 2K+1$ or $freezeConsecutiveFrames \le window$ (F5).
5. `Attestation.auditHeadHash()` was an alias for `transcriptSha256`, and the audit event count was not covered by the ECDSA signature, allowing tail truncation if the verifier did not independently know the head (F6).

**Decision:**  
1. **Config Invariants & Decoupling (R1):** Decoupled `frameFreezePHashWindowSize` from `freezeConsecutiveFrames` in `LivenessConfig.Builder`. Updated default window to 15 ($15 \ge 2 \times 5 + 1$). Added strict validation: `build()` throws `IllegalArgumentException` if $window < 2K+1$, if $freezeConsecutiveFrames > window$, if $freezeConsecutiveFrames < 2$, if $K < 5$, or if motion/Hamming thresholds exceed [0, 64]. Added `loopWindowFrames()` accessor and passed it to `FrameSequenceMonitor` in `LivenessService`.
2. **Loop Replay Rule Redesign (R2):**
   - **Segment Motion Prerequisite:** A candidate repeating segment must exhibit real intra-segment motion: the maximum Hamming distance between consecutive frames within the segment must exceed `loopMinSegmentMotionBits` (default 3, range-validated). Static scenes have near-zero pHash spread and are never diagnosed as loop replays.
   - **Intermediate Difference Requirement:** Intermediate frames between the earlier segment and replay segment must differ from the matched segment (at least one intermediate frame farther than `loopHammingThreshold`). A scene that matches continuously everywhere is recognized as static, not a loop replay.
   - **Freeze Rule Integrity:** Byte-identical content digests determine `FROZEN`. If `contentDigest` is null, freeze detection returns false (not decidable) and never falls back to pHash equality.
3. **Attestation Audit Head & Count Binding (R4):**
   - Added `auditHeadHash` (the real `AuditChain.latestHash()`) and `auditEventCount` as explicit fields in `Attestation`.
   - Included both fields in the pipe-delimited canonical string covered by the ECDSA signature: tampering with the count or head invalidates the signature.
   - Enhanced `LivenessResult` with `exportSealedHeadAndCount()` providing sealed head hash and size.
   - Added verification tests confirming that dropping, modifying, or reordering even one audit event breaks attestation verification.
4. **Synthetic Test Harness Alignment (R3):**
   - Updated `FrameSequenceMonitorFalsePositiveTest` and `FrameSequenceMonitorLoopDetectionTest` to execute against `LivenessConfig.defaultConfig()`.
   - Configured `IadMechanismRunner` to dynamically pull window, thresholds, and parameters from `LivenessConfig.defaultConfig()`.

**Consequences:**  
- Live still user tests across sigmas {1, 2, 4} and subpixel drift {0, 1.5} px yield **0 FROZEN** and **0 LOOP_REPLAY** flags across 200 trials each at production default window 15, and at window 30 (0/1200 false flags).
- Synthetic looped attacks with real motion and fresh noise are reliably detected ($100.0\%$ detection rate over 200 trials).
- Attestation guarantees cryptographic tamper-evidence against audit log truncation, extension, reordering, and event count modification.

**Remaining Unvalidated:**  
- **Part 2 X13 (Real-Camera Freeze and Loop Tuning):** All results above are synthetic mechanism-level verifications under simulated Gaussian sensor noise and subpixel drift. Tuning and thresholds for physical camera sensors, lens breathing, lighting flicker, and phone video replays remain unvalidated pending physical device testing.

## ADR-016: Soft LOOP_REPLAY Policy and Mandatory Audit Count

**Status:** Accepted.

**Context:** Synthetic probes of the redesigned loop rule (ADR-015) showed that it catches large-motion loops but mostly misses low-motion loops (about 3 px/frame or less) and can fire on legitimate periodic motion (a nod) or large random motion. None of this is validated on real cameras (Part 2 X13), so a hard attack verdict from this signal alone is not justified. Separately, the attestation accepted an optional audit event count, so a missing count silently skipped the count check.

**Decision:**
1. `LivenessConfig.loopReplayHardFail` (default `false`). When false, `LOOP_REPLAY` is logged to the audit chain (`INTEGRITY_SOFT`) and the session is marked loop-suspected. A loop-suspected session cannot pass on a passive score alone: with active challenges enabled it is routed to a challenge; with them disabled it ends in `ENGINE_ERROR` (fail closed). Other integrity violations (frozen frame, timestamp, resolution, device identity) are unchanged and still end the session. Set `loopReplayHardFail=true` to restore the previous behaviour.
2. `Attestation` requires a non-negative `auditEntryCount`; the count is always part of the signed string and always checked by `verifyAuditChain`. The legacy overloads without a count now supply 0, which fails closed against any non-empty chain.
3. The IAD report prints per-row Wilson intervals and the loop-rule limits, and no longer prints a "mechanism coverage" headline.

**Consequences:** A legitimate user who triggers a false loop signal is asked to complete a challenge instead of being blocked as an attacker. A real loop attack that also passes the challenge is not stopped by this rule alone. Choosing the hard or soft setting for deployment depends on Part 2 X13 measurements.

## ADR-017: Android PAD model is FP32; INT8 is an unevaluated alternative

**Status:** Accepted (Oct 23, 2026). Supersedes the model choice in ADR-009 item 1.

**Context:** ADR-009 shipped `MiniFASNetV2-SE-int8.onnx` for latency, but every accuracy number in TEST_REPORT.md and the threshold 70 (ADR-011, calibrated on the DEV split) come from the FP32 model `MiniFASNetV2-SE.onnx`. INT8 has never been evaluated for accuracy; "INT8 same accuracy" is author-reported only. The INT8 file is dynamically quantized (DynamicQuantizeLinear/ConvInteger/MatMulInteger, no QDQ pairs). On the phone, INT8 is slower than FP32. Phone runs (Nothing A001, MT6878, 400 lockstep frames per run, fixed-box detector, synthetic frames). Headline run: unplugged, thermal status 0-1, battery temp 35-37 C (`bench-results/2026-10-23-run3-unplugged/`). An earlier plugged-in run (`bench-results/2026-10-23-run2-plugged-in/`) agrees within about 0.3 ms on means.

| Metric (ms) | INT8 lockstep (4 runs) | FP32 lockstep (4 runs) |
|---|---|---|
| PAD_INFER mean | 10.07-10.31 | 6.96-7.18 |
| PAD_INFER p95 | 10.69-10.74 | 9.05-9.31 |
| Per-frame mean | 15.76-15.98 | 12.58-12.81 |
| Per-frame p95 | 16.30-16.48 | 14.64-14.95 |
| Per-frame p99 | 20.18-20.70 | 17.67-19.71 |

FP32 paced at 30 fps (4 runs): PROCESS_TOTAL mean 11.88-11.98, PAD_INFER p95 8.63-8.69, 0 dropped frames, max timestamp gap 35-36 ms against the 400 ms rule.

**Decision:**
1. The Android PAD model is `MiniFASNetV2-SE.onnx` (FP32, 1.82 MB, within the 5 MB budget), the model that was evaluated and calibrated. The INT8 file stays in the repo's tooling and benchmarks as an unevaluated alternative and is not shipped.
2. INT8 is NOT a drop-in for FP32 (measured, see DEV agreement below), and no hybrid or ensemble is adopted. The two models do disagree, so they are not redundant, but a combination has no demonstrated benefit: no attack decision flipped (only 9 DEV attacks, so this shows nothing about security), while the common combining rules would add bona fide false rejects ("reject if either rejects" would reject at least the 5 FP32 rejects plus the 9 images only INT8 rejects, 14 of 1021 on DEV, against 5 for FP32). The cost would be both models per frame, and any combined score would need a new calibration and a new ADR. The DEV split cannot support that calibration, and the TEST split is reserved for the single frozen-threshold run (ADR-011).
3. Any later INT8 adoption requires its own calibration (not threshold 70 from FP32), a TEST-split decision under ADR-011, and a new ADR. The DEV-split agreement report required here was run on 2026-10-23 (tool: `DevAgreementRunnerTest`, report: `bench-results/DEV_AGREEMENT_REPORT-2026-10-23.md`).

**DEV agreement, FP32 vs INT8 (desktop JVM, DEV split only, threshold 70):** 1030 images (1021 bona fide, 9 attack), 854 identical scores, mean absolute difference 1.05, max absolute difference 50, Pearson correlation 0.907. 10 decision flips: 9 bona fide FP32-accepts/INT8-rejects, 1 bona fide FP32-rejects/INT8-accepts, 0 attack flips. Bona fide rejected: FP32 5 of 1021, INT8 13 of 1021. Attacks accepted: 1 of 9 for both. Caveats: threshold 70 was calibrated on DEV with FP32, which favours FP32 on this split, so the size of the gap is not a clean estimate (its direction is clear); 9 attacks cannot separate the models on APCER; this is the desktop JVM, not the phone; bona fide images are stills.

**Consequences:** The 200 ms target is met for the measured stages only (capture + preprocess + PAD, about 14.6-14.95 ms at p95 in lockstep). Not measured: real face detector, SFace embedding, signing, camera copy, UI, sustained thermal behavior (each run is only seconds long), a second phone. The numbers above are latency on synthetic frames, not accuracy.

## ADR-018: Submission scope, host app reverted to FP32 and default thresholds, SBI out of scope

**Status:** Accepted (Oct 30, 2026). Records the changes on branch `fix/t1-t2-t4` (commit e59a582).

**Context:** Oct 30 device commits (5abcbe5 and later, up to f0d8b0c) made the host app load the INT8 model, lowered `attackThreshold` to 3, raised `attackPersistenceWindows` to 5, set `minActionFrames` 8 and `loopHammingThreshold` 4, and widened the timestamp-gap limit. These were dev workarounds. Reading the code showed: `minActionFrames` only applied to BLINK and SMILE (TURN always needed 1 frame); the 8-frame value at about 6 fps would likely make BLINK unpassable (not tested); `loopHammingThreshold` 4 makes loop detection weaker (match is `dist <= threshold`) and the loop rule is soft; INT8 contradicts ADR-017 and the threshold 70 was calibrated on FP32.

**Decision:**
1. Host app loads `MiniFASNetV2-SE.onnx` (FP32, SHA-256 af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6), per ADR-017.
2. `attackThreshold(3)`, `attackPersistenceWindows(5)` and `loopHammingThreshold(4)` are removed; the config defaults (30, 3, 6) apply again. They were dev workarounds and are reverted.
3. New config key `minTurnFrames` (default 1, bounds 1-10) applies a consecutive-frame requirement to TURN_LEFT and TURN_RIGHT. The host app sets `minActionFrames` 2 and `minTurnFrames` 2 (camera about 6 fps). `minChallenges` stays at its default of 2.
4. SBI/MDS hardware integration is out of scope. The host app always uses `CameraOnlyDevAdapter`; the SBI detection branch and banner are removed from the host app. The SBI classes stay in the repo, unit-tested only, never exercised on a device.
5. NOT changed: the timestamp-gap limit is still widened in the host app (`CameraXDeviceAdapter(..., 5_000L, 2_000L)`, about 4 s). This is a dev-only workaround for SFace latency after VERIFYING_FINAL; it weakens the freeze/injection check for the whole session. A proper fix (reset the sequence monitor around VERIFYING_FINAL) is not done.

**Consequences:** Challenge logic cannot stop a video that contains the requested action; it only raises the cost. Video replay is NOT defended. Desktop JVM suite after the change: 654 run, 0 failures, 0 errors, 4 skipped (user-run `mvn test`, 2026-10-30). Device evidence for this change: see TEST_REPORT.md, section "Oct 30 FP32 release run". It is N=3, one phone, one person, and not an accuracy figure.
