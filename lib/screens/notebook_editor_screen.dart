import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import '../models/notebook.dart';
import '../services/storage_service.dart';
import '../widgets/cover_widget.dart';

/// Capas prontas incluídas no app. Você pode enviar as imagens depois e
/// colocá-las em assets/covers/ com esses mesmos nomes (ou eu ajusto a lista).
const kBuiltInCovers = <String>[
  'assets/covers/cover_leaf.jpg',
  'assets/covers/cover_paper.jpg',
  'assets/covers/cover_marble.jpg',
  'assets/covers/cover_night.jpg',
];

const kGradientPresets = <List<int>>[
  [0xFF2E7D6B, 0xFF1B4D3E],
  [0xFF6A11CB, 0xFF2575FC],
  [0xFFEE9CA7, 0xFFFFDDE1],
  [0xFF232526, 0xFF414345],
  [0xFFF7971E, 0xFFFFD200],
];

const kColorPresets = <int>[
  0xFF2E7D6B,
  0xFF34495E,
  0xFF8E44AD,
  0xFFC0392B,
  0xFF16A085,
  0xFFD35400,
];

class NotebookEditorScreen extends StatefulWidget {
  const NotebookEditorScreen({super.key, required this.storage, this.existing});

  final StorageService storage;
  final Notebook? existing;

  @override
  State<NotebookEditorScreen> createState() => _NotebookEditorScreenState();
}

class _NotebookEditorScreenState extends State<NotebookEditorScreen> {
  final _titleCtrl = TextEditingController();

  CoverType _coverType = CoverType.color;
  int _coverColor = kColorPresets.first;
  List<int> _gradient = kGradientPresets.first;
  String? _assetPath;
  String? _devicePath;
  CoverFont _font = CoverFont.montserrat;
  int _titleColor = 0xFFFFFFFF;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    if (e != null) {
      _titleCtrl.text = e.title;
      _coverType = e.coverType;
      _coverColor = e.coverColor ?? _coverColor;
      _gradient = e.gradientColors ?? _gradient;
      _assetPath = e.assetImagePath;
      _devicePath = e.deviceImagePath;
      _font = e.font;
      _titleColor = e.titleColor;
    }
  }

  Notebook get _preview => Notebook(
        id: 'preview',
        title: _titleCtrl.text.isEmpty ? 'Nome do caderno' : _titleCtrl.text,
        coverType: _coverType,
        coverColor: _coverColor,
        gradientColors: _gradient,
        assetImagePath: _assetPath,
        deviceImagePath: _devicePath,
        font: _font,
        titleColor: _titleColor,
      );

  Future<void> _pickDeviceImage() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.gallery, imageQuality: 85);
    if (picked == null) return;

    // Copia a imagem para a pasta de dados do app, pra não depender do
    // caminho original da galeria (que pode sumir). A pasta precisa existir
    // antes do copy — se não, o copy falha silenciosamente.
    final dir = await getApplicationDocumentsDirectory();
    final coversDir = Directory('${dir.path}/covers');
    if (!await coversDir.exists()) {
      await coversDir.create(recursive: true);
    }
    final fileName = '${const Uuid().v4()}.jpg';
    final saved = await File(picked.path).copy('${coversDir.path}/$fileName');

    setState(() {
      _coverType = CoverType.deviceImage;
      _devicePath = saved.path;
    });
  }

  Future<void> _save() async {
    final title = _titleCtrl.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Dê um nome ao caderno.')));
      return;
    }

    if (widget.existing != null) {
      final nb = widget.existing!;
      nb
        ..title = title
        ..coverType = _coverType
        ..coverColor = _coverColor
        ..gradientColors = _gradient
        ..assetImagePath = _assetPath
        ..deviceImagePath = _devicePath
        ..font = _font
        ..titleColor = _titleColor;
      await widget.storage.updateNotebook(nb);
      if (mounted) Navigator.of(context).pop(nb);
      return;
    }

    final created = await widget.storage.createNotebook(
      title: title,
      coverType: _coverType,
      coverColor: _coverColor,
      gradientColors: _gradient,
      assetImagePath: _assetPath,
      deviceImagePath: _devicePath,
      font: _font,
      titleColor: _titleColor,
    );
    if (mounted) Navigator.of(context).pop(created);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? 'Novo caderno' : 'Editar caderno'),
        actions: [
          TextButton(onPressed: _save, child: const Text('Salvar')),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Center(child: CoverWidget(notebook: _preview, width: 180, height: 250)),
          const SizedBox(height: 24),
          TextField(
            controller: _titleCtrl,
            decoration: const InputDecoration(labelText: 'Nome do caderno'),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 24),
          Text('Tipo de capa', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          SegmentedButton<CoverType>(
            showSelectedIcon: false,
            style: SegmentedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              textStyle: const TextStyle(fontSize: 12.5),
            ),
            segments: const [
              ButtonSegment(value: CoverType.color, label: Text('Cor')),
              ButtonSegment(value: CoverType.gradient, label: Text('Gradiente')),
              ButtonSegment(value: CoverType.assetImage, label: Text('Prontas')),
              ButtonSegment(value: CoverType.deviceImage, label: Text('Celular')),
            ],
            selected: {_coverType},
            onSelectionChanged: (s) => setState(() => _coverType = s.first),
          ),
          const SizedBox(height: 16),
          if (_coverType == CoverType.color) _buildColorPicker(),
          if (_coverType == CoverType.gradient) _buildGradientPicker(),
          if (_coverType == CoverType.assetImage) _buildAssetPicker(),
          if (_coverType == CoverType.deviceImage) _buildDevicePicker(),
          const SizedBox(height: 24),
          Text('Fonte do título', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          ...CoverFont.values.map((f) => RadioListTile<CoverFont>(
                value: f,
                groupValue: _font,
                onChanged: (v) => setState(() => _font = v!),
                title: Text(CoverWidget.fontLabel(f), style: CoverWidget.previewStyle(f)),
              )),
        ],
      ),
    );
  }

  Widget _buildColorPicker() {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: kColorPresets.map((c) {
        final selected = c == _coverColor;
        return GestureDetector(
          onTap: () => setState(() => _coverColor = c),
          child: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: Color(c),
              shape: BoxShape.circle,
              border: selected ? Border.all(width: 3, color: Colors.black87) : null,
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildGradientPicker() {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: kGradientPresets.map((g) {
        final selected = g == _gradient;
        return GestureDetector(
          onTap: () => setState(() => _gradient = g),
          child: Container(
            width: 60,
            height: 44,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              gradient: LinearGradient(colors: g.map((c) => Color(c)).toList()),
              border: selected ? Border.all(width: 3, color: Colors.black87) : null,
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildAssetPicker() {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: kBuiltInCovers.map((path) {
        final selected = path == _assetPath;
        return GestureDetector(
          onTap: () => setState(() => _assetPath = path),
          child: Container(
            width: 70,
            height: 96,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              border: selected ? Border.all(width: 3, color: Colors.black87) : null,
              image: DecorationImage(image: AssetImage(path), fit: BoxFit.cover),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildDevicePicker() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_devicePath != null)
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(File(_devicePath!), width: 100, height: 130, fit: BoxFit.cover),
          ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _pickDeviceImage,
          icon: const Icon(Icons.add_photo_alternate_outlined),
          label: const Text('Escolher imagem do celular'),
        ),
      ],
    );
  }
}
