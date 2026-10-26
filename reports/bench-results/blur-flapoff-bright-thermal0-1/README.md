# Brighter room, flap OFF, button 4 (sharp_textured), 3 runs

30 warmup + 300 frames each, delivered 720x720 rot270, complete in all 3, 0 errors. Brightness: Y p50 81-83 (not matched to the flap-on set at Y 23-34).
Flap off, unplugged and same room are user-stated, not recorded in the JSONs (Not verified).
Battery 37.0 C read after the runs (no before reading, Not verified; above the 36.0 C rule). USB powered: true was read after reconnecting; unplugged during the runs is user-stated (Not verified).
Medians and max are over frames that have a value; per-frame handling of no-face and multi-face frames is Not verified.

| timestamp | thermal before/after | one face | no face | multi face | crop_ratio p50 | full_norm p50 | full_norm max | Y p50 |
|---|---|---|---|---|---|---|---|---|
| 1792859398354 | 0/0 | 300 | 0 | 0 | 0.443 | 0.021 | 0.025 | 80.796 |
| 1792859425022 | 0/1 | 299 | 0 | 1 | 0.443 | 0.022 | 0.024 | 81.072 |
| 1792859451364 | 1/1 | 281 | 17 | 2 | 0.429 | 0.021 | 0.023 | 83.49 |

Sharp captures (flap off). No blur label, no gate chosen. Brightness is not matched to the flap-on dim set (Y 23-34), so no flap-on vs flap-off comparison is made here.
Across flap-off sets full_norm p50 was 0.247-0.278 at Y 12-13, 0.162-0.184 at Y 7-9, 0.021-0.022 at Y 81-83: it tracks brightness. Cause Not verified.
Not verified: other phones, other distances, scene equality across sets, matched flap-on/off control.
Generated from the JSONs with assertions.
