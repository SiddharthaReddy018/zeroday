Composite latency bench (YuNet + ML Kit contours), 2026-10-24
Device: Nothing A001 (MT6878, Android 15), io.oxalpha.liveness.bench, release build.
Status: benchmark/candidate only. Not wired into LivenessAndroidBootstrap. No accuracy validation.

RESULTS (mean ms)
1-person crop (upscaled, face ~2/3 height), 3 runs, faces=1, pose ok, 42 landmark values:
  yunet 34.2-43.9 (noisy; run 1 stall, p95 120)
  mlkit 29.2-29.7
  composite 52.9-53.2 mean, p95 59.0-59.8, p99 60.4-61.9
  composite is ~10 ms below yunet+mlkit sum. Cause not established.
2-person frame: YuNet counts 2, composite returns early (~34 ms), ML Kit skipped,
  pose unavailable, landmarks null. By design in CompositeFaceDetector.detect.
  ML Kit alone counts 1 face on that frame, a different person than YuNet's top face (ML Kit bbox 98,143 168x195; YuNet bbox 282,224 162x225). Which is larger is not established (different bbox sources).
YuNet-only reference (thermal 1-2): 47.1-48.1 before 0002, 32.6-33.5 after; ORT ~88% of call.

CAVEATS
thermal_status was 3 for composite runs (YuNet-only runs: 1-2). Numbers are likely pessimistic.
Airplane/DND state not recorded in JSON.
Budget: composite ~53 + PAD ~16 = ~69 ms, sum of separate runs, not end to end.
Does not fit inline at 30 fps (33.3 ms): needs worker thread or every Nth frame.

NOT VERIFIED
End-to-end latency, camera/ImageProxy copy, SFace, signing, accuracy, ML Kit time vs face size,
cool-phone (thermal 0) numbers, why composite < sum, NaN entries in merged landmark array,
ML Kit FAST-mode pose quality, 1280x720 camera path.
Runs: mapping derived from JSON face counts (see table). Folder 3 = 2-person frame, folder 4 = 1-person frame. Frame file itself not committed.

PER-RUN TABLE (ms, from JSONs; all thermal_status 3 before and after)
file | frame | yunet mean/p95 | mlkit mean/p95 | composite mean/p95/p99 | faces | pose | landmark values
1792827315425 | 2-person | 33.8/40.2 | 20.5/30.2 | 34.3/42.5/43.7 | 2 | False | 0
1792827377679 | 2-person | 35.1/43.1 | 29.8/32.1 | 34.6/42.7/43.7 | 2 | False | 0
1792827436270 | 2-person | 35.0/43.1 | 29.6/31.8 | 34.3/42.5/43.9 | 2 | False | 0
1792827747144 | 1-person | 43.9/120.4 | 29.7/32.0 | 53.2/59.8/60.4 | 1 | True | 42
1792827802776 | 1-person | 34.2/43.0 | 29.7/31.7 | 52.9/59.0/61.9 | 1 | True | 42
1792827859843 | 1-person | 39.0/48.1 | 29.2/34.1 | 53.2/59.6/60.6 | 1 | True | 42
