# Dark room, flap OFF, button 5 (file label defocused, treated as sharp), 3 runs

30 warmup + 300 frames each, delivered 720x720 rot270, complete in all 3, 0 errors. Brightness: Y p50 7-9 (darkest set).
Flap off, unplugged and same room are user-stated, not recorded in the JSONs (Not verified).
Battery 38.0 C read before the runs (adb, above the 36.0 C rule). adb showed USB powered: true before the runs; unplugged is user-stated (Not verified).
Medians and max are over frames that have a value; per-frame handling of no-face and multi-face frames is Not verified.

| timestamp | thermal before/after | one face | no face | multi face | crop_ratio p50 | full_norm p50 | full_norm max | Y p50 |
|---|---|---|---|---|---|---|---|---|
| 1792858919088 | 0/0 | 300 | 0 | 0 | 0.489 | 0.162 | 0.19 | 8.815 |
| 1792858949700 | 0/1 | 300 | 0 | 0 | 0.492 | 0.184 | 0.242 | 7.295 |
| 1792858976416 | 1/1 | 300 | 0 | 0 | 0.499 | 0.181 | 0.223 | 7.38 |

Sharp captures (flap off). No blur label, no gate chosen. Brightness is not matched to the flap-on dim set (Y 23-34), so no flap-on vs flap-off comparison is made here.
Across flap-off sets full_norm p50 was 0.247-0.278 at Y 12-13, 0.162-0.184 at Y 7-9, 0.021-0.022 at Y 81-83: it tracks brightness. Cause Not verified.
Not verified: other phones, other distances, scene equality across sets, matched flap-on/off control.
Generated from the JSONs with assertions.
