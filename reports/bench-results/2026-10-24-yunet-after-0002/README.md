# YuNet detector-only, after Java-side speedup (0002)

Device: Nothing A001 (MT6878), Android 15, release build, ORT CPU, BASIC_OPT, 640x640 fixed input.
Frame: 640x480 synthetic noise (0 detections, so decode_nms is the no-face case). 60 warmup, 400 measured.
Model sha256: 8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4

Mean ms per stage, before (stage-split build, 3 runs) vs after (0002, 3 runs):

| stage          | before      | after       |
|----------------|-------------|-------------|
| prepare_input  | 8.23-8.25   | 1.33-1.35   |
| tensor_create  | 2.65-2.80   | 1.98-2.06   |
| ort_run        | 30.02-31.09 | 28.66-29.53 |
| output_extract | 5.89-6.02   | 0.41-0.43   |
| decode_nms     | 0.163       | 0.077       |
| total mean     | 47.11-48.11 | 32.59-33.46 |
| total p95      | 51.52-52.57 | 37.31-38.08 |

Java-side work fell from about 17.0 ms to about 3.9 ms. ORT is now about 88% of the call.

## Caveats
- The 1:1 fast path applies only at 640x480. Other camera resolutions will cost more in prepare_input (desktop JVM: 6.3 ms at 1280x720, not a phone number).
- The 1.5-2 ms drop in ort_run was not caused by the code change. Thermal state is a possible cause (battery 36-37 C vs 38-41 C before); thermal_status was 1-2 in both sets. Not proven.
- Run 1 has p99 56.9 / max 65.9 ms; the spike is inside ort_run (p99 53.1), not the Java stages.
- Airplane mode and DND are not recorded in the JSON.
- Detector only. Not end to end. No ML Kit, no accuracy.
