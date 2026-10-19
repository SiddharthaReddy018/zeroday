import 'package:flutter_test/flutter_test.dart';
import 'package:liveness_ui/liveness_models.dart';

void main() {
  test('all 13 engine states map onto the 8 UI states', () {
    const states = [
      'IDLE', 'DEVICE_CHECK', 'POSITIONING', 'PASSIVE', 'CHALLENGE_PROMPT', 'CHALLENGE_WAIT',
      'FINAL_CAPTURE', 'VERIFYING_FINAL', 'SUCCESS', 'RETRY_AVAILABLE', 'BLOCKED_ATTACK',
      'FAILED_MAX_RETRIES', 'DEVICE_ERROR'
    ];
    expect(states.map(uiStateFor).toSet().length, 8);
  });

  test('events fold into the view model', () {
    var vm = const LivenessViewModel();
    vm = vm.apply({'eventType': 'STATE_CHANGE', 'data': {'currentState': 'PASSIVE', 'message': 'Checking', 'rtl': false}});
    expect(vm.state, LivenessUiState.checking);
    vm = vm.apply({'eventType': 'CAPTURE_PROGRESS', 'data': {'captureProgress': 0.4}});
    expect(vm.progress, 0.4);
    vm = vm.apply({'eventType': 'CHALLENGE_ISSUED', 'data': {'challengeAction': 'BLINK', 'challengeTimeoutMs': 5000}});
    expect(vm.state, LivenessUiState.challenge);
    expect(vm.challenge, ChallengeAction.blink);
    vm = vm.apply({'eventType': 'SESSION_COMPLETE', 'data': {'verdict': 'PASS', 'currentState': 'SUCCESS'}});
    expect(vm.state, LivenessUiState.success);
    expect(vm.challenge, isNull);
  });
}
