# Blur gate design note, draft v2 (no code, nothing fitted)

Status: draft. Fail closed decided by the user when the session baseline is lost. All numbers are candidates from an offline replay of 61 committed runs.

## 1. Goal
Reject blurred capture within a session without an absolute threshold. Sharp-frame full_norm varies 0.021 to 0.196 across sets (scene and exposure), so no absolute floor works. In the one matched control, flap ON vs OFF differed about 3x.

## 2. Metric
- Whole-frame full_norm, as the bench computes it. The replay used per-frame full_norm and y_brightness only.
- Not verified: that the app capture path can compute full_norm per frame today. The gate currently uses lumaStats -> FaceFrame.laplacianVariance. Reading CameraXDeviceAdapter and the bench full_norm definition is the first code task.
- crop_ratio is out of the gate: it moved the wrong way in the control (ON higher by 0.016-0.038) and separated in only one bright scene pair.

## 3. Rule (candidates, not fitted)
- Window: median over the last W=30 frames that have a value. Minimum 30 samples before any verdict, else INSUFFICIENT (not passed, fail closed).
- Baseline: highest windowed median full_norm seen this session, with the Y median at that time.
- BASELINE_LOST (checked first): windowed Y median differs from baseline Y by more than 1.5x either way. Fail closed: reject and prompt for steady light.
- BLUR: windowed full_norm < baseline / 2.
- No-face frames add no sample and count as neither blur nor sharp. Long loss falls to the existing no-face path.
- Pass only in state OK.

## 4. Replay evidence (61 committed runs)
- Per run: 60 of 61 all OK, no false BLUR. One run (1792861282005) 22/22 LOST, another 1 LOST window.
- Control (order user-stated): OFF runs 28/28 OK; ON runs BLUR in 26, 25, 28 of 28 windows, rest LOST.
- Light jump (bright set then dark2 set, flap off): 28/28 OK, then 28/28 LOST, 0 false BLUR.

## 5. Known holes
- The Y-shift check overlaps the flap effect (ON runs shifted Y about 1.2x to 1.7x, near the 1.5x cutoff). Fail closed rejects either way, but the stated reason would be light, not blur. Cause of the Y change (flap vs auto-exposure) Not verified.
- A session that starts blurred has no sharp baseline and is not caught. An absolute fallback is unusable on current evidence. Open design question.
- Honest users who change light or move mid-session may be rejected. Rate on real sessions not measured.
- One phone, one scene for the control, runs are 300-frame chunks, per_frame order assumed to be capture order, flap state user-stated, attack accuracy, hand shake and other distances untested.

## 6. Files to touch
- CameraXDeviceAdapter (compute), FaceFrame (new field, sentinel -1 = unavailable, type Not verified), LivenessSession.java:245 (pass-through), QualityGate.java:56 (windowed state check), LivenessConfig (W, drop factor, Y-shift factor, minimum samples).
- laplacianVariance and the 100.0 threshold (LivenessConfig:161) stay untouched; the new check is additive. QualityGate.java:89-90 sharpnessWeight unchanged for now.
- Tests and tools: CameraXDeviceAdapterTest:33, tools/evaluate_video_sessions.py:81, PipelineBenchmarkBlurOverrideTest, PipelineBenchmarkGateDiagTest.

## 7. Validation before merge
Maven gate; Gradle build and install (user gate); gate-passing latency run (ImageProxy copy cost Not verified); live check in one fixed light (passes flap OFF, rejects flap ON); PACED and FROZEN under rot270.

## 8. Open items
- Coarse fallback for the blurred-from-frame-1 case, or a documented limit?
- Should BASELINE_LOST prompt differently from BLUR in the UI?
