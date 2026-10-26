Live front-camera blur CANDIDATE survey, 2026-10-24 (face crop vs whole frame)
Device: Nothing A001 (MT6878, Android 15), bench app io.oxalpha.liveness.bench, release build. thermal_status 0 or 1 before and after every run (set 1: 1/1 in all 10 runs; set 2: 0/0, 0/1, then 1/1 in four runs).
What: ImageAnalysis only, real composite detector (YuNet + ML Kit), per frame: old whole-frame stride-2 Laplacian variance (production lumaStats via LumaProbe)
and BlurCandidates on the detector face crop resized to 112x112: crop_lap112 (Laplacian variance), crop_norm (variance / (mean^2 + 1)),
crop_ratio (Crete-style re-blur ratio, 5-tap box blur, min of horizontal and vertical; higher = sharper). 30 warmup + 300 measured frames per run.
Request: 640x480, KEEP_ONLY_LATEST, YUV_420_888, front camera. Exactly one face in 300/300 frames in 14 of 16 runs; no crop was null.
Exceptions: set 2 defocused run 1792849559993 had 293 one-face + 7 no-face frames; set 2 sharp_textured run 1792849649582 had 297 one-face + 3 multi-face frames.
Crop metrics exist only for one-face frames (293 and 297 samples in those two runs). Losing the face only in a blurred run is one observation, not verified as a signal.
Set 1 = first batch (10 runs), set 2 = retest (6 runs). Labels come from the button pressed, not from a check of the image.
Code: BlurCandidates, CameraBlurCandidatesBenchmark, LumaProbe and the bench-app wiring are on local branch feat/blur-candidates
(commit ab383b0), NOT pushed and NOT on code main. Metrics are bench-only; nothing is wired into captureFrame or QualityGate.

LAYOUT CHECKS (seen)
Delivered shape in every frame of every run: 720x720 rot270 yRowStride768 uvRowStride768 uvPixelStride2. The 640x480 request was not honoured.
Chroma planes: uvRowStride 768, uvPixelStride 2, no U/V stride mismatch in any frame. The earlier assumption of rowStride 720 was wrong.
YUV->RGB layout: Y-plane brightness and RGB-derived mean luma differ by about 0.4 in every run, so the RGB buffer layout is consistent.

PER-RUN RESULTS (p5/p50/p95 over 300 frames)
set | file ts | button label | one/none/multi faces | judged by crop_ratio p50 (<0.40 = blurred) | flag | whole_lap | crop_lap112 | crop_norm | crop_ratio | Y brightness p50
1 | 1792848735935 | sharp_textured | 300/0/0 | sharp | - | 96.88/284/413.7 | 48.83/138.7/193.7 | 0.023/0.063/0.104 | 0.382/0.457/0.483 | 115.0
1 | 1792848764438 | sharp_textured | 300/0/0 | sharp | - | 254/384.8/481.8 | 95.26/117.1/140.3 | 0.053/0.065/0.076 | 0.449/0.462/0.476 | 112.1
1 | 1792848793370 | sharp_textured | 300/0/0 | sharp | - | 311.1/415.3/505.8 | 90.72/114.6/147.3 | 0.047/0.059/0.074 | 0.449/0.464/0.476 | 112.5
1 | 1792848903483 | defocused | 300/0/0 | blurred | - | 31/34.94/47.45 | 26.19/31.42/39.15 | 0.01/0.012/0.015 | 0.284/0.296/0.323 | 116.3
1 | 1792848930542 | defocused | 300/0/0 | sharp | CONTAMINATED | 66.94/264.9/441.9 | 38.33/120.5/189.2 | 0.019/0.05/0.08 | 0.336/0.415/0.459 | 105.2
1 | 1792848958875 | defocused | 300/0/0 | sharp | CONTAMINATED | 116.9/314.6/409.4 | 91.08/158.4/206 | 0.041/0.07/0.081 | 0.406/0.456/0.469 | 106.7
1 | 1792849028636 | bright_light | 300/0/0 | sharp | - | 92.26/240.2/438.7 | 55.86/101.5/161.8 | 0.029/0.052/0.074 | 0.37/0.42/0.453 | 111.9
1 | 1792849065092 | bright_light | 300/0/0 | sharp | - | 98.75/341.9/408.2 | 97.93/224/288.5 | 0.024/0.053/0.068 | 0.35/0.458/0.479 | 118.9
1 | 1792849093050 | bright_light | 300/0/0 | sharp | - | 92.06/300.1/414.8 | 83.14/207.4/268.4 | 0.02/0.047/0.061 | 0.356/0.454/0.473 | 116.8
1 | 1792849150262 | plain_wall | 300/0/0 | sharp | - | 68.61/143.9/165.3 | 153.7/231.6/319.4 | 0.034/0.056/0.072 | 0.422/0.469/0.483 | 113.3
2 | 1792849532431 | defocused | 300/0/0 | blurred | - | 61.43/123.3/171 | 31.18/47.66/59.93 | 0.008/0.012/0.014 | 0.31/0.345/0.358 | 120.4
2 | 1792849559993 | defocused | 293/7/0 | blurred | - | 93.94/145.3/185 | 22.44/41.78/70.74 | 0.007/0.011/0.018 | 0.305/0.35/0.364 | 119.5
2 | 1792849587630 | defocused | 300/0/0 | blurred | - | 136.3/168.2/204.8 | 38.04/66.15/76.94 | 0.01/0.017/0.02 | 0.343/0.36/0.367 | 119.4
2 | 1792849616370 | sharp_textured | 300/0/0 | blurred | CONTAMINATED | 118.6/149.3/185.9 | 50.22/68/77.86 | 0.012/0.017/0.019 | 0.341/0.355/0.364 | 120.5
2 | 1792849649582 | sharp_textured | 297/0/3 | sharp | - | 625.4/1455/1770 | 135.6/326.7/423.3 | 0.048/0.118/0.146 | 0.37/0.455/0.471 | 118.0
2 | 1792849677884 | sharp_textured | 300/0/0 | sharp | - | 1141/1540/1783 | 238.2/310.2/358 | 0.078/0.102/0.117 | 0.432/0.46/0.466 | 117.0

FINDINGS
Split at crop_ratio p50 0.40, descriptive only, not a threshold: 11 runs at or above (p50 0.415-0.469), 5 runs below (0.296-0.360). The medians fall in two groups with a gap of 0.055.
The at-or-above group includes bright_light, plain_wall and the two set 1 defocused runs flagged CONTAMINATED, so it is not the sharp_textured count.
Contaminated labels (flagged above): set 1 defocused runs 2 and 3 (not blurred), set 2 sharp_textured run 1 (blurred, probably the lens was still smeared;
not verified). How each condition was produced is not recorded.
crop_ratio is the most repeatable on sharp runs and is stable across sharp, bright_light and plain_wall-with-face runs. crop_norm separates the
medians too but varies about 2x between sharp runs (scene contrast). crop_lap112 varies widely across sharp-judged runs and tracks lighting.
Per-frame values overlap: sharp runs reach p5 ~0.38 and blurred runs reach p95 ~0.37, so a per-frame hard threshold would misclassify some frames.

POOLED AUC by button label, sharp_textured vs defocused (1.0 = perfect, 0.5 = none). LOWER BOUNDS: they include the contaminated runs.
  whole_lap    AUC=0.855 (n sharp=1800, n defocused=1800)
  crop_lap112  AUC=0.818 (n sharp=1797, n defocused=1793)
  crop_norm    AUC=0.831 (n sharp=1797, n defocused=1793)
  crop_ratio   AUC=0.834 (n sharp=1797, n defocused=1793)

NO GATE CHOSEN. No threshold was fitted. A usable gate likely needs smoothing over several frames or best-of-N; that is an open design decision.

NOT VERIFIED
A clean set (all labels correct) with repeats; second scene; other phones; accuracy; behaviour of crop_ratio vs face size, distance and skin tone;
camera/ImageProxy copy cost; the three bright_light runs were taken by button only (conditions not recorded).
