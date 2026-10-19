import 'package:flutter/material.dart';
import '../liveness_client.dart';
import 'liveness_screen_base.dart';

/// RESIDENT_REGISTRATION workflow screen (8 UI states, EN/FR/AR with RTL via LivenessScreenBase).
class ResidentRegistrationScreen extends StatelessWidget {
  final String subjectId;
  final String machineUserId;
  final String locale;
  final Widget? cameraPreview;
  final LivenessClient client;
  final void Function(Map<dynamic, dynamic> attestation)? onSuccess;
  final VoidCallback? onClose;

  const ResidentRegistrationScreen({
    super.key,
    required this.subjectId,
    required this.machineUserId,
    required this.client,
    this.locale = 'en',
    this.cameraPreview,
    this.onSuccess,
    this.onClose,
  });

  @override
  Widget build(BuildContext context) => LivenessScreenBase(
        titleKey: 'title.resident',
        workflow: 'RESIDENT_REGISTRATION',
        subjectId: subjectId,
        machineUserId: machineUserId,
        client: client,
        locale: locale,
        cameraPreview: cameraPreview,
        onSuccess: onSuccess,
        onClose: onClose,
      );
}
