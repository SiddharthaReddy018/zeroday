# DEV-split agreement: FP32 vs INT8

Split: dev only. Threshold: 70 (score >= threshold is accepted as bona fide). A = FP32 (reference), B = INT8.

## Samples

- Total: 1030 (bona fide 1021, attack 9)

## Score differences (B - A, integer scores 0-100)

- Identical scores: 854 of 1030
- Mean absolute difference: 1.0495
- Mean signed difference: -0.7854
- Max absolute difference: 50
- Pearson correlation: 0.9073

## Decision flips at threshold 70

- Total flips: 10 of 1030
- Bona fide: FP32 accepts and INT8 rejects: 9 (adds false rejects)
- Bona fide: FP32 rejects and INT8 accepts: 1
- Attack: FP32 rejects and INT8 accepts: 0 (adds false accepts)
- Attack: FP32 accepts and INT8 rejects: 0

## Raw counts at the threshold (descriptive only)

| Model | Bona fide rejected | Attacks accepted |
|---|---|---|
| FP32 | 5 of 1021 | 1 of 9 |
| INT8 | 13 of 1021 | 1 of 9 |

## Flipped samples

| path | label | species | A | B |
|---|---|---|---|---|
| onot/digital/S19423222/S19423222-GM-A45-EAF-T14-S1-LH-C1-I0055-F00.png | bona_fide |  | 60 | 84 |
| onot/digital/S230352474/S230352474-GF-A73-EAS-T01-S1-LH-C1-I0026-F00.png | bona_fide |  | 71 | 50 |
| onot/digital/S230352474/S230352474-GF-A73-EAS-T01-S1-LH-C1-I0052-F00.png | bona_fide |  | 84 | 65 |
| onot/digital/S277323635/S277323635-GF-A45-EME-T00-S1-LH-C1-I0032-F00.png | bona_fide |  | 96 | 46 |
| onot/digital/S340675536/S340675536-GF-A37-EEA-T14-S1-LH-C1-I0018-F00.png | bona_fide |  | 90 | 59 |
| onot/digital/S340675536/S340675536-GF-A37-EEA-T14-S1-LH-C1-I0033-F00.png | bona_fide |  | 90 | 61 |
| onot/digital/S340675536/S340675536-GF-A37-EEA-T14-S1-LH-C1-I0045-F00.png | bona_fide |  | 96 | 63 |
| onot/digital/S340675536/S340675536-GF-A37-EEA-T14-S1-LH-C1-I0052-F00.png | bona_fide |  | 84 | 44 |
| onot/digital/S411588159/S411588159-GF-A21-EEA-T03-S1-LH-C1-I0048-F00.png | bona_fide |  | 98 | 54 |
| onot/digital/S415761895/S415761895-GF-A62-EAS-T03-S1-LH-C1-I0050-F00.png | bona_fide |  | 81 | 45 |

## How to read this

- Zero flips means the two models made the same decision on every DEV image at this threshold. It does not prove equal accuracy: the DEV split has few attacks.
- Any flip means INT8 is a different classifier at threshold 70 and would need its own calibration and decision record.
- Scores are rounded to integers by the production scorer, so small differences below 1 point are not visible.
- Bona fide images are still images; this is not a live-presentation measurement and says nothing about speed.

## Provenance

- Reference model file: MiniFASNetV2-SE.onnx, SHA-256 af2381b88f38769222ed93379e12444e2a50814575de1c46170de570c55a42b6
- Alternative model file: MiniFASNetV2-SE-int8.onnx, SHA-256 fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9
- Manifest: evaluation_benchmark_manifest.csv, dev split only (1030 entries).
- Reference Face localisation: 1029 scored with a detected face box (1.5x crop), 1 scored with a whole-image centred crop (no box available). Results for those samples do not reflect the production crop.
- Alternative Face localisation: 1029 scored with a detected face box (1.5x crop), 1 scored with a whole-image centred crop (no box available). Results for those samples do not reflect the production crop.
- Host: Mac OS X aarch64, Java 25.0.1, ORT CPU execution provider, BASIC_OPT, default intra-op threads.
