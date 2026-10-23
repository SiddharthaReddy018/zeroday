package liveness.android;

import liveness.android.EnvironmentIntegrityChecker.Finding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static liveness.android.AndroidTestSupport.FakeProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checker lower-cases module names and device strings before matching. With the JVM default
 * locale set to Turkish, String.toLowerCase() maps 'I' to a dotless i, so "FRIDA" would no longer
 * contain "frida" and an uppercase emulator string would stop matching. The checker must be
 * locale-independent.
 */
class EnvironmentIntegrityCheckerLocaleTest {

    private Locale saved;

    @BeforeEach
    void useTurkishDefaultLocale() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        // Guard: prove this test really runs under the locale that triggers the bug.
        assertEquals("ı", "I".toLowerCase(), "default-locale lower-casing must map I to dotless i for this test to mean anything");
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(saved);
    }

    private static EnvironmentIntegrityChecker.Result run(FakeProbe p) {
        return new EnvironmentIntegrityChecker(p).check();
    }

    @Test
    void fridaModuleWithCapitalIIsDetectedUnderTurkishLocale() {
        FakeProbe p = new FakeProbe();
        p.modules.add("/data/local/tmp/FRIDA-AGENT-64.SO");
        assertTrue(run(p).findings().contains(Finding.HOOK_FRIDA));
    }

    @Test
    void emulatorModelWithCapitalIIsDetectedUnderTurkishLocale() {
        FakeProbe p = new FakeProbe();
        p.props.put("ro.product.model", "ANDROID SDK BUILT FOR X86");
        assertTrue(run(p).findings().contains(Finding.EMULATOR));
    }
}
