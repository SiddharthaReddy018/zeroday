package liveness.engine;

import liveness.policy.LivenessConfig;

/**
 * Per-frame decision whether the requested challenge action is visible. Extracted from
 * LivenessSession so it can be unit tested. Pure function of one frame's measurements.
 * Missing or undecidable measurements return false (fail closed).
 */
public final class ChallengeActionDetector {
    private ChallengeActionDetector() {}

    public static boolean detect(String action, double[] landmarks, double yawDeg, double pitchDeg,
                                 LivenessConfig config) {
        if (action == null || config == null) return false;
        switch (action) {
            case "BLINK": {
                // both eyes must be closed: a wink (one eye) is not a blink
                double ear = LandmarkLayout.maxEar(landmarks);
                return !Double.isNaN(ear) && LandmarkDetector.detectBlink(ear, config.earBlinkThreshold());
            }
            case "SMILE": {
                double mar = LandmarkLayout.mar(landmarks);
                return !Double.isNaN(mar) && LandmarkDetector.detectSmile(mar, config.marSmileThreshold());
            }
            case "TURN_LEFT":
                return LandmarkDetector.detectTurn(yawDeg, config.yawTurnThreshold(), "LEFT");
            case "TURN_RIGHT":
                return LandmarkDetector.detectTurn(yawDeg, config.yawTurnThreshold(), "RIGHT");
            case "LOOK_LEFT":
                return LandmarkDetector.detectTurn(yawDeg, config.yawLookThreshold(), "LEFT");
            case "LOOK_RIGHT":
                return LandmarkDetector.detectTurn(yawDeg, config.yawLookThreshold(), "RIGHT");
            case "LOOK_UP":
                return pitchDeg < -config.pitchLookThreshold();
            case "LOOK_DOWN":
                return pitchDeg > config.pitchLookThreshold();
            default:
                return false;
        }
    }
}
