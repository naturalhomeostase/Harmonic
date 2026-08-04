import 'package:flutter/material.dart';
import 'package:flutter_colorpicker/flutter_colorpicker.dart';
import 'package:provider/provider.dart';

import '../models/notebook.dart';
import '../services/auth_service.dart';
import '../services/settings_service.dart';
import '../services/storage_service.dart';
import '../widgets/cover_widget.dart';
import 'about_screen.dart';
import 'archived_notes_screen.dart';
import 'change_password_screen.dart';
import 'trash_screen.dart';

const kThemeColorPresets = <int>[
  0xFF2E7D6B, // verde (padrão)
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

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.storage, required this.auth});

  final StorageService storage;
  final AuthService auth;

  Future<void> _openCustomColorPicker(BuildContext context, SettingsService settings) async {
    Color temp = settings.seedColor;
    final picked = await showDialog<Color>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Cor personalizada'),
        content: SingleChildScrollView(
          child: ColorPicker(
            pickerColor: settings.seedColor,
            onColorChanged: (c) => temp = c,
            enableAlpha: false,
            labelTypes: const [],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, temp), child: const Text('Usar essa cor')),
        ],
      ),
    );
    if (picked != null) settings.setSeedColor(picked);
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();

    return Scaffold(
      appBar: AppBar(title: const Text('Configurações')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('Dados', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            margin: EdgeInsets.zero,
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.archive_outlined),
                  title: const Text('Notas arquivadas'),
                  subtitle: const Text('Notas que você tirou da lista, sem prazo de validade'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => ArchivedNotesScreen(storage: storage, auth: auth)),
                    );
                  },
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.delete_outline),
                  title: const Text('Lixeira'),
                  subtitle: const Text('Cadernos e notas excluídos recentemente'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => TrashScreen(storage: storage)),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          Text('Segurança', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            margin: EdgeInsets.zero,
            child: ListTile(
              leading: const Icon(Icons.password_outlined),
              title: const Text('Trocar senha'),
              subtitle: const Text('Sua senha é usada para cifrar as notas — trocar re-cifra tudo automaticamente'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => ChangePasswordScreen(auth: auth, storage: storage),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 20),
          Text('Bloquear automaticamente', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 4),
          Text(
            'Quanto tempo o app espera em segundo plano (trocar de app, apagar a tela) '
            'antes de pedir a senha de novo.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          SegmentedButton<int>(
            showSelectedIcon: false,
            style: SegmentedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              textStyle: const TextStyle(fontSize: 12.5),
            ),
            segments: const [
              ButtonSegment(value: 0, label: Text('Imediato')),
              ButtonSegment(value: 30, label: Text('30s')),
              ButtonSegment(value: 60, label: Text('1 min')),
              ButtonSegment(value: 300, label: Text('5 min')),
            ],
            selected: {settings.autoLockSeconds},
            onSelectionChanged: (s) => settings.setAutoLockSeconds(s.first),
          ),
          const SizedBox(height: 28),
          Text('Tema', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          SegmentedButton<ThemeMode>(
            showSelectedIcon: false,
            style: SegmentedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              textStyle: const TextStyle(fontSize: 12.5),
            ),
            segments: const [
              ButtonSegment(
                value: ThemeMode.system,
                label: Text('Sistema'),
                icon: Icon(Icons.brightness_auto_outlined, size: 16),
              ),
              ButtonSegment(
                value: ThemeMode.light,
                label: Text('Claro'),
                icon: Icon(Icons.light_mode_outlined, size: 16),
              ),
              ButtonSegment(
                value: ThemeMode.dark,
                label: Text('Escuro'),
                icon: Icon(Icons.dark_mode_outlined, size: 16),
              ),
            ],
            selected: {settings.themeMode},
            onSelectionChanged: (s) => settings.setThemeMode(s.first),
          ),
          const SizedBox(height: 28),
          Text('Fonte do nome "${settings.libraryName}"', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            'A mesma lista de fontes usada nos títulos de capa dos cadernos.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          Card(
            margin: const EdgeInsets.only(top: 8),
            child: Column(
              children: CoverFont.values.map((f) {
                return RadioListTile<CoverFont>(
                  value: f,
                  groupValue: settings.libraryNameFont,
                  onChanged: (v) => settings.setLibraryNameFont(v!),
                  title: Text(CoverWidget.fontLabel(f), style: CoverWidget.previewStyle(f)),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 28),
          Text('Cor de destaque', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 14,
            runSpacing: 14,
            children: kThemeColorPresets.map((c) {
              final selected = settings.seedColor.value == c;
              return GestureDetector(
                onTap: () => settings.setSeedColor(Color(c)),
                child: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: Color(c),
                    shape: BoxShape.circle,
                    border: selected
                        ? Border.all(width: 3, color: Theme.of(context).colorScheme.outline)
                        : null,
                  ),
                  child: selected ? const Icon(Icons.check, color: Colors.white, size: 20) : null,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: () => _openCustomColorPicker(context, settings),
            icon: const Icon(Icons.colorize_outlined),
            label: const Text('Cor personalizada (roda de cores)'),
          ),
          const SizedBox(height: 28),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Fundo colorido'),
            subtitle: const Text(
              'Usa um tom suave da cor de destaque no fundo do app, em vez '
              'do branco/preto neutro.',
            ),
            value: settings.coloredBackground,
            onChanged: (v) => settings.setColoredBackground(v),
          ),
          const SizedBox(height: 20),
          Text('Tamanho dos cadernos', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            'Quantos cadernos por linha na tela principal. Menos colunas = '
            'capas maiores. Também dá pra beliscar (pinch) a tela pra ajustar.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 2, label: Text('Grande')),
              ButtonSegment(value: 3, label: Text('Média')),
              ButtonSegment(value: 4, label: Text('Pequena')),
            ],
            selected: {settings.gridColumns},
            onSelectionChanged: (s) => settings.setGridColumns(s.first),
          ),
          const SizedBox(height: 28),
          Card(
            margin: EdgeInsets.zero,
            child: ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('Sobre'),
              subtitle: const Text('Versão, criptografia usada, política de privacidade, suporte'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const AboutScreen()),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
