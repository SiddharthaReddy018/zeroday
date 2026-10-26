# Relative blur gate: final report (2026-10-25)

```
MOSIP Decode 2026 P04: relative blur gate, final report draft (2026-10-25)
Status: summary of work merged as of 2026-10-25 (code main e73911d, docs main 3f90d60 before this file). Constants are candidates, not fitted.

WHAT IS ON MAIN
Code repo (SiddharthaReddy018/zeroday) main = e73911d:
- FaceFrame.fullFrameNorm (step C).
- LivenessConfig relativeBlurEnabled (default false), Window 30, DropFactor 2.0, YShiftFactor 1.5 (step D).
- LivenessSession hook after the QualityGate fail block: gate fed only when enabled; a non-OK state after samples exist is a quality failure (QUALITY_DROPPED from PASSIVE, else stay in POSITIONING).
- Live gate bench (CameraRelativeGateBenchmark) and latency bench (FullNormLatencyBenchmark).
- RelativeBlurSessionTest: 7 tests, including 2 that read the audit trail for the blur reason and the steady control.
- Maven on the last branch: 639 tests, 0 failures, 0 errors, BUILD SUCCESS.
Bench results (now in reports/bench-results/): live-relative-gate-flip and fullnorm-latency-thermal1 folders with READMEs and raw JSONs, index updated.

RESULTS (one phone: Nothing A001, MT6878, Android 15, release build)
- Flip run (user-confirmed Run 1): norm p50 0.0214 frames 0-149 vs 0.0067 frames 250-329, ratio 3.19 (above the 2.0 drop factor). Counts I 29, O 136, B 78, L 0, not_fed 87. First blur at index 203. Y p50 stayed 103-109 after the first block.
- Control (Run 2, flap OFF all run): I 29, O 301, B 0, L 0, not_fed 0, ratio 1.07.
- Four no-flip runs: B 0, L 0. Sharp-level run-to-run drift about 1.5x, close to the 2.0 drop factor.
- Latency (2 runs, 500 timed): fullFrameNorm p50 1.223 / 1.221 ms, p95 1.259 / 1.256. toRgb p50 3.444 / 3.455 ms. fullFrameNorm is about 35% of toRgb. No detector running, no ImageProxy copy. Thermal status 1, battery 37.0 C (above the 36.0 C rule), charging False per JSON.
- Session test: with the flag on, a 3x drop after a sharp baseline puts guide.quality.blur_relative in the audit trail (synthetic device).
- Sensitivity check (temporary local edit, reverted, tree verified clean): with the reason string changed, 1 of 7 RelativeBlurSessionTest tests failed (the blur-reason test). In that run the session ended in POSITIONING (verdict PENDING), one run only.

DECISIONS
- Baseline LOST: fail closed (user decision).
- Sessions with no valid full-frame sample: relative check skipped. Documented hole, never decided closed.
- Feature is additive, default off. Nothing in the bench or the real app turns it on.
- laplacianVariance and blurMinVariance 100.0 untouched.
- Constants 30, 2.0, 1.5 are candidates, not fitted.

NOT VERIFIED
- LivenessSession with the flag on at the real blurMinVariance 100 on the phone (never run; the session bench ran with it lowered to 10.0).
- UPDATE 2026-10-25 (after PR #24 and docs PR #19): a real LivenessSession with relativeBlurEnabled(true) WAS run live on the phone: 4 runs, 2 controls with no blur_relative entry and 2 flips with blur_relative in the audit trail. blurMinVariance lowered to 10.0, no verdict can fire, roles user-stated. See bench-results/session-relative-gate-thermal0-1/README.md. The 'no bench job builds a session' wording elsewhere in this report is superseded.
- PACED composite run; FROZEN under rot270.
- Other phones, lights, distances; hand shake or motion blur; face-region blur over a sharp background.
- Accuracy against attacks.
- Cost while the detector runs concurrently; ImageProxy copy cost.
- Flip time of the flip run (not recorded); flapon_1792871351791 (still on the phone, never pulled, excluded).
- Flap state, same light, unplugged for the gate runs: user-stated, not in the JSONs. Gate JSONs have no thermal or charging fields.
- Signing; UI mapping of the reason strings; :liveness-android module compile (Flutter missing).
- Hackathon judging criteria.

OPEN DESIGN HOLES
- No reset for LOST: a sustained light change stays LOST.
- INSUFFICIENT at session start blocks the first 30 fed frames (fail closed; pass-through is a one-line change).
- A session that starts blurred is not caught (the baseline is its own first window).
- Honest users who change light get rejected.
- Sessions with no samples bypass the check.
- Class comment says "face crop" but full_norm uses the whole-frame bbox path.
```

## ADDENDUM 2026-10-25 (real blurMinVariance 100)

Supersedes the NOT VERIFIED line saying the session was never run at the real threshold. Current mains: code eeaca0b, docs e639ea3. One phone (Nothing A001), release build, relativeBlurEnabled(true), no verdict can fire. Roles and order are user-stated: control, flip near 12 s, control.

### What was run at the real threshold 100 (6 runs, 2 sets)
- Set 1, no probe (bench-results/session-real-gate-blur100-thermal0-1): blur_relative 0 in all 3 runs, including the flip run. Runs 1 and 2 stayed mostly in POSITIONING (312 and 323 of 330 frames). The flip run logged 4 quality_ok. QualityGate failure reasons were not recorded.
- Set 2, with an independent per-frame QualityGate probe (bench-results/session-real-gate-blur100-probe-thermal1-1): controls passed the probe gate on 329 and 330 of 330 frames, blur_relative 0. Flip run: 152 pass, 174 guide.quality.blur, 4 guide.face.none of 330; Laplacian first below 100 at frame index 153; blur_relative 0.

### Reading (limits of the evidence)
- At the real threshold 100 on this phone, the absolute blur gate rejected this flap before the relative gate was reached. The relative gate was NOT exercised at 100 in this condition.
- The 10.0-threshold result (gate fires on a flip) does not carry over to 100 from these runs.
- Whether the relative gate fires for a milder blur that keeps Laplacian at or above 100 is Not verified.
- The probe is an independent QualityGate evaluation on every captured frame, not the session audit trail. Differences from what the session itself gated are Not verified.
- Why the Set 1 controls failed the gate more often than the Set 2 controls: Not verified.
- One phone, one light, one flip run per set. Not a gate accuracy figure.

### Open design hole added
- At the real threshold the absolute gate catches strong blur first, so the relative gate adds value only for blur that keeps Laplacian at or above 100. Not verified that such blur occurs in practice.

## CORRECTION 2026-10-25 (supersedes the addendum line "NOT exercised at 100")

Source: bench-results/session-real-gate-blur100-strongflip-rounds-thermal1-1 (8 runs, docs PR #24). One phone, one light, release build, real blurMinVariance 100, independent QualityGate probe.
- The relative gate DID fire at the real threshold: blur_relative appeared in the audit trail in 2 of 6 strong-blur flips (both in round 1). It did not fire in the 3 flips of round 2, where the face was lost on 151 to 175 frames (guide.face.none). Controls: 0 of 2 fired (no false fire).
- Roles are inferred from run order, not user-stated. Blur method was not recorded. The blur was strong (Laplacian fell to about 45-100, full_norm about 2.5x down), not the intended mild blur.
- Why round 1 fired and round 2 did not is Not verified. Which gate rejected each frame is Not verified (the probe is independent of the audit trail). 2 of 6 is not a detection rate.
- Still untested: blur that keeps Laplacian at or above 100 with the face detected.
- The earlier Set 1 / Set 2 statements stay valid for those runs (probe set: relative gate not fired on the one flip).
- A round 3 (face lost on all frames in 3 runs, control baseline about 3x different) is raw only and not documented.
