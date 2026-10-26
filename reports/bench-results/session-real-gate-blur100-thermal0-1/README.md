# Real LivenessSession, relative gate on, REAL blurMinVariance 100: 3 runs (2 controls, 1 flip)

Job: CameraSessionRelativeBlurBenchmark with no override (blur_min_variance_used 100.0 in every JSON). Real LivenessSession, real PAD int8, real composite detector, live front camera, relativeBlurEnabled(true), window 30, drop 2.0, y shift 1.5 (candidates, NOT fitted). Nothing A001, MT6878, Android 15, release build, one phone, 330 frames per run. The 10.0-threshold runs are in session-relative-gate-thermal0-1.

## Limits (read first)
- minQualifyingFrames is huge, so no PASS or failure verdict can fire.
- Roles and order are USER-STATED, not in the JSONs: run 1 control, run 2 flip (flap put on near 12 s, exact time not known), run 3 control.
- QualityGate failure reasons are NOT recorded, so why frames failed at threshold 100 is Not verified. No per-frame Y in the JSONs.
- One phone, one light, one flip run. All three files carry condition_label sessreal100.
- Not verified: other phones, hand shake, attacks, UI mapping of reason strings.

## Per run

### Run 1: camera_session_relative_blur_sessreal100_1792875945392.json
- role: control (user-stated: flap OFF whole run)
- state counts: POSITIONING 312, PASSIVE 18; run-length (P positioning, A passive): P31 A1 P2 A2 P1 A5 P1 A3 P266 A1 P1 A3 P8 A1 P1 A2 P1
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 8, quality_ok 8
- full_norm p50 frames 0-99 0.0212, 250-329 0.0183; ratio 1.16
- adapter_dropped_frames 416, wall_clock_ms 26341
- thermal_status 1 -> 0; battery 34.0 -> 34.0 C; charging False (recorded)

### Run 2: camera_session_relative_blur_sessreal100_1792875972726.json
- role: flip (user-stated: flap put on near 12 s on the stopwatch, exact time not known; approximate)
- state counts: POSITIONING 323, PASSIVE 7; run-length (P positioning, A passive): P100 A2 P2 A1 P1 A3 P5 A1 P215
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 4, quality_ok 4
- full_norm p50 frames 0-99 0.0253, 250-329 0.0129; ratio 1.96
- adapter_dropped_frames 339, wall_clock_ms 25268
- thermal_status 0 -> 0; battery 34.0 -> 35.0 C; charging False (recorded)

### Run 3: camera_session_relative_blur_sessreal100_1792876005523.json
- role: control (user-stated: flap OFF whole run)
- state counts: POSITIONING 33, PASSIVE 297; run-length (P positioning, A passive): P29 A68 P2 A154 P2 A75
- audit trail counts: blur_relative 0, light_unsteady 0, quality_dropped 2, quality_ok 3
- full_norm p50 frames 0-99 0.0315, 250-329 0.0379; ratio 0.83
- adapter_dropped_frames 551, wall_clock_ms 30088
- thermal_status 0 -> 1; battery 35.0 -> 35.0 C; charging False (recorded)

## Reading
- At the real threshold, runs 1 and 2 stayed mostly in POSITIONING (312 and 323 of 330 frames), so few frames reached the relative hook. Run 3 stayed in PASSIVE (297 frames) and logged no blur reason.
- In the flip run (2) full_norm p50 fell by a ratio of about 1.96, but blur_relative is 0. The gate did NOT fire in that run. A likely cause is too few OK frames to build a baseline, which is an inference, not seen.
- So the 10.0-threshold result (gate fires on a flip) does not carry over to the real threshold on this phone, from one flip run. This is a limit of the evidence, not a gate accuracy figure.

## Files
- camera_session_relative_blur_sessreal100_1792875945392.json
- camera_session_relative_blur_sessreal100_1792875972726.json
- camera_session_relative_blur_sessreal100_1792876005523.json
