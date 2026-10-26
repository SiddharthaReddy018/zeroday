# Real LivenessSession with the relative blur gate on (live camera): 2 controls, 2 flips

Job: CameraSessionRelativeBlurBenchmark. Real LivenessSession, real PAD model (MiniFASNetV2-SE-int8), real composite detector (YuNet + ML Kit), live front camera, relativeBlurEnabled(true). Nothing A001, MT6878, Android 15, release build, one phone. 330 frames per run, all fed to the session.
Gate constants: window 30, drop_factor 2.0, y_shift_factor 1.5 (candidates, NOT fitted).

## Limits (read first)
- blurMinVariance was LOWERED to 10.0 (blur_gate_override in each JSON) so live frames reach the relative hook. The QualityGate is NOT representative.
- minQualifyingFrames is huge, so no PASS or failure verdict can fire. The runs show only whether the relative gate fires inside a real session. They say nothing about final PASS or attack detection.
- Roles are USER-STATED, not in the JSONs: A and C flap OFF all run; B and D flap put on at about 12 s on the user's stopwatch, the two flip runs within about 0.5 s of each other (approximate; the flip frame is not recorded).
- All four files carry condition_label sessflapoff. The code passes sessflip for the flip button (MainActivity), and no file is named sessflip, so by file name all four runs came from the flap-OFF button. B and D were therefore flips done by hand on that button. Inferred from file names; that the flip button was never tapped is Not verified.
- The session JSONs contain NO per-frame Y (brightness), so a light shift cannot be checked here. light_unsteady count is 0 in all four.
- One phone, one light, two flip runs. Not a repeatability or accuracy claim. Latency was not measured in this job.
- Not verified: hand shake or motion blur, other phones, attacks, UI mapping of the reason strings.

## Per run

### Run A: camera_session_relative_blur_sessflapoff_1792874832768.json
- role: control (user-stated: flap OFF whole run)
- state counts: POSITIONING 29, PASSIVE 301; run-length (P positioning, A passive): P29 A301
- first POSITIONING after PASSIVE at index: -1
- audit trail counts: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 0, quality_ok 1
- full_norm p50 frames 0-99 0.0286, 100-129 0.0295, 140-199 0.0311, 250-329 0.0320; ratio 0-99 / 250-329 = 0.89
- adapter_dropped_frames 445, wall_clock_ms 31857
- thermal_status before 0, after 0; battery temp 35.0 -> 35.0 C; charging False before and after (recorded in the JSON)

### Run B: camera_session_relative_blur_sessflapoff_1792874861480.json
- role: flip (user-stated: flap put on at about 12 s on the stopwatch, within about 0.5 s of the moment in run D; approximate)
- state counts: POSITIONING 199, PASSIVE 131; run-length (P positioning, A passive): P29 A121 P68 A10 P102
- first POSITIONING after PASSIVE at index: 150
- audit trail counts: blur_relative 2, light_unsteady 0, relative_blur_warmup 0, quality_dropped 2, quality_ok 2
- full_norm p50 frames 0-99 0.0314, 100-129 0.0352, 140-199 0.0114, 250-329 0.0134; ratio 0-99 / 250-329 = 2.34
- adapter_dropped_frames 332, wall_clock_ms 27223
- thermal_status before 0, after 1; battery temp 35.0 -> 36.0 C; charging False before and after (recorded in the JSON)

### Run C: camera_session_relative_blur_sessflapoff_1792875176471.json
- role: control (user-stated: flap OFF whole run)
- state counts: POSITIONING 29, PASSIVE 301; run-length (P positioning, A passive): P29 A301
- first POSITIONING after PASSIVE at index: -1
- audit trail counts: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 0, quality_ok 1
- full_norm p50 frames 0-99 0.0244, 100-129 0.0267, 140-199 0.0266, 250-329 0.0295; ratio 0-99 / 250-329 = 0.83
- adapter_dropped_frames 368, wall_clock_ms 28651
- thermal_status before 1, after 1; battery temp 35.0 -> 35.0 C; charging False before and after (recorded in the JSON)

### Run D: camera_session_relative_blur_sessflapoff_1792875203403.json
- role: flip (user-stated: flap put on at about 12 s on the stopwatch; approximate)
- state counts: POSITIONING 214, PASSIVE 116; run-length (P positioning, A passive): P29 A116 P185
- first POSITIONING after PASSIVE at index: 145
- audit trail counts: blur_relative 1, light_unsteady 0, relative_blur_warmup 0, quality_dropped 1, quality_ok 1
- full_norm p50 frames 0-99 0.0287, 100-129 0.0275, 140-199 0.0118, 250-329 0.0126; ratio 0-99 / 250-329 = 2.28
- adapter_dropped_frames 298, wall_clock_ms 25841
- thermal_status before 1, after 1; battery temp 35.0 -> 35.0 C; charging False before and after (recorded in the JSON)

## Reading
- In both controls the session stays in PASSIVE after the first 29 frames, the audit trail has no blur_relative entry, and full_norm rises slowly.
- In both flip runs full_norm falls by a ratio above 2.0, the trail carries guide.quality.blur_relative, and the session returns to POSITIONING. In D it stays there; in B it returned to PASSIVE for a short stretch and dropped again. This matches the documented no-reset hole (the baseline stays at the pre-drop level) but is not absolute. Two flip runs, one phone.
- First POSITIONING after PASSIVE is at index 150 (B) and 145 (D). The gate reacts after the change, so these indices do not give the flip frame.

## Files
- camera_session_relative_blur_sessflapoff_1792874832768.json
- camera_session_relative_blur_sessflapoff_1792874861480.json
- camera_session_relative_blur_sessflapoff_1792875176471.json
- camera_session_relative_blur_sessflapoff_1792875203403.json
