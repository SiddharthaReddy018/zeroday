import 'dart:async';
import 'package:flutter/services.dart';

/// Thin wrapper over the native channels. Method names match LivenessPlatformChannel.java.
class LivenessClient {
  static const _methods = MethodChannel('mosip.liveness/methods');
  static const _events = EventChannel('mosip.liveness/events');
  static const _preview = EventChannel('mosip.liveness/preview');

  Stream<Map<dynamic, dynamic>> get events =>
      _events.receiveBroadcastStream().map((e) => e as Map<dynamic, dynamic>);

  Stream<Uint8List> get previewFrames =>
      _preview.receiveBroadcastStream().map((e) => e as Uint8List);

  /// [workflow]: RESIDENT_REGISTRATION | OPERATOR_AUTH | SUPERVISOR_AUTH | INFANT
  Future<void> startSession({
    required String subjectId,
    required String workflow,
    required String machineUserId,
    String locale = 'en',
  }) =>
      _methods.invokeMethod('startSession', {
        'subjectId': subjectId,
        'workflow': workflow,
        'machineUserId': machineUserId,
        'locale': locale,
      });

  Future<void> cancelSession() => _methods.invokeMethod('cancelSession');
  Future<void> retrySession() => _methods.invokeMethod('retrySession');

  /// Presentation keys only: locale, previewEnabled, previewMaxFps. The engine rejects anything else.
  Future<void> updateConfig(Map<String, Object> cfg) => _methods.invokeMethod('updateConfig', cfg);
}
