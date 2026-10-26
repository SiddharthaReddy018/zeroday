# Real LivenessSession, relative gate on, REAL blurMinVariance 100, with independent QualityGate probe: 3 runs (2 controls, 1 flip)

Job: CameraSessionRelativeBlurBenchmark with no override (blur_min_variance_used 100.0 in every JSON), plus a probe: an independent QualityGate with the same config is evaluated on every captured frame before processFrame. Real LivenessSession, real PAD int8, real composite detector, live front camera, relativeBlurEnabled(true), window 30, drop 2.0, y shift 1.5 (candidates, NOT fitted). Nothing A001, MT6878, Android 15, release build, one phone, 330 frames per run. Earlier runs without the probe: session-real-gate-blur100-thermal0-1.

## Limits (read first)
- minQualifyingFrames is huge, so no PASS or failure verdict can fire.
- Roles and order are USER-STATED, not in the JSONs: run 1 control, run 2 flip (flap put on near 12 s by stopwatch), run 3 control.
- The probe is an independent QualityGate evaluation per captured frame. It is NOT the session audit trail. Counts may differ slightly from what the session itself gated; that difference is Not verified.
- The absolute gate (blur 100) blocked the flip run: from frame 153 on most frames fail guide.quality.blur, so they cannot reach the relative hook. The relative gate was NOT exercised at threshold 100 in this condition.
- Whether the relative gate would fire for a milder blur (Laplacian staying above 100) is Not verified.
- Time to frame mapping is Not verified: frames are not evenly spaced (adapter drops 339 to 574 per run). Flip onset below is read from the Laplacian CSV.
- One phone, one light, one flip run. Not verified: other phones, hand shake, attacks, why the earlier 100 controls failed the gate more often, UI mapping of reason strings.

## Per run

### camera_session_relative_blur_sessreal100_1792876709258.json (control, user-stated)
- probe QualityGate reasons over 330 captured frames: pass 329, guide.quality.blur 1
- session state counts: POSITIONING 30, PASSIVE 300
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 1, quality_ok 2
- Laplacian per frame (probe): min 92.0, median 163.30, max 204.9, frames below 100: 1
- adapter_dropped_frames 574, wall_clock_ms 30845, thermal_status 1 -> 1

### camera_session_relative_blur_sessreal100_1792876737810.json (flip, user-stated)
- probe QualityGate reasons over 330 captured frames: pass 152, guide.face.none 4, guide.quality.blur 174
- session state counts: POSITIONING 207, PASSIVE 123
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 2, quality_ok 2
- Laplacian per frame (probe): min 40.1, median 85.50, max 187.7, frames below 100: 177
- adapter_dropped_frames 456, wall_clock_ms 26862, thermal_status 1 -> 1

### camera_session_relative_blur_sessreal100_1792876769959.json (control, user-stated)
- probe QualityGate reasons over 330 captured frames: pass 330
- session state counts: POSITIONING 29, PASSIVE 301
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 0, quality_ok 1
- Laplacian per frame (probe): min 108.5, median 152.55, max 187.1, frames below 100: 0
- adapter_dropped_frames 549, wall_clock_ms 30029, thermal_status 1 -> 1

## Flip run trace (run 2, from the per-frame CSVs)
- Laplacian median frames 120-149: 153.2; frames 150-179: 78.0. First frame with Laplacian below 100: index 153.
- full_norm median frames 0-29: 0.0340; frames 150-179: 0.0122; ratio about 2.80 (computed from those medians, not fitted).
- After frame 153 the Laplacian stays mostly below 100 (window medians 57.0 to 78.0 over frames 150-329), so the absolute gate rejects most frames.

## Reading
- Controls: 329 of 330 and 330 of 330 frames pass the probe QualityGate; blur_relative 0.
- Flip: 174 blur fails and 4 no-face of 330; blur_relative 0. The trail logged 2 drops with blur_relative 0; their reasons were not counted per type (Not verified, inference: absolute gate).
- At the real threshold 100 on this phone, the absolute gate rejects this flap before the relative gate is reached. This is a limit of the evidence, not a gate accuracy figure.

## Files
- camera_session_relative_blur_sessreal100_1792876709258.json
- camera_session_relative_blur_sessreal100_1792876737810.json
- camera_session_relative_blur_sessreal100_1792876769959.json
