package liveness.audit;

import liveness.util.Sha256;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hash-chained audit log (§6.6).
 * <p>
 * {@code hash[i] = SHA-256( hash[i-1] || entry[i].canonical() )}
 * <p>
 * First entry uses {@code "GENESIS"} as the previous hash.
 * <p>
 * {@link #verify()} checks chain integrity.
 * {@link #verifySealed()} additionally checks the log has not grown since
 * {@link #seal()} was called.
 */
public final class AuditChain {

    private static final String GENESIS = "GENESIS";

    private final List<AuditEvent> entries = new ArrayList<>();
    private final List<String> hashes = new ArrayList<>();
    private int sealedSize = -1;

    /**
     * Appends an event and computes its chain hash.
     *
     * @throws IllegalStateException if the chain has been sealed
     */
    public void append(AuditEvent event) {
        if (sealedSize >= 0) {
            throw new IllegalStateException("Audit chain is sealed; cannot append");
        }
        String previousHash = hashes.isEmpty() ? GENESIS : hashes.get(hashes.size() - 1);
        String combined = previousHash + event.canonical();
        String hash = Sha256.hexOf(combined);
        entries.add(event);
        hashes.add(hash);
    }

    /**
     * Verifies the integrity of the entire chain.
     *
     * @return true if every hash matches re-computation from scratch
     */
    public boolean verify() {
        if (entries.size() != hashes.size()) {
            return false;
        }
        String prev = GENESIS;
        for (int i = 0; i < entries.size(); i++) {
            String combined = prev + entries.get(i).canonical();
            String expected = Sha256.hexOf(combined);
            if (!expected.equals(hashes.get(i))) {
                return false;
            }
            prev = hashes.get(i);
        }
        return true;
    }

    /**
     * Seals the chain at its current length.
     * After sealing, no new entries may be appended, and {@link #verifySealed()}
     * will also check that the log has not grown.
     */
    public void seal() {
        this.sealedSize = entries.size();
    }

    /**
     * Verifies chain integrity AND that no entries were added after seal.
     *
     * @return true if verify() passes and entries.size() == sealedSize
     * @throws IllegalStateException if seal() was never called
     */
    public boolean verifySealed() {
        if (sealedSize < 0) {
            throw new IllegalStateException("Chain was never sealed");
        }
        return entries.size() == sealedSize && verify();
    }

    /** Number of entries in the chain. */
    public int size() {
        return entries.size();
    }

    /** Unmodifiable view of entries. */
    public List<AuditEvent> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Unmodifiable view of hashes. */
    public List<String> hashes() {
        return Collections.unmodifiableList(hashes);
    }

    /** Latest hash, or GENESIS if empty. */
    public String latestHash() {
        return hashes.isEmpty() ? GENESIS : hashes.get(hashes.size() - 1);
    }

    /** True if the chain has been sealed. */
    public boolean isSealed() {
        return sealedSize >= 0;
    }

    /** Size when sealed, or -1 if unsealed. */
    public int sealedSize() {
        return sealedSize;
    }

    /**
     * Exports the sealed audit trail as a formatted string.
     *
     * @throws IllegalStateException if the chain has not been sealed
     */
    public String exportTrail() {
        if (sealedSize < 0) {
            throw new IllegalStateException("Audit chain must be sealed before export");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AUDIT_TRAIL_V1\n");
        sb.append("SEALED_SIZE:").append(sealedSize).append("\n");
        sb.append("HEAD_HASH:").append(latestHash()).append("\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(i).append(":").append(hashes.get(i)).append(":").append(entries.get(i).canonical()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Reconstructs an AuditChain from an exported trail string and seals it.
     */
    public static AuditChain importTrail(String exported) {
        if (exported == null || exported.isEmpty()) {
            throw new IllegalArgumentException("Exported trail cannot be null or empty");
        }
        AuditChain chain = new AuditChain();
        String[] lines = exported.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("AUDIT_TRAIL_V1")
                    || line.startsWith("SEALED_SIZE:") || line.startsWith("HEAD_HASH:")) {
                continue;
            }
            int firstColon = line.indexOf(':');
            int secondColon = line.indexOf(':', firstColon + 1);
            if (firstColon >= 0 && secondColon > firstColon) {
                String canonical = line.substring(secondColon + 1);
                String[] parts = canonical.split("\\|", 4);
                if (parts.length == 4) {
                    chain.append(new AuditEvent(Long.parseLong(parts[0]), parts[1], parts[2], parts[3]));
                }
            }
        }
        chain.seal();
        return chain;
    }

    // --- Package-private for testing: allows tampering to prove verify() catches it ---

    /**
     * FOR TESTING ONLY. Replaces an entry to simulate tampering.
     */
    void tamperEntry(int index, AuditEvent replacement) {
        entries.set(index, replacement);
    }
}
