import 'package:flutter/material.dart';

import '../models/note.dart';

/// Desenha, por cima do fundo da página, o tipo de pauta escolhido —
/// pautado, quadriculado, pontilhado ou cornell — imitando papel de verdade.
class PaperPainter extends CustomPainter {
  PaperPainter({
    required this.style,
    required this.lineColor,
    this.spacing = 24,
    this.topOffset = 0,
  });

  final PageStyle style;
  final Color lineColor;

  /// Distância entre as linhas — ajustado pra ficar perto da altura de
  /// linha do texto do editor (fonte 16, com o espaçamento entre letras
  /// usado no app).
  final double spacing;

  /// Quanto pular no topo antes de começar a desenhar — pra pauta não
  /// cruzar por cima do título da nota.
  final double topOffset;

  @override
  void paint(Canvas canvas, Size size) {
    switch (style) {
      case PageStyle.blank:
        return;
      case PageStyle.lined:
        _drawLined(canvas, size);
        break;
      case PageStyle.grid:
        _drawGrid(canvas, size);
        break;
      case PageStyle.dotted:
        _drawDotted(canvas, size);
        break;
      case PageStyle.cornell:
        _drawCornell(canvas, size);
        break;
    }
  }

  void _drawLined(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = lineColor
      ..strokeWidth = 1;
    for (double y = topOffset + spacing; y < size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  void _drawGrid(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = lineColor
      ..strokeWidth = 1;
    for (double y = topOffset + spacing; y < size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
    for (double x = spacing; x < size.width; x += spacing) {
      canvas.drawLine(Offset(x, topOffset), Offset(x, size.height), paint);
    }
  }

  void _drawDotted(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = lineColor
      ..style = PaintingStyle.fill;
    for (double y = topOffset + spacing; y < size.height; y += spacing) {
      for (double x = spacing; x < size.width; x += spacing) {
        canvas.drawCircle(Offset(x, y), 1.3, paint);
      }
    }
  }

  void _drawCornell(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = lineColor
      ..strokeWidth = 1.2;
    // Coluna vertical de "pistas" à esquerda (estilo método Cornell).
    final cueX = size.width * 0.28;
    canvas.drawLine(Offset(cueX, topOffset), Offset(cueX, size.height * 0.82), paint);
    // Linha horizontal de resumo na parte inferior.
    final summaryY = size.height * 0.82;
    canvas.drawLine(Offset(0, summaryY), Offset(size.width, summaryY), paint);
    // Linhas pautadas na área principal de anotações.
    for (double y = topOffset + spacing; y < summaryY; y += spacing) {
      canvas.drawLine(Offset(cueX, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(covariant PaperPainter oldDelegate) {
    return oldDelegate.style != style ||
        oldDelegate.lineColor != lineColor ||
        oldDelegate.spacing != spacing ||
        oldDelegate.topOffset != topOffset;
  }
}

/// Widget de conveniência: fundo (cor ou imagem) + pauta desenhada por cima.
class PaperBackground extends StatelessWidget {
  const PaperBackground({
    super.key,
    required this.child,
    required this.style,
    required this.lineColor,
    this.backgroundColor,
    this.backgroundImage,
    this.topOffset = 0,
  });

  final Widget child;
  final PageStyle style;
  final Color lineColor;
  final Color? backgroundColor;
  final ImageProvider? backgroundImage;

  /// Quanto pular no topo antes de começar a desenhar a pauta (pra não
  /// cruzar com título/cabeçalho da página).
  final double topOffset;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: backgroundImage == null ? (backgroundColor ?? Colors.white) : null,
        image: backgroundImage != null
            ? DecorationImage(image: backgroundImage!, fit: BoxFit.cover)
            : null,
      ),
      child: Stack(
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: PaperPainter(style: style, lineColor: lineColor, topOffset: topOffset),
            ),
          ),
          child,
        ],
      ),
    );
  }
}
