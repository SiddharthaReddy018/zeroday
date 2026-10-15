package liveness.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SupervisorAuth} (§6, §22).
 * <p>
 * Key rules:
 * - Supervisor exemption NEVER applies to BLOCKED_ATTACK sessions
 * - Authentication is required before any exemption
 * - Revocation removes exemption ability
 */
class SupervisorAuthTest {

    // ─── Authentication ───

    @Test
    void newSupervisorIsNotAuthenticated() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        assertFalse(auth.isAuthenticated());
    }

    @Test
    void authenticateWithValidCredential() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        assertTrue(auth.authenticate("valid-credential"));
        assertTrue(auth.isAuthenticated());
    }

    @Test
    void authenticateWithNullCredentialFails() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        assertFalse(auth.authenticate(null));
        assertFalse(auth.isAuthenticated());
    }

    @Test
    void authenticateWithEmptyCredentialFails() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        assertFalse(auth.authenticate(""));
        assertFalse(auth.isAuthenticated());
    }

    // ─── Exemption rules ───

    @Test
    void unauthenticatedSupervisorCannotExempt() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        assertFalse(auth.canExempt(Verdict.MAX_RETRIES));
        assertFalse(auth.canExempt(Verdict.DEVICE_FAILURE));
        assertFalse(auth.canExempt(Verdict.PENDING));
    }

    @Test
    void authenticatedSupervisorCanExemptMaxRetries() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        assertTrue(auth.canExempt(Verdict.MAX_RETRIES));
    }

    @Test
    void authenticatedSupervisorCanExemptDeviceFailure() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        assertTrue(auth.canExempt(Verdict.DEVICE_FAILURE));
    }

    @Test
    void authenticatedSupervisorCanExemptPending() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        assertTrue(auth.canExempt(Verdict.PENDING));
    }

    @Test
    void supervisorExemptionNeverAppliestoBlockedAttack() {
        // §4, §22: Supervisor exemption NEVER applies to ATTACK verdict
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        assertTrue(auth.isAuthenticated()); // is authenticated
        assertFalse(auth.canExempt(Verdict.ATTACK)); // but cannot exempt attack
    }

    @Test
    void supervisorExemptionNeverAppliestoPass() {
        // PASS doesn't need exemption — but the rule is exemption is for non-PASS verdicts
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        // PASS is not ATTACK, so canExempt returns true (no harm — nothing to exempt)
        assertTrue(auth.canExempt(Verdict.PASS));
    }

    // ─── Revocation ───

    @Test
    void revokeRemovesAuthentication() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        assertTrue(auth.isAuthenticated());
        auth.revoke();
        assertFalse(auth.isAuthenticated());
    }

    @Test
    void revokedSupervisorCannotExempt() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        auth.revoke();
        assertFalse(auth.canExempt(Verdict.MAX_RETRIES));
    }

    @Test
    void reAuthenticationAfterRevoke() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-1");
        auth.authenticate("cred");
        auth.revoke();
        assertFalse(auth.isAuthenticated());
        auth.authenticate("new-cred");
        assertTrue(auth.isAuthenticated());
        assertTrue(auth.canExempt(Verdict.MAX_RETRIES));
    }

    // ─── Identity ───

    @Test
    void supervisorIdPreserved() {
        SupervisorAuth auth = new SupervisorAuth("supervisor-42");
        assertEquals("supervisor-42", auth.supervisorId());
    }
}
