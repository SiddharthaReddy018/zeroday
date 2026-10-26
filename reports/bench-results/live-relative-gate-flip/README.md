# Live relative blur gate: one flip run, one control, four no-flip runs

Bench: camera_relative_gate_live (live front camera -> CameraXDeviceAdapter -> BlurWindowGate). Device: Nothing A001, MT6878, Android 15, release build, one phone. Delivered 720x720 rot270, 30 warmup + 300 frames per run (330 captured).
Gate constants in every JSON: window 30, drop_factor 2.0, y_shift_factor 1.5. These are CANDIDATES, not fitted.

## Labels
- User-confirmed: camera_relative_gate_flapoff_1792871567893.json is Run 1 (flap OFF, then flipped ON mid-run). camera_relative_gate_flapoff_1792871594438.json is Run 2 (flap OFF the whole run).
- Flip time was NOT recorded. Frame 150 is read off the data (norm p50 drops there), not a logged event.
- User-stated, not recorded in the JSONs (Not verified): flap state, same light, phone unplugged. The condition_label field (flapoff/flapon) is the user's label.
- Thermal status and battery are NOT recorded in these gate JSONs. Charging state is not recorded either.
- LivenessSession is NOT run in this job. Only BlurWindowGate is fed.
- Evidence is one flip run plus one control (plus four no-flip runs). Not a repeatability or accuracy claim.
- Not verified: other phones, lights, distances, hand shake or motion blur, attacks, cost with the detector running.

## Flip run vs control (norm = full_norm, Y = frame luma, p50 per block)

### Run 1 flip: camera_relative_gate_flapoff_1792871567893.json
- counts: insufficient 29, ok 136, blur 78, lost 0, not_fed 87
- first_blur_index 203, first_lost_index -1, adapter_dropped_frames 351, wall_clock_ms 23586
- norm p50 frames 0-149: 0.0214, frames 250-329: 0.0067, ratio 3.19
- norm p50 per 50-frame block (last block 30 frames): 0.0207, 0.0222, 0.0218, 0.0074, 0.0068, 0.0068, 0.0066
- Y p50 per 50-frame block: 126.9, 105.4, 108.8, 103.4, 107.4, 108.1, 108.0

### Run 2 control: camera_relative_gate_flapoff_1792871594438.json
- counts: insufficient 29, ok 301, blur 0, lost 0, not_fed 0
- first_blur_index -1, first_lost_index -1, adapter_dropped_frames 417, wall_clock_ms 25399
- norm p50 frames 0-149: 0.0232, frames 250-329: 0.0216, ratio 1.07
- norm p50 per 50-frame block (last block 30 frames): 0.0236, 0.0234, 0.0231, 0.0235, 0.0214, 0.0215, 0.0218
- Y p50 per 50-frame block: 104.8, 105.1, 107.3, 107.0, 107.8, 109.1, 108.8

Reading: in the flip run norm p50 falls by a ratio above the 2.0 drop factor while Y p50 stays in a band (no light shift flagged: lost = 0), and the gate reports blur. In the control no blur or lost state occurs. Adapter dropped frames are reported in both runs (analysis slower than camera arrival).

## No-flip runs (fresh gate each, user-stated pairs OFF/ON)
- camera_relative_gate_flapoff_1792870849577.json (label flapoff): I 29, O 289, B 0, L 0, not_fed 12; norm p50 first block 0.0328, last block 0.0337
- camera_relative_gate_flapon_1792870884026.json (label flapon): I 29, O 229, B 0, L 0, not_fed 72; norm p50 first block 0.0200, last block 0.0123
- camera_relative_gate_flapoff_1792871151690.json (label flapoff): I 29, O 301, B 0, L 0, not_fed 0; norm p50 first block 0.0200, last block 0.0222
- camera_relative_gate_flapon_1792871190248.json (label flapon): I 29, O 299, B 0, L 0, not_fed 2; norm p50 first block 0.0100, last block 0.0124

Each ON run settles to a stable level on its own baseline, so the gate (relative to the run's own first window) does not call blur. Sharp-level run-to-run drift is about 1.5x, close to the 2.0 drop factor (derived from the norm blocks above, not a tuned margin).

## Files
- camera_relative_gate_flapoff_1792871567893.json
- camera_relative_gate_flapoff_1792871594438.json
- camera_relative_gate_flapoff_1792870849577.json
- camera_relative_gate_flapon_1792870884026.json
- camera_relative_gate_flapoff_1792871151690.json
- camera_relative_gate_flapon_1792871190248.json
