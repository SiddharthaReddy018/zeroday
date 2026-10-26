# Architecture (package level)

Source: class lists from `face-liveness-pad` (src/main/java/liveness/*), 2026-10-25.
All edges are INFERRED from class and package names, not read from source. Not verified.

```mermaid
flowchart TD
  UI["Flutter UI package (flutter/liveness_ui)\nno main.dart, no host app"]
  PLUG["Android glue (android/liveness-android)\nLivenessFlutterPlugin, LivenessAndroidBootstrap,\nCameraXController, MlKitFaceDetector\nnot wired by any app"]
  SVC["service\nLivenessService, LivenessCallback,\nConfigSyncManager, LivenessServiceConfig"]
  FLOW["flow\nLivenessSession, LivenessStateMachine,\nRetryRateLimiter, SupervisorAuth, Verdict"]
  CH["challenge\nChallengeGenerator, ChallengeResponseValidator,\nBoundChallenge, ChallengeSequenceStore"]
  ENG["engine\nQualityGate, MiniFasNetScorer, SpatialPadScorer,\nLandmarkDetector, ChallengeActionDetector,\nSameFaceChecker, DecisionEngine, InferenceWorker"]
  CAP["capture\nFreshnessChecker, FrameSequenceMonitor,\nFrameFingerprint, RCaptureBindingVerifier,\nSessionTransactionBinder, CaptureIntegrityChecker"]
  DEV["device\nDeviceAdapter, MockDeviceAdapter, MdsDeviceInfo\nSBI/MDS adapters: Not verified (in liveness/android?)"]
  POL["policy\nLivenessConfig, LivenessPolicy, ChallengeProfile,\nStubPolicy (stub)"]
  AUD["audit\nAuditLog, AuditChain, AuditSeal, AuditEvent"]
  CRY["crypto\nAttestation, Signer, AndroidKeyStoreSigner,\nEcdsaSignerStub (stub)"]
  CORE["core\nFaceFrame, FrameQuality"]
  EVAL["evaluation (offline harness)\nAPCER, BPCER, fairness"]
  UI -.->|"channel, ui-contract/events-schema.json"| PLUG
  PLUG -.-> SVC
  SVC -.-> FLOW
  FLOW -.-> CH
  FLOW -.-> ENG
  FLOW -.-> CAP
  FLOW -.-> POL
  FLOW -.-> AUD
  FLOW -.-> CRY
  CAP -.-> DEV
  ENG -.-> CORE
  CAP -.-> CORE
  AUD -.-> CRY
  EVAL -.-> ENG
```

## Not in this diagram (Not verified)
- liveness/android (36 files): SBI intent adapter, MDS HTTP adapter, JWT verifier, CrossStreamBinder, SFaceEmbedder, env integrity. Placement unknown.
- liveness/desktop, liveness/ui, liveness/util: not listed.
- Bench harness (android/bench-app): measures engine on-device, not part of the runtime flow.
- Real-time use: no app in the repo launches the plugin; no desktop main() exists.
