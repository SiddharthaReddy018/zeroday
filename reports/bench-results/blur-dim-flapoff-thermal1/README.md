Dim room, flap OFF, live camera blur candidates, 2026-10-24 (Nothing A001), 4 runs
Device: Nothing A001 (MT6878, Android 15), bench app io.oxalpha.liveness.bench, release build. Same CameraBlurCandidatesBenchmark as blur-flap-controlled-thermal1, 30 warmup + 300 measured frames per run, delivered 720x720 rot270, real composite detector.
Conditions: room lights low, flap off for all 4 runs, buttons 4, 6, 4, 6 (labels sharp_textured, bright_light, sharp_textured, bright_light; the button labels do not describe the lighting). Before the runs adb showed thermal status 1, battery 34.0 C, level 80, USB powered: true. Charging during the runs is not recorded in the JSONs (Not verified). Planned setup: face at 50 cm, phone fixed (distance not measured, Not verified).

PER-RUN RESULTS (medians over 300 one-face frames, 0 no-face, 0 multi-face in every run)
file ts | button label | thermal before/after | crop_ratio p50 | full_ratio p50 | full_norm p50 | Y brightness p50
1792855959952 | cand_sharp_textured | 0/0 | 0.386 | 0.496 | 0.2470 | 13.3
1792855987247 | cand_bright_light | 0/0 | 0.427 | 0.502 | 0.2500 | 12.7
1792856013984 | cand_sharp_textured | 0/1 | 0.442 | 0.503 | 0.2780 | 12.3
1792856043218 | cand_bright_light | 1/1 | 0.445 | 0.502 | 0.2600 | 11.5

FINDINGS
Y brightness p50 11.5-13.3 against 92-126 in blur-flap-controlled-thermal1. The detector kept one face in 1200/1200 frames.
crop_ratio p50 0.386-0.445, inside the range of the lit sharp runs (0.36-0.49); it does not drop in the dark.
Whole-frame full_norm 0.2470-0.2780, above the 0.035 floor and the highest of the 2026-10-24 session (lit unblurred runs 0.046-0.221). At this brightness the Laplacian variance is probably dominated by sensor noise while the denominator (mean^2 + 1) is small, so a high value here is not evidence of sharpness. Not verified.
So dim light does not push a sharp run under the floor, but this folder does not show that the floor can detect blur in the dark.

NO GATE CHOSEN. No threshold was fitted.
NOT RUN: dim room with the flap ON (blurred in the dark); dim room unplugged; other phones; other distances.
NOT VERIFIED: charging state during the runs; exact lighting level (only Y brightness is recorded); sensor noise as the cause of high full_norm.
