package liveness.evaluation;

import liveness.engine.MiniFasNetScorer;
import liveness.engine.PadPreprocessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scores manifest images with the REAL MiniFASNetV2-SE model. Decode failures throw: a sample that cannot be
 * scored must never be silently counted.
 * <p>
 * Face localisation (ADR-010): reads an optional sidecar {@code <manifest>.bbox.csv} (header
 * {@code path,x,y,w,h}, produced by tools/detect_face_boxes.py). Samples with a box use the production crop
 * (1.5x face box). Samples without one fall back to a centred whole-image square and are COUNTED, so the report
 * can state exactly how many results were produced without real localisation.
 */
final class OnnxManifestScorer implements EvaluationHarness.Scorer {
    private final MiniFasNetScorer scorer;
    private final Path baseDir;
    private final Map<String, int[]> boxes = new HashMap<>();
    private final AtomicInteger withBox = new AtomicInteger();
    private final AtomicInteger wholeImage = new AtomicInteger();
    private final LatencyMetrics psPd = new LatencyMetrics();

    /** PS-PD timings (ISO/IEC 30107-3): PAD preprocessing + model inference only; excludes image decode. */
    LatencyMetrics psPd() { return psPd; }

    OnnxManifestScorer(MiniFasNetScorer scorer, Path baseDir, Path bboxSidecar) throws IOException {
        if (scorer.isStubMode()) throw new IllegalStateException("refusing to evaluate with the stub scorer");
        this.scorer = scorer;
        this.baseDir = baseDir;
        if (bboxSidecar != null && Files.exists(bboxSidecar)) {
            List<String> lines = Files.readAllLines(bboxSidecar);
            for (int i = 1; i < lines.size(); i++) {
                String[] f = lines.get(i).split(",");
                if (f.length < 5) continue;
                boxes.put(f[0].trim(), new int[]{Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()),
                        Integer.parseInt(f[3].trim()), Integer.parseInt(f[4].trim())});
            }
        }
    }

    boolean hasSidecar() { return !boxes.isEmpty(); }

    /** One-line statement for the report's provenance section. */
    String localisationSummary() {
        int a = withBox.get(), b = wholeImage.get();
        return "Face localisation: " + a + " scored with a detected face box (1.5x crop), " + b
                + " scored with a whole-image centred crop (no box available)."
                + (b > 0 ? " Results for those samples do not reflect the production crop." : "");
    }

    @Override
    public int score(ManifestEntry entry) {
        File f = new File(entry.path());
        if (!f.exists()) f = baseDir.resolve(entry.path()).toFile();
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new IllegalStateException("undecodable image: " + f);
            int w = img.getWidth(), h = img.getHeight();
            byte[] rgb = new byte[w * h * 3];
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                img.getRGB(0, y, w, 1, row, 0, w);
                for (int x = 0; x < w; x++) {
                    int o = (y * w + x) * 3;
                    rgb[o] = (byte) (row[x] >> 16);
                    rgb[o + 1] = (byte) (row[x] >> 8);
                    rgb[o + 2] = (byte) row[x];
                }
            }
            int[] b = boxes.get(entry.path());
            int[] box;
            if (b != null) {
                withBox.incrementAndGet();
                box = b;
            } else {
                wholeImage.incrementAndGet();
                box = new int[]{0, 0, 0, 0};
            }
            long t0 = System.nanoTime();
            float[] input = PadPreprocessor.prepare(rgb, w, h, box[0], box[1], box[2], box[3]);
            long t1 = System.nanoTime();
            int score = scorer.score(input);
            long t2 = System.nanoTime();
            psPd.record("ps_pd_preprocess", t1 - t0);
            psPd.record("ps_pd_inference", t2 - t1);
            psPd.record("ps_pd_total", t2 - t0);
            return score;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + f, e);
        }
    }
}
