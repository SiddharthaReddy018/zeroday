package liveness.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluation output feeds the published APCER/BPCER/latency numbers, so it must not depend on the
 * machine's default locale: no decimal commas (German, French, ...) and no Turkish dotless-i
 * surprises when matching manifest column names.
 */
public class EvaluationLocaleTest {

    private Locale saved;

    @BeforeEach
    void rememberLocale() {
        saved = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(saved);
    }

    @Test
    void wilsonIntervalUsesDecimalPointUnderGermanLocale() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("0,5", String.format("%.1f", 0.5), "guard: default locale must use a decimal comma for this test to mean anything");
        String s = WilsonConfidenceInterval.compute(10, 100).toString();
        assertTrue(s.matches("\\[\\d\\.\\d{4}, \\d\\.\\d{4}\\]"), s);
    }

    @Test
    void latencyReportUsesDecimalPointUnderGermanLocale() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("0,5", String.format("%.1f", 0.5), "guard: default locale must use a decimal comma for this test to mean anything");
        LatencyMetrics m = new LatencyMetrics();
        for (int i = 1; i <= 20; i++) m.record("pad_infer", i * 1_250_000L);
        String report = m.report();
        assertTrue(report.contains("pad_infer"), report);
        assertFalse(report.matches("(?s).*\\d,\\d.*"), "decimal comma found in: " + report);
    }

    @Test
    void manifestUppercaseHeadersAreRecognisedUnderTurkishLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertEquals("ı", "I".toLowerCase(), "guard: default locale must map I to dotless i for this test to mean anything");
        String csv = "PATH,LABEL,SPECIES,SUBJECT_ID,SPLIT\n"
                + "img001.jpg,bona_fide,,subj_001,test\n";
        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(1, entries.size());
        assertEquals("subj_001", entries.get(0).subjectId());
    }
}
