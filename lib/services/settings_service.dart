import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/notebook.dart';

/// Preferências de aparência do app (cor de destaque e modo claro/escuro).
/// Não é informação sensível, então fica fora da criptografia da senha
/// mestre — assim o app já nasce com a cor/tema certos antes mesmo de
/// desbloquear.
class SettingsService extends ChangeNotifier {
  static const _seedColorKey = 'seed_color';
  static const _themeModeKey = 'theme_mode';
  static const _libraryNameKey = 'library_name';
  static const _libraryNameFontKey = 'library_name_font';
  static const _coloredBackgroundKey = 'colored_background';
  static const _gridColumnsKey = 'grid_columns';
  static const _autoLockSecondsKey = 'auto_lock_seconds';

  static const defaultSeedColor = Color(0xFF2E7D6B);
  static const defaultLibraryName = 'Meus cadernos';
  static const defaultLibraryNameFont = CoverFont.montserrat;
  static const defaultGridColumns = 2;

  /// 0 = tranca imediatamente ao sair do app (padrão, mais seguro).
  static const defaultAutoLockSeconds = 0;

  /// Opções oferecidas na tela de configurações. Sem opção de "nunca" de
  /// propósito — isso é um app de notas criptografadas, então sempre trava
  /// em algum momento depois de deixar de estar em primeiro plano.
  static const autoLockOptions = <int>[0, 30, 60, 300];

  Color _seedColor = defaultSeedColor;
  ThemeMode _themeMode = ThemeMode.system;
  String _libraryName = defaultLibraryName;
  CoverFont _libraryNameFont = defaultLibraryNameFont;
  bool _coloredBackground = false;
  int _gridColumns = defaultGridColumns;
  int _autoLockSeconds = defaultAutoLockSeconds;

  Color get seedColor => _seedColor;
  ThemeMode get themeMode => _themeMode;
  String get libraryName => _libraryName;

  /// Fonte usada no nome "Meus cadernos" — as mesmas opções disponíveis
  /// pros títulos de capa dos cadernos (ver CoverWidget).
  CoverFont get libraryNameFont => _libraryNameFont;

  /// Se true, o fundo do app usa um tom bem suave derivado da cor de tema
  /// em vez do branco/preto neutro padrão.
  bool get coloredBackground => _coloredBackground;

  /// Quantos cadernos por linha na tela principal (2 a 4). Controla o
  /// tamanho das capas — menos colunas = capas maiores.
  int get gridColumns => _gridColumns;

  /// Quanto tempo o app espera em segundo plano antes de trancar
  /// sozinho. 0 = tranca assim que sai do primeiro plano.
  int get autoLockSeconds => _autoLockSeconds;

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final colorValue = prefs.getInt(_seedColorKey);
    if (colorValue != null) _seedColor = Color(colorValue);

    final modeIndex = prefs.getInt(_themeModeKey);
    if (modeIndex != null && modeIndex < ThemeMode.values.length) {
      _themeMode = ThemeMode.values[modeIndex];
    }

    final name = prefs.getString(_libraryNameKey);
    if (name != null && name.trim().isNotEmpty) _libraryName = name;

    final fontIndex = prefs.getInt(_libraryNameFontKey);
    if (fontIndex != null && fontIndex < CoverFont.values.length) {
      _libraryNameFont = CoverFont.values[fontIndex];
    }

    _coloredBackground = prefs.getBool(_coloredBackgroundKey) ?? false;

    final columns = prefs.getInt(_gridColumnsKey);
    if (columns != null && columns >= 2 && columns <= 4) _gridColumns = columns;

    final autoLock = prefs.getInt(_autoLockSecondsKey);
    if (autoLock != null && autoLockOptions.contains(autoLock)) _autoLockSeconds = autoLock;

    notifyListeners();
  }

  Future<void> setAutoLockSeconds(int seconds) async {
    if (!autoLockOptions.contains(seconds)) return;
    _autoLockSeconds = seconds;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_autoLockSecondsKey, seconds);
  }

  Future<void> setLibraryName(String name) async {
    final trimmed = name.trim();
    _libraryName = trimmed.isEmpty ? defaultLibraryName : trimmed;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_libraryNameKey, _libraryName);
  }

  Future<void> setLibraryNameFont(CoverFont font) async {
    _libraryNameFont = font;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_libraryNameFontKey, font.index);
  }

  Future<void> setSeedColor(Color color) async {
    _seedColor = color;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_seedColorKey, color.value);
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    _themeMode = mode;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_themeModeKey, mode.index);
  }

  Future<void> setColoredBackground(bool value) async {
    _coloredBackground = value;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_coloredBackgroundKey, value);
  }

  Future<void> setGridColumns(int columns) async {
    _gridColumns = columns.clamp(2, 4);
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_gridColumnsKey, _gridColumns);
  }
}
