# Licence Register

All models, libraries, and datasets used in this project.

| Item | Type | Licence | Commercial Use | SHA-256 | Notes |
|------|------|---------|----------------|---------|-------|
| MiniFASNetV2-SE ONNX (FP32) | Model | Apache-2.0 | Yes | `af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6` | ~1.82 MB; from `facenox/face-antispoof-onnx` |
| MiniFASNetV2-SE ONNX (INT8) | Model | Apache-2.0 | Yes | `fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9` | ~612 KB; quantized variant from `facenox/face-antispoof-onnx` |
| ONNX Runtime | Library | MIT | Yes | n/a (Maven Central) | `com.microsoft.onnxruntime:onnxruntime:1.16.3` (baseline) |
| JUnit 5 | Library | EPL-2.0 | Yes | n/a (Maven Central) | Test only |
| YuNet | Model | Apache-2.0 | Yes | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` | Face detection; 227 KB from OpenCV Zoo |
| SFace | Model | Apache-2.0 | Yes | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` | Same-face embedder; 37.8 MB from OpenCV Zoo |
| CelebA-Spoof | Dataset | Non-commercial, no redistribution | **No** | n/a | Primary PAD evaluation (OULU-NPU alternative) |
| OULU-NPU | Dataset | Research-only EULA | **No** | n/a | Cross-device PAD evaluation (replaces CelebA-Spoof) |
| LFW | Dataset | Non-commercial research | **No** | n/a | Same-face threshold calibration |
| ONOT | Dataset | CC BY-NC 4.0 | **No** | n/a | Bona fide synthetic ICAO source (179 subjects, 5.8k images) |
| Replay-Attack (Kaggle/Idiap) | Dataset | Research / Open CC | **No** | n/a | Video replay attacks (Kaggle Antispoofing Replay / Idiap) |
