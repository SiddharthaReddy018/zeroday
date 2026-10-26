# fullFrameNorm latency (thermal status 1)

Bench: fullframenorm_latency, Nothing A001 (MT6878, Android 15, release build, build_debuggable False). Input: face_frame_720_sensor.png as 720x720 rot270 (see input_shape in the JSON). 50 warmup + 500 timed calls per run, two runs.
Microbenchmark: no detector running, no ImageProxy copy (see note field in each JSON).

| metric (ms) | run 1 (1792872180265) | run 2 (1792872185274) |
|---|---|---|
| fullFrameNorm p50 | 1.223 | 1.221 |
| fullFrameNorm p95 | 1.259 | 1.256 |
| fullFrameNorm max | 1.438 | 1.924 |
| YuvConverter.toRgb p50 | 3.444 | 3.455 |
| YuvConverter.toRgb p95 | 3.864 | 3.909 |
| YuvConverter.toRgb max | 4.610 | 4.534 |
| LumaProbe.stats (reference) p50 | 0.496 | 0.496 |
| LumaProbe.stats (reference) p95 | 0.508 | 0.506 |
| LumaProbe.stats (reference) max | 0.555 | 0.518 |

- norm_value_check: 0.070658 in both runs.
- Recorded in the JSONs: thermal_status 1 before and after, battery temp 37.0 C before and after (above the 36.0 C rule), charging False before and after.

## Derived (computed from p50 above)
- run 1: fullFrameNorm p50 is 35.5% of toRgb p50, 0.61% of a 200 ms interval, 3.7% of a 33 ms frame
- run 2: fullFrameNorm p50 is 35.3% of toRgb p50, 0.61% of a 200 ms interval, 3.7% of a 33 ms frame

The 200 ms and 33 ms figures are ASSUMED reference intervals (adapter interval, 30 fps), not measured in this job.
Not verified: cost while the detector runs concurrently, ImageProxy copy cost, other phones, sustained thermal.

## Files
- fullnorm_latency_1792872180265.json
- fullnorm_latency_1792872185274.json
