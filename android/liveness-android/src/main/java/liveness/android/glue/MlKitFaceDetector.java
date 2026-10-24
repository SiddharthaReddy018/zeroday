package liveness.android.glue;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import liveness.android.FaceDetector;
import liveness.android.MlKitLandmarkMapper;

/**
 * ML Kit contour detector adapter for the existing CameraX liveness contract.
 *
 * <p>Receives the packed RGB pixels produced by CameraXDeviceAdapter, converts them to a reusable
 * ARGB bitmap, and runs bundled on-device face detection synchronously on the calling worker thread.
 * It reports face count, bounding box, Euler pose, eye/mouth contours, and contour-derived Align5.
 *
 * <p>Align5 is geometric approximation from ML Kit contours, not asserted to be numerically
 * equivalent to OpenCV YuNet/SFace landmarks. Validate SFace same-face error rates and the 0.48
 * threshold on representative data before using this adapter in a production cross-stream flow.
 */
public final class MlKitFaceDetector implements FaceDetector, AutoCloseable {
    private final com.google.mlkit.vision.face.FaceDetector detector;
    private Bitmap bitmap;
    private int[] argbPixels;
    private int bitmapWidth;
    private int bitmapHeight;
    private boolean closed;

    public MlKitFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);
    }

    @Override
    public synchronized Observation detect(byte[] rgb, int width, int height) {
        if (closed) throw new IllegalStateException("ML Kit detector is closed");
        if (rgb == null || width <= 0 || height <= 0
                || (long) width * height * 3L > Integer.MAX_VALUE
                || rgb.length < (long) width * height * 3L) {
            throw new IllegalArgumentException("Invalid RGB buffer for ML Kit detection");
        }

        ensureBitmap(width, height);
        int count = width * height;
        for (int i = 0, p = 0; i < count; i++, p += 3) {
            int red = rgb[p] & 0xff;
            int green = rgb[p + 1] & 0xff;
            int blue = rgb[p + 2] & 0xff;
            argbPixels[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        bitmap.setPixels(argbPixels, 0, width, 0, 0, width, height);

        final List<Face> faces;
        try {
            faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during ML Kit face detection", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("ML Kit face detection failed", e.getCause());
        }

        if (faces == null || faces.isEmpty()) return Observation.NONE;
        Face selected = largestFace(faces);
        Rect bounds = selected.getBoundingBox();
        int left = clamp(bounds.left, 0, width);
        int top = clamp(bounds.top, 0, height);
        int right = clamp(bounds.right, 0, width);
        int bottom = clamp(bounds.bottom, 0, height);
        if (right <= left || bottom <= top) return new Observation(
                faces.size(), left, top, 0, 0,
                unavailablePose(), unavailablePose(), unavailablePose(), null);

        double[] landmarks = MlKitLandmarkMapper.map(
                contour(selected, FaceContour.LEFT_EYE),
                contour(selected, FaceContour.RIGHT_EYE),
                contour(selected, FaceContour.UPPER_LIP_TOP),
                contour(selected, FaceContour.UPPER_LIP_BOTTOM),
                contour(selected, FaceContour.LOWER_LIP_TOP),
                contour(selected, FaceContour.LOWER_LIP_BOTTOM),
                contour(selected, FaceContour.NOSE_BOTTOM));

        double yaw = finiteOrUnavailable(selected.getHeadEulerAngleY());
        double pitch = finiteOrUnavailable(selected.getHeadEulerAngleX());
        double roll = finiteOrUnavailable(selected.getHeadEulerAngleZ());

        return new Observation(faces.size(), left, top, right - left, bottom - top,
                yaw, pitch, roll, landmarks);
    }

    private void ensureBitmap(int width, int height) {
        if (bitmap != null && bitmapWidth == width && bitmapHeight == height) return;
        if (bitmap != null) bitmap.recycle();
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        argbPixels = new int[width * height];
        bitmapWidth = width;
        bitmapHeight = height;
    }

    private static Face largestFace(List<Face> faces) {
        Face best = faces.get(0);
        long bestArea = area(best.getBoundingBox());
        for (int i = 1; i < faces.size(); i++) {
            Face face = faces.get(i);
            long area = area(face.getBoundingBox());
            if (area > bestArea) {
                best = face;
                bestArea = area;
            }
        }
        return best;
    }

    private static long area(Rect r) {
        return Math.max(0L, (long) r.width()) * Math.max(0L, (long) r.height());
    }

    private static float[] contour(Face face, int type) {
        FaceContour contour = face.getContour(type);
        if (contour == null || contour.getPoints() == null || contour.getPoints().isEmpty()) return null;
        List<PointF> points = contour.getPoints();
        float[] coordinates = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            PointF p = points.get(i);
            coordinates[2 * i] = p.x;
            coordinates[2 * i + 1] = p.y;
        }
        return coordinates;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double finiteOrUnavailable(double value) {
        return Double.isFinite(value) ? value : Double.MAX_VALUE;
    }

    private static double unavailablePose() {
        return Double.MAX_VALUE;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        detector.close();
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
            argbPixels = null;
        }
    }
}
