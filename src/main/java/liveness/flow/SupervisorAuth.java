package liveness.flow;

/**
 * Supervisor authentication for exemptions (§6, §22).
 * <p>
 * Supervisor exemptions may apply to:
 * - Age/disability policy relaxations
 * - Retry budget extensions (except for ATTACK band sessions)
 * <p>
 * Supervisor exemption NEVER applies to a session ended in BLOCKED_ATTACK.
 * All exemption attempts are audited.
 */
public final class SupervisorAuth {

    private final String supervisorId;
    private boolean authenticated;

    /**
     * @param supervisorId the supervisor's identifier
     */
    public SupervisorAuth(String supervisorId) {
        this.supervisorId = supervisorId;
        this.authenticated = false;
    }

    /**
     * Authenticates the supervisor with a credential.
     * In production this validates against the biometric store.
     *
     * @param credential the supervisor's authentication credential
     * @return true if authentication succeeded
     */
    public boolean authenticate(String credential) {
        // M2 stub: accept any non-null, non-empty credential
        // Production: biometric verification against onboarded template
        if (credential != null && !credential.isEmpty()) {
            this.authenticated = true;
            return true;
        }
        return false;
    }

    /**
     * Checks if a supervisor exemption is allowed for the given verdict.
     * <p>
     * NEVER allowed for ATTACK band sessions (§4, §22).
     *
     * @param verdict the session's verdict
     * @return true if exemption is permitted
     */
    public boolean canExempt(Verdict verdict) {
        if (!authenticated) {
            return false;
        }
        // Supervisor exemption never applies to attack verdicts
        return verdict != Verdict.ATTACK;
    }

    /** Whether the supervisor has been authenticated. */
    public boolean isAuthenticated() { return authenticated; }

    /** The supervisor's identifier. */
    public String supervisorId() { return supervisorId; }

    /** Revokes authentication (e.g., session end or timeout). */
    public void revoke() {
        this.authenticated = false;
    }
}
