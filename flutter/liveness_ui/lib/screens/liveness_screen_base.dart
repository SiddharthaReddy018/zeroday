import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../liveness_client.dart';
import '../liveness_models.dart';
import '../liveness_strings.dart';
import '../widgets/challenge_animation.dart';
import '../widgets/oval_guide.dart';

/// Shared screen for the three workflows. Renders engine events; makes no liveness decisions.
/// [cameraPreview] is the native CameraX view (PlatformView / Texture) supplied by the host app, because
/// raw frames are not streamed over the channel. Optional JPEG [previewFrames] are shown only if provided.
class LivenessScreenBase extends StatefulWidget {
  final String titleKey;
  final String workflow;
  final String subjectId;
  final String machineUserId;
  final String locale;
  final Widget? cameraPreview;
  final LivenessClient client;
  final void Function(Map<dynamic, dynamic> attestation)? onSuccess;
  final VoidCallback? onClose;

  const LivenessScreenBase({
    super.key,
    required this.titleKey,
    required this.workflow,
    required this.subjectId,
    required this.machineUserId,
    required this.client,
    this.locale = 'en',
    this.cameraPreview,
    this.onSuccess,
    this.onClose,
  });

  @override
  State<LivenessScreenBase> createState() => _LivenessScreenBaseState();
}

class _LivenessScreenBaseState extends State<LivenessScreenBase> {
  LivenessViewModel _vm = const LivenessViewModel();
  StreamSubscription? _sub;
  StreamSubscription? _prevSub;
  Uint8List? _jpeg;
  bool _started = false;

  LivenessStrings get _s => LivenessStrings(widget.locale);

  @override
  void initState() {
    super.initState();
    _sub = widget.client.events.listen((e) {
      setState(() => _vm = _vm.apply(e));
      if (e['eventType'] == 'SESSION_COMPLETE' && (e['data'] as Map)['verdict'] == 'PASS') {
        widget.onSuccess?.call(((e['data'] as Map)['attestation'] as Map?) ?? const {});
      }
    });
    _prevSub = widget.client.previewFrames.listen((b) => setState(() => _jpeg = b));
    _start();
  }

  Future<void> _start() async {
    if (_started) return;
    _started = true;
    await widget.client.updateConfig({'locale': widget.locale});
    await widget.client.startSession(
      subjectId: widget.subjectId,
      workflow: widget.workflow,
      machineUserId: widget.machineUserId,
      locale: widget.locale,
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    _prevSub?.cancel();
    if (_vm.state != LivenessUiState.success &&
        _vm.state != LivenessUiState.maxRetry &&
        _vm.state != LivenessUiState.deviceError) {
      widget.client.cancelSession().catchError((_) {});
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final rtl = _s.rtl || _vm.rtl;
    return Directionality(
      textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(title: Text(_s.of(widget.titleKey))),
        body: SafeArea(
          child: Column(children: [
            Expanded(child: _preview()),
            _statusPanel(),
          ]),
        ),
      ),
    );
  }

  Widget _preview() {
    final aligned = _vm.state != LivenessUiState.positioning && _vm.state != LivenessUiState.connecting;
    return Stack(fit: StackFit.expand, children: [
      if (widget.cameraPreview != null) widget.cameraPreview!,
      if (widget.cameraPreview == null && _jpeg != null) Image.memory(_jpeg!, fit: BoxFit.cover, gaplessPlayback: true),
      if (_vm.state != LivenessUiState.success &&
          _vm.state != LivenessUiState.maxRetry &&
          _vm.state != LivenessUiState.deviceError)
        OvalGuide(progress: _vm.progress, aligned: aligned, semanticsLabel: _s.of('a11y.oval')),
      if (_vm.state == LivenessUiState.challenge && _vm.challenge != null)
        Positioned(top: 16, right: 16, child: ChallengeAnimation(action: _vm.challenge!)),
      if (_vm.state == LivenessUiState.connecting ||
          (_vm.state == LivenessUiState.checking && _vm.progress >= 1))
        const Center(child: CircularProgressIndicator()),
    ]);
  }

  IconData _icon() {
    switch (_vm.state) {
      case LivenessUiState.success: return Icons.check_circle_outline;
      case LivenessUiState.retry: return Icons.refresh;
      case LivenessUiState.maxRetry: return Icons.block;
      case LivenessUiState.deviceError: return Icons.videocam_off_outlined;
      case LivenessUiState.challenge: return Icons.face_retouching_natural;
      case LivenessUiState.checking: return Icons.hourglass_top;
      default: return Icons.center_focus_strong;
    }
  }

  Widget _statusPanel() {
    final terminal = _vm.state == LivenessUiState.success ||
        _vm.state == LivenessUiState.maxRetry ||
        _vm.state == LivenessUiState.deviceError;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      color: Theme.of(context).colorScheme.surfaceVariant,
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Row(children: [
          Icon(_icon(), size: 32), // icon + text: status is never colour-only
          const SizedBox(width: 12),
          Expanded(
            child: Semantics(
              liveRegion: true,
              child: Text(_vm.message, style: Theme.of(context).textTheme.titleMedium),
            ),
          ),
        ]),
        if (_vm.state == LivenessUiState.checking || _vm.state == LivenessUiState.positioning)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: LinearProgressIndicator(value: _vm.progress > 0 ? _vm.progress : null),
          ),
        const SizedBox(height: 12),
        Row(mainAxisAlignment: MainAxisAlignment.end, children: [
          if (_vm.state == LivenessUiState.retry || _vm.state == LivenessUiState.maxRetry)
            TextButton(
              onPressed: () => widget.client.retrySession(),
              child: Text(_s.of('btn.retry')),
            ),
          TextButton(
            onPressed: () {
              if (!terminal) widget.client.cancelSession().catchError((_) {});
              widget.onClose?.call();
            },
            child: Text(_s.of(terminal ? 'btn.close' : 'btn.cancel')),
          ),
        ]),
      ]),
    );
  }
}
