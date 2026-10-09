# Licence Register

All models, libraries, and datasets used in this project.

| Item | Type | Licence | Commercial Use | SHA-256 | Notes |
|------|------|---------|----------------|---------|-------|
| MiniFASNetV2-SE ONNX (FP32) | Model | Apache-2.0 | Yes | `[TO BE RECORDED AT DOWNLOAD]` | ~1.82 MB; from `facenox/face-antispoof-onnx` |
| MiniFASNetV2-SE ONNX (INT8) | Model | Apache-2.0 | Yes | `[TO BE RECORDED AT DOWNLOAD]` | ~600 KB; quantized variant |
| ONNX Runtime | Library | MIT | Yes | n/a (Maven Central) | `com.microsoft.onnxruntime:onnxruntime:1.16.3` (baseline) |
| JUnit 5 | Library | EPL-2.0 | Yes | n/a (Maven Central) | Test only |
| YuNet | Model | Apache-2.0 (per third-party docs) | **CONFIRM** | `[TO BE RECORDED]` | Face detection; from OpenCV Zoo |
| SFace | Model | Apache-2.0 (per third-party docs) | **CONFIRM** | `[TO BE RECORDED]` | Same-face embedder; from OpenCV Zoo |
| CelebA-Spoof | Dataset | Non-commercial, no redistribution | **No** | n/a | Primary PAD evaluation |
| LFW | Dataset | Non-commercial research | **No** | n/a | Same-face threshold calibration |
| ONOT | Dataset | Check licence at download | Check | n/a | Bona fide source for self-made attacks |
| Replay-Attack (Idiap) | Dataset | Restricted EULA | **No** | n/a | Temporal + video PAD evaluation |
