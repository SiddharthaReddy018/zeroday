Live front-camera blur survey, 2026-10-24
Device: Nothing A001 (MT6878, Android 15), bench app io.oxalpha.liveness.bench, release build. thermal_status 2 before and after every run.
What: ImageAnalysis only (no preview, no detector, no PAD), production lumaStats (stride-2 Laplacian variance of the Y plane,
whole frame). 30 warmup + 300 measured frames per run. Request: 640x480, KEEP_ONLY_LATEST, YUV_420_888, front camera.
Code: CameraBlurBenchmark and LumaProbe are NOT on code main yet (held in a local stash, uncommitted). Treat these as results
from an unmerged diagnostic.

RESULTS (laplacian variance, 300 frames per run, 1 run per condition)
file ts | condition | p5 | p50 | p95 | max | brightness mean | pass@30 | pass@50 | pass@100
1792839625173 | unlabelled | 15.8 | 18.2 | 30.0 | 60.0 | 57.9 | 0.050 | 0.017 | 0.000
1792839645265 | unlabelled | 17.5 | 20.2 | 22.2 | 25.9 | 60.0 | 0.000 | 0.000 | 0.000
1792839663818 | unlabelled | 19.6 | 20.9 | 22.3 | 29.7 | 58.9 | 0.000 | 0.000 | 0.000
1792840181821 | bright_light | 30.5 | 41.5 | 49.4 | 54.5 | 100.6 | 0.953 | 0.037 | 0.000
1792840160297 | defocused | 25.2 | 35.3 | 55.0 | 87.2 | 90.8 | 0.823 | 0.097 | 0.000
1792840132971 | sharp_textured | 24.2 | 26.9 | 30.4 | 32.7 | 87.6 | 0.093 | 0.000 | 0.000

FINDINGS
Delivered frame shape was 720x720, rotation 270, row stride 768 in every frame of every run. The 640x480 request was not honoured.
The production blur gate (variance >= 100.0) passed 0% of 1,800 frames. Highest single frame: 87.2.
No sharp/blur separation: the sharp_textured run has the LOWEST p50 (26.9); defocused is higher (35.3), as is bright_light (41.5).
Variance tracks scene content and brightness more than focus. Inference only: sensor noise or hand motion may add Laplacian
variance in the defocused run; not verified.
Offline recomputation of the same metric on face_frame.png gives 18.28, matching the device JSON (18.276).
Conclusion: a whole-frame stride-2 Laplacian variance with a fixed threshold is not a usable blur gate on this camera path.
No threshold was chosen. Design options (not built): face-region Laplacian, brightness normalisation, blur-ratio metric.

CAVEATS
1 run per condition; how well each condition was produced is not recorded. The 3 unlabelled runs were taken before labelling was added
(described as plain wall, dim; not verified). One phone. Run-to-run drift of about 2.7 in p50 appeared under near-identical conditions.

NOT VERIFIED
Face-region metric, other phones, camera/ImageProxy copy cost, ISP denoise contribution, repeated runs per condition,
Gradle log of the survey build (only inferred from the JSONs).
