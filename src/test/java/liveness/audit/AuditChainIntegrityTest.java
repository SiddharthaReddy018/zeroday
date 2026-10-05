package liveness.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hash-chained audit log (§6.6).
 * <ul>
 *   <li>Append entries, verify() passes</li>
 *   <li>Tamper one entry, verify() fails</li>
 *   <li>Seal + verifySealed()</li>
 * </ul>
 */
class AuditChainIntegrityTest {

    @Test
    void emptyChainVerifies() {
        AuditChain chain = new AuditChain();
        assertTrue(chain.verify(), "Empty chain should verify");
    }

    @Test
    void singleEntryVerifies() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        assertTrue(chain.verify());
        assertEquals(1, chain.size());
    }

    @Test
    void multipleEntriesVerify() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.append(new AuditEvent(1001L, "STATE", "TRANSITION", "from=DEVICE_CHECK,to=POSITIONING"));
        chain.append(new AuditEvent(1002L, "PAD", "SCORE", "scorer=MiniFASNet,score=85,quality=0.9"));
        chain.append(new AuditEvent(1003L, "STATE", "TRANSITION", "from=PASSIVE,to=FINAL_CAPTURE"));

        assertTrue(chain.verify());
        assertEquals(4, chain.size());
    }

    @Test
    void tamperingCausesVerifyToFail() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.append(new AuditEvent(1001L, "STATE", "TRANSITION", "from=DEVICE_CHECK,to=POSITIONING"));
        chain.append(new AuditEvent(1002L, "PAD", "SCORE", "scorer=MiniFASNet,score=85,quality=0.9"));

        assertTrue(chain.verify(), "Chain should verify before tampering");

        // Tamper the middle entry
        chain.tamperEntry(1, new AuditEvent(1001L, "STATE", "TRANSITION", "from=DEVICE_CHECK,to=SUCCESS"));

        assertFalse(chain.verify(), "Chain should fail verification after tampering");
    }

    @Test
    void tamperingFirstEntryCausesVerifyToFail() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.append(new AuditEvent(1001L, "PAD", "SCORE", "score=50"));

        assertTrue(chain.verify());

        // Tamper the first entry (Genesis-dependent)
        chain.tamperEntry(0, new AuditEvent(9999L, "TAMPERED", "EVIL", "hack=true"));

        assertFalse(chain.verify());
    }

    @Test
    void tamperingLastEntryCausesVerifyToFail() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.append(new AuditEvent(1001L, "PAD", "SCORE", "score=50"));
        chain.append(new AuditEvent(1002L, "STATE", "TRANSITION", "from=PASSIVE,to=SUCCESS"));

        assertTrue(chain.verify());
        chain.tamperEntry(2, new AuditEvent(1002L, "STATE", "TRANSITION", "from=PASSIVE,to=BLOCKED_ATTACK"));
        assertFalse(chain.verify());
    }

    // ─── Seal tests ───

    @Test
    void sealPreventsFurtherAppends() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.seal();

        assertThrows(IllegalStateException.class,
                () -> chain.append(new AuditEvent(2000L, "STATE", "EXTRA", "should=fail")));
    }

    @Test
    void verifySealedPassesWhenUnchanged() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "from=IDLE,to=DEVICE_CHECK"));
        chain.append(new AuditEvent(1001L, "PAD", "SCORE", "score=90"));
        chain.seal();

        assertTrue(chain.verifySealed());
    }

    @Test
    void verifySealedThrowsIfNotSealed() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "event=test"));

        assertThrows(IllegalStateException.class, chain::verifySealed);
    }

    // ─── Hash continuity ───

    @Test
    void hashesAreDistinct() {
        AuditChain chain = new AuditChain();
        chain.append(new AuditEvent(1000L, "A", "B", "C"));
        chain.append(new AuditEvent(1001L, "D", "E", "F"));

        assertNotEquals(chain.hashes().get(0), chain.hashes().get(1),
                "Different entries should produce different hashes");
    }

    @Test
    void latestHashReturnsGenesisWhenEmpty() {
        AuditChain chain = new AuditChain();
        assertEquals("GENESIS", chain.latestHash());
    }

    @Test
    void latestHashUpdatesOnAppend() {
        AuditChain chain = new AuditChain();
        String initial = chain.latestHash();

        chain.append(new AuditEvent(1000L, "STATE", "TRANSITION", "test"));
        assertNotEquals(initial, chain.latestHash());
    }

    // ─── AuditLog integration ───

    @Test
    void auditLogDelegatesToChain() {
        AuditLog log = new AuditLog();
        log.logTransition("IDLE", "DEVICE_CHECK", "START_CAPTURE");
        log.logScore("MiniFASNet", 85, 0.95);
        log.logBand("CLEAR_BONA_FIDE", 85, 3);

        assertEquals(3, log.chain().size());
        assertTrue(log.chain().verify());
    }

    @Test
    void auditLogSeal() {
        AuditLog log = new AuditLog();
        log.logTransition("IDLE", "DEVICE_CHECK", "START_CAPTURE");
        log.seal();

        assertTrue(log.chain().verifySealed());
    }
}
