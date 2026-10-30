# UI/UX Specification & Inclusive Design Guide

> **Status note (2026-10-30, ADR-018):** Android uses the FP32 PAD model (ADR-017). SBI/MDS hardware integration is OUT OF SCOPE for the submission: the SBI code is implemented against the spec and unit-tested only, never exercised on a device. The submission demo is camera-only on the host app. Where this document says otherwise, this note wins. Not re-edited line by line.

## MOSIP Face Liveness Detection + Presentation Attack Detection
### Version: 0.6.0 | MOSIP Decode 2026 — Problem 04

---

## 1. Executive Summary & Design Principles

The MOSIP Face Liveness and Presentation Attack Detection (PAD) UI/UX is built to serve diverse global populations across desktop registration centers and mobile field deployments. Biometric capture in national identity systems must balance strict anti-spoofing security with dignity, accessibility, and clarity.

### Core Principles
1. **Never Indicate Status by Color Alone:** Every state transition uses distinct icons, clear typography, and tactile/auditory cues so color-blind or low-vision residents have equal feedback.
2. **Language-Independent Icons:** Visual animations (e.g., blinking eyes, turning arrows, smiling mouth) guide users intuitively regardless of literacy or language barriers.
3. **No Model Internals Disclosed to End Users:** Residents receive calm, actionable instructions without revealing PAD confidence scores, internal band cutoffs, or feature weights.
4. **Decoupled Asynchronous UI:** The UI never drives the biometric pipeline directly. All visual scenes are rendered reactively from typed events (`UiEvent`) emitted over `ui-contract/events-schema.json`.
5. **Supervisor-Only Diagnostics:** Advanced metrics, raw frame dimensions, luma histograms, and sensor metadata are isolated to an authenticated supervisor view.

---

## 2. Personas & Three-Screen Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MOSIP Registration Client                       │
├─────────────────────┬───────────────────────────┬──────────────────────┤
│ 1. Resident Screen  │ 2. Operator Screen        │ 3. Supervisor Screen │
│ (Facing Enrollee)   │ (Enrolment Officer)       │ (Audit & Override)   │
├─────────────────────┼───────────────────────────┼──────────────────────┤
│ • Fullscreen oval   │ • Small video preview     │ • Diagnostic metrics │
│ • Animated guide    │ • Real-time quality flags │ • Attack reason code │
│ • Challenge prompts │ • Pipeline state badge    │ • Tamper-evident log │
│ • Success / Retry   │ • Manual profile toggle   │ • Secure override    │
└─────────────────────┴───────────────────────────┴──────────────────────┘
```

### 2.1 Resident Face Capture Screen
- **Target Audience:** General public, including elderly residents, persons with disabilities, and young children.
- **Visual Focus:** A high-contrast oval guide positioned in the center of the display.
- **Guidance Elements:**
  - Real-time positioning feedback (arrow indicators for "move closer", "step back", or "center face").
  - Challenge animations overlaid directly on the oval guide.
  - Clear countdown bar during active challenge phases.
  - Unambiguous terminal screens: Green checkmark for success, neutral prompt with remaining attempt counter for retries.

### 2.2 Operator Monitoring Screen
- **Target Audience:** Enrolment officers managing the registration station.
- **Visual Focus:** Compact split-pane layout showing camera feed, session lifecycle, and environmental quality indicators.
- **Real-Time Indicators:**
  - **Quality Gates:** Lighting adequacy (lux), focus sharpness (Laplacian variance), face size ratio, and head pose angles (yaw, pitch, roll in degrees).
  - **Session State:** Current state (`POSITIONING`, `PASSIVE`, `CHALLENGE_WAIT`, `FINAL_CAPTURE`).
  - **Profile Selector:** Manual toggle to activate `ACCESSIBILITY` or `INFANT` profiles when appropriate.
  - **Actionable Hints:** Clear messages such as *"Subject needs better ambient lighting"* or *"Adjust camera height to level position"*.

### 2.3 Supervisor Authentication & Override Screen
- **Target Audience:** Certified registration supervisors with cryptographic smartcards or biometrics.
- **Security Invariant:** Presentations blocked as presentation attacks (`BLOCKED_ATTACK`) can **never** be overridden by a supervisor.
- **Capabilities:**
  - Review persistent audit records with rolling SHA-256 hash chains.
  - Inspect detailed non-identifying telemetry (e.g., failure due to `DEVICE_DISCONNECT` or `MAX_RETRIES_EXCEEDED`).
  - Authorize benign quality failure exemptions (e.g., resident with permanent ocular conditions unable to complete eye blinks).

---

## 3. The 8 Interface States & Guidance Mapping

The UI is driven directly by the 13-state machine via mapped `UiEventType` events:

```
      ┌─────────────────────────────────────────────────────────┐
      │                      1. POSITIONING                     │
      │  "Position face in oval" · Directional alignment arrows  │
      └────────────────────────────┬────────────────────────────┘
                                   │ Quality checks pass
                                   ▼
      ┌─────────────────────────────────────────────────────────┐
      │                        2. PASSIVE                       │
      │   "Checking face liveness..." · Gentle progress spinner │
      └──────────────────┬───────────────────┬──────────────────┘
    Score >= 70          │                   │ Score < 30 (persistent)
   (Clear Bona Fide)     │ Score 30-69       │ (Clear Attack)
                         │ (Uncertain)       ▼
                         ▼      ┌───────────────────────────────┐
  ┌──────────────────────────┐  │       7. BLOCKED_ATTACK       │
  │   3. CHALLENGE_PROMPT    │  │ "Verification could not be    │
  │ "Please blink / turn..." │  │  completed. Please try again" │
  └──────────────┬───────────┘  └───────────────────────────────┘
                 │ Challenge displayed
                 ▼
  ┌──────────────────────────┐
  │    4. CHALLENGE_WAIT     │
  │  "Hold still" · Timer    │
  └──────────────┬───────────┘
                 │ Success
                 ▼
┌───────────────────────────────────────────────────────────────┐
│                           5. SUCCESS                          │
│  "Good, face captured successfully" · Green checkmark · Audio │
└───────────────────────────────────────────────────────────────┘

[On Timeout or Quality Lapse]
┌───────────────────────────────────────────────────────────────┐
│                       6. RETRY_AVAILABLE                      │
│ "We could not verify face liveness. Please try again (2 left)"│
└───────────────────────────────────────────────────────────────┘

[On Device Unplugged / Hardware Failure]
┌───────────────────────────────────────────────────────────────┐
│                        8. DEVICE_ERROR                        │
│   "Camera unavailable. Please check connection and reconnect" │
└───────────────────────────────────────────────────────────────┘
```

### Detailed State Specifications

| State | Resident Message (EN Default) | Visual Elements | Audio Cue |
|---|---|---|---|
| **`POSITIONING`** | "Position your face in the oval" | Pulsing neutral oval border, dynamic alignment arrows | None |
| **`PASSIVE`** | "Checking face liveness…" | Solid blue/cyan oval border, subtle radial spinner | None |
| **`CHALLENGE_PROMPT`** | "Please blink" / "Please smile" / "Please turn your head left" | Large animated glyph illustrating requested motion | Soft chime |
| **`CHALLENGE_WAIT`** | "Please continue" / "Hold still" | Progress ring / countdown bar showing remaining seconds | None |
| **`SUCCESS`** | "Good, face captured successfully" | Solid green border, bold checkmark icon (`✓`) | Confirmation tone |
| **`RETRY_AVAILABLE`** | "We could not verify face liveness. Please try again." | Amber border, retry circular arrow icon, attempts remaining pill | Neutral double-beep |
| **`BLOCKED_ATTACK`** | "Face verification could not be completed. Please try again." | Neutral failure banner, guidance to consult enrolment officer | Low buzz |
| **`DEVICE_ERROR`** | "Camera unavailable. Please check connection." | Disconnect icon, operator attention badge | Alert tone |

---

## 4. Multi-Language & Internationalization (i18n)

All user messages are decoupled from application logic into localized resource bundles (`UiMessages.java`):

### Supported Locales
- **English (`en`):** Default international lingua franca.
- **French (`fr`):** Standard for West and Central African partner deployments.
- **Arabic (`ar`):** Full Right-to-Left (RTL) mirror layout support for Middle East / North Africa deployments.

### Localized String Table

| Key | English (`en`) | French (`fr`) | Arabic (`ar`) [RTL] |
|---|---|---|---|
| `guide.face.position` | Position your face in the oval | Placez votre visage dans l'ovale | ضع وجهك داخل الشكل البيضاوي |
| `guide.face.too_far` | Move closer to the camera | Rapprochez-vous de la caméra | اقترب أكثر من الكاميرا |
| `guide.face.too_close` | Move slightly back | Reculez légèrement | ابتعد قليلاً عن الكاميرا |
| `status.liveness.checking` | Checking face liveness… | Vérification de la vivacité… | جارٍ التحقق من حيوية الوجه... |
| `challenge.prompt.blink` | Please blink your eyes | Veuillez cligner des yeux | يُرجى وميض العينين |
| `challenge.prompt.smile` | Please smile | Veuillez sourire | يُرجى الابتسام |
| `challenge.prompt.turn_left`| Please turn your head to the left | Tournez la tête vers la gauche | يُرجى تحريك الرأس إلى اليسار |
| `status.success` | Face captured successfully | Visage capturé avec succès | تم التقاط صورة الوجه بنجاح |
| `err.liveness.retry` | Could not verify liveness. Try again | Échec de vérification. Réessayez | تعذر التحقق من الحيوية. حاول ثانية |
| `err.device.unavailable` | Camera disconnected or unavailable | Caméra déconnectée ou indisponible | الكاميرا مفصولة أو غير متوفرة |

---

## 5. Accessibility & Inclusive Design Profiles

In adherence to ISO/IEC TR 29794-5 and accessibility research (C19 AMBER project), the system enforces `ChallengeProfile` bounds:

### 1. `DEFAULT` Profile
- Standard adult enrolment flow.
- Active challenge timeout: **5.0 seconds**.
- Full challenge pool enabled: Blink, Smile, Head Turn Left, Head Turn Right.
- Max retries: 3 attempts before `FAILED_MAX_RETRIES`.

### 2. `ACCESSIBILITY` Profile
- Designed for elderly residents, individuals with tremors, or limited motor agility.
- Active challenge timeout: **Relaxed to 10.0 seconds**.
- Modified challenge pool: Excludes rotational head turns (for limited cervical spine mobility); prioritizes simple blinks and mouth smiles.
- Compound challenges disabled (only single challenges issued).
- Max retries: Bounded to 5 attempts to prevent participant fatigue.

### 3. `LOW_QUALITY` Profile
- Designed for remote field environments with non-uniform sunlight or low-grade optics.
- Wider exposure and contrast tolerance window before raising positioning alerts.
- Extended frame accumulation buffer (requires 8 qualifying frames instead of 5 for noise smoothing).

### 4. `INFANT` Profile
- Dedicated policy for children under 5 years (`agegroup-config`).
- **Passive-Only Execution:** Active challenge-response is strictly disabled.
- Operator presence verification and attestation token signing.
- Prevents false rejections caused by young children's natural inability to follow automated prompts.

> [!IMPORTANT]
> **Security Invariant (§R5):** Profiles may relax timeout durations, retry counts, and challenge pools, but **never** lower security thresholds (`attackThreshold`, `passThreshold`, persistence windows, or same-face distance).

---

## 6. Decoupled Event Contract (`ui-contract/events-schema.json`)

All UI updates flow asynchronously from the engine to the UI layer using typed JSON events. The UI never queries the state machine directly:

```json
{
  "sessionId": "b10907f1-370e-41c1-a0d3-7d2863352376",
  "eventType": "CHALLENGE_ISSUED",
  "state": "CHALLENGE_PROMPT",
  "timestampMs": 1771459200000,
  "payload": {
    "challengeId": "ch-7a8f9b",
    "action": "BLINK",
    "timeoutMs": 5000,
    "icon": "icon_blink_animation",
    "localizedMessage": "Please blink your eyes"
  }
}
```

This unidirectional event architecture guarantees that the identical core logic drives both the JavaFX desktop registration client and the mobile Flutter UI without code duplication or state desynchronization.
