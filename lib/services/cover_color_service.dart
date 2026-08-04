import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;

/// Extrai uma cor de destaque a partir de uma imagem (ex: capa de caderno)
/// sem depender de nenhum pacote externo de "palette" — decodifica a
/// imagem e calcula uma média ponderada dos pixels, dando mais peso aos
/// mais saturados e ignorando tons quase-brancos/quase-pretos/acinzentados
/// (que costumam ser fundo/céu, não a cor "marcante" da foto).
class CoverColorService {
  static final Map<String, Color> _cache = {};

  /// Retorna a cor já calculada anteriormente pra essa imagem, se houver.
  static Color? cachedColor(String key) => _cache[key];

  static Future<Color> extractAccentColor({
    required String cacheKey,
    required bool isAsset,
    required String path,
    Color fallback = const Color(0xFF2B2B2B),
  }) async {
    if (_cache.containsKey(cacheKey)) return _cache[cacheKey]!;

    try {
      final Uint8List bytes = isAsset
          ? (await rootBundle.load(path)).buffer.asUint8List()
          : await File(path).readAsBytes();

      final codec = await ui.instantiateImageCodec(bytes, targetWidth: 64);
      final frame = await codec.getNextFrame();
      final image = frame.image;
      final byteData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      if (byteData == null) return fallback;

      final pixels = byteData.buffer.asUint8List();
      double rSum = 0, gSum = 0, bSum = 0, weightSum = 0;

      for (int i = 0; i + 3 < pixels.length; i += 4) {
        final r = pixels[i];
        final g = pixels[i + 1];
        final b = pixels[i + 2];
        final a = pixels[i + 3];
        if (a < 200) continue;

        final maxC = r > g ? (r > b ? r : b) : (g > b ? g : b);
        final minC = r < g ? (r < b ? r : b) : (g < b ? g : b);
        final lightness = (maxC + minC) / 2 / 255;
        final chroma = (maxC - minC) / 255;
        final saturation = lightness == 0 || lightness == 1
            ? 0.0
            : chroma / (1 - (2 * lightness - 1).abs());

        // Ignora quase-branco, quase-preto e tons pouco saturados (cinzas).
        if (lightness < 0.12 || lightness > 0.92) continue;
        if (saturation < 0.15) continue;

        final weight = saturation;
        rSum += r * weight;
        gSum += g * weight;
        bSum += b * weight;
        weightSum += weight;
      }

      final result = weightSum == 0
          ? fallback
          : Color.fromARGB(
              255,
              (rSum / weightSum).round().clamp(0, 255),
              (gSum / weightSum).round().clamp(0, 255),
              (bSum / weightSum).round().clamp(0, 255),
            );

      _cache[cacheKey] = result;
      return result;
    } catch (_) {
      return fallback;
    }
  }
}
