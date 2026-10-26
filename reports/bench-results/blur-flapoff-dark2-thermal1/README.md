# Flap OFF, button 4 (sharp_textured), 3 runs, Y p50 8.8-9.6

30 warmup + 300 frames each, delivered 720x720 rot270, complete in all 3, 0 errors. Run at about 22:08, minutes after the Y 81-83 set (22:00).
User-stated, not recorded in the JSONs (Not verified): flap off, cable not attached, nothing changed in the room since the 22:00 set.
Y p50 here (8.8-9.6) contradicts "same lighting" as the 22:00 set (80.8-83.5). Cause Not verified (light, auto-exposure, position).
No battery reading for this set (Not verified). Medians and max are over frames that have a value; per-frame handling of no-face frames is Not verified.

| timestamp | thermal before/after | one face | no face | multi face | crop_ratio p50 | full_norm p50 | full_norm max | Y p50 |
|---|---|---|---|---|---|---|---|---|
| 1792859881409 | 1/1 | 286 | 14 | 0 | 0.488 | 0.061 | 0.15 | 9.622 |
| 1792859907323 | 1/1 | 300 | 0 | 0 | 0.483 | 0.061 | 0.112 | 9.074 |
| 1792859933676 | 1/1 | 300 | 0 | 0 | 0.482 | 0.062 | 0.076 | 8.807 |

Sharp captures (flap off). No blur label, no gate chosen.
At Y about 9 full_norm p50 is 0.061-0.062, while the btn5 set at Y 7-9 was 0.162-0.184: about 3x apart at similar brightness. full_norm is not explained by Y alone. Cause Not verified.
crop_ratio p50 0.482-0.488 here vs 0.429-0.443 in the Y 81-83 set: sharp-frame crop_ratio moves by about 0.05 with the scene, similar to the 0.05-0.07 blur gap seen in bright light.
Not verified: other phones, other distances, scene equality across sets, matched flap-on/off control.
Generated from the JSONs with assertions.
