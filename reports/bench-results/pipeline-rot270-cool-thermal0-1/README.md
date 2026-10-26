Cool latency, composite pipeline, 720x720 rot270 vs 640x480, 2026-10-24
Device: Nothing A001 (MT6878, Android 15), bench app io.oxalpha.liveness.bench, release build, INT8 PAD (sha256 fde20585635cae62...).
Conditions: thermal_status 0 or 1 before every run, battery 35-39 C (battery_temp_c), unplugged, airplane mode, DND, screen on.
What: PipelineBenchmark LOCKSTEP, REAL composite detector (YuNet ORT CPU + ML Kit FAST CONTOUR_MODE_ALL), detector cost included.
Input: STORED frame pushed by adb (not the live camera), RGB->YUV420 round trip. 60 warmup + 400 measured frames per run. Frames never committed.
GATE OVERRIDE: blur gate lowered from 100.0 to 10.0 in every run. These runs measure latency only. QualityGate is NOT representative.
Run order: 3x rot270 then 3x 640x480, back to back, one phone. Battery temperature rose across the sequence (35 -> 39 C).

RESULTS (serial per-frame ms; stage means ms)
file ts | input | battery C | thermal before/after | serial mean | p95 | max | DETECT | PREPROCESS | YUV_TO_RGB | PAD_INFER
1792848429183 | 720x720 rot270 | 35.0 | 0/1 | 83.1 | 91.8 | 98.7 | 61.8 | 6.0 | 3.4 | 10.9
1792848491992 | 720x720 rot270 | 36.0 | 1/1 | 82.4 | 91.2 | 116.0 | 61.3 | 6.1 | 3.4 | 10.8
1792848535781 | 720x720 rot270 | 37.0 | 1/1 | 83.8 | 93.1 | 97.4 | 62.3 | 6.1 | 3.4 | 11.1
1792848577881 | 640x480 rot0 | 38.0 | 1/1 | 67.3 | 77.5 | 103.0 | 50.7 | 3.4 | 2.0 | 10.5
1792848631132 | 640x480 rot0 | 38.0 | 1/1 | 70.9 | 80.9 | 102.6 | 53.3 | 3.6 | 2.2 | 11.2
1792848666795 | 640x480 rot0 | 39.0 | 1/1 | 71.5 | 81.0 | 128.5 | 53.8 | 3.6 | 2.2 | 11.2

FINDINGS
p95 <= 200 ms target: met in all 6 runs (worst p95 93.1 ms).
Mean of run means: rot270 83.1 ms, 640x480 69.9 ms.
Shape cost, mean of run means: +13.2 ms total (DETECT +9.2, PREPROCESS +2.5, YUV_TO_RGB +1.3). Inferred from non-interleaved runs
at slightly different battery temperature; not a controlled test.
Cool rot270 (82-84 ms) is below the earlier hot thermal-3 rot270 figures (87.7 mean, see pipeline-rot270-hot-thermal3).
That difference mixes heat with run order; not a controlled thermal comparison.

CAVEATS
Stored frame, not live camera. The chroma layout of the stored rot270 frame is ASSUMED (rowStride 720, pixelStride 2); the live camera was observed
to deliver uvRowStride 768, uvPixelStride 2 (see camera-blur-candidates-thermal1). Camera/ImageProxy copy cost not included.
FROZEN defeat under rotation 270 not verified. SFace, signing, UI, accuracy not measured. PACED composite not run. One phone.
