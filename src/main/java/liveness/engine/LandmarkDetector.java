package liveness.engine;

public final class LandmarkDetector {
    
    public static double computeEAR(double[] eyeLandmarks6) {
        if (eyeLandmarks6.length != 12) {
            throw new IllegalArgumentException("Expected 6 points (12 coordinates)");
        }
        double p2p6 = distance(eyeLandmarks6[2], eyeLandmarks6[3], eyeLandmarks6[10], eyeLandmarks6[11]);
        double p3p5 = distance(eyeLandmarks6[4], eyeLandmarks6[5], eyeLandmarks6[8], eyeLandmarks6[9]);
        double p1p4 = distance(eyeLandmarks6[0], eyeLandmarks6[1], eyeLandmarks6[6], eyeLandmarks6[7]);
        
        return (p2p6 + p3p5) / (2.0 * p1p4);
    }

    public static double computeMAR(double[] mouthLandmarks) {
        if (mouthLandmarks.length < 8) return 0.0;
        double vDist = distance(mouthLandmarks[2], mouthLandmarks[3], mouthLandmarks[6], mouthLandmarks[7]);
        double hDist = distance(mouthLandmarks[0], mouthLandmarks[1], mouthLandmarks[4], mouthLandmarks[5]);
        return hDist == 0 ? 0 : vDist / hDist;
    }

    public static boolean detectBlink(double ear, double threshold) {
        return ear < threshold;
    }

    public static boolean detectSmile(double mar, double threshold) {
        return mar > threshold;
    }

    public static boolean detectTurn(double yawDeg, double requiredDeg, String direction) {
        if ("LEFT".equalsIgnoreCase(direction)) {
            return yawDeg <= -requiredDeg;
        } else if ("RIGHT".equalsIgnoreCase(direction)) {
            return yawDeg >= requiredDeg;
        }
        return Math.abs(yawDeg) >= requiredDeg;
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
