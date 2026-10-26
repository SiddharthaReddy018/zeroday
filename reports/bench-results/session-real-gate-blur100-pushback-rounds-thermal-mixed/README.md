# Real LivenessSession, relative gate on, REAL blurMinVariance 100: push-back rounds (round 4 plus 4 distance runs, 8 runs)

Same job and build as session-real-gate-blur100-strongflip-rounds-thermal1-1 (independent per-frame QualityGate probe, relativeBlurEnabled(true), window 30, drop 2.0, y shift 1.5, candidates NOT fitted). Nothing A001, release build, one phone, 330 frames per run. Thermal differs between runs (see per run), so runs are not thermally comparable.

## Limits (read first)
- Round 4 (4 runs): blur method USER-STATED: phone pushed back about 5.5 to 6 cm, estimated from a 3-key span on the Mac keyboard, NOT ruler-measured. Starting distance not recorded. Roles (control, then 3 mild) are INFERRED from run order, not confirmed.
- Distance runs (4 runs, labeled A to D by filename order): the user reported running them. Starting distance, which run was the control, the push-back distance per run, push-back timing and light are NOT stated. Roles NOT assigned. Nothing here is attributed to any distance.
- The probe is an independent QualityGate evaluation, not the audit trail. Which gate rejected each frame is Not verified.
- Frame-to-time mapping is Not verified (adapter dropped many frames); positions are frame indices, not seconds.
- Window size 30 for the medians below is the document author choice, matching relative_blur_window.
- Excluded, raw only, not documented: round 3 (files 1792878334714..401399: face lost on all 330 frames in 3 runs, control baseline about 3x different), old unpulled BlurWindowGate files (2 of 4 labels contradict results). Three phone files created 03:40-03:41 (1792879844261, 1792879877584, 1792879913739) were not pulled into this folder; whether they belong to these rounds is Not verified.
- No verdict can fire (minQualifyingFrames huge). One phone, one session of lighting. Eight runs are not a detection rate.

## Summary
- blur_relative fired in 0 of 8 runs (asserted when this README was generated). No false fire and no detection.
- Round 4 (about 5.5 to 6 cm push-back, user-stated estimate): no onset seen, see per-run window medians. Across all 8 runs the smallest window-to-window full_norm ratio is 0.71, in distance run A (camera_session_relative_blur_sessreal100_1792879968983.json); a ratio of 0.50 or lower would be a 2x drop between 30-frame window medians.
- Distance runs C and D show a short dip in per-frame values (sampled every 5 frames, not gate medians). C: frame 65 lap 208.0 fn 0.0323; frame 70 lap 65.4 fn 0.0165; frame 75 lap 88.6 fn 0.0127; frame 80 lap 115.3 fn 0.0437. D: frame 55 lap 161.6 fn 0.0288; frame 60 lap 45.3 fn 0.0180; frame 65 lap 93.8 fn 0.0175; frame 70 lap 84.7 fn 0.0351.
- In both dips Laplacian was below 100 in the same sampled frames as the full_norm drop (C: pre 0.0323 to low 0.0127, 2.54x; D: pre 0.0288 to low 0.0153 (the minimum over frames 60-69, a frame not in the 5-frame sample above), 1.88x; per-frame, not window medians). So the central question (full_norm down 2x or more while Laplacian stays at or above 100 and the face is detected) is STILL UNTESTED.
- After the dip full_norm settled HIGHER, not lower: C median frames 100-329 0.0446 vs frames 0-59 0.0307; D 0.0566 vs 0.0271. Cause Not verified (framing change, phone ending closer or tilted, autofocus: none confirmed).
- The early frames of several runs show a rising Laplacian (window 0 below window 1). Treated as a start-up transient; cause Not verified.

## Per run

### round 4, control (inferred): camera_session_relative_blur_sessreal100_1792879179237.json
- probe reasons: guide.face.none 5, guide.quality.blur 3, pass 322
- states: POSITIONING 37, PASSIVE 293
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 0, quality_ok 1
- Laplacian: median 204.75, first window (0-29) 173.80, last window (300-329) 219.90; frames below 100: 8; min 29.1 at frame 5
- full_norm median: 0.0314, first window 0.0278, last window 0.0328; min 0.0244 at frame 28
- smallest window-to-window full_norm median ratio (30-frame windows): 0.96 (window 0 -> 1)
- window Laplacian medians: 174 207 211 201 201 208 203 186 207 218 220
- window full_norm medians: 0.0278 0.0268 0.0301 0.0304 0.0312 0.0319 0.0312 0.0314 0.0327 0.0323 0.0328
- thermal_status 0 -> 0, adapter_dropped_frames 558, wall_clock_ms 30307

### round 4, mild push-back (inferred): camera_session_relative_blur_sessreal100_1792879222411.json
- probe reasons: pass 317, guide.quality.blur 13
- states: POSITIONING 42, PASSIVE 288
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 3, quality_ok 4
- Laplacian: median 191.85, first window (0-29) 166.40, last window (300-329) 205.65; frames below 100: 13; min 56.1 at frame 54
- full_norm median: 0.0336, first window 0.0329, last window 0.0320; min 0.0138 at frame 56
- smallest window-to-window full_norm median ratio (30-frame windows): 0.88 (window 1 -> 2)
- window Laplacian medians: 166 180 150 187 193 195 204 201 208 194 206
- window full_norm medians: 0.0329 0.0338 0.0298 0.0329 0.0346 0.0342 0.0340 0.0347 0.0345 0.0333 0.0320
- thermal_status 1 -> 1, adapter_dropped_frames 548, wall_clock_ms 29956

### round 4, mild push-back (inferred): camera_session_relative_blur_sessreal100_1792879254882.json
- probe reasons: pass 323, guide.quality.blur 7
- states: POSITIONING 36, PASSIVE 294
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 1, quality_ok 2
- Laplacian: median 184.10, first window (0-29) 186.95, last window (300-329) 188.85; frames below 100: 7; min 62.0 at frame 68
- full_norm median: 0.0333, first window 0.0359, last window 0.0335; min 0.0200 at frame 70
- smallest window-to-window full_norm median ratio (30-frame windows): 0.89 (window 1 -> 2)
- window Laplacian medians: 187 225 153 169 172 180 190 184 186 187 189
- window full_norm medians: 0.0359 0.0358 0.0319 0.0331 0.0353 0.0333 0.0329 0.0347 0.0309 0.0314 0.0335
- thermal_status 1 -> 1, adapter_dropped_frames 537, wall_clock_ms 30018

### round 4, mild push-back (inferred): camera_session_relative_blur_sessreal100_1792879287948.json
- probe reasons: pass 325, guide.quality.blur 5
- states: POSITIONING 34, PASSIVE 296
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 1, quality_ok 2
- Laplacian: median 182.90, first window (0-29) 181.35, last window (300-329) 183.90; frames below 100: 5; min 54.4 at frame 44
- full_norm median: 0.0322, first window 0.0357, last window 0.0335; min 0.0193 at frame 45
- smallest window-to-window full_norm median ratio (30-frame windows): 0.92 (window 0 -> 1)
- window Laplacian medians: 181 174 176 185 183 187 188 182 186 180 184
- window full_norm medians: 0.0357 0.0328 0.0302 0.0321 0.0336 0.0323 0.0321 0.0325 0.0316 0.0319 0.0335
- thermal_status 1 -> 1, adapter_dropped_frames 590, wall_clock_ms 31353

### distance run A, role NOT stated: camera_session_relative_blur_sessreal100_1792879968983.json
- probe reasons: guide.face.none 12, guide.quality.blur 2, pass 316
- states: POSITIONING 43, PASSIVE 287
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 0, quality_ok 1
- Laplacian: median 209.35, first window (0-29) 113.15, last window (300-329) 212.35; frames below 100: 14; min 41.9 at frame 4
- full_norm median: 0.0330, first window 0.0408, last window 0.0352; min 0.0281 at frame 33
- smallest window-to-window full_norm median ratio (30-frame windows): 0.71 (window 0 -> 1)
- window Laplacian medians: 113 148 208 218 225 227 221 204 202 216 212
- window full_norm medians: 0.0408 0.0291 0.0318 0.0329 0.0341 0.0333 0.0324 0.0309 0.0324 0.0342 0.0352
- thermal_status 1 -> 1, adapter_dropped_frames 464, wall_clock_ms 31807

### distance run B, role NOT stated: camera_session_relative_blur_sessreal100_1792880002416.json
- probe reasons: pass 322, guide.quality.blur 8
- states: POSITIONING 37, PASSIVE 293
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 1, quality_ok 2
- Laplacian: median 172.15, first window (0-29) 219.15, last window (300-329) 172.00; frames below 100: 8; min 45.4 at frame 52
- full_norm median: 0.0353, first window 0.0355, last window 0.0357; min 0.0165 at frame 57
- smallest window-to-window full_norm median ratio (30-frame windows): 0.95 (window 7 -> 8)
- window Laplacian medians: 219 214 180 166 164 165 162 172 172 176 172
- window full_norm medians: 0.0355 0.0350 0.0349 0.0361 0.0352 0.0348 0.0351 0.0369 0.0349 0.0341 0.0357
- thermal_status 1 -> 1, adapter_dropped_frames 563, wall_clock_ms 31552

### distance run C, role NOT stated: camera_session_relative_blur_sessreal100_1792880035823.json
- probe reasons: pass 320, guide.quality.blur 10
- states: POSITIONING 39, PASSIVE 291
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 4, quality_ok 5
- Laplacian: median 215.40, first window (0-29) 173.10, last window (300-329) 260.45; frames below 100: 10; min 63.3 at frame 69
- full_norm median: 0.0441, first window 0.0308, last window 0.0466; min 0.0127 at frame 75
- smallest window-to-window full_norm median ratio (30-frame windows): 0.96 (window 4 -> 5)
- window Laplacian medians: 173 214 122 230 220 214 219 214 213 217 260
- window full_norm medians: 0.0308 0.0305 0.0326 0.0471 0.0458 0.0439 0.0439 0.0437 0.0436 0.0449 0.0466
- thermal_status 1 -> 2, adapter_dropped_frames 549, wall_clock_ms 31118

### distance run D, role NOT stated: camera_session_relative_blur_sessreal100_1792880070249.json
- probe reasons: pass 298, guide.quality.blur 32
- states: POSITIONING 61, PASSIVE 269
- audit trail: blur_relative 0, light_unsteady 0, relative_blur_warmup 0, quality_dropped 13, quality_ok 14
- Laplacian: median 158.05, first window (0-29) 118.35, last window (300-329) 171.55; frames below 100: 32; min 45.3 at frame 60
- full_norm median: 0.0552, first window 0.0255, last window 0.0538; min 0.0097 at frame 73
- smallest window-to-window full_norm median ratio (30-frame windows): 0.90 (window 4 -> 5)
- window Laplacian medians: 118 164 94 141 165 148 164 164 162 171 172
- window full_norm medians: 0.0255 0.0284 0.0365 0.0624 0.0625 0.0561 0.0578 0.0577 0.0556 0.0546 0.0538
- thermal_status 2 -> 2, adapter_dropped_frames 551, wall_clock_ms 30693

## Files
- camera_session_relative_blur_sessreal100_1792879179237.json
- camera_session_relative_blur_sessreal100_1792879222411.json
- camera_session_relative_blur_sessreal100_1792879254882.json
- camera_session_relative_blur_sessreal100_1792879287948.json
- camera_session_relative_blur_sessreal100_1792879968983.json
- camera_session_relative_blur_sessreal100_1792880002416.json
- camera_session_relative_blur_sessreal100_1792880035823.json
- camera_session_relative_blur_sessreal100_1792880070249.json
