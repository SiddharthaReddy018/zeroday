# Architecture Decision Records (ADR)

## ADR-001: DEVICE_ERROR is terminal per session, recovery via new session

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 16.2)  
**Context:** S4 calls DEVICE_ERROR terminal, S23.4 describes reconnecting.  
**Decision:** DEVICE_ERROR is terminal for the session. Recovery is always a new session starting at DEVICE_CHECK (MOSIPDISC rediscovery). The transition carries a reason code:
- `DEVICE_UNAVAILABLE` (benign): free — no retry budget charged; bounded by `device.max_reconnect_attempts`.
- `INTEGRITY_FAILURE` (security): counts toward the lock budget; supervisor exemption not applicable.  
**Consequence:** No boolean flags needed; reason is part of the transition event.

## ADR-002: RETRY_AVAILABLE → PASSIVE retry path

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 15.7 item 4)  
**Context:** Problem statement requires retry support with clear indication and recovery guidance.  
**Decision:** RETRY_AVAILABLE → PASSIVE (row 22), which can fall back to POSITIONING by row 9 if quality drops. Matches the problem statement's resident workflow.  
**Consequence:** Retry always goes through PASSIVE, not directly to POSITIONING.

## ADR-003: FINAL_CAPTURE RCAPTURE failure → DEVICE_ERROR

**Date:** 2026-10-06  
**Status:** DECIDED (HANDOFF-2 16.2 row 17)  
**Context:** Problem statement requires handling device communication failures.  
**Decision:** RCAPTURE failure/timeout → DEVICE_ERROR with reason DEVICE_UNAVAILABLE.  
**Consequence:** Subject is guided to retry via new session; no budget charged.

## ADR-004: BLOCKED_ATTACK terminal for session, not person

**Date:** 2026-10-06  
**Status:** DECIDED (MASTER_PLAN §4)  
**Context:** v1 said "no retry ever" which conflicts with the problem statement's retry requirement.  
**Decision:** BLOCKED_ATTACK is terminal per session. A new session is allowed through RetryRateLimiter. Each blocked session counts against the lock budget. Supervisor exemption never applies.  
**Consequence:** Prevents abuse while allowing legitimate re-enrollment.

## ADR-005: Standalone reference implementation + thin integration shim

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, §14 item 10)  
**Context:** Unknown whether this is a patch against the real registration client.  
**Decision:** Ship standalone + thin integration shim. Do not invent MOSIP class names.  
**Consequence:** Minimizes integration risk; can be adapted later.

## ADR-006: Infant/guardian policy — passive-only + operator attestation

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, §14 item 9)  
**Context:** Adult-trained quality models over-reject young children (U26).  
**Decision:** Passive-only with operator attestation for INFANT profile.  
**Consequence:** ChallengeProfile.INFANT has longer timeouts, no compound challenges.

## ADR-007: Auth-purpose devices — DISCRETE mode

**Date:** 2026-10-06  
**Status:** ASSUMPTION (pending MOSIP answer, §14 item 7)  
**Context:** Auth-purpose devices have no STREAM, no preview.  
**Decision:** Assume DISCRETE mode; document the limitation.  
**Consequence:** Liveness evidence is reduced for auth devices; same-face check + time gap compensate.
