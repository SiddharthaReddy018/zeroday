Pipeline bench, 720x720 rot270 vs 640x480 rot0, interleaved, SUPER HOT phone (battery 40-43 C, thermal 3)
Intended as a cool rerun; the phone did NOT cool (local folder was named 9_cool_rerun). Run order was interleaved, not grouped.
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

RESULTS (ms, in run order, per-frame serial)
order | file ts | job | serial mean | p95 | p99 | max | DETECT | PREPROCESS | YUV_TO_RGB | PAD_INFER | battery C before/after
1 | 1792842862300 | rot270 720x720 | 101.39 | 112.16 | 117.88 | 154.93 | 75.83 | 7.28 | 4.23 | 12.95 | 40/40
2 | 1792842906434 | 640x480 rot0 | 83.29 | 94.99 | 100.97 | 157.43 | 63.23 | 3.96 | 2.48 | 12.86 | 40/41
3 | 1792842977481 | 640x480 rot0 | 84.87 | 98.81 | 118.42 | 163.28 | 64.66 | 3.97 | 2.49 | 12.99 | 41/41
4 | 1792843032370 | rot270 720x720 | 101.41 | 113.54 | 118.51 | 126.84 | 75.92 | 7.29 | 4.25 | 12.93 | 42/42
5 | 1792843090314 | 640x480 rot0 | 83.41 | 92.84 | 99.85 | 112.78 | 63.32 | 3.98 | 2.48 | 12.88 | 42/43
6 | 1792843155029 | rot270 720x720 | 116.95 | 128.90 | 133.92 | 141.37 | 86.68 | 8.56 | 4.95 | 15.54 | 43/43

READING (inferred from the pairs, not a controlled test)
Shape cost at similar temperature: rot270 about 101.4 vs 640x480 about 83.3-84.9, roughly +17 ms mean, mostly DETECT (+12),
  then PREPROCESS (+3.3) and YUV_TO_RGB (+1.7). The same size of gap appears in the HOT folder, so it is not mainly thermal.
Thermal cost: the 640x480 job is 83-85 ms here vs 69.7-71.7 ms at thermal 1, about +13 ms.
Run 6 (battery 43 C): mean 116.95, PAD_INFER 15.5 ms vs about 12.9 in earlier runs. Looks like throttling; cause not verified.
Worst case across the six runs: p95 128.9 ms, max 163.3 ms. The 200 ms p95 target is met with margin.
Per-run values vary with battery temperature, so quote the group, not one run.

NOT VERIFIED
thermal 0-1 numbers for the 720x720 rot270 shape (a cool run is pending), SFace, camera/ImageProxy copy, signing, UI,
any accuracy, PACED behaviour, real camera chroma layout, other phones.
