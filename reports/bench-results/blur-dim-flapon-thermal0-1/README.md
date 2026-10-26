# Dim room, flap ON, button 5 (defocused), 4 runs

Runs: 30 warmup + 300 frames each, delivered 720x720 rot270, 300/300 one face, 0 errors, complete in all 4.
Conditions (user-stated, not recorded in JSONs, Not verified): room lights low, flap ON, phone unplugged, same darkness as flap-off dim runs.
Y brightness p50 was 23-34 here vs 11.5-13.3 in the flap-off dim runs, so the room was not equally dark (confound).
Battery temperature not recorded (Not verified). Thermal status per run shown below.

| timestamp | thermal before/after | crop_ratio p50 | full_norm p50 | full_norm max | Y p50 |
|---|---|---|---|---|---|
| 1792857509155 | 0/0 | 0.434 | 0.029 | 0.072 | 23.061 |
| 1792857538154 | 0/1 | 0.454 | 0.019 | 0.031 | 29.013 |
| 1792857566080 | 1/1 | 0.44 | 0.018 | 0.037 | 33.582 |
| 1792857592902 | 1/1 | 0.45 | 0.018 | 0.035 | 29.397 |

Reading: full_norm p50 0.018-0.029 vs 0.247-0.278 in flap-off dim runs (about 10x lower, at or under the 0.035 floor at p50; per-frame max up to 0.072).
crop_ratio p50 0.434-0.454 vs 0.386-0.445 flap-off: no separation in the dark.
Not verified: flap state per run, equal darkness, other phones, other distances. Flap-off sharp runs at Y 23-34 not run. No gate chosen.
Generated from the JSONs with assertions.
