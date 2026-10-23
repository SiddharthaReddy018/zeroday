package liveness.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DevAgreementReportTest {

    private Locale saved;

    @BeforeEach
    void rememberLocale() {
        saved = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(saved);
    }

    private static ManifestEntry entry(String path, String label, String split) {
        return new ManifestEntry(path, label, "bona_fide".equals(label) ? "" : "print", "s_" + path, split);
    }

    private static DevAgreementReport.Pair pair(String path, String label, int a, int b) {
        return new DevAgreementReport.Pair(entry(path, label, "dev"), a, b);
    }

    @Test
    void identicalScoresGiveZeroFlipsAndPerfectCorrelation() {
        List<DevAgreementReport.Pair> pairs = Arrays.asList(
                pair("a.jpg", "bona_fide", 95, 95),
                pair("b.jpg", "bona_fide", 80, 80),
                pair("c.jpg", "attack", 10, 10),
                pair("d.jpg", "attack", 40, 40));
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertEquals(4, r.total());
        assertEquals(2, r.bonaFide());
        assertEquals(2, r.attacks());
        assertEquals(4, r.identicalScores());
        assertEquals(0, r.maxAbsDiff());
        assertEquals(0.0, r.meanAbsDiff(), 1e-12);
        assertEquals(1.0, r.correlation(), 1e-12);
        assertEquals(0, r.totalFlips());
        assertTrue(r.generate().contains("None."));
    }

    @Test
    void flipsAreCountedPerDirectionAndLabel() {
        List<DevAgreementReport.Pair> pairs = Arrays.asList(
                pair("bf1.jpg", "bona_fide", 75, 60),
                pair("bf2.jpg", "bona_fide", 60, 75),
                pair("bf3.jpg", "bona_fide", 90, 91),
                pair("at1.jpg", "attack", 30, 80),
                pair("at2.jpg", "attack", 85, 20),
                pair("at3.jpg", "attack", 5, 6));
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertEquals(1, r.bonaFideAcceptToReject());
        assertEquals(1, r.bonaFideRejectToAccept());
        assertEquals(1, r.attackRejectToAccept());
        assertEquals(1, r.attackAcceptToReject());
        assertEquals(4, r.totalFlips());
        assertEquals(0, r.identicalScores());
        String md = r.generate();
        assertTrue(md.contains("| bf1.jpg | bona_fide |"), md);
        assertTrue(md.contains("| at2.jpg | attack | print | 85 | 20 |"), md);
        assertFalse(md.contains("bf3.jpg"), "a non-flipped sample must not be listed");
    }

    @Test
    void scoreEqualToThresholdIsAccepted() {
        List<DevAgreementReport.Pair> pairs = Collections.singletonList(pair("edge.jpg", "bona_fide", 70, 69));
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertEquals(1, r.bonaFideAcceptToReject());
        assertEquals(0, r.bonaFideRejectToAccept());
    }

    @Test
    void differenceStatisticsAreComputedFromPairs() {
        List<DevAgreementReport.Pair> pairs = Arrays.asList(
                pair("a.jpg", "bona_fide", 80, 82),
                pair("b.jpg", "bona_fide", 90, 87),
                pair("c.jpg", "attack", 10, 10));
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertEquals(1, r.identicalScores());
        assertEquals(3, r.maxAbsDiff());
        assertEquals(5.0 / 3.0, r.meanAbsDiff(), 1e-12);
        assertEquals(-1.0 / 3.0, r.meanSignedDiff(), 1e-12);
    }

    @Test
    void correlationIsNaNWhenOneModelIsConstant() {
        List<DevAgreementReport.Pair> pairs = Arrays.asList(
                pair("a.jpg", "bona_fide", 50, 10),
                pair("b.jpg", "attack", 50, 90));
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertTrue(Double.isNaN(r.correlation()));
        assertTrue(r.generate().contains("n/a (zero variance)"));
    }

    @Test
    void rejectsTestAndTrainEntriesSoTestLabelsCannotEnterTheReport() {
        IllegalArgumentException t = assertThrows(IllegalArgumentException.class,
                () -> new DevAgreementReport.Pair(entry("t.jpg", "attack", "test"), 10, 10));
        assertTrue(t.getMessage().contains("DEV"), t.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new DevAgreementReport.Pair(entry("t.jpg", "attack", "train"), 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new DevAgreementReport.Pair(null, 10, 10));
    }

    @Test
    void devOnlyFiltersOutTrainAndTest() {
        List<ManifestEntry> all = new ArrayList<>();
        all.add(entry("a.jpg", "bona_fide", "train"));
        all.add(entry("b.jpg", "bona_fide", "dev"));
        all.add(entry("c.jpg", "attack", "test"));
        all.add(entry("d.jpg", "attack", "dev"));
        List<ManifestEntry> dev = DevAgreementReport.devOnly(all);
        assertEquals(2, dev.size());
        for (ManifestEntry e : dev) {
            assertEquals("dev", e.split());
        }
    }

    @Test
    void rejectsInvalidArguments() {
        List<DevAgreementReport.Pair> one = Collections.singletonList(pair("a.jpg", "bona_fide", 80, 80));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute("FP32", "INT8", Collections.emptyList(), 70));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute("FP32", "INT8", null, 70));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute("FP32", "INT8", one, -1));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute("FP32", "INT8", one, 101));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute(" ", "INT8", one, 70));
        assertThrows(IllegalArgumentException.class, () -> DevAgreementReport.compute("FP32", null, one, 70));
        assertThrows(IllegalArgumentException.class, () -> new DevAgreementReport.Pair(entry("a.jpg", "attack", "dev"), 101, 0));
        assertThrows(IllegalArgumentException.class, () -> new DevAgreementReport.Pair(entry("a.jpg", "attack", "dev"), 0, -1));
    }

    @Test
    void flipListingIsCappedButCountsStayComplete() {
        List<DevAgreementReport.Pair> pairs = new ArrayList<>();
        int flips = DevAgreementReport.MAX_LISTED_FLIPS + 5;
        for (int i = 0; i < flips; i++) {
            pairs.add(pair("img" + i + ".jpg", "bona_fide", 90, 10));
        }
        DevAgreementReport r = DevAgreementReport.compute("FP32", "INT8", pairs, 70);
        assertEquals(flips, r.totalFlips());
        String md = r.generate();
        assertTrue(md.contains("Listing capped at " + DevAgreementReport.MAX_LISTED_FLIPS + " of " + flips + " flips."), md);
        assertTrue(md.contains("img0.jpg"));
        assertFalse(md.contains("img" + (flips - 1) + ".jpg"));
    }

    @Test
    void reportUsesDecimalPointUnderGermanLocale() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("0,5", String.format("%.1f", 0.5), "guard: default locale must use a decimal comma for this test to mean anything");
        List<DevAgreementReport.Pair> pairs = Arrays.asList(
                pair("a.jpg", "bona_fide", 80, 81),
                pair("b.jpg", "attack", 10, 12),
                pair("c.jpg", "attack", 20, 19));
        String md = DevAgreementReport.compute("FP32", "INT8", pairs, 70).generate();
        assertFalse(md.matches("(?s).*\\d,\\d.*"), "decimal comma found in: " + md);
        assertTrue(md.contains("Mean absolute difference: 1.3333"), md);
    }

    @Test
    void reportStatesDevOnlyAndThreshold() {
        String md = DevAgreementReport.compute("FP32", "INT8",
                Collections.singletonList(pair("a.jpg", "bona_fide", 80, 80)), 70).generate();
        assertTrue(md.contains("Split: dev only. Threshold: 70"), md);
        assertTrue(md.contains("| FP32 | 0 of 1 | 0 of 0 |"), md);
        assertTrue(md.contains("| INT8 | 0 of 1 | 0 of 0 |"), md);
    }
}
