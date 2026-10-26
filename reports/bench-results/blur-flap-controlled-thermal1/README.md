Controlled flap defocus vs live camera blur candidates, 2026-10-24 (Nothing A001), 42 runs
Device: Nothing A001 (MT6878, Android 15), bench app io.oxalpha.liveness.bench, release build. Same CameraBlurCandidatesBenchmark as camera-blur-candidates-thermal1, 30 warmup + 300 measured frames per run, delivered 720x720 rot270, real composite detector. Battery temperature is not recorded in these JSONs (Not verified). thermal_status before and after is listed per run.
Defocus was produced with a hand-made flap over the lens. Labels come from the button pressed; flap state was reported by the owner only where marked. Not an accuracy result.

GROUPS
G1 | 12 runs | weak flap attempt 1 (flap state not recorded, no measurable defocus)
G2 | 5 runs | weak flap attempt 2 (flap state not recorded, no measurable defocus)
G3 | 4 runs | stronger flap attempt (flap state not recorded, no measurable defocus)
G4 | 2 runs | snapshot pair 1, both flap OFF (owner confirmed)
G5 | 2 runs | snapshot pair 2, flap off then on (assumed from data)
G6 | 6 runs | scene A, 3x sharp/blur, flap assumed alternating off/on (Not verified)
G7 | 6 runs | scene B (window background), 3x sharp/blur, flap assumed alternating off/on (Not verified)
G8 | 5 runs | extra runs, flap OFF throughout (owner confirmed), one button-defocused run is really sharp

PER-RUN RESULTS (medians over one-face frames)
group | file ts | button label | thermal before/after | one/none/multi faces | crop_ratio p50 | full_ratio p50 | full_norm p50 | Y brightness p50
G1 | 1792851158319 | cand_sharp_textured | 1/1 | 237/63/0 | 0.470 | 0.521 | 0.0590 | 100.3
G1 | 1792851198349 | cand_defocused | 0/1 | 299/1/0 | 0.465 | 0.532 | 0.0680 | 97.6
G1 | 1792851232331 | cand_sharp_textured | 1/1 | 300/0/0 | 0.440 | 0.561 | 0.1300 | 105.9
G1 | 1792851269133 | cand_defocused | 1/1 | 298/0/2 | 0.443 | 0.554 | 0.1200 | 112.2
G1 | 1792851299678 | cand_sharp_textured | 1/3 | 300/0/0 | 0.458 | 0.565 | 0.1180 | 106.0
G1 | 1792851332847 | cand_defocused | 3/3 | 297/0/3 | 0.463 | 0.564 | 0.1150 | 108.1
G1 | 1792851362254 | cand_sharp_textured | 3/3 | 216/0/84 | 0.447 | 0.565 | 0.1270 | 110.4
G1 | 1792851392414 | cand_defocused | 3/3 | 300/0/0 | 0.459 | 0.559 | 0.1270 | 109.4
G1 | 1792851420700 | cand_sharp_textured | 3/3 | 300/0/0 | 0.443 | 0.550 | 0.1160 | 113.4
G1 | 1792851449658 | cand_defocused | 3/3 | 300/0/0 | 0.443 | 0.546 | 0.1130 | 113.0
G1 | 1792851485434 | cand_sharp_textured | 3/3 | 300/0/0 | 0.425 | 0.530 | 0.1050 | 114.4
G1 | 1792851521711 | cand_defocused | 3/3 | 300/0/0 | 0.452 | 0.536 | 0.1010 | 115.5
G2 | 1792851831219 | cand_sharp_textured | 3/3 | 300/0/0 | 0.465 | 0.567 | 0.1410 | 109.7
G2 | 1792851860051 | cand_defocused | 3/3 | 300/0/0 | 0.447 | 0.557 | 0.1430 | 110.7
G2 | 1792851888054 | cand_sharp_textured | 3/3 | 300/0/0 | 0.457 | 0.559 | 0.1440 | 110.1
G2 | 1792851916236 | cand_bright_light | 3/3 | 300/0/0 | 0.410 | 0.562 | 0.1445 | 109.8
G2 | 1792851945532 | cand_defocused | 3/3 | 300/0/0 | 0.455 | 0.568 | 0.1580 | 107.0
G3 | 1792852144276 | cand_sharp_textured | 3/3 | 300/0/0 | 0.477 | 0.526 | 0.1110 | 99.4
G3 | 1792852177069 | cand_defocused | 3/3 | 300/0/0 | 0.474 | 0.524 | 0.1060 | 99.4
G3 | 1792852206260 | cand_sharp_textured | 3/3 | 300/0/0 | 0.474 | 0.531 | 0.0980 | 90.4
G3 | 1792852237374 | cand_defocused | 3/3 | 300/0/0 | 0.472 | 0.537 | 0.0980 | 94.3
G4 | 1792853312897 | cand_sharp_textured | 1/1 | 300/0/0 | 0.407 | 0.539 | 0.0400 | 108.1
G4 | 1792853351964 | cand_defocused | 1/1 | 300/0/0 | 0.447 | 0.543 | 0.0480 | 103.9
G5 | 1792853567297 | cand_sharp_textured | 0/0 | 300/0/0 | 0.365 | 0.502 | 0.0390 | 101.2
G5 | 1792853608185 | cand_defocused | 1/1 | 300/0/0 | 0.283 | 0.430 | 0.0420 | 94.4
G6 | 1792853772384 | cand_sharp_textured | 1/1 | 258/42/0 | 0.363 | 0.508 | 0.0460 | 94.7
G6 | 1792853878200 | cand_defocused | 1/1 | 300/0/0 | 0.296 | 0.402 | 0.0200 | 91.6
G6 | 1792853907823 | cand_sharp_textured | 1/1 | 300/0/0 | 0.361 | 0.496 | 0.0680 | 97.0
G6 | 1792853939432 | cand_defocused | 1/1 | 300/0/0 | 0.305 | 0.393 | 0.0160 | 101.1
G6 | 1792853968756 | cand_sharp_textured | 1/1 | 300/0/0 | 0.369 | 0.501 | 0.0720 | 103.6
G6 | 1792854005707 | cand_defocused | 1/1 | 300/0/0 | 0.279 | 0.354 | 0.0130 | 105.4
G7 | 1792854109328 | cand_sharp_textured | 2/2 | 300/0/0 | 0.469 | 0.457 | 0.0970 | 114.3
G7 | 1792854139571 | cand_defocused | 2/2 | 284/16/0 | 0.411 | 0.276 | 0.0130 | 125.6
G7 | 1792854191355 | cand_sharp_textured | 2/2 | 300/0/0 | 0.445 | 0.471 | 0.1070 | 112.1
G7 | 1792854221600 | cand_defocused | 2/2 | 300/0/0 | 0.394 | 0.312 | 0.0180 | 121.2
G7 | 1792854251941 | cand_sharp_textured | 2/2 | 300/0/0 | 0.470 | 0.493 | 0.1230 | 108.0
G7 | 1792854281204 | cand_defocused | 2/2 | 300/0/0 | 0.402 | 0.325 | 0.0280 | 114.1
G8 | 1792854451523 | cand_bright_light | 2/2 | 300/0/0 | 0.488 | 0.506 | 0.1620 | 106.3
G8 | 1792854480165 | cand_plain_wall | 2/2 | 291/0/9 | 0.427 | 0.496 | 0.1480 | 100.5
G8 | 1792854511271 | cand_bright_light | 2/2 | 294/4/2 | 0.405 | 0.477 | 0.2210 | 97.8
G8 | 1792854542388 | cand_defocused | 2/2 | 300/0/0 | 0.397 | 0.497 | 0.2070 | 97.6
G8 | 1792854572077 | cand_plain_wall | 2/2 | 300/0/0 | 0.395 | 0.504 | 0.2040 | 98.9

FINDINGS
G1 to G3 (21 runs): crop_ratio p50 0.410-0.477 for both button labels, no separation. No measurable defocus reached the captured frames; whether the flap was on is not recorded.
G4 (flap off both): p50 0.407 and 0.447 on two unblurred runs, so run-to-run level noise at an identical setup is about 0.04. The run labelled defocused was a sharp run.
G5: flap off 0.365, flap on 0.283 (crop_ratio p50, 300 frames each). Stored frames score within 0.01 of the run medians. grad_p99 14.2 vs 9.0, lap_var 28.1 vs 17.7 on the face crop. First valid defocus pair.
G6 scene A: sharp p50 0.363 0.361 0.369, blur p50 0.296 0.305 0.279, median gap 0.067. G7 scene B (window background): sharp p50 0.469 0.445 0.470, blur p50 0.411 0.394 0.402, median gap 0.067. In each scene every sharp run median is above every blur run median.
Across scenes the levels overlap: scene B blur (0.394-0.411) sits above scene A sharp (0.361-0.369). No single crop_ratio threshold separates both scenes, so the level is scene dependent by about 0.1, larger than the defocus effect.
Scene B Y brightness p50 108-126 against scene A 92-105. Scene B was not dim by this measure; a dim room was not tested.
Whole-frame full_norm (Laplacian variance / (mean^2 + 1)): blur-labelled runs in G6 and G7 (flap assumed on, Not verified) max 0.0280; unblurred runs in G6, G7, G8 min 0.0460, max 0.2210. No crossing in these runs, margin about 1.6x. It also varies about 4x between sharp runs across lighting, so it measures scene texture as much as blur.
full_ratio (whole frame) did not carry over: the earlier camera-blur-candidates-thermal1 set 2 blurred runs score 0.472-0.487, above this folder blur runs. Do not use full_ratio.
Gaussian sweep on a stored live frame (G4 sharp run, face crop, 112x112): crop_ratio 0.452 at sigma 0, 0.408 at 1, 0.300 at 2, 0.224 at 3, 0.175 at 4. The metric falls monotonically with blur; flap defocus in G5 to G7 matches about sigma 2 to 3.
Face loss: G6 first sharp run lost the face on 42 of 300 frames; G7 first blur run on 16 of 300; other runs 0 to 4. A gate needs a no-face rule.

NO GATE CHOSEN. No threshold was fitted. The data supports a relative or windowed rule per session at most; absolute thresholds fail across scenes.

NOT RUN: dim room, flap off (the full_norm floor of about 0.035 is untested in dim light).
NOT VERIFIED: flap state per run in G1 to G3, G6 and G7 (assumed alternating off/on in G6 and G7, supported by the data only); scene A description; other phones; other distances; flap strengths other than the one used; real defocus from motion or hand shake; face-region blur with a sharp background (whole-frame metrics miss it); accuracy on attacks; second flap material.
Code: bench-app snapshot (post-rotation frame PNG and bbox overlay at the median frame) is on code main, commit f47e82a via PR #17. Stored PNGs are not committed here.
