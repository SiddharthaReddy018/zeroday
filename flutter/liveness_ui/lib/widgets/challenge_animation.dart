import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../liveness_models.dart';

/// Looping cartoon face demonstrating the requested action (blink, smile, head turn, gaze).
/// Purely illustrative; validation of the user's response happens in the engine.
class ChallengeAnimation extends StatefulWidget {
  final ChallengeAction action;
  const ChallengeAnimation({super.key, required this.action});

  @override
  State<ChallengeAnimation> createState() => _ChallengeAnimationState();
}

class _ChallengeAnimationState extends State<ChallengeAnimation> with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1400))..repeat(reverse: true);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
        child: AnimatedBuilder(
          animation: _c,
          builder: (_, __) => CustomPaint(
            size: const Size(96, 96),
            painter: _FacePainter(widget.action, Curves.easeInOut.transform(_c.value),
                Theme.of(context).colorScheme.onSurface),
          ),
        ),
      );
}

class _FacePainter extends CustomPainter {
  final ChallengeAction a;
  final double t; // 0..1
  final Color ink;
  _FacePainter(this.a, this.t, this.ink);

  @override
  void paint(Canvas canvas, Size s) {
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..color = ink;
    final fill = Paint()..color = ink;
    double yaw = 0, pitch = 0, gazeX = 0, gazeY = 0, blink = 0, smile = 0;
    switch (a) {
      case ChallengeAction.turnLeft: yaw = -t; break;
      case ChallengeAction.turnRight: yaw = t; break;
      case ChallengeAction.lookLeft: gazeX = -t; break;
      case ChallengeAction.lookRight: gazeX = t; break;
      case ChallengeAction.lookUp: gazeY = -t; pitch = -t * 0.3; break;
      case ChallengeAction.lookDown: gazeY = t; pitch = t * 0.3; break;
      case ChallengeAction.blink: blink = t; break;
      case ChallengeAction.smile: smile = t; break;
    }
    final c = Offset(s.width / 2 + yaw * 12, s.height / 2 + pitch * 10);
    canvas.drawOval(Rect.fromCenter(center: c, width: s.width * (0.7 - yaw.abs() * 0.15), height: s.height * 0.9), p);
    final eyeDx = s.width * 0.16 * (1 - yaw.abs() * 0.3);
    for (final dir in [-1, 1]) {
      final e = Offset(c.dx + dir * eyeDx, c.dy - s.height * 0.1);
      if (blink > 0.7) {
        canvas.drawLine(e.translate(-6, 0), e.translate(6, 0), p);
      } else {
        canvas.drawCircle(e, 6, p);
        canvas.drawCircle(e.translate(gazeX * 3, gazeY * 3), 2.5, fill);
      }
    }
    final mouth = Rect.fromCenter(center: Offset(c.dx, c.dy + s.height * 0.22), width: s.width * 0.3, height: 10 + smile * 14);
    canvas.drawArc(mouth, 0, math.pi, false, p);
  }

  @override
  bool shouldRepaint(_FacePainter o) => o.t != t || o.a != a;
}
