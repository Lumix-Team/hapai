import 'package:flutter/material.dart';

/// Hapai mascot - a small cat face drawn with [CustomPaint].
///
/// Deliberately asset-free: everything is built from the active
/// [ColorScheme] (outline and features in `primary`, face in
/// `tertiaryContainer`), so the mascot stays legible in both light and dark
/// themes and needs no @2x/@3x raster variants.
///
/// Used on onboarding surfaces (empty subscriptions list) where a bit of
/// character helps a first-time user.
class HapaiCat extends StatelessWidget {
  const HapaiCat({
    super.key,
    this.size = 96,
    this.stroke,
    this.fill,
  });

  /// Square edge length in logical pixels.
  final double size;

  /// Outline / eyes / whiskers colour. Defaults to `colorScheme.primary`.
  final Color? stroke;

  /// Face fill colour. Defaults to `colorScheme.tertiaryContainer`.
  final Color? fill;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _HapaiCatPainter(
          stroke: stroke ?? scheme.primary,
          fill: fill ?? scheme.tertiaryContainer,
        ),
      ),
    );
  }
}

class _HapaiCatPainter extends CustomPainter {
  _HapaiCatPainter({required this.stroke, required this.fill});

  final Color stroke;
  final Color fill;

  @override
  void paint(Canvas canvas, Size size) {
    // Geometry is authored on a 100x100 grid and scaled to `size`.
    final s = size.shortestSide / 100.0;

    final face = Paint()
      ..color = fill
      ..isAntiAlias = true;
    final line = Paint()
      ..color = stroke
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.4 * s
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;
    final dot = Paint()
      ..color = stroke
      ..isAntiAlias = true;

    // Ears (filled + outlined first: the head oval is painted over their
    // bases, so no seam line crosses the face).
    final earLeft = Path()
      ..moveTo(20 * s, 46 * s)
      ..lineTo(13 * s, 8 * s)
      ..lineTo(48 * s, 27 * s)
      ..close();
    final earRight = Path()
      ..moveTo(80 * s, 46 * s)
      ..lineTo(87 * s, 8 * s)
      ..lineTo(52 * s, 27 * s)
      ..close();
    for (final ear in [earLeft, earRight]) {
      canvas.drawPath(ear, face);
      canvas.drawPath(ear, line);
    }

    // Head.
    final head = Path()
      ..addOval(Rect.fromLTRB(13 * s, 24 * s, 87 * s, 88 * s));
    canvas.drawPath(head, face);
    canvas.drawPath(head, line);

    // Eyes.
    canvas.drawCircle(Offset(37 * s, 54 * s), 6 * s, dot);
    canvas.drawCircle(Offset(63 * s, 54 * s), 6 * s, dot);

    // Nose (small triangle between the eyes' baseline).
    canvas.drawPath(
      Path()
        ..moveTo(50 * s, 68 * s)
        ..lineTo(45 * s, 62 * s)
        ..lineTo(55 * s, 62 * s)
        ..close(),
      dot,
    );

    // Mouth: two mirrored smile arcs from the nose.
    line.strokeWidth = 2.6 * s;
    canvas.drawPath(
      Path()
        ..moveTo(50 * s, 68 * s)
        ..quadraticBezierTo(44 * s, 74 * s, 39 * s, 69 * s)
        ..moveTo(50 * s, 68 * s)
        ..quadraticBezierTo(56 * s, 74 * s, 61 * s, 69 * s),
      line,
    );

    // Whiskers.
    line.strokeWidth = 2.2 * s;
    canvas.drawPath(
      Path()
        ..moveTo(12 * s, 56 * s)
        ..lineTo(0.5 * s, 53 * s)
        ..moveTo(12 * s, 62 * s)
        ..lineTo(0.5 * s, 64 * s)
        ..moveTo(88 * s, 56 * s)
        ..lineTo(99.5 * s, 53 * s)
        ..moveTo(88 * s, 62 * s)
        ..lineTo(99.5 * s, 64 * s),
      line,
    );

    // Blush.
    final blush = Paint()
      ..color = stroke.withValues(alpha: 0.18)
      ..isAntiAlias = true;
    canvas.drawCircle(Offset(28 * s, 66 * s), 5 * s, blush);
    canvas.drawCircle(Offset(72 * s, 66 * s), 5 * s, blush);
  }

  @override
  bool shouldRepaint(_HapaiCatPainter old) =>
      old.stroke != stroke || old.fill != fill;
}
