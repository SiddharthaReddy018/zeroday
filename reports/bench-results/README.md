# Phone benchmark results (bench-app)

Device: Nothing A001 (CMF Phone 2 Pro), MT6878, 8 cores, Android 15, not rooted, release build, ORT 1.16.3 CPU, BASIC_OPT, default intra-op threads.

Models (SHA-256):
- MiniFASNetV2-SE.onnx (FP32): af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6
- MiniFASNetV2-SE-int8.onnx (dynamic INT8): fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9

Files are named `<job>_<model>_<epoch-millis>.json`. Model .onnx files are never committed.

## 2026-10-23-run2-plugged-in
13 runs on the bench-app build with targetSdk 34 (4 INT8 lockstep, 4 FP32 lockstep, 5 FP32 paced). Every JSON reports `charging: true`, so these were taken while plugged in. Thermal status 1, battery temp 37-38 C. Each run is about 4-6 s.

Caveats for every file here: fixed-box detector (real detector cost NOT included), synthetic noise frames (latency only, not accuracy), SFace/signing/camera copy/UI not measured, one phone, no sustained-thermal claim. In paced runs the `per_frame_serial` and `max_sustainable_fps_by_mean` fields only echo the 30 fps producer; use PROCESS_TOTAL and lockstep figures.

## 2026-10-23-run3-unplugged
12 runs on the same build (4 INT8 lockstep, 4 FP32 lockstep, 4 FP32 paced), interleaved. Every JSON reports `charging: false`, thermal status 0 (1 at the end of the last paced run), battery temp 35-37 C, battery 71%. Each run is about 4-6 s. Airplane mode and Do Not Disturb are not recorded in the JSON; the owner reported them on.

## DEV_AGREEMENT_REPORT-2026-10-23.md
Output of `DevAgreementRunnerTest` (desktop JVM, DEV split only, FP32 vs INT8 at threshold 70). Not a phone result. Both model hashes are listed inside the report and match the hashes above.

## 2026-10-24-yunet-after-0002
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## composite-latency-thermal3
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## pipeline-composite-lockstep-thermal1
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## pipeline-rot270-hot-thermal3
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## pipeline-rot270-vs-640-superhot-thermal3
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## camera-blur-survey-thermal2
See the README inside this folder. (Index entry added 2026-10-24; this line makes no claim about its contents.)

## pipeline-rot270-cool-thermal0-1
6 runs (3 rot270 720x720, 3 640x480) of the composite pipeline, lockstep, thermal 0-1, battery 35-39 C, blur gate lowered to 10.0 (latency only). p95 within 200 ms in all 6. See its README.

## camera-blur-candidates-thermal1
16 live-camera runs comparing whole-frame Laplacian variance with face-crop candidates (re-blur ratio best). Labels partly contaminated, no gate chosen. Code on local branch feat/blur-candidates, not on main. See its README.

## blur-flap-controlled-thermal1
42 runs on 2026-10-24 with a hand-made flap defocus, plus controlled 3 sharp + 3 blur sets in two scenes. crop_ratio separates within a scene (gap 0.067), not across scenes. No gate chosen. Dim room not run. See the README inside this folder.

## blur-dim-flapoff-thermal1
4 flap-off runs in a dim room (Y brightness 12-13). full_norm stays above 0.035 but is likely noise-dominated; blur detection in the dark not shown. No gate chosen. See the README inside this folder.

## blur-dim-flapon-thermal0-1
4 flap-on runs in a dim room (user-stated; flap state not in the JSONs). full_norm p50 0.018-0.029 vs 0.247-0.278 flap-off dim; crop_ratio p50 0.434-0.454, no separation from flap-off dim. Confound: Y brightness 23-34 here vs 11.5-13.3 flap-off, so the room was not equally dark. No gate chosen. See the README inside this folder.

## blur-dim-flapoff-btn5-thermal0-1
3 flap-off runs (button 5, file label defocused, treated as sharp) in a dark room, Y p50 7-9, 300/300 one face each. full_norm p50 0.162-0.184, crop_ratio p50 0.489-0.499. Battery 38.0 C before. No gate chosen. See the README inside this folder.

## blur-flapoff-bright-thermal0-1
3 flap-off runs (button 4) in a brighter room, Y p50 81-83. full_norm p50 0.021-0.022 on sharp frames (same range as the flap-on dim runs), crop_ratio p50 0.429-0.443. Face counts: 300, 299 (1 multi), 281 (17 no-face, 2 multi). full_norm tracks brightness, so the 0.035 floor is not usable on current evidence. No gate chosen. See the README inside this folder.

## blur-flapoff-dark2-thermal1
3 flap-off runs (button 4, user-stated) minutes after the Y 81-83 set, but at Y p50 8.8-9.6 (light or exposure changed, cause Not verified). full_norm p50 0.061-0.062 (btn5 set at similar Y: 0.162-0.184), crop_ratio p50 0.482-0.488. Faces 286 (14 no-face), 300, 300. full_norm is not explained by brightness alone. No gate chosen. See the README inside this folder.

## blur-control-alt-thermal0-1
Matched control, 6 runs alternating flap OFF/ON (order user-stated) in one session. full_norm p50 OFF 0.185-0.196 vs ON 0.057-0.065 (about 3x, no overlap); crop_ratio p50 goes the wrong way (ON 0.512-0.534 vs OFF 0.496-0.506). Y p50 drifted 6.8-11.7; blur vs exposure effect Not verified. No absolute threshold usable. No gate chosen. See the README inside this folder.

## live-relative-gate-flip
6 live-camera runs of BlurWindowGate (window 30, drop 2.0, y shift 1.5; candidates, not fitted), 330 frames each: one flip run (flap OFF then ON, flip time not recorded), one flap-OFF control, four no-flip runs. Flip run reports blur, control does not. LivenessSession not run. One phone. Thermal not recorded in these JSONs. See the README inside this folder.

## fullnorm-latency-thermal1
2 runs of the fullFrameNorm microbenchmark (500 timed each): fullFrameNorm p50 1.223 / 1.221 ms vs toRgb p50 3.444 / 3.455 ms. Thermal status 1, battery 37.0 C, unplugged per JSON. No detector running. See the README inside this folder.

## session-relative-gate-thermal0-1
4 live-camera runs of a REAL LivenessSession with relativeBlurEnabled(true) (blurMinVariance lowered to 10.0, no verdict can fire, QualityGate not representative): 2 user-stated controls (no blur reason, stay PASSIVE), 2 user-stated flips (blur_relative in the audit trail, session returns to POSITIONING). Roles user-stated, flip time approximate, no per-frame Y recorded. One phone. See the README inside this folder.

## session-real-gate-blur100-thermal0-1
3 live-camera runs of a REAL LivenessSession with relativeBlurEnabled(true) at the REAL blurMinVariance 100 (user-stated: control, flip near 12 s, control). Runs 1 and 2 stayed mostly in POSITIONING, run 3 stayed PASSIVE; blur_relative 0 in all three, including the flip run. QualityGate failure reasons not recorded. One phone. See the README inside this folder.

## session-real-gate-blur100-probe-thermal1-1
3 live-camera runs of a REAL LivenessSession with relativeBlurEnabled(true) at the REAL blurMinVariance 100, plus an independent per-frame QualityGate probe (roles user-stated: control, flip near 12 s, control). Flip run: 174 of 330 frames failed the absolute blur gate (Laplacian below 100 from frame 153), blur_relative 0, so the relative gate was not exercised. Controls: 329 and 330 of 330 frames passed. Probe is not the audit trail. One phone. See the README inside this folder.

## session-real-gate-blur100-strongflip-rounds-thermal1-1
8 live-camera runs (2 rounds) of a REAL LivenessSession at the REAL blurMinVariance 100 with the independent QualityGate probe. Roles inferred from order, blur method not recorded; blur was strong, not mild. Flips: blur_relative fired in 2 of 6 (both round 1), 0 in round 2 where the face was often lost. Controls: 0 of 2 fired. Round 3 excluded. One phone. See the README inside this folder.
