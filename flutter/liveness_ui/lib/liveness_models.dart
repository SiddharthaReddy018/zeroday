/// Models for ui-contract/events-schema.json. The UI only RENDERS these; it never decides liveness.

/// The 8 UI states (MASTER_PLAN S9: preview+oval+guidance, liveness status+progress, challenge prompt,
/// success, retry, max-retry, device error) plus an initial "connecting" state.
enum LivenessUiState {
  connecting,
  positioning,
  checking,
  challenge,
  success,
  retry,
  maxRetry,
  deviceError,
}

/// Maps the 13 engine states onto the 8 UI states.
LivenessUiState uiStateFor(String engineState) {
  switch (engineState) {
    case 'IDLE':
    case 'DEVICE_CHECK':
      return LivenessUiState.connecting;
    case 'POSITIONING':
      return LivenessUiState.positioning;
    case 'PASSIVE':
    case 'FINAL_CAPTURE':
    case 'VERIFYING_FINAL':
      return LivenessUiState.checking;
    case 'CHALLENGE_PROMPT':
    case 'CHALLENGE_WAIT':
      return LivenessUiState.challenge;
    case 'SUCCESS':
      return LivenessUiState.success;
    case 'RETRY_AVAILABLE':
      return LivenessUiState.retry;
    case 'BLOCKED_ATTACK':
    case 'VERIFICATION_FAILED':
    case 'FAILED_MAX_RETRIES':
      return LivenessUiState.maxRetry;
    case 'DEVICE_ERROR':
      return LivenessUiState.deviceError;
    default:
      return LivenessUiState.connecting;
  }
}

enum ChallengeAction { blink, smile, turnLeft, turnRight, lookLeft, lookRight, lookUp, lookDown }

ChallengeAction? challengeFromWire(String? s) {
  switch (s) {
    case 'BLINK': return ChallengeAction.blink;
    case 'SMILE': return ChallengeAction.smile;
    case 'TURN_LEFT': return ChallengeAction.turnLeft;
    case 'TURN_RIGHT': return ChallengeAction.turnRight;
    case 'LOOK_LEFT': return ChallengeAction.lookLeft;
    case 'LOOK_RIGHT': return ChallengeAction.lookRight;
    case 'LOOK_UP': return ChallengeAction.lookUp;
    case 'LOOK_DOWN': return ChallengeAction.lookDown;
    default: return null;
  }
}

/// Immutable view-model folded from schema events.
class LivenessViewModel {
  final LivenessUiState state;
  final String message; // localized by the engine
  final bool rtl;
  final double progress; // 0..1
  final ChallengeAction? challenge;
  final int challengeTimeoutMs;
  final String? guidanceKey;
  final String? verdict;
  final String? errorCode;

  const LivenessViewModel({
    this.state = LivenessUiState.connecting,
    this.message = '',
    this.rtl = false,
    this.progress = 0,
    this.challenge,
    this.challengeTimeoutMs = 0,
    this.guidanceKey,
    this.verdict,
    this.errorCode,
  });

  LivenessViewModel apply(Map<dynamic, dynamic> event) {
    final type = event['eventType'] as String?;
    final data = (event['data'] as Map?) ?? const {};
    switch (type) {
      case 'STATE_CHANGE':
        final s = data['currentState'] as String? ?? '';
        return _copy(
          state: uiStateFor(s),
          message: data['message'] as String? ?? message,
          rtl: data['rtl'] as bool? ?? rtl,
          clearChallenge: uiStateFor(s) != LivenessUiState.challenge,
          clearGuidance: uiStateFor(s) != LivenessUiState.positioning,
        );
      case 'CHALLENGE_ISSUED':
        return _copy(
          state: LivenessUiState.challenge,
          message: data['message'] as String? ?? message,
          challenge: challengeFromWire(data['challengeAction'] as String?),
          challengeTimeoutMs: (data['challengeTimeoutMs'] as num?)?.toInt() ?? 0,
        );
      case 'CAPTURE_PROGRESS':
        return _copy(progress: (data['captureProgress'] as num?)?.toDouble() ?? progress);
      case 'QUALITY_GUIDANCE':
        return _copy(guidanceKey: data['guidanceKey'] as String?);
      case 'SESSION_COMPLETE':
        final v = data['verdict'] as String?;
        final st = uiStateFor(data['currentState'] as String? ?? '');
        return _copy(
          verdict: v,
          state: st,
          message: data['message'] as String? ?? message,
          clearChallenge: true,
        );
      case 'ERROR':
        return _copy(errorCode: data['errorCode'] as String?, message: data['message'] as String? ?? message);
      default:
        return this;
    }
  }

  LivenessViewModel _copy({
    LivenessUiState? state,
    String? message,
    bool? rtl,
    double? progress,
    ChallengeAction? challenge,
    int? challengeTimeoutMs,
    String? guidanceKey,
    String? verdict,
    String? errorCode,
    bool clearChallenge = false,
    bool clearGuidance = false,
  }) =>
      LivenessViewModel(
        state: state ?? this.state,
        message: message ?? this.message,
        rtl: rtl ?? this.rtl,
        progress: progress ?? this.progress,
        challenge: clearChallenge ? null : (challenge ?? this.challenge),
        challengeTimeoutMs: challengeTimeoutMs ?? this.challengeTimeoutMs,
        guidanceKey: clearGuidance ? null : (guidanceKey ?? this.guidanceKey),
        verdict: verdict ?? this.verdict,
        errorCode: errorCode ?? this.errorCode,
      );
}
