import 'dart:io';

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../models/notebook.dart';

/// Renderiza a capa de um caderno: fundo (cor sólida, gradiente, imagem de
/// asset ou imagem escolhida no celular) + título grande sobreposto com a
/// fonte escolhida.
class CoverWidget extends StatelessWidget {
  const CoverWidget({
    super.key,
    required this.notebook,
    this.width = 160,
    this.height = 220,
    this.noteCount,
  });

  final Notebook notebook;
  final double width;
  final double height;

  /// Quantidade de notas do caderno — mostrada em fonte menor abaixo do
  /// título, quando informada. Passe null pra não mostrar (ex: no preview
  /// de criação do caderno, onde ainda não existe contagem real).
  final int? noteCount;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.25),
            blurRadius: 8,
            offset: const Offset(2, 4),
          ),
        ],
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Fundo da capa: cor, gradiente ou imagem.
          Container(
            decoration: BoxDecoration(
              color: notebook.coverType == CoverType.color
                  ? Color(notebook.coverColor ?? 0xFF2E7D6B)
                  : null,
              gradient: notebook.coverType == CoverType.gradient
                  ? LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: (notebook.gradientColors ?? [0xFF2E7D6B, 0xFF1B4D3E])
                          .map((c) => Color(c))
                          .toList(),
                    )
                  : null,
              image: _resolveImage(),
            ),
          ),
          // Sombra gradiente atrás do título, pra garantir contraste do
          // texto branco mesmo em capas claras ou fotos muito iluminadas.
          Align(
            alignment: Alignment.bottomCenter,
            child: Container(
              height: height * 0.55,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, Colors.black.withOpacity(0.75)],
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Align(
              alignment: Alignment.bottomLeft,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    notebook.title,
                    style: _fontStyle(notebook.font).copyWith(
                      color: Colors.white,
                      fontSize: 24,
                      shadows: const [
                        Shadow(color: Colors.black87, blurRadius: 6, offset: Offset(0, 1)),
                      ],
                    ),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (noteCount != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      noteCount == 1 ? '1 nota' : '$noteCount notas',
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.85),
                        fontSize: 12,
                        letterSpacing: 0.3,
                        shadows: const [
                          Shadow(color: Colors.black87, blurRadius: 4, offset: Offset(0, 1)),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  DecorationImage? _resolveImage() {
    if (notebook.coverType == CoverType.assetImage && notebook.assetImagePath != null) {
      return DecorationImage(
        image: AssetImage(notebook.assetImagePath!),
        fit: BoxFit.cover,
      );
    }
    if (notebook.coverType == CoverType.deviceImage && notebook.deviceImagePath != null) {
      return DecorationImage(
        image: FileImage(File(notebook.deviceImagePath!)),
        fit: BoxFit.cover,
      );
    }
    return null;
  }

  /// Mapeia o enum CoverFont para o TextStyle do google_fonts.
  /// Dancing Script e Great Vibes são as opções cursivas.
  static TextStyle _fontStyle(CoverFont font) {
    switch (font) {
      case CoverFont.playfairDisplay:
        return GoogleFonts.playfairDisplay(fontWeight: FontWeight.w600);
      case CoverFont.montserrat:
        return GoogleFonts.montserrat(fontWeight: FontWeight.w700);
      case CoverFont.poppins:
        return GoogleFonts.poppins(fontWeight: FontWeight.w600);
      case CoverFont.dancingScript:
        return GoogleFonts.dancingScript(fontWeight: FontWeight.w700);
      case CoverFont.greatVibes:
        return GoogleFonts.greatVibes(fontWeight: FontWeight.w400, fontSize: 30);
    }
  }

  /// Exposto para telas que precisam mostrar um preview de cada fonte
  /// (ex: seletor de fonte na criação do caderno).
  static TextStyle previewStyle(CoverFont font) => _fontStyle(font);

  static String fontLabel(CoverFont font) {
    switch (font) {
      case CoverFont.playfairDisplay:
        return 'Playfair Display';
      case CoverFont.montserrat:
        return 'Montserrat';
      case CoverFont.poppins:
        return 'Poppins';
      case CoverFont.dancingScript:
        return 'Dancing Script (cursiva)';
      case CoverFont.greatVibes:
        return 'Great Vibes (cursiva)';
    }
  }
}
