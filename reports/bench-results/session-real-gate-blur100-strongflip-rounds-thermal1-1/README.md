# Real LivenessSession, relative gate on, REAL blurMinVariance 100: strong-blur flips, rounds 1 and 2 (8 runs)

Same job and build as session-real-gate-blur100-probe-thermal1-1 (independent per-frame QualityGate probe, relativeBlurEnabled(true), window 30, drop 2.0, y shift 1.5, candidates NOT fitted). Nothing A001, release build, one phone, 330 frames per run, thermal_status 1 -> 1 in all runs.

## Limits (read first)
- Roles are NOT user-stated for these runs. They are inferred from run order (control then 3 blur runs per round) and are consistent with the per-frame CSVs. Not confirmed.
- Blur method NOT recorded. These were intended as MILD blur; the data shows they were STRONG: Laplacian fell to about 45-100 and full_norm fell about 2.5x. The question of whether blur that keeps Laplacian at or above 100 and keeps the face detected triggers the relative gate is still untested.
- The probe is an independent QualityGate evaluation, not the audit trail. Which gate rejected each frame is Not verified.
- Why round 1 fired and round 2 did not is Not verified. Round 2 flips lost the face on 151-175 frames (guide.face.none).
- No verdict can fire (minQualifyingFrames huge). One phone, one light. 2 of 6 flips is not a detection rate.
- A round 3 (4 runs, files 1792878334714..401399) is excluded: face lost on all 330 frames in 3 runs and a control baseline about 3x different. Raw only, not documented.

## Summary
- Flips (6): blur_relative fired in 2 (round 1, runs 3 and 4), 0 in round 2.
- Controls (2): blur_relative 0 in both. No false fire.

## Per run

### round 1, control (inferred): camera_session_relative_blur_sessreal100_1792877896319.json
- probe reasons: pass 303, guide.quality.blur 27
- states: POSITIONING 56, PASSIVE 274
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 4, quality_ok 5
- Laplacian: median 158.80, first window (0-29) 107.55, last window (300-329) 179.75; frames below 100: 27
- full_norm median: first window 0.0196, last window 0.0330
- adapter_dropped_frames 528, wall_clock_ms 29994

### round 1, flip (inferred): camera_session_relative_blur_sessreal100_1792877925372.json
- probe reasons: pass 127, guide.quality.blur 190, guide.face.none 13
- states: POSITIONING 238, PASSIVE 92
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 9, quality_ok 9
- Laplacian: median 92.80, first window (0-29) 187.05, last window (300-329) 97.80; frames below 100: 202
- full_norm median: first window 0.0338, last window 0.0132
- adapter_dropped_frames 441, wall_clock_ms 26365

### round 1, flip (inferred): camera_session_relative_blur_sessreal100_1792877955201.json
- probe reasons: pass 171, guide.face.none 3, guide.quality.blur 156
- states: POSITIONING 208, PASSIVE 122
- audit trail: blur_relative 1, light_unsteady 0, quality_dropped 10, quality_ok 10
- Laplacian: median 102.35, first window (0-29) 224.90, last window (300-329) 73.05; frames below 100: 157
- full_norm median: first window 0.0364, last window 0.0112
- adapter_dropped_frames 343, wall_clock_ms 27078

### round 1, flip (inferred): camera_session_relative_blur_sessreal100_1792877986039.json
- probe reasons: pass 171, guide.face.none 1, guide.quality.blur 158
- states: POSITIONING 214, PASSIVE 116
- audit trail: blur_relative 1, light_unsteady 0, quality_dropped 8, quality_ok 8
- Laplacian: median 101.45, first window (0-29) 205.40, last window (300-329) 87.25; frames below 100: 159
- full_norm median: first window 0.0386, last window 0.0134
- adapter_dropped_frames 318, wall_clock_ms 26699

### round 2, control (inferred): camera_session_relative_blur_sessreal100_1792878126245.json
- probe reasons: pass 311, guide.quality.blur 16, guide.face.none 3
- states: POSITIONING 48, PASSIVE 282
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 1, quality_ok 2
- Laplacian: median 192.85, first window (0-29) 96.25, last window (300-329) 183.70; frames below 100: 18
- full_norm median: first window 0.0168, last window 0.0311
- adapter_dropped_frames 531, wall_clock_ms 29531

### round 2, flip (inferred): camera_session_relative_blur_sessreal100_1792878151783.json
- probe reasons: pass 149, guide.face.none 175, guide.quality.blur 6
- states: POSITIONING 210, PASSIVE 120
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 1, quality_ok 1
- Laplacian: median 55.85, first window (0-29) 168.55, last window (300-329) 51.30; frames below 100: 181
- full_norm median: first window 0.0295, last window 0.0132
- adapter_dropped_frames 380, wall_clock_ms 24334

### round 2, flip (inferred): camera_session_relative_blur_sessreal100_1792878179474.json
- probe reasons: pass 165, guide.face.none 151, guide.quality.blur 14
- states: POSITIONING 194, PASSIVE 136
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 1, quality_ok 1
- Laplacian: median 99.75, first window (0-29) 167.10, last window (300-329) 55.10; frames below 100: 165
- full_norm median: first window 0.0341, last window 0.0137
- adapter_dropped_frames 404, wall_clock_ms 25153

### round 2, flip (inferred): camera_session_relative_blur_sessreal100_1792878205583.json
- probe reasons: pass 134, guide.face.none 173, guide.quality.blur 23
- states: POSITIONING 225, PASSIVE 105
- audit trail: blur_relative 0, light_unsteady 0, quality_dropped 1, quality_ok 1
- Laplacian: median 63.70, first window (0-29) 157.20, last window (300-329) 58.20; frames below 100: 196
- full_norm median: first window 0.0343, last window 0.0132
- adapter_dropped_frames 373, wall_clock_ms 24109

## Files
- camera_session_relative_blur_sessreal100_1792877896319.json
- camera_session_relative_blur_sessreal100_1792877925372.json
- camera_session_relative_blur_sessreal100_1792877955201.json
- camera_session_relative_blur_sessreal100_1792877986039.json
- camera_session_relative_blur_sessreal100_1792878126245.json
- camera_session_relative_blur_sessreal100_1792878151783.json
- camera_session_relative_blur_sessreal100_1792878179474.json
- camera_session_relative_blur_sessreal100_1792878205583.json
