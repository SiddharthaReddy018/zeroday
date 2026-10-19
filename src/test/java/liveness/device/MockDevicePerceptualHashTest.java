package liveness.device;

import liveness.core.FaceFrame;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MockDevicePerceptualHashTest {

    private static Set<Long> hashes(MockDeviceAdapter.Scenario s, int n) throws Exception {
        MockDeviceAdapter d = new MockDeviceAdapter(s);
        d.open();
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < n; i++) {
            FaceFrame f = d.captureFrame();
            out.add(f.pHash());
        }
        return out;
    }

    @Test
    void bonaFideFramesAreAllDistinct() throws Exception {
        assertEquals(12, hashes(MockDeviceAdapter.Scenario.BONA_FIDE, 12).size());
    }

    @Test
    void freezeRepeatsOneHash() throws Exception {
        assertEquals(1, hashes(MockDeviceAdapter.Scenario.FRAME_FREEZE, 12).size());
    }

    @Test
    void loopRepeatsWithItsCycle() throws Exception {
        Set<Long> h = hashes(MockDeviceAdapter.Scenario.FRAME_LOOP, 24);
        assertTrue(h.size() > 1 && h.size() < 24, "loop must repeat: " + h.size());
    }

    @Test
    void captureModeDefaultsToStream() {
        assertEquals("STREAM", new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE).captureMode());
    }
}
