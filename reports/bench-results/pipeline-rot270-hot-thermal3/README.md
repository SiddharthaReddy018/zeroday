Pipeline bench, camera-shaped frame (720x720, rotation 270, stride 768), HOT phone (battery 38-39 C, thermal 3)
Device: Nothing A001 (MT6878, Android 15), io.oxalpha.liveness.bench, release build, 2026-10-24.
Pipeline: REAL composite (YuNet + ML Kit contours) + MiniFASNetV2-SE INT8, lockstep, 400 frames, 60 warmup.
Status: benchmark/candidate only. Latency-valid, NOT gate-passing. No accuracy validation.

LABELS
blur gate lowered 100.0 -> 10.0 (benchmark-only override, labelled in each JSON)
input frame sharpened offline (unsharp mask): measured blur variance 78.33 for the 720x720 frame, still below 100.0
720x720 job: sensor-oriented frame, rotation 270, Y rowStride 768 (shape the live front camera delivered in the blur survey);
  chroma rowStride 720 / pixelStride 2 ASSUMED, not observed; FROZEN defeat for rotation 270 not verified
  (all runs completed 400 PAD calls, so no frame was rejected)
640x480 job: face_frame.png, rotation 0, as in bench-results/pipeline-composite-lockstep-thermal1
pad_infer_calls 400 and dropped_by_queue 0 in every run; timestamp_gap not informative in lockstep
thermal_status was 3 before and after every run here. HOT and SUPER HOT are labels from battery_temp_c, not Android statuses.

RESULTS (ms, per-frame serial, 3 runs)
file ts | job | serial mean | p95 | p99 | max | DETECT | PREPROCESS | YUV_TO_RGB | PAD_INFER | battery C before/after
1792842193752 | rot270 720x720 | 87.67 | 97.05 | 100.80 | 128.78 | 65.26 | 6.39 | 3.68 | 11.33 | 38/39
1792842255551 | rot270 720x720 | 87.70 | 97.23 | 100.84 | 103.67 | 65.11 | 6.38 | 3.73 | 11.49 | 39/39
1792842298163 | rot270 720x720 | 87.76 | 98.77 | 100.97 | 109.64 | 65.21 | 6.36 | 3.72 | 11.49 | 39/39

p95 about 97-99 ms vs the 200 ms target: about 100 ms headroom at thermal 3.
Compared with the 640x480 rot0 run at thermal 1 (bench-results/pipeline-composite-lockstep-thermal1: mean 69.7-71.7,
p95 76.2-78.5), this is about +17 ms mean and +20 ms p95. The thermal state differs, so this is not a matched comparison.
See pipeline-rot270-vs-640-superhot-thermal3 for the pairs taken in one session.

NOT VERIFIED
thermal 0-1 numbers for the 720x720 rot270 shape (a cool run is pending), SFace, camera/ImageProxy copy, signing, UI,
any accuracy, PACED behaviour, real camera chroma layout, other phones.
