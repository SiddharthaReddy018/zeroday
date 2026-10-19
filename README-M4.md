# Milestone 4 - Android (MOSIP Face Liveness + PAD)

Patch for `SiddharthaReddy018/zeroday` (`main`). Unzip `m4-android-patch.zip` into the project root.

## 1. What is in the patch

| Area | Files |
|---|---|
| Pure-Java Android core (Maven-built, JVM-tested) | `src/main/java/liveness/android/*` |
| Android-only glue (Gradle, not built by Maven) | `android/liveness-android/**` |
| Flutter reference UI | `flutter/liveness_ui/**` |
| Tests | `src/test/java/liveness/android/*`, `src/test/java/liveness/device/MockDevicePerceptualHashTest.java` |
| Small core fixes (section 5) | `DeviceAdapter`, `MockDeviceAdapter`, `FaceFrame`, `LivenessSession`, `LivenessService` |

Design rule: everything with logic is plain Java 11 behind small interfaces (`SbiIntentLauncher`, `EnvProbe`,
`FaceDetector`, `FaceEmbedder`, `RgbDecoder`), so it is unit-tested on a normal JVM. Files that touch `android.*`,
CameraX or Flutter (`glue/`) are thin and contain no decisions.

Frame sources (S21.2 / S18.6): live frames are host CameraX (T3). The accepted image is SBI `rCapture` (T1), bound to
the live stream by session nonce + max time gap + same-face (`CrossStreamBinder`).

| Class | Role |
|---|---|
| `CameraXDeviceAdapter` | T3 frames: bounded drop-oldest queue, never blocks the camera thread, YUV->RGB, blur/brightness/pHash, stable device signature |
| `SbiIntentBridge`, `SbiIntentDeviceAdapter` | SBI discovery / Info / rCapture per the MOSIP Android SBI spec, JWT verification, binding before the image is returned |
| `SbiJwtVerifier` | RS256 + x5c verification against adopter trust anchors, validity window, `alg` allow-list |
| `Iso19794Face`, `IsoFaceRgbDecoder` | Unwrap ISO/IEC 19794-5 face records (JPEG / JPEG 2000) |
| `CrossStreamBinder` | nonce + time gap + `SameFaceChecker`; fails closed |
| `EnvironmentIntegrityChecker` | root / ADB / debugger / Xposed / Frida / emulator findings -> `DEVICE_ERROR` |
| `AndroidDeviceAdapter` | Composite for `LivenessService`: integrity check, camera, SBI; reports capture mode |
| `LivenessPlatformChannel` | `startSession`, `cancelSession`, `retrySession`, `updateConfig`; emits schema events |
| `LatencyBenchmark` + `glue/ScorerBenchmark` | On-device p50/p95/p99 measurement of the INT8 scorer |

### SBI contract (checked against docs.mosip.io MDS spec 1.1.5, "Android SBI Specification", draft 0.9)
- Discovery action `io.sbi.device`, request `{"type":"Face"}`; the response's `callbackId` is the appId.
- Info action `<appId>.Info`; rCapture action `<appId>.rCapture` with `FLAG_GRANT_READ_URI_PERMISSION`.
- Request is a `byte[]` extra `input`; response is extra `response` (bytes, or a `content://` URI read once).
- `transactionId` must be 4..50 chars of letters, digits, hyphen (else error 112). The engine's bound id
  (`<uuid>:<uuid>`) violates that, so the adapter sends `"T" + 40 hex of SHA-256(boundId)` and, on an exact echo,
  hands the engine its original bound id; any other value fails the nonce check.
- Registration capture `bioValue` is base64url ISO biometric data, unencrypted; `data` is a JWT (`RS256`, `x5c`).
- The M4 brief's names (`io.sbi.device.Info`, `io.sbi.device.rCapture`) differ from the spec; the spec is followed.

### Event names (brief vs shared schema)
`STATE_CHANGED`->`STATE_CHANGE`, `CHALLENGE_PROMPT`->`CHALLENGE_ISSUED`, `PROGRESS`->`CAPTURE_PROGRESS`,
`RESULT`->`SESSION_COMPLETE`, `ERROR`->`ERROR`. `PREVIEW_FRAME` is not a schema event: JPEG bytes use a separate
`mosip.liveness/preview` channel (raw CameraX frames are never streamed; Flutter shows the native preview view).
`updateConfig` accepts only `locale`, `previewEnabled`, `previewMaxFps`; the UI cannot change thresholds.

## 2. Gradle setup
`android/liveness-android/build.gradle` (minSdk 28, Java 11, CameraX 1.3.1, AndroidX,
`com.microsoft.onnxruntime:onnxruntime-android:1.16.3`, `noCompress 'onnx'`) compiles the shared `liveness.*` sources
from `../../src/main/java` and excludes desktop-only code. Replace the Flutter embedding coordinate
(`flutter_embedding_release:...REPLACE_WITH_ENGINE_HASH`) with your Flutter version's.

Model: copy `MiniFASNetV2-SE-int8.onnx` (INT8, ~612 KB) to `android/liveness-android/src/main/assets/`.
The repo contains no model file, so none is included.

## 3. AndroidManifest
`CAMERA` plus a `<queries>` block for `io.sbi.device` and for the root/hook packages the integrity checker looks for
(on Android 11+ `getPackageInfo` reports invisible packages as "not installed", which would silently disable detection).

## 4. Wiring (host Flutter app)
```java
// Activity.onCreate (ActivityResult registration must happen before STARTED)
AndroidSbiLauncher sbiLauncher = new AndroidSbiLauncher(this);
CameraXDeviceAdapter camera = new CameraXDeviceAdapter("front", myFaceDetector);        // ML Kit / MediaPipe wrapper
CrossStreamBinder binder = new CrossStreamBinder(new SameFaceChecker(0.6), myFaceEmbedder, 8000);
SbiJwtVerifier verifier = new SbiJwtVerifier(trustAnchors, Instant::now);               // device-provider / MOSIP roots
RgbDecoder decoder = new IsoFaceRgbDecoder(new AndroidRgbDecoder(), myJpeg2000DecoderOrNull);
SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(sbiLauncher, binder, camera::latestFrame,
        decoder, verifier, "Staging", "Registration", 20000);
Supplier<DeviceAdapter> factory = () -> new AndroidDeviceAdapter(
        new EnvironmentIntegrityChecker(new AndroidEnvProbe(this)), camera, sbi);
LivenessFlutterPlugin.configure(livenessService, factory);
new CameraXController().bind(this, this, previewView, camera);
```
`SbiJwtVerifier.disabledForDevelopment()` exists for mock SBIs and must not ship. Dart: `ResidentRegistrationScreen`,
`OperatorAuthScreen`, `SupervisorAuthScreen` take a `LivenessClient` and an optional native camera widget; locales
`en`, `fr`, `ar` (RTL); messages arrive localized from the engine.

## 5. Changes to existing core files (backward compatible)
- `DeviceAdapter`: default methods `captureFinal(boundTxId)`, `lastReturnedTransactionId()`, `expectedFrameIntervalMs()`, `captureMode()`.
- `LivenessService`: uses `captureFinal`; passes the id the device RETURNED (not the one it sent) to `processRCapture`;
  takes `requestTs` before the capture; uses the device's frame interval; calls `callback.onResult` on early device failure.
- `FaceFrame`: optional `pHash` (old constructors unchanged, default 0). `LivenessSession` feeds it to the sequence monitor
  and records the device's capture mode in the attestation instead of a hard-coded `"STREAM"`.
- `LivenessSession`: `imageSha256` was `Sha256.hex(bytes)`, which hex-ENCODES the whole image; it is now the SHA-256 digest.
- `MockDeviceAdapter`: frames now carry a perceptual hash (unique per frame; constant for FRAME_FREEZE; cyclic for FRAME_LOOP).

Bugs these fixed (all reproduced before fixing): default 5-frame sessions failed with false FROZEN because every frame
hashed to 0; a rooted/disconnected device never produced a `SESSION_COMPLETE` for the UI; the attestation contained the
full image in hex instead of its hash; the SBI `transactionId` format violated the SBI spec.

## 6. Run the tests
Maven (needs Maven Central, JDK 11+): `mvn -q test`; Android part only: `mvn -q -Dtest='liveness.android.*Test' test`.
Gradle: `cd android && gradle :liveness-android:testDebugUnitTest`. Dart: `cd flutter/liveness_ui && flutter test`.

Verified in the authoring environment (JDK 21, `--release 11`, JUnit console launcher, ONNX Runtime stubbed so the 3
`ModelQualificationTest` tests were excluded): **330 tests pass = 228 existing + 102 new**, repeated runs stable, and the
same result from a fresh clone with the patch unzipped. New: `CameraXDeviceAdapterTest`, `SbiIntentBridgeTest`,
`SbiJwtVerifierTest` (openssl-generated test PKI), `Iso19794FaceTest`, `EnvironmentIntegrityCheckerTest`,
`LivenessPlatformChannelTest` (every event validated against `events-schema.json`), `AndroidPipelineIntegrationTest`
(real `LivenessService`, default config), `LatencyBenchmarkTest`, `MockDevicePerceptualHashTest`.

Device check: install an SBI app, grant camera, run a resident session; expect `STATE_CHANGE ... SESSION_COMPLETE
{verdict:PASS, attestation.deviceTier:T1}`. On a rooted device/emulator expect `SESSION_COMPLETE{verdict:DEVICE_ERROR}`
and no SBI launch. Latency: `ScorerBenchmark.run(ctx, "MiniFASNetV2-SE-int8.onnx", 30, 300)` and read the `LivenessBench` log.

## 7. Still open (cannot be closed from the authoring environment)
- **Not compiled or run: everything under `android/` and `flutter/`** (no Android or Dart SDK reachable). The logic they
  call is tested; the glue itself is reviewed code.
- **p95 <= 200 ms is not measured.** The harness exists; it needs the model file and a reference low-end phone.
- **Same-face needs an embedding model and `SameFaceChecker` threshold calibration**; none ships in the repo. `FaceDetector`
  (ML Kit / MediaPipe) is also host-supplied.
- **JPEG 2000 decoding** needs a native decoder (e.g. OpenJPEG); Android has none. Without one, JP2 face images fail the
  binding closed. The 2005 ISO layout is parsed by offsets; other versions use a signature scan. Tested on synthetic records only.
- **Trust anchors** for `SbiJwtVerifier` come from the adopter (device-provider / MOSIP certificates); multi-level chains and
  revocation are not handled. The `hash` field chain of the SBI response is not verified (the spec's concatenation is ambiguous).
- The Android SBI section is a spec *draft*; real SBI apps may differ. Integrity checks are heuristics that raise attacker cost.
