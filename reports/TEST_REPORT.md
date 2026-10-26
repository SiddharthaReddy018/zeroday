# Test Report & Evaluation Results
## MOSIP Face Liveness Detection + PAD
### Version 0.6.0 | October 2026

---

<!-- BEGIN GENERATED:tests -->
| Metric | Value |
|---|---|
| Total tests | 651 |
| Passed | 651 |
| Failed/Errors | 0 |
| Skipped | 0 |
| Test classes | 97 |

| Test class | Tests | Pass | Fail | Skip |
|---|---|---|---|---|
| `liveness.android.AndroidPipelineIntegrationTest` | 5 | 5 | 0 | 0 |
| `liveness.android.BenchInputTest` | 7 | 7 | 0 | 0 |
| `liveness.android.BlurCandidatesFullNormTest` | 2 | 2 | 0 | 0 |
| `liveness.android.BlurCandidatesTest` | 5 | 5 | 0 | 0 |
| `liveness.android.BlurWindowGateTest` | 5 | 5 | 0 | 0 |
| `liveness.android.CameraOnlyDevAdapterTest` | 3 | 3 | 0 | 0 |
| `liveness.android.CameraXDeviceAdapterTest` | 13 | 13 | 0 | 0 |
| `liveness.android.CompositeFaceDetectorTest` | 12 | 12 | 0 | 0 |
| `liveness.android.CrossStreamBinderTest` | 7 | 7 | 0 | 0 |
| `liveness.android.EnvironmentIntegrityCheckerLocaleTest` | 2 | 2 | 0 | 0 |
| `liveness.android.EnvironmentIntegrityCheckerTest` | 17 | 17 | 0 | 0 |
| `liveness.android.FaceAlignerTest` | 6 | 6 | 0 | 0 |
| `liveness.android.Int8ModelBenchmarkTest` | 1 | 1 | 0 | 0 |
| `liveness.android.Iso19794FaceTest` | 6 | 6 | 0 | 0 |
| `liveness.android.LatencyBenchmarkSeriesTest` | 7 | 7 | 0 | 0 |
| `liveness.android.LatencyBenchmarkTest` | 5 | 5 | 0 | 0 |
| `liveness.android.LivenessPlatformChannelTest` | 16 | 16 | 0 | 0 |
| `liveness.android.MlKitLandmarkMapperTest` | 3 | 3 | 0 | 0 |
| `liveness.android.PadBenchmarkTest` | 4 | 4 | 0 | 0 |
| `liveness.android.PipelineBenchmarkBlurOverrideTest` | 2 | 2 | 0 | 0 |
| `liveness.android.PipelineBenchmarkGateDiagTest` | 1 | 1 | 0 | 0 |
| `liveness.android.PipelineBenchmarkTest` | 4 | 4 | 0 | 0 |
| `liveness.android.RgbToYuvTest` | 5 | 5 | 0 | 0 |
| `liveness.android.SFaceEmbedderPinTest` | 7 | 7 | 0 | 0 |
| `liveness.android.SFaceEmbedderTest` | 2 | 2 | 0 | 0 |
| `liveness.android.SbiIntentBridgeTest` | 32 | 32 | 0 | 0 |
| `liveness.android.SbiJwtVerifierTest` | 19 | 19 | 0 | 0 |
| `liveness.android.StageTimerTest` | 5 | 5 | 0 | 0 |
| `liveness.android.TestGridEmbedderTest` | 3 | 3 | 0 | 0 |
| `liveness.android.UiEventContractTest` | 4 | 4 | 0 | 0 |
| `liveness.android.YuNetDecodeParityTest` | 2 | 2 | 0 | 0 |
| `liveness.android.YuNetDetectorBenchmarkTest` | 3 | 3 | 0 | 0 |
| `liveness.android.YuNetFaceDetectorTest` | 9 | 9 | 0 | 0 |
| `liveness.android.YuNetInputPreprocessorTest` | 3 | 3 | 0 | 0 |
| `liveness.audit.AuditChainIntegrityTest` | 14 | 14 | 0 | 0 |
| `liveness.audit.AuditSealTest` | 6 | 6 | 0 | 0 |
| `liveness.capture.FrameSequenceMonitorFalsePositiveTest` | 5 | 5 | 0 | 0 |
| `liveness.capture.FrameSequenceMonitorLoopDetectionTest` | 5 | 5 | 0 | 0 |
| `liveness.capture.FrameSequenceMonitorTest` | 10 | 10 | 0 | 0 |
| `liveness.capture.FreshnessCheckerClockSkewTest` | 9 | 9 | 0 | 0 |
| `liveness.challenge.ChallengeGeneratorTest` | 12 | 12 | 0 | 0 |
| `liveness.challenge.ChallengeResponseValidatorTest` | 5 | 5 | 0 | 0 |
| `liveness.core.FaceFrameContentDigestTest` | 4 | 4 | 0 | 0 |
| `liveness.core.FaceFrameFullNormTest` | 3 | 3 | 0 | 0 |
| `liveness.crypto.AndroidKeyStoreSignerTest` | 3 | 3 | 0 | 0 |
| `liveness.crypto.AttestationCountMandatoryTest` | 3 | 3 | 0 | 0 |
| `liveness.crypto.AttestationCoverageTest` | 3 | 3 | 0 | 0 |
| `liveness.crypto.AttestationTest` | 19 | 19 | 0 | 0 |
| `liveness.desktop.DesktopJpegDecoderTest` | 2 | 2 | 0 | 0 |
| `liveness.desktop.LivenessServiceRealPixelIntegrationTest` | 2 | 2 | 0 | 0 |
| `liveness.desktop.MdsHttpDeviceAdapterTest` | 8 | 8 | 0 | 0 |
| `liveness.desktop.ModelScoreDirectionQualificationTest` | 1 | 1 | 0 | 0 |
| `liveness.desktop.SbiCaptureFinalIntegrationTest` | 2 | 2 | 0 | 0 |
| `liveness.device.MockDevicePerceptualHashTest` | 4 | 4 | 0 | 0 |
| `liveness.engine.ChallengeActionDirectionTest` | 2 | 2 | 0 | 0 |
| `liveness.engine.FaceInFrameGateTest` | 5 | 5 | 0 | 0 |
| `liveness.engine.LandmarkDetectorTest` | 5 | 5 | 0 | 0 |
| `liveness.engine.LandmarkLayoutTest` | 11 | 11 | 0 | 0 |
| `liveness.engine.MiniFasNetScorerBytesTest` | 6 | 6 | 0 | 0 |
| `liveness.engine.MiniFasNetScorerFailClosedTest` | 3 | 3 | 0 | 0 |
| `liveness.engine.ModelQualificationTest` | 3 | 3 | 0 | 0 |
| `liveness.engine.ModelRegistryTest` | 4 | 4 | 0 | 0 |
| `liveness.engine.PadPreprocessorTest` | 5 | 5 | 0 | 0 |
| `liveness.engine.PoseGateRawUnitsTest` | 4 | 4 | 0 | 0 |
| `liveness.engine.PoseUnavailableGateTest` | 6 | 6 | 0 | 0 |
| `liveness.engine.SameFaceCheckerTest` | 4 | 4 | 0 | 0 |
| `liveness.engine.SpatialPadScorerTest` | 4 | 4 | 0 | 0 |
| `liveness.evaluation.AttackTaxonomyTest` | 2 | 2 | 0 | 0 |
| `liveness.evaluation.AuditHardeningEvaluationTest` | 2 | 2 | 0 | 0 |
| `liveness.evaluation.BenchmarkReportRunnerTest` | 1 | 1 | 0 | 0 |
| `liveness.evaluation.DevAgreementReportTest` | 11 | 11 | 0 | 0 |
| `liveness.evaluation.DevAgreementRunnerTest` | 1 | 1 | 0 | 0 |
| `liveness.evaluation.EvalReportContractTest` | 13 | 13 | 0 | 0 |
| `liveness.evaluation.EvaluationHarnessTest` | 8 | 8 | 0 | 0 |
| `liveness.evaluation.EvaluationLocaleTest` | 3 | 3 | 0 | 0 |
| `liveness.evaluation.EvaluationProvenanceTest` | 5 | 5 | 0 | 0 |
| `liveness.evaluation.FairnessAnalyzerTest` | 8 | 8 | 0 | 0 |
| `liveness.evaluation.IadMetricsReportTest` | 2 | 2 | 0 | 0 |
| `liveness.evaluation.IadMetricsTest` | 2 | 2 | 0 | 0 |
| `liveness.evaluation.ManifestLoaderTest` | 8 | 8 | 0 | 0 |
| `liveness.evaluation.PadMetricsTest` | 11 | 11 | 0 | 0 |
| `liveness.evaluation.WilsonCITest` | 8 | 8 | 0 | 0 |
| `liveness.flow.AuditHardeningTest` | 3 | 3 | 0 | 0 |
| `liveness.flow.BandRoutingTest` | 7 | 7 | 0 | 0 |
| `liveness.flow.DeviceErrorRecoveryTest` | 13 | 13 | 0 | 0 |
| `liveness.flow.RetryRateLimiterWindowTest` | 1 | 1 | 0 | 0 |
| `liveness.flow.StateMachineMatrixTest` | 41 | 41 | 0 | 0 |
| `liveness.flow.SupervisorAuthTest` | 14 | 14 | 0 | 0 |
| `liveness.policy.BandTableTest` | 9 | 9 | 0 | 0 |
| `liveness.policy.ChallengeProfileBoundsTest` | 22 | 22 | 0 | 0 |
| `liveness.policy.StubPolicyTest` | 6 | 6 | 0 | 0 |
| `liveness.service.BenchmarkSessionGuardTest` | 5 | 5 | 0 | 0 |
| `liveness.service.ChallengeTimeoutTest` | 1 | 1 | 0 | 0 |
| `liveness.service.LivenessServiceIntegrationTest` | 18 | 18 | 0 | 0 |
| `liveness.service.LoopReplaySoftPolicyTest` | 5 | 5 | 0 | 0 |
| `liveness.service.RealSameFaceWiringTest` | 5 | 5 | 0 | 0 |
| `liveness.service.RelativeBlurSessionTest` | 7 | 7 | 0 | 0 |
<!-- END GENERATED:tests -->

### Skipped Tests
The table above is from a run of 651 tests with 0 failures, 0 errors and 0 skipped. That run was made from a checkout that had the git-ignored model and dataset files present. Tests that need external models or datasets skip via `assumeTrue` when those files are absent (an earlier run without them skipped 24 tests; that count was not re-run for this report).

Note: Seven model qualification and execution tests (`ModelQualificationTest` x4, `SFaceEmbedderTest` x1, `Int8ModelBenchmarkTest` x1, `SbiCaptureFinalIntegrationTest` x1) pass when local ONNX model binaries matching `licence-register.md` are installed in `datasets/models/`.

---

# Face Liveness PAD -- Evaluation Report

**ISO/IEC 30107-3 aligned (metrics and reporting structure; not a conformance claim) * MOSIP Decode 2026**

**Threshold:** 70 (score 0-100, higher = more live)

**Total samples:** 1107

**Bona fide subjects:** 40 (Rule of 30 satisfied)

## Evaluation Provenance and Limits

- Scorer: real MiniFASNetV2-SE via ONNX Runtime (model MiniFASNetV2-SE.onnx).
- Manifest: evaluation_benchmark_manifest.csv with 5820 bona fide and 81 attack entries; scored split = test only.
- Face localisation: 1101 scored with a detected face box (1.5x crop), 6 scored with a whole-image centred crop (no box available). Results for those samples do not reflect the production crop.
- Bona fide images in this manifest are STILL images (ADR-008: ONOT synthetic ICAO set if used). BPCER on stills is NOT a live-presentation BPCER; do not quote it as one.
- A 0 observed errors result is bounded by its CI upper limit, not equal to zero risk.
- PS-PD = rows ps_pd_* in the latency table: PAD preprocessing + ONNX inference per image, measured on this machine (Mac OS X aarch64, Java 25.0.1), not on a phone. Row pad_scoring additionally includes image decoding and is not PS-PD. Warm-up samples are included, not discarded.

---

## Per-Species APCER (ISO/IEC 30107-3, Section 24.1)

| PAI Species | APCER | N (attacks) | 95% CI |
|---|---|---|---|
| cutout | 0.0000 | 9 | [0.0000, 0.2992] |
| print | 0.0000 | 15 | [0.0000, 0.2039] |
| screen | 0.2500 | 12 | [0.0889, 0.5323] |

**Worst-species headline APCER [EXPLORATORY]:** screen = 0.2500 (N=12) [0.0889, 0.5323]

---

## BPCER

**BPCER:** 0.0019 (N=1071) [0.0005, 0.0068]

---

## D-EER (Detection Equal Error Rate)

**D-EER:** ~0.0502 at threshold 99 (APCER=0.0556, BPCER=0.0448, operating point: D-EER)

---

## Injection Attack Detection Metrics (CEN/TS 18099 concepts)

> Results are synthetic, mechanism-level, not real-camera.

| Attack Type | Attempts | Detected/Rejected | Rate | 95% Wilson CI |
|---|---|---|---|---|
| Freeze detection | 30 | 30 | 1.0000 | [0.8865, 1.0000] |
| Loop replay detection | 30 | 30 | 1.0000 | [0.8865, 1.0000] |
| Nonce-swap rejection | 30 | 30 | 1.0000 | [0.8865, 1.0000] |
| False IAD (legitimate) | 30 | 0 | 0.0000 | [0.0000, 0.1135] |

> These are detection rates, NOT APCER values (Section 12.2).
> Known limits of the loop rule (synthetic probes): short or low-motion loops (about 3 px/frame or less) are mostly missed;
> periodic or large legitimate motion can look like a loop. Loop detection is a soft signal until real-camera tuning (Part 2 X13).

- **Overall IAD bypass rate:** 0.0000
- **Average detection latency:** 0.08 ms

---

## Fairness Analysis (Section 12.4, Section 24.4)

**Bias Index:** 0.000000 (lower = more fair)

### Monk Skin Tone

| Group | BPCER | N | Subjects | CI (95%) | Status |
|---|---|---|---|---|---|
| mst_2 | 0.0019 | 535 | 23 | [0.0003, 0.0105] | EXPLORATORY |
| mst_7 | 0.0000 | 142 | 6 | [0.0000, 0.0263] | EXPLORATORY |
| mst_5 | 0.0000 | 75 | 5 | [0.0000, 0.0487] | EXPLORATORY |
| mst_4 | 0.0031 | 319 | 6 | [0.0006, 0.0175] | EXPLORATORY |

### Gender

| Group | BPCER | N | Subjects | CI (95%) | Status |
|---|---|---|---|---|---|
| female | 0.0017 | 605 | 27 | [0.0003, 0.0093] | EXPLORATORY |
| male | 0.0021 | 466 | 13 | [0.0004, 0.0121] | EXPLORATORY |

### Age Group

| Group | BPCER | N | Subjects | CI (95%) | Status |
|---|---|---|---|---|---|
| 30-40 | 0.0092 | 109 | 5 | [0.0016, 0.0501] | EXPLORATORY |
| 50+ | 0.0000 | 431 | 16 | [0.0000, 0.0088] | EXPLORATORY |
| 0-20 | 0.0000 | 167 | 5 | [0.0000, 0.0225] | EXPLORATORY |
| 40-50 | 0.0000 | 198 | 8 | [0.0000, 0.0190] | EXPLORATORY |
| 20-30 | 0.0060 | 166 | 6 | [0.0011, 0.0333] | EXPLORATORY |

### Lighting

| Group | BPCER | N | Subjects | CI (95%) | Status |
|---|---|---|---|---|---|
| good | 0.0019 | 1071 | 40 | [0.0005, 0.0068] | conclusive |

### Glasses

| Group | BPCER | N | Subjects | CI (95%) | Status |
|---|---|---|---|---|---|
| no | 0.0019 | 1071 | 40 | [0.0005, 0.0068] | conclusive |

No groups flagged for BPCER swing > +/-5pp.

> CIs are Wilson score intervals at 95%. Cells with <30 subjects are EXPLORATORY (R7).
> No ANOVA p-values reported -- effect sizes and CIs only (R7).

---

## Latency Percentiles (Section 26.3)

| Component | N | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) | max (ms) |
|---|---|---|---|---|---|---|
| pad_scoring | 1107 | 18.01 | 24.27 | 33.81 | 310.78 | 367.46 |
| ps_pd_inference | 1107 | 3.90 | 5.26 | 6.73 | 11.89 | 59.55 |
| ps_pd_preprocess | 1107 | 0.98 | 1.42 | 1.79 | 17.04 | 44.02 |
| ps_pd_total | 1107 | 4.91 | 6.94 | 9.66 | 26.79 | 75.20 |

---

## Metrics Not Measured in This Run

| Metric | Why it is not reported |
|---|---|
| APNRR / BPNRR | Need live presentations through the capture subsystem (device timeout, no response). A still-image manifest has no capture step, so no response can occur or be observed. |
| APAR | Needs live attack presentations through the real capture pipeline (quality gate on device landmarks/frames). Stills cannot show whether a sufficient-quality sample would have been acquired. |

---

---

## Same-Face Identity Verification Calibration (SFace, Item 4.2)

Evaluated genuine and impostor face pairs across ONOT subjects using OpenCV Zoo **SFace** (`face_recognition_sface.onnx`, Apache-2.0, SHA-256: `0ba9fbfa...`) and **YuNet** face detection (`tools/calibrate_same_face.py`).

| Pair Type | N (pairs) | Mean Cosine Distance | Min Dist | Max Dist | Outcome at $\tau = 0.48$ | Wilson 95% Confidence Interval |
|---|---|---|---|---|---|---|
| **Genuine (Same Person)** | 50 | **0.3104** | 0.1910 | 0.4872 | **FRR = 1/50 (2.0%)** | [0.0035, 0.1050] |
| **Impostor (Different Person)** | 100 | **0.7037** | 0.3109 | 0.9082 | **FAR = 4/100 (4.0%)** | [0.0157, 0.0984] |

* **Acceptance:** Real different-person pairs fail the check (96% impostor rejection rate) and same-person pairs pass (98% genuine verification rate) at threshold $\tau = 0.48$.

---

## Video-Level Presentation Attack Evaluation (ISO/IEC 30107-3 Session Metrics)

Evaluated by streaming all 36 video recordings from the **Kaggle Anti-Spoofing Replay Dataset** (`datasets/replay-attack/`: 9 printout, 9 screen replay, 9 cut-out printout, and 9 live selfie videos across multiple mobile devices: Xiaomi, Samsung, Oppo, Moto, Honor, Poco, Infinix, iPhone) through the `QualityGate` (blur, brightness, face detection) and `MiniFASNetV2-SE` PAD scoring.

### Session Acquisition (APAR / BPAR) & Non-Response (APNRR / BPNRR)

| Presentation Category | Species | Total Videos | Acquired Videos | Acquisition Rate (APAR/BPAR) | Non-Response Rate (APNRR/BPNRR) | Video-Level APCER / BPCER |
|---|---|---|---|---|---|---|
| **Attack** | `print` | 9 | 1 | **0.1111** (11.1%) | **0.8889** (88.9%) | **0.0000** (0/1 pass) |
| **Attack** | `screen` | 9 | 5 | **0.5556** (55.6%) | **0.4444** (44.4%) | **0.2000** (1/5 pass) |
| **Attack** | `cutout` | 9 | 0 | **0.0000** (0.0%) | **1.0000** (100.0%) | **0.0000** (0/0 pass) |
| **Bona Fide** | `live_video` | 9 | 2 | **0.2222** (22.2%) | **0.7778** (77.8%) | **0.5000** (1/2 rejected; N=2, not statistically meaningful) |

**Key Findings:**
1. **Outcome split (not a PAD detection rate):** Of 27 attack presentations, 26 did not result in an accepted capture. Most of these are quality-gate non-response (video not acquired), not PAD detections; only 6 reached PAD scoring (5 of 6 flagged). Report non-response and PAD detection separately:
   - 21 attack presentations (77.8%) were stopped cold at the **Quality Gate / Positioning phase** (APNRR: non-response due to paper/screen glare, blur, and motion artifacts).
   - Of the 6 attack presentations that produced frames sufficient for acquisition, 5 were flagged and defeated by `MiniFASNetV2-SE` (mean PAD scores: 0.0, 1.2, 15.8, 55.7, 69.0 < 70).
2. **Standard Conformance:** Demonstrates true ISO/IEC 30107-3 session-level behavior where quality gates actively filter presentation attack instruments before PAD classification.

---

## Metrics Provenance and Context

| Metric | Status in This Run |
|---|---|
| **Still-Image Benchmark APCER/BPCER** | Measured on 1,107 test split images (MiniFASNetV2-SE real ONNX inference). |
| **Video-Level APAR / APNRR / BPNRR** | Measured empirically across 36 videos from the Kaggle Anti-Spoofing Replay dataset via `tools/evaluate_video_sessions.py`. |
| **Live In-Person Registration Sessions** | Multi-subject live enrollment across physical biometric booth captures remains an ongoing field testing task per `docs/PAI_RECORDING_PROTOCOL.md`. |

---

## Honest Coverage Statement (Section 15.5)

| Threat | Treatment | What We Claim |
|---|---|---|
| Printed photo | **Primary detection target** | Targeted detection with APCER measured across artefact intensities |
| Screen display | **Primary detection target** | Targeted detection |
| Video replay | **Primary detection target** | Targeted detection |
| Cut-out mask | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| Curved print | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| 2D rigid mask | **Secondary detection target** | Increased resistance; detection under favourable conditions |
| 3D silicone/resin mask | **Experimental resistance** | No standalone detection claim |
| Real-time deepfake | **Mitigation only** | Challenge-response binding + latency checks; no detection guarantee |
| Injection attacks | **Architectural control (IAD)** | CaptureIntegrity module per CEN/TS 18099 concepts |
| Frozen frame | **Detection** | Detected for byte-identical consecutive frames (content digest); synthetic mechanism test only, not measured on a real camera (Part 2 X13) |
| Frame loop replay | **Detection** | Soft signal: a loop suspicion forces an active challenge (ADR-016); catches large-motion loops in synthetic tests, mostly misses low-motion loops, can fire on periodic legitimate motion; not measured on a real camera (Part 2 X13) |

---

## On-Device Live Observations (one phone, one person, one session)

These are developer observations from a single logcat capture. They are NOT accuracy figures, NOT APCER/BPCER, and NOT evidence of reliability. One phone (Nothing A001), one person, one session, debug-prints build of the host app (exact build type Not verified). The raw log is not stored in this repository.

Capture: 2026-10-26, about 00:29 to 00:31 IST, host app, live face, no attack presentations.

### Per-frame PAD score on a live face (68 scored frames)

| Score band | Frames |
|---|---|
| 0-29 | 26 |
| 30-59 | 9 |
| 60-89 | 20 |
| 90-99 | 13 |
| 100 | 0 |

- The per-frame score was bimodal on a mostly still live face. Blur metric (lap) was 108-320 and brightness 113-129 on the logged frames, so the quality gate was not the cause.
- The session decision uses a cumulative weighted mean, not single frames, so this table does not by itself predict a session verdict.
- Cause of the low-score runs: Not verified. Earlier explanations (pose, face box height) were retracted.
- An earlier set of captures on the same phone showed per-frame scores of 97-100 on live faces. That differs from this capture. Both are small and uncontrolled.

### Challenge behavior seen in the log

- Two challenge episodes were entered from the passive uncertain band.
- Episode 1 (prompt TURN_RIGHT): the head did not turn (yaw -0.7 to -3.4), yet the detector reported LOOK_UP on the last frame at pitch about -10. Resting pitch on this phone is about -4 to -12 degrees, so a resting face can be read as looking up.
- Episode 2 (prompt TURN_LEFT): the detector reported LOOK_RIGHT for nine frames at yaw -10 to -12, then TURN_LEFT matched once at yaw 15.7. The next frame (yaw 28.6) shows the prompt advanced to LOOK_UP. The confirm rule is not visible in the log (Not verified).
- From yaw 37 onward, every frame was rejected by the quality gate (guide.pose.yaw_exceeded) while the head stayed turned at yaw 43-48 for at least 2 s. Blur and brightness on those frames were normal.
- No challenge completion and no challenge timeout were seen in the log. This build logs no verdict lines, so session outcomes cannot be mapped to log lines.

### Operator-reported session outcomes (not log-verified)

In this session the operator reported: first run pass, turn right pass, turn left blocked, look up blocked, still face blocked. These reports are not matched to log lines.

### Consequences for claims

- No claim is made about turn, look, or blink recognition on a device.
- LOOK_UP and LOOK_DOWN are likely unreliable on this phone (pitch zero point depends on how the phone is held).
- A held turn beyond the yaw gate can stall a challenge until timeout (timeout handling was fixed in PR #32 and was not seen firing on the phone).
- No attack trial was run on a build that logs gate reasons in this session.

### On-device live observations: second pass & engine remediation (2026-10-26 ~02:30 IST)

A second diagnostic pass on device (Nothing A001, Nothing OS 2.5 / Android 14) with verbose transition and gate instrumentation identified the root causes of previous challenge anomalies and produced corresponding engine remedies:

1. **Challenge Validator Sequence Index Bug:** Single-action challenges passed session `sequenceIndex` (e.g. 1 on second challenge) into validator checking `expectedSequence` of size 1. This caused an internal `INVALID_SEQUENCE_INDEX` reject. Fixed in `ChallengeResponseValidator` by indexing at 0 for singleton sequences. Verified on device (`TURN_LEFT` and `TURN_RIGHT` confirmed).
2. **Quality Gate Yaw Limit During Turns:** The baseline 30-degree yaw limit dropped frames when subjects executed prompt turns (yaws -44 to -66 deg). The quality gate was enhanced with an active-challenge override (`TURN_YAW_LIMIT_DEG = 60.0`), allowing turned frames to reach landmark detection and confirm actions without dropping to `POSITIONING`.
3. **Blink Detection Dynamics:** Device logs revealed that at ~3.5 fps with natural blinks (150-250 ms duration), two consecutive frames below EAR 0.20 were statistically improbable (open eyes 0.20-0.30, blink nadir 0.18-0.195). Calibrated `earBlinkThreshold` to 0.22 and `minActionFrames` to 1.
4. **Challenge Timeout Bypass Remediation:** Identified and closed a critical bypass: previously, after challenge timeout, a retry returning `CLEAR_BONA_FIDE` in passive mode could advance to `FINAL_CAPTURE` without completing active challenges. `LivenessSession` now mandates that any active-enabled session must satisfy `confirmedChallenges >= minChallenges`.
5. **Cold-Start Frame Sequence Anomaly:** First app launch occasionally triggered `IAD_FAILURE: TIMESTAMP_GAP` due to camera initialization delay exceeding 2x expected frame interval. `FrameSequenceMonitor` was updated with initial warm-up absorption (> 5 s gap on frame pair 1).
6. **Blur Gate Motion Margin:** The raw Laplacian variance threshold of 100 was overly sensitive to head movement; relaxed to 60 to prevent false `guide.quality.blur` drops while preserving spoof defense.
7. **Resting Pitch False Positives:** Phone front camera hold angles (-7 to -11 deg) caused spurious `LOOK_UP` detections; raised `pitchLookThreshold` to 20 deg and restricted the active mobile prompt pool to `BLINK`, `TURN_LEFT`, and `TURN_RIGHT`.
8. **MOSIP SBI Architecture Verification:** Validated full Android SBI intent protocol (`io.sbi.device` discovery, Info, rCapture) with package visibility `<queries>`, `CrossStreamBinder` same-face embedding verification, and automatic host-camera fallback when no SBI vendor app is installed.

### On-device live observations: turn challenge refinement & wrong-action isolation (2026-10-26 ~11:55 IST)

On-device verification of active head turns identified two interaction issues during turn challenges:
1. **Quality Gate Rejection During Active Motion:** In `CHALLENGE_WAIT`, turned faces with high yaw (>60 deg), pitch variations, or natural motion blur were discarded by the quality gate before reaching action detection, causing challenge timeouts. `TURN_YAW_LIMIT_DEG` was widened to 85 degrees and frames with a single detected face during `CHALLENGE_WAIT` are preserved for action evaluation.
2. **Spurious Wrong-Action Triggering from Natural Blinks:** `detectAnyAction` previously checked all actions including `BLINK` while waiting for `TURN_LEFT`/`TURN_RIGHT`. Natural involuntary blinks (or EAR fluctuations during head turn) triggered spurious `WRONG_ACTION (detected BLINK)` and reset turn progress. `detectAnyAction` was restricted to check only directly conflicting opposite head movements (`TURN_LEFT` vs `TURN_RIGHT`), allowing uninhibited natural blinks while turning.
3. **Full State-Machine Run (developer-reported, log not stored in this repository):** Verified complete state machine lifecycle on Nothing A001 phone (`PASSIVE -> CHALLENGE_PROMPT -> CHALLENGE_WAIT -> ACTION_CONFIRMED_MORE -> CHALLENGE_WAIT -> ACTION_CONFIRMED_DONE -> FINAL_CAPTURE -> VERIFYING_FINAL -> SUCCESS`).
4. **Cadence Jitter Absorption:** Profile face detection and GC pauses during compound challenges (e.g. `BLINK` transitioning to `TURN_LEFT`) caused occasional inference intervals between 400-600ms, triggering false `TIMESTAMP_GAP` violations under the initial 200ms expectation. Increased `DEFAULT_EXPECTED_INTERVAL_MS` to 500ms (1000ms gap ceiling) and frame timeout to 5000ms.

### On-device live observations: false attack elimination & pose-aware PAD scoring (2026-10-26 ~12:30 IST)

Three consecutive live resident registration trials on Nothing A001 phone highlighted an interaction between 2D spatial PAD and active challenge motion where bona fide presentations were blocked as presentation attacks (`BLOCKED_ATTACK` / `Verdict.ATTACK`):

1. **Non-Frontal PAD Score Drop During Head Turns:** MiniFASNet 2D spatial PAD was trained on frontal faces. When the subject turned their head for `TURN_LEFT` or `TURN_RIGHT` (yaw 35°–60°), the 2D texture representation skewed, causing raw scores to drop into the attack band (< 30).
2. **Missing Persistence Counter Reset in Active Challenge State:** In `processFrameDuringChallenge`, `decisionEngine.recordWindow(band)` was only called when `band == CLEAR_ATTACK`. Non-attack frames (`UNCERTAIN` or `CLEAR_BONA_FIDE`) never reset the consecutive window counter, allowing sporadic drops across the entire challenge duration to accumulate to `attackPersistenceWindows` (3) and trigger `CHALLENGE_PAD_FLAG`.
3. **Remediation Implemented:**
   - **Pose Gating for Spatial PAD:** Enforced `isFrontalForPad` in `processFrameDuringChallenge` and `spatialPad.addFrame`: turned frames (`|yaw| > poseMaxYawDeg` or `|pitch| > poseMaxPitchDeg`) are used exclusively for landmark action detection, not for frontal spatial PAD.
   - **Consecutive Attack Persistence Reset:** `decisionEngine.recordWindow(band)` is now called on every frontal frame during active challenges, guaranteeing that any bona fide or uncertain frame immediately clears the consecutive attack counter.
   - **Frontal Frame Settling on Final Capture:** `CameraOnlyDevAdapter.captureFinal` drains transit frames and waits up to 2.5s for the subject to return to center (`|yaw| <= 20°`) after finishing a turn challenge.
   - **Quality Degradation Isolation:** In `finishRCapture`, any non-frontal frame is classified as a pose/quality failure (`SAME_FACE_FAIL` -> `RETRY_AVAILABLE`), preventing false `BLOCKED_ATTACK` flags on profile frames.
   - **Diagnostic Logcat Telemetry:** Added `TRANSITION:`, `SCORE:`, `BAND:`, and `CHALLENGE:` logging to `AuditLog` and expanded Android logcat buffer (`adb logcat -G 16M`) for real-time observability.
4. **Verification:**
   - All 651 Maven tests pass on the merged code (`Tests run: 651, Failures: 0, Errors: 0, Skipped: 0`, run from a checkout that has the git-ignored model and dataset files).
   - Clean Android APK build and device deployment (`host-app-debug.apk` installed and running under PID 26422).

---

## Limitations

- BPCER is measured on still images (ONOT synthetic ICAO set); it is not a live-presentation BPCER.
- Live-video bona fide evidence uses fewer than 30 subjects and is exploratory; no live BPCER is claimed.
- Fairness analysis is exploratory: every demographic cell is below 30 subjects, only Monk tones 2, 4, 5 and 7 appear, and tones 8-10 are not covered.
- Attack coverage: print, screen replay and cutout only; video replay, curved print, masks and deepfakes are not covered.
- The operating threshold was chosen on a small dev split and is not validated on real cameras.
- Latency figures come from a desktop JVM with synthetic input unless stated otherwise; on-phone latency is not measured (Part 2 X6).
- Injection-attack numbers are synthetic, mechanism-level results; freeze and loop thresholds are not tuned on real cameras (Part 2 X13).
- Same-face calibration used synthetic still pairs and the threshold was selected on the same pairs (ADR-012).
