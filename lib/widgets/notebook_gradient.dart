import 'package:flutter/material.dart';

import '../models/notebook.dart';
import '../services/cover_color_service.dart';

/// Deriva um degradê a partir da capa de um caderno, pra usar como fundo
/// de telas relacionadas a ele (barra do topo, tela de notas, etc).
List<Color> notebookGradientColors(Notebook nb) {
  switch (nb.coverType) {
    case CoverType.color:
      final base = Color(nb.coverColor ?? 0xFF2E7D6B);
      return [base, Color.alphaBlend(Colors.black.withOpacity(0.28), base)];
    case CoverType.gradient:
      final colors = nb.gradientColors ?? [0xFF2E7D6B, 0xFF1B4D3E];
      return colors.map((c) => Color(c)).toList();
    case CoverType.assetImage:
    case CoverType.deviceImage:
      // Capa é uma foto — não dá pra saber a cor dominante sem processar a
      // imagem, então usamos um degradê neutro escuro, que combina com
      // qualquer foto e mantém os ícones brancos legíveis.
      return [const Color(0xFF2B2B2B), const Color(0xFF141414)];
  }
}

/// Diz se o fundo (capa/degradê) do caderno é escuro o bastante pra exigir
/// ícones e texto claros por cima dele. Usa a luminância média das cores do
/// degradê (não só a primeira), porque capas de cor sólida têm as duas
/// entradas próximas, mas alguns dos degradês prontos (ex: rosa claro,
/// laranja/amarelo) têm uma ponta bem clara — usar só a primeira cor erraria
/// o contraste nesses casos.
bool notebookBackdropIsDark(Notebook nb) {
  final colors = notebookGradientColors(nb);
  final avgLuminance = colors.map((c) => c.computeLuminance()).reduce((a, b) => a + b) / colors.length;
  return avgLuminance < 0.5;
}

/// Cores básicas prontas pro seletor de "cor das abas" de um caderno.
const kTabColorPresets = <int>[
  0xFF2E7D6B, // verde
  0xFF1565C0, // azul
  0xFF6A1B9A, // roxo
  0xFFC62828, // vermelho
  0xFFEF6C00, // laranja
  0xFF00838F, // ciano
  0xFFAD1457, // rosa/magenta
  0xFF37474F, // grafite
  0xFF827717, // oliva
  0xFF4527A0, // índigo
];

/// Uma única cor sólida representativa do caderno — usada em botões e
/// ícones que precisam de uma cor "de identidade" (ex: o + de nova nota, as
/// abas de notas). Prioridades: cor de aba escolhida manualmente > cor/
/// gradiente da capa > cor extraída de foto (ver CoverColorService) > um
/// neutro escuro de reserva.
Color notebookAccentColor(Notebook nb) {
  if (nb.tabColor != null) return Color(nb.tabColor!);

  switch (nb.coverType) {
    case CoverType.color:
      return Color(nb.coverColor ?? 0xFF2E7D6B);
    case CoverType.gradient:
      final colors = nb.gradientColors ?? [0xFF2E7D6B, 0xFF1B4D3E];
      return Color(colors.first);
    case CoverType.assetImage:
    case CoverType.deviceImage:
      final path = nb.coverType == CoverType.assetImage ? nb.assetImagePath : nb.deviceImagePath;
      if (path != null) {
        final cached = CoverColorService.cachedColor(path);
        if (cached != null) return cached;
      }
      return const Color(0xFF2B2B2B);
  }
}

/// Dispara (se ainda não tiver sido feita) a extração da cor de destaque a
/// partir da capa do caderno, quando ela for uma foto e o usuário não tiver
/// escolhido uma cor de aba manualmente. Chame no initState da tela e passe
/// um callback que dá setState quando o resultado chegar.
void ensureNotebookAccentColorExtracted(Notebook nb, void Function() onReady) {
  if (nb.tabColor != null) return;
  final isImage = nb.coverType == CoverType.assetImage || nb.coverType == CoverType.deviceImage;
  if (!isImage) return;
  final path = nb.coverType == CoverType.assetImage ? nb.assetImagePath : nb.deviceImagePath;
  if (path == null || CoverColorService.cachedColor(path) != null) return;

  CoverColorService.extractAccentColor(
    cacheKey: path,
    isAsset: nb.coverType == CoverType.assetImage,
    path: path,
  ).then((_) => onReady());
}
