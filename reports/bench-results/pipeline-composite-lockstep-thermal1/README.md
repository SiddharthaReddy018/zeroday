Pipeline bench, end to end with REAL composite (YuNet + ML Kit contours) + MiniFASNetV2-SE INT8, 2026-10-24
Device: Nothing A001 (MT6878, Android 15), io.oxalpha.liveness.bench, release build.
Status: benchmark/candidate only. Latency-valid, NOT a gate-passing run. No accuracy validation.

LABELS
blur gate lowered 100.0 -> 10.0 (benchmark-only override, labelled in result)
rotation 0 (YUV->RGB cost not camera-representative)
lockstep (not paced); 48-frame pool; 640x480; frame = face_frame.png, 1 person, not committed
thermal_status 1 before and after; battery 38-39 C
pad_infer_calls 400 each run; dropped_by_queue 0
timestamp_gap not informative in lockstep

PER-RUN TABLE (ms, per-frame serial)
file | mean | p95 | p99 | max
1792837888052 | 69.7 | 76.2 | 80.3 | 84.2
1792837924255 | 71.7 | 78.0 | 83.7 | 109.5
1792837958577 | 70.8 | 78.5 | 81.7 | 83.3
Mean spread across runs: 2.0. Run-to-file mapping is by file timestamp order and values are
transcribed from run notes, not re-read from JSON content.

STAGE MEANS (ms, range over 3 runs)
DETECT 52.2-54.1 (p95 58.1-61.0)
PAD_INFER 11.1 (p95 11.8-12.0)
PREPROCESS 3.5
YUV_TO_RGB 2.19 (rotation 0)
QUALITY ~0.002
process_other 0.15-0.18
Stage sum ~69 = serial mean.

RESULT
p95 target 200 ms met with ~120 ms headroom.

CALIBRATION NOTE
laplacian_variance_mean_measured 18.28 vs production blur gate 100.0. With the production gate all
400 frames failed guide.quality.blur before the override (bbox 291x324, yaw/pitch 0, brightness 117.9,
faceCount 1). This is one still frame. Whether a LIVE camera frame passes 100.0 is not established;
the production gate could reject real users.

NOT VERIFIED
SFace, camera/ImageProxy copy, signing, UI, any accuracy, rotation 270 YUV->RGB cost,
PACED composite behaviour, cool-phone (thermal 0) numbers, airplane/DND state (not in JSON),
live-camera blur pass rate, ML Kit time vs face size, :liveness-android whole-module compile.
