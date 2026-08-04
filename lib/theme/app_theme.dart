import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AppTheme {
  static ThemeData light(Color seedColor, {bool coloredBackground = false}) {
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: seedColor, brightness: Brightness.light),
    );
    final background = coloredBackground
        ? Color.alphaBlend(seedColor.withOpacity(0.08), Colors.white)
        : const Color(0xFFF7F5F0);
    return base.copyWith(
      scaffoldBackgroundColor: background,
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: seedColor,
        selectionColor: seedColor.withOpacity(0.3),
        selectionHandleColor: seedColor,
      ),
      appBarTheme: const AppBarThemeData(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        foregroundColor: Color(0xFF1B1B1B),
        // Sem isso, o AppBar calcula sozinho o estilo da barra de status
        // (a partir do próprio backgroundColor transparente) e pode
        // escolher ícones claros mesmo no tema claro — daí ficarem
        // brancos e somem contra o fundo claro. Fixando aqui, o AppBar
        // usa esse estilo em vez de adivinhar um por conta própria.
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.dark,
          statusBarBrightness: Brightness.light,
          systemNavigationBarColor: Colors.transparent,
          systemNavigationBarIconBrightness: Brightness.dark,
          systemNavigationBarDividerColor: Colors.transparent,
        ),
      ),
      cardTheme: const CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16))),
      ),
      inputDecorationTheme: InputDecorationThemeData(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
    );
  }

  static ThemeData dark(Color seedColor, {bool coloredBackground = false}) {
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: seedColor, brightness: Brightness.dark),
    );
    final background = coloredBackground
        ? Color.alphaBlend(seedColor.withOpacity(0.18), const Color(0xFF121212))
        : const Color(0xFF121212);
    return base.copyWith(
      scaffoldBackgroundColor: background,
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: seedColor,
        selectionColor: seedColor.withOpacity(0.4),
        selectionHandleColor: seedColor,
      ),
      appBarTheme: const AppBarThemeData(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
          statusBarBrightness: Brightness.dark,
          systemNavigationBarColor: Colors.transparent,
          systemNavigationBarIconBrightness: Brightness.light,
          systemNavigationBarDividerColor: Colors.transparent,
        ),
      ),
      cardTheme: const CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16))),
      ),
    );
  }
}
