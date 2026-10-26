# Matched control: flap OFF/ON alternating, button 4, 6 runs, one session

30 warmup + 300 frames each, delivered 720x720 rot270, complete in all 6, 0 errors. Runs 22:30-22:32.
User-stated, not recorded in the JSONs (Not verified): order OFF, ON, OFF, ON, OFF, ON; flap state per run; light fixed; cable not attached during runs.
Battery 34.0 C before (adb, cable attached), 37.0 C after (above the 36.0 C rule). Thermal status 1 both times.
Y p50 was not constant: 6.8-11.7, drifting down across runs and higher on each ON run than on the OFF run before it. Cause Not verified.
Medians and max are over frames that have a value; per-frame handling of no-face frames is Not verified.

| run | flap (user-stated) | timestamp | thermal before/after | one face | no face | multi face | crop_ratio p50 | full_norm p50 | full_norm max | Y p50 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | OFF | 1792861192519 | 1/0 | 300 | 0 | 0 | 0.499 | 0.196 | 0.215 | 9.56 |
| 2 | ON | 1792861225157 | 0/0 | 300 | 0 | 0 | 0.512 | 0.057 | 0.072 | 11.678 |
| 3 | OFF | 1792861252754 | 0/0 | 300 | 0 | 0 | 0.496 | 0.196 | 0.23 | 7.845 |
| 4 | ON | 1792861282005 | 0/1 | 300 | 0 | 0 | 0.534 | 0.058 | 0.082 | 9.752 |
| 5 | OFF | 1792861309519 | 1/1 | 300 | 0 | 0 | 0.506 | 0.185 | 0.227 | 6.778 |
| 6 | ON | 1792861338851 | 1/1 | 289 | 11 | 0 | 0.525 | 0.065 | 0.109 | 8.858 |

Observed in this one session and scene:
- full_norm p50: OFF 0.185-0.196, ON 0.057-0.065. Every ON below every OFF, about 3x; per-frame max OFF 0.215-0.230, ON 0.072-0.109, no overlap in these runs.
- crop_ratio p50: OFF 0.496-0.506, ON 0.512-0.534. ON is higher in every pair, the opposite direction of the bright-room result (blur lower by 0.067).
- full_norm level on sharp frames differs across sets (0.021 at Y 81, 0.061 at Y 9, 0.185-0.196 here), so no absolute threshold; the 0.035 floor is not usable.
- Whether the full_norm drop comes from blur or from the flap changing exposure is Not verified.
Not verified: other phones, other distances, other scenes, hand shake, attack accuracy. No gate chosen.
Generated from the JSONs with assertions.
