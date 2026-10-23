package liveness.android;

import liveness.engine.LandmarkLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Maps ML Kit eye/lip contours into the shared liveness landmark contract.
 *
 * <p>Contour geometry is used rather than fixed point indices, so the mapper does not depend
 * on the vendor's contour traversal direction. The resulting five alignment points are
 * approximate contour-derived points; their suitability for the calibrated SFace threshold
 * must be measured before this detector is used for production same-face binding.
 */
public final class MlKitLandmarkMapper {
    private static final double EPSILON = 1e-6;

    private MlKitLandmarkMapper() {}

    /**
     * @param leftEye subject-left eye contour, interleaved x/y coordinates
     * @param rightEye subject-right eye contour, interleaved x/y coordinates
     * @param upperLipTop upper boundary of the upper lip
     * @param upperLipBottom lower boundary of the upper lip
     * @param lowerLipTop upper boundary of the lower lip
     * @param lowerLipBottom lower boundary of the lower lip
     * @param noseBottom nose-bottom contour
     * @return shared 42-coordinate layout. Missing feature groups remain NaN and fail closed.
     */
    public static double[] map(float[] leftEye, float[] rightEye,
                               float[] upperLipTop, float[] upperLipBottom,
                               float[] lowerLipTop, float[] lowerLipBottom,
                               float[] noseBottom) {
        double[] out = new double[LandmarkLayout.FULL_LENGTH];
        Arrays.fill(out, Double.NaN);

        Eye left = eye(leftEye);
        Eye right = eye(rightEye);
        Mouth mouth = mouth(upperLipTop, upperLipBottom, lowerLipTop, lowerLipBottom);

        if (left != null) writeEye(out, LandmarkLayout.LEFT_EYE, left);
        if (right != null) writeEye(out, LandmarkLayout.RIGHT_EYE, right);
        if (mouth != null) writeMouth(out, mouth);

        Point nose = noseCenter(noseBottom);
        if (left != null && right != null && mouth != null && nose != null
                && LandmarkLayout.hasBase(out)) {
            Point rightEyeCenter = centroid(right.points);
            Point leftEyeCenter = centroid(left.points);
            out[LandmarkLayout.ALIGN5] = rightEyeCenter.x;
            out[LandmarkLayout.ALIGN5 + 1] = rightEyeCenter.y;
            out[LandmarkLayout.ALIGN5 + 2] = leftEyeCenter.x;
            out[LandmarkLayout.ALIGN5 + 3] = leftEyeCenter.y;
            out[LandmarkLayout.ALIGN5 + 4] = nose.x;
            out[LandmarkLayout.ALIGN5 + 5] = nose.y;
            out[LandmarkLayout.ALIGN5 + 6] = mouth.left.x;
            out[LandmarkLayout.ALIGN5 + 7] = mouth.left.y;
            out[LandmarkLayout.ALIGN5 + 8] = mouth.right.x;
            out[LandmarkLayout.ALIGN5 + 9] = mouth.right.y;
        }
        return out;
    }

    private static void writeEye(double[] out, int offset, Eye eye) {
        put(out, offset, eye.leftCorner);
        put(out, offset + 2, eye.upperLeft);
        put(out, offset + 4, eye.upperRight);
        put(out, offset + 6, eye.rightCorner);
        put(out, offset + 8, eye.lowerRight);
        put(out, offset + 10, eye.lowerLeft);
    }

    private static void writeMouth(double[] out, Mouth mouth) {
        put(out, LandmarkLayout.MOUTH, mouth.left);
        put(out, LandmarkLayout.MOUTH + 2, mouth.upperCenter);
        put(out, LandmarkLayout.MOUTH + 4, mouth.right);
        put(out, LandmarkLayout.MOUTH + 6, mouth.lowerCenter);
    }

    private static void put(double[] out, int offset, Point p) {
        out[offset] = p.x;
        out[offset + 1] = p.y;
    }

    private static Eye eye(float[] coordinates) {
        List<Point> points = points(coordinates, 6);
        if (points == null) return null;

        Point left = extremeX(points, true);
        Point right = extremeX(points, false);
        double width = right.x - left.x;
        if (!Double.isFinite(width) || width <= EPSILON) return null;

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        double midY = (minY + maxY) / 2.0;
        Point upperLeft = nearestXInHalf(points, left.x + width / 3.0, midY, true);
        Point upperRight = nearestXInHalf(points, left.x + width * 2.0 / 3.0, midY, true);
        Point lowerLeft = nearestXInHalf(points, left.x + width / 3.0, midY, false);
        Point lowerRight = nearestXInHalf(points, left.x + width * 2.0 / 3.0, midY, false);
        if (upperLeft == null || upperRight == null || lowerLeft == null || lowerRight == null) {
            return null;
        }
        if (distance(upperLeft, lowerLeft) <= EPSILON
                || distance(upperRight, lowerRight) <= EPSILON) {
            return null;
        }
        return new Eye(points, left, right, upperLeft, upperRight, lowerLeft, lowerRight);
    }

    private static Point nearestXInHalf(List<Point> points, double targetX, double midY, boolean upper) {
        Point best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Point p : points) {
            if (upper ? p.y >= midY : p.y <= midY) continue;
            double distance = Math.abs(p.x - targetX);
            if (distance < bestDistance
                    || (Math.abs(distance - bestDistance) < EPSILON && best != null
                    && (upper ? p.y < best.y : p.y > best.y))) {
                best = p;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Mouth mouth(float[] upperTop, float[] upperBottom,
                               float[] lowerTop, float[] lowerBottom) {
        List<Point> top = points(upperTop, 2);
        List<Point> upperInside = points(upperBottom, 2);
        List<Point> lowerInside = points(lowerTop, 2);
        List<Point> bottom = points(lowerBottom, 2);
        if (top == null || upperInside == null || lowerInside == null || bottom == null) return null;

        List<Point> all = new ArrayList<>(top.size() + upperInside.size()
                + lowerInside.size() + bottom.size());
        all.addAll(top);
        all.addAll(upperInside);
        all.addAll(lowerInside);
        all.addAll(bottom);

        Point left = extremeX(all, true);
        Point right = extremeX(all, false);
        double width = right.x - left.x;
        if (!Double.isFinite(width) || width <= EPSILON) return null;
        double centerX = (left.x + right.x) / 2.0;
        Point upperCenter = nearestX(top, centerX, true);
        Point lowerCenter = nearestX(bottom, centerX, false);
        if (upperCenter == null || lowerCenter == null) return null;
        return new Mouth(left, right, upperCenter, lowerCenter);
    }

    private static Point nearestX(List<Point> points, double targetX, boolean preferUpper) {
        Point best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Point p : points) {
            double distance = Math.abs(p.x - targetX);
            if (distance < bestDistance
                    || (Math.abs(distance - bestDistance) < EPSILON && best != null
                    && (preferUpper ? p.y < best.y : p.y > best.y))) {
                best = p;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Point noseCenter(float[] coordinates) {
        List<Point> points = points(coordinates, 2);
        if (points == null) return null;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
        }
        return nearestX(points, (minX + maxX) / 2.0, false);
    }

    private static List<Point> points(float[] coordinates, int minimumPoints) {
        if (coordinates == null || coordinates.length < minimumPoints * 2
                || (coordinates.length & 1) != 0) return null;
        List<Point> result = new ArrayList<>(coordinates.length / 2);
        for (int i = 0; i < coordinates.length; i += 2) {
            float x = coordinates[i];
            float y = coordinates[i + 1];
            if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
            result.add(new Point(x, y));
        }
        return result;
    }

    private static Point extremeX(List<Point> points, boolean left) {
        Point best = points.get(0);
        for (Point p : points) {
            if (left ? p.x < best.x : p.x > best.x) best = p;
        }
        return best;
    }

    private static Point centroid(List<Point> points) {
        double x = 0.0;
        double y = 0.0;
        for (Point p : points) {
            x += p.x;
            y += p.y;
        }
        return new Point(x / points.size(), y / points.size());
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static final class Point {
        final double x;
        final double y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    private static final class Eye {
        final List<Point> points;
        final Point leftCorner, rightCorner, upperLeft, upperRight, lowerLeft, lowerRight;
        Eye(List<Point> points, Point leftCorner, Point rightCorner,
            Point upperLeft, Point upperRight, Point lowerLeft, Point lowerRight) {
            this.points = points;
            this.leftCorner = leftCorner;
            this.rightCorner = rightCorner;
            this.upperLeft = upperLeft;
            this.upperRight = upperRight;
            this.lowerLeft = lowerLeft;
            this.lowerRight = lowerRight;
        }
    }

    private static final class Mouth {
        final Point left, right, upperCenter, lowerCenter;
        Mouth(Point left, Point right, Point upperCenter, Point lowerCenter) {
            this.left = left;
            this.right = right;
            this.upperCenter = upperCenter;
            this.lowerCenter = lowerCenter;
        }
    }
}
