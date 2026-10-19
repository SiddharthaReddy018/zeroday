import 'dart:math' as math;
import 'package:flutter/material.dart';

/// Oval positioning guide with a progress ring. Status is never colour-only: the state also changes the
/// stroke pattern (dashed = adjust, solid = ok) and a text message is always shown by the screen.
class OvalGuide extends StatelessWidget {
  final double progress; // 0..1
  final bool aligned;
  final String semanticsLabel;

  const OvalGuide({super.key, required this.progress, required this.aligned, required this.semanticsLabel});

  @override
  Widget build(BuildContext context) => Semantics(
        label: semanticsLabel,
        child: CustomPaint(
          painter: _OvalPainter(progress, aligned, Theme.of(context).colorScheme),
          child: const SizedBox.expand(),
        ),
      );
}

class _OvalPainter extends CustomPainter {
  final double progress;
  final bool aligned;
  final ColorScheme scheme;
  _OvalPainter(this.progress, this.aligned, this.scheme);

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Rect.fromCenter(
        center: size.center(Offset.zero), width: size.width * 0.66, height: size.height * 0.62);
    final path = Path()..addOval(rect);

    // Dim everything outside the oval.
    final overlay = Path()
      ..addRect(Offset.zero & size)
      ..addPath(path, Offset.zero)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(overlay, Paint()..color = Colors.black54);

    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4
      ..color = aligned ? scheme.primary : scheme.error;
    if (aligned) {
      canvas.drawPath(path, base);
    } else {
      for (final m in path.computeMetrics()) {
        double d = 0;
        while (d < m.length) {
          canvas.drawPath(m.extractPath(d, math.min(d + 14, m.length)), base);
          d += 24;
        }
      }
    }
    if (progress > 0) {
      final ring = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 8
        ..strokeCap = StrokeCap.round
        ..color = scheme.primary;
      canvas.drawArc(rect.inflate(10), -math.pi / 2, 2 * math.pi * progress.clamp(0, 1), false, ring);
    }
  }

  @override
  bool shouldRepaint(_OvalPainter o) => o.progress != progress || o.aligned != aligned;
}
