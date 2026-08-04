import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_quill/flutter_quill.dart' as quill;
import 'package:flutter_quill_extensions/flutter_quill_extensions.dart';
import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:uuid/uuid.dart';

import '../models/note.dart';
import '../models/notebook.dart';
import '../services/attachment_service.dart';
import '../services/auth_service.dart';
import '../services/note_export_service.dart';
import '../services/storage_service.dart';
import '../widgets/note_toolbar.dart';
import '../widgets/notebook_gradient.dart';
import '../widgets/paper_painter.dart';

/// Tamanho de fonte do corpo da nota (texto digitado e hint "Comece a
/// escrever..." usam exatamente o mesmo valor).
const kNoteBodyFontSize = 17.0;

const kNoteColorPresets = <int>[
  0xFFFFFFFF, // branco (padrão)
  0xFFFFFDF7, // papel levemente amarelado
  0xFFFFF3D6, // amarelado
  0xFFE8F5E9, // verde clarinho
  0xFFE3F2FD, // azul clarinho
  0xFFFCE4EC, // rosa clarinho
  0xFF263238, // escuro (nota "noturna")
];

/// Papéis de carta prontos, incluídos no app. Envie novas imagens e eu
/// adiciono aqui na lista.
const kBuiltInPapers = <String>[
  'assets/papers/paper_pastel_lilac.png',
  'assets/papers/paper_pastel_rose.png',
];

/// Controllers de edição (título, texto rico, foco, scroll) de uma nota
/// específica. Criados sob demanda conforme o usuário desliza entre notas,
/// e mantidos vivos enquanto a tela do editor estiver aberta.
class _NoteEditingState {
  _NoteEditingState(this.note) {
    titleController = TextEditingController(text: note.title);
    quillController = _buildQuillController(note.content);
    focusNode = FocusNode();
    scrollController = ScrollController();
    // Precisa ser capturado AQUI, antes de qualquer _save() rodar — se
    // fosse um `late` calculado só na primeira leitura, pegaria o valor
    // errado (o _save já teria sobrescrito note.title/content antes da
    // primeira comparação).
    originalTitle = note.title;
    originalContent = note.content;
  }

  final Note note;
  late final TextEditingController titleController;
  late final quill.QuillController quillController;
  late final FocusNode focusNode;
  late final ScrollController scrollController;

  /// Cada nota começa travada (modo visualização) até o usuário destravar
  /// explicitamente pelo cadeado. Ao sair dela (trocar de nota ou fechar o
  /// editor), volta a travar sozinha.
  bool viewOnly = true;

  /// Título e conteúdo tal como estavam quando a nota foi carregada —
  /// comparados em [_NoteEditorScreenState._save] pra só persistir (e
  /// atualizar a data de edição) quando algo realmente mudou. Sem isso,
  /// só *ler* uma nota (abrir e sair, sem editar nada) já bastava pra
  /// atualizar a data de edição e pular pro topo da lista ordenada por
  /// "última atualização".
  late String originalTitle;
  late String originalContent;

  /// Carrega o conteúdo salvo (JSON do Quill Delta). Notas antigas, salvas
  /// como texto puro antes da edição rica, são aproveitadas como um
  /// parágrafo simples — nada se perde na migração.
  static quill.QuillController _buildQuillController(String raw) {
    if (raw.trim().isEmpty) {
      return quill.QuillController.basic();
    }
    try {
      final jsonData = jsonDecode(raw);
      final doc = quill.Document.fromJson(jsonData as List<dynamic>);
      return quill.QuillController(
        document: doc,
        selection: const TextSelection.collapsed(offset: 0),
      );
    } catch (_) {
      final doc = quill.Document()..insert(0, raw);
      return quill.QuillController(
        document: doc,
        selection: const TextSelection.collapsed(offset: 0),
      );
    }
  }

  void dispose() {
    titleController.dispose();
    quillController.dispose();
    focusNode.dispose();
    scrollController.dispose();
  }
}

class NoteEditorScreen extends StatefulWidget {
  const NoteEditorScreen({
    super.key,
    required this.notebook,
    required this.notes,
    required this.initialIndex,
    required this.storage,
    required this.auth,
    this.startUnlocked = false,
  });

  /// O caderno ao qual essas notas pertencem — usado só pra colorir a barra
  /// do topo com o degradê da capa dele.
  final Notebook notebook;

  /// Todas as notas do caderno atual, na ordem em que aparecem na lista —
  /// é entre elas que o usuário desliza.
  final List<Note> notes;
  final int initialIndex;
  final StorageService storage;
  final AuthService auth;

  /// Se true, a nota inicial já abre destravada pra edição (usado ao criar
  /// uma nota nova, pra poder digitar direto sem precisar destravar).
  final bool startUnlocked;

  @override
  State<NoteEditorScreen> createState() => _NoteEditorScreenState();
}

class _NoteEditorScreenState extends State<NoteEditorScreen> {
  late final PageController _pageController;
  late int _currentIndex;
  final Map<String, _NoteEditingState> _states = {};
  final _attachments = AttachmentService();

  /// Id da nota que foi criada agora há pouco pra essa sessão do editor
  /// (`startUnlocked` só é true nesse caso — ver `_newNote` em
  /// notebook_notes_screen.dart). Null quando o editor foi aberto pra ver
  /// uma nota já existente. Usado só pra decidir se descarta a nota
  /// automaticamente caso a pessoa saia sem escrever nada.
  String? _freshNoteId;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _pageController = PageController(initialPage: _currentIndex);
    final initial = _ensureState(widget.notes[_currentIndex]);
    if (widget.startUnlocked) {
      initial.viewOnly = false;
      initial.quillController.readOnly = false;
      _freshNoteId = widget.notes[_currentIndex].id;
    }
  }

  /// Verdadeiro se a nota não tem título, texto, nem anexos — ou seja,
  /// nada que valha a pena guardar.
  bool _isBlank(Note note) {
    final state = _states[note.id];
    final title = (state?.titleController.text ?? note.title).trim();
    final plainText = (state?.quillController.document.toPlainText() ?? note.plainText).trim();
    // De propósito, só título/texto/anexo contam como "conteúdo real". Só
    // mexer nas ferramentas (cor de fundo, tipo de pauta, ou até inserir
    // uma data) sem escrever nada não deveria "salvar" uma nota vazia.
    return title.isEmpty && plainText.isEmpty && note.attachments.isEmpty;
  }

  /// Se a nota que acabou de ser criada nessa sessão continua em branco
  /// (a pessoa abriu o editor e saiu sem escrever nada, sem título, sem
  /// anexo), descarta ela de vez em vez de guardar uma nota vazia sem
  /// utilidade nenhuma. Só se aplica à nota recém-criada — uma nota já
  /// existente que a pessoa esvaziou de propósito não é mexida aqui.
  Future<void> _discardFreshNoteIfBlank() async {
    final freshId = _freshNoteId;
    if (freshId == null) return;
    Note? note;
    for (final n in widget.notes) {
      if (n.id == freshId) {
        note = n;
        break;
      }
    }
    if (note == null || !_isBlank(note)) return;
    await widget.storage.permanentlyDeleteNote(note);
  }

  _NoteEditingState _ensureState(Note note) {
    return _states.putIfAbsent(note.id, () {
      final state = _NoteEditingState(note);
      state.quillController.readOnly = state.viewOnly;
      state.quillController.document.changes.listen((_) => _save(note, state));
      return state;
    });
  }

  Future<void> _save(Note note, _NoteEditingState state) async {
    final newTitle = state.titleController.text;
    final newContent = jsonEncode(state.quillController.document.toDelta().toJson());

    note.title = newTitle;
    note.content = newContent;

    // Só grava (e só atualiza a data de edição) se algo mudou de fato.
    // Sem essa checagem, até só abrir e sair de uma nota sem editar nada
    // bastava pra "atualizar" ela e pular pro topo da lista ordenada por
    // última atualização.
    if (newTitle == state.originalTitle && newContent == state.originalContent) return;
    state.originalTitle = newTitle;
    state.originalContent = newContent;

    await widget.storage.updateNote(note);
  }

  Future<void> _saveCurrent() async {
    final note = widget.notes[_currentIndex];
    final state = _states[note.id];
    if (state != null) await _save(note, state);
  }

  /// Carimba a data no canto da página — um texto fixo separado do conteúdo
  /// editável, pra não bagunçar a formatação do texto.
  void _insertDate() {
    final note = widget.notes[_currentIndex];
    final dateStr = DateFormat('dd/MM/yyyy HH:mm').format(DateTime.now());
    setState(() => note.dateStamp = dateStr);
    widget.storage.updateNote(note);
  }

  Future<void> _insertImage() async {
    final note = widget.notes[_currentIndex];
    final state = _ensureState(note);

    final picker = ImagePicker();
    final picked = await widget.auth.runWithoutAutoLock(
      () => picker.pickImage(source: ImageSource.gallery, imageQuality: 85),
    );
    if (picked == null) return;

    final dir = await getApplicationDocumentsDirectory();
    final imagesDir = Directory('${dir.path}/note_images');
    if (!await imagesDir.exists()) {
      await imagesDir.create(recursive: true);
    }
    final fileName = '${const Uuid().v4()}.jpg';
    final saved = await File(picked.path).copy('${imagesDir.path}/$fileName');

    final docLength = state.quillController.document.length;
    final selectionIndex = state.quillController.selection.baseOffset;
    final index = (selectionIndex >= 0 && selectionIndex <= docLength)
        ? selectionIndex
        : (docLength > 0 ? docLength - 1 : 0);

    state.quillController.document.insert(index, quill.BlockEmbed.image(saved.path));
    state.quillController.updateSelection(
      TextSelection.collapsed(offset: index + 1),
      quill.ChangeSource.local,
    );
  }

  /// Escolhe um arquivo qualquer (PDF, documento etc.) do aparelho e
  /// anexa à nota atual, cifrado.
  Future<void> _attachFile() async {
    final note = widget.notes[_currentIndex];

    // Sem isso, o app trancaria sozinho ao voltar do seletor de arquivos
    // (ver AuthService.runWithoutAutoLock).
    final result = await widget.auth.runWithoutAutoLock(
      () => FilePicker.pickFiles(withData: true, allowMultiple: false),
    );
    final picked = result?.files.firstOrNull;
    if (picked == null || picked.bytes == null) return;

    final attachment = await _attachments.addAttachment(
      noteId: note.id,
      key: widget.auth.sessionKey!,
      fileName: picked.name,
      bytes: picked.bytes!,
    );

    setState(() => note.attachments.add(attachment));
    await widget.storage.updateNote(note);
  }

  Future<void> _openAttachment(NoteAttachment attachment) async {
    final note = widget.notes[_currentIndex];
    final tempFile = await _attachments.decryptToTemp(
      noteId: note.id,
      key: widget.auth.sessionKey!,
      attachment: attachment,
    );
    // O app externo que for abrir isso só precisa ler o arquivo por um
    // instante — não faz sentido deixar a cópia decifrada parada no
    // aparelho por muito tempo depois disso.
    _attachments.scheduleTempCleanup(tempFile);
    await OpenFile.open(tempFile.path);
  }

  Future<void> _removeAttachment(NoteAttachment attachment) async {
    final note = widget.notes[_currentIndex];
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Remover anexo?'),
        content: Text('"${attachment.fileName}" vai ser apagado. Essa ação não pode ser desfeita.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Remover')),
        ],
      ),
    );
    if (confirmed != true) return;

    await _attachments.deleteAttachment(noteId: note.id, attachment: attachment);
    setState(() => note.attachments.removeWhere((a) => a.id == attachment.id));
    await widget.storage.updateNote(note);
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  /// Insere uma linha divisória simples na posição do cursor (funciona em
  /// qualquer lugar do texto, não só no topo).
  void _insertDivider() {
    final note = widget.notes[_currentIndex];
    final state = _ensureState(note);
    final dividerLine = '\n${List.filled(28, '─').join()}\n';

    final docLength = state.quillController.document.length;
    final selectionIndex = state.quillController.selection.baseOffset;
    final index = (selectionIndex >= 0 && selectionIndex <= docLength)
        ? selectionIndex
        : (docLength > 0 ? docLength - 1 : 0);

    state.quillController.document.insert(index, dividerLine);
    state.quillController.updateSelection(
      TextSelection.collapsed(offset: index + dividerLine.length),
      quill.ChangeSource.local,
    );
  }

  static const _weekdaysPt = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];
  static const _monthsPt = [
    'jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'
  ];

  String _formatFullDate(DateTime dt) {
    final weekday = _weekdaysPt[dt.weekday - 1];
    final month = _monthsPt[dt.month - 1];
    final hh = dt.hour.toString().padLeft(2, '0');
    final mm = dt.minute.toString().padLeft(2, '0');
    return '$weekday, ${dt.day} de $month de ${dt.year}, $hh:$mm';
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    return '${(bytes / 1024).toStringAsFixed(1)} KB';
  }

  Future<void> _confirmDeleteNote() async {
    final note = widget.notes[_currentIndex];
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Mover para a lixeira?'),
        content: Text(
          '"${note.title.isEmpty ? 'Sem título' : note.title}" vai para a lixeira por '
          '$trashRetentionDays dias, e depois é apagada de vez. Você pode '
          'restaurar antes disso.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Mover')),
        ],
      ),
    );
    if (confirmed != true) return;

    // Essa é uma exclusão de verdade pedida pela pessoa — evita que o
    // PopScope do descarte automático de nota em branco tente mexer nela
    // de novo ao sair (ela já não é mais a nota "recém-criada" de qualquer
    // forma, mas por clareza desligamos o rastro aqui).
    if (_freshNoteId == note.id) _freshNoteId = null;

    await widget.storage.moveNoteToTrash(note);
    if (mounted) Navigator.of(context).pop();
  }

  void _showStats() {
    final note = widget.notes[_currentIndex];
    final state = _ensureState(note);
    final plainText = state.quillController.document.toPlainText();
    final trimmed = plainText.trim();

    final characters = trimmed.length;
    final words = trimmed.isEmpty ? 0 : trimmed.split(RegExp(r'\s+')).length;
    final paragraphs = trimmed.isEmpty
        ? 0
        : trimmed.split('\n').where((line) => line.trim().isNotEmpty).length;
    final sizeBytes = utf8.encode(trimmed).length;
    final readMinutes = (words / 200).ceil();
    final readTime = words == 0 || readMinutes <= 1 ? 'menos de 1 minuto' : '$readMinutes minutos';

    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Estatísticas da nota'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$words palavra${words == 1 ? '' : 's'} · '
                '$characters caractere${characters == 1 ? '' : 's'} · '
                '$paragraphs parágrafo${paragraphs == 1 ? '' : 's'}',
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              Text('Tempo de leitura: $readTime'),
              const Divider(height: 24),
              _statLine('Última modificação', _formatFullDate(note.updatedAt)),
              _statLine('Criada em', _formatFullDate(note.createdAt)),
              _statLine('ID da nota', note.id),
              _statLine('Tamanho', _formatSize(sizeBytes)),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Fechar')),
        ],
      ),
    );
  }

  Widget _statLine(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(text: '$label: ', style: TextStyle(color: Theme.of(context).hintColor)),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }

  Future<void> _showShareMenu() async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (_) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.content_copy_outlined),
              title: const Text('Copiar texto'),
              subtitle: const Text('Some da área de transferência sozinho em 45s'),
              onTap: () => Navigator.pop(context, 'copy'),
            ),
            ListTile(
              leading: const Icon(Icons.text_snippet_outlined),
              title: const Text('Enviar como texto'),
              onTap: () => Navigator.pop(context, 'text'),
            ),
            ListTile(
              leading: const Icon(Icons.picture_as_pdf_outlined),
              title: const Text('Salvar/enviar como PDF'),
              onTap: () => Navigator.pop(context, 'pdf'),
            ),
          ],
        ),
      ),
    );
    if (action == null) return;
    if (action == 'copy') {
      await copyNoteToClipboard(widget.notes[_currentIndex]);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Copiado — some da área de transferência em 45s.')),
        );
      }
    } else if (action == 'text') {
      await _shareAsText();
    } else if (action == 'pdf') {
      await _shareAsPdf();
    }
  }

  Future<void> _shareAsText() async {
    final note = widget.notes[_currentIndex];
    final state = _ensureState(note);
    // Usa o texto que está sendo editado agora (pode ter mudanças ainda não
    // salvas no objeto Note em si).
    await _save(note, state);
    await shareNoteAsText(note);
  }

  Future<void> _shareAsPdf() async {
    final note = widget.notes[_currentIndex];
    final state = _ensureState(note);
    await _save(note, state);
    await shareNoteAsPdf(note);
  }

  void _pickPageStyle() {
    final note = widget.notes[_currentIndex];
    showModalBottomSheet(
      context: context,
      builder: (_) => SafeArea(
        child: Wrap(
          children: PageStyle.values.map((s) {
            return ListTile(
              title: Text(_styleLabel(s)),
              trailing: note.pageStyle == s ? const Icon(Icons.check) : null,
              onTap: () async {
                setState(() => note.pageStyle = s);
                await widget.storage.updateNote(note);
                if (context.mounted) Navigator.pop(context);
              },
            );
          }).toList(),
        ),
      ),
    );
  }

  void _pickBackgroundColor() {
    final note = widget.notes[_currentIndex];
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Cor de fundo', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: kNoteColorPresets.map((c) {
                    return GestureDetector(
                      onTap: () async {
                        setState(() {
                          note.backgroundType = PageBackgroundType.color;
                          note.backgroundColor = c;
                          note.backgroundImagePath = null;
                          note.backgroundAssetPath = null;
                          note.lineColor = c == 0xFF263238 ? 0x33FFFFFF : 0x33000000;
                        });
                        await widget.storage.updateNote(note);
                        if (context.mounted) Navigator.pop(context);
                      },
                      child: Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: Color(c),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.black26),
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 24),
                Text('Papel de carta', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: kBuiltInPapers.map((path) {
                    return GestureDetector(
                      onTap: () async {
                        setState(() {
                          note.backgroundType = PageBackgroundType.asset;
                          note.backgroundAssetPath = path;
                          note.backgroundImagePath = null;
                          note.lineColor = 0x33000000;
                        });
                        await widget.storage.updateNote(note);
                        if (context.mounted) Navigator.pop(context);
                      },
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Image.asset(path, width: 70, height: 96, fit: BoxFit.cover),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _styleLabel(PageStyle s) {
    switch (s) {
      case PageStyle.blank:
        return 'Liso (sem pauta)';
      case PageStyle.lined:
        return 'Pautado';
      case PageStyle.grid:
        return 'Quadriculado';
      case PageStyle.dotted:
        return 'Pontilhado';
      case PageStyle.cornell:
        return 'Cornell (anotações + resumo)';
    }
  }

  ImageProvider? _resolveBackgroundImage(Note note) {
    if (note.backgroundType == PageBackgroundType.asset && note.backgroundAssetPath != null) {
      return AssetImage(note.backgroundAssetPath!);
    }
    if (note.backgroundType == PageBackgroundType.image && note.backgroundImagePath != null) {
      return FileImage(File(note.backgroundImagePath!));
    }
    return null;
  }

  /// Fundo da barra do topo: se a capa do caderno for uma foto, mostra o
  /// topo dessa foto (com um véu escuro por cima, pra manter os ícones
  /// legíveis); senão, usa o degradê derivado da cor/gradiente da capa.
  Widget _buildAppBarBackground() {
    final nb = widget.notebook;
    final isImageCover = nb.coverType == CoverType.assetImage || nb.coverType == CoverType.deviceImage;
    final imagePath = nb.coverType == CoverType.assetImage ? nb.assetImagePath : nb.deviceImagePath;

    if (isImageCover && imagePath != null) {
      final image = nb.coverType == CoverType.assetImage
          ? Image.asset(imagePath, fit: BoxFit.cover, alignment: Alignment.topCenter)
          : Image.file(File(imagePath), fit: BoxFit.cover, alignment: Alignment.topCenter);
      return Stack(
        fit: StackFit.expand,
        children: [
          image,
          Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.black.withOpacity(0.6), Colors.black.withOpacity(0.3)],
              ),
            ),
          ),
        ],
      );
    }

    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: notebookGradientColors(nb),
        ),
      ),
    );
  }

  /// Se o fundo da barra do topo é escuro. Capa de foto sempre é (por causa
  /// do véu escuro desenhado por cima em [_buildAppBarBackground]), mas capa
  /// de cor sólida ou degradê pode ser clara (alguns dos degradês prontos
  /// são pastel/amarelo) — nesses casos ícones e texto brancos ficam
  /// camuflados, então a cor certa depende da capa real desse caderno.
  bool get _appBarIsDark {
    final nb = widget.notebook;
    final isImageCover = nb.coverType == CoverType.assetImage || nb.coverType == CoverType.deviceImage;
    if (isImageCover) return true;
    return notebookBackdropIsDark(nb);
  }

  @override
  Widget build(BuildContext context) {
    final currentNote = widget.notes[_currentIndex];
    final currentState = _ensureState(currentNote);
    final appBarIsDark = _appBarIsDark;
    final onAppBarColor = appBarIsDark ? Colors.white : Colors.black87;

    // A área embaixo da barra de status aqui é a capa do caderno ou o
    // gradiente atrás do título — que pode ser clara ou escura dependendo
    // da capa escolhida —, então os ícones da barra de status (hora,
    // bateria, notificações) precisam acompanhar essa cor real, e não
    // presumir que é sempre escura. Sem isso, com uma capa clara, os
    // ícones brancos ficavam camuflados contra o fundo claro.
    //
    // IMPORTANTE: isso é passado direto pro `AppBar.systemOverlayStyle`,
    // não por um AnnotatedRegion por fora do Scaffold. O AppBar já cria a
    // própria AnnotatedRegion internamente, e a documentação do Flutter é
    // explícita: "apps should not enclose an AppBar with their own
    // AnnotatedRegion" — a região do AppBar sempre vence a de fora, então
    // um AnnotatedRegion externo aqui simplesmente não tinha efeito
    // nenhum (foi o que aconteceu na primeira tentativa).
    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) _discardFreshNoteIfBlank();
      },
      child: Scaffold(
      appBar: AppBar(
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: appBarIsDark ? Brightness.light : Brightness.dark,
          statusBarBrightness: appBarIsDark ? Brightness.dark : Brightness.light,
        ),
        foregroundColor: onAppBarColor,
        flexibleSpace: _buildAppBarBackground(),
        leadingWidth: 56,
        leading: Center(
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: onAppBarColor.withOpacity(0.12),
              shape: BoxShape.circle,
            ),
            child: IconButton(
              padding: EdgeInsets.zero,
              icon: const Icon(Icons.arrow_back, size: 20),
              tooltip: 'Voltar',
              onPressed: () => Navigator.maybePop(context),
            ),
          ),
        ),
        titleSpacing: 0,
        title: Center(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
            decoration: BoxDecoration(
              color: onAppBarColor.withOpacity(0.12),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              '${_currentIndex + 1} / ${widget.notes.length}',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: onAppBarColor),
            ),
          ),
        ),
        // Só o essencial fica sempre à vista: o cadeado (a ação mais usada,
        // já que troca entre ler e editar) e um menu "mais" com o resto —
        // compartilhar/exportar e estatísticas. As ferramentas de inserir
        // (cor, pauta, imagem, data, anexo) se mudaram pra uma fileira fixa
        // na barra de baixo, perto de onde o polegar já está enquanto se
        // escreve — antes eram nove botões espremidos aqui em cima, o que
        // fazia a seta de voltar ficar disputando espaço com eles.
        actions: [
          IconButton(
            tooltip: currentState.viewOnly ? 'Modo edição' : 'Modo visualização',
            icon: Icon(currentState.viewOnly ? Icons.lock_outline : Icons.lock_open_outlined),
            onPressed: () {
              setState(() {
                currentState.viewOnly = !currentState.viewOnly;
                currentState.quillController.readOnly = currentState.viewOnly;
              });
            },
          ),
          PopupMenuButton<String>(
            tooltip: 'Mais opções',
            icon: const Icon(Icons.more_vert),
            onSelected: (value) {
              switch (value) {
                case 'share':
                  _showShareMenu();
                  break;
                case 'stats':
                  _showStats();
                  break;
                case 'delete':
                  _confirmDeleteNote();
                  break;
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'share',
                child: ListTile(
                  leading: Icon(Icons.share_outlined),
                  title: Text('Compartilhar / Exportar'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: 'stats',
                child: ListTile(
                  leading: Icon(Icons.bar_chart_outlined),
                  title: Text('Estatísticas da nota'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuDivider(),
              PopupMenuItem(
                value: 'delete',
                child: ListTile(
                  leading: Icon(Icons.delete_outline),
                  title: Text('Mover para a lixeira'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ],
      ),
      body: PageView.builder(
        controller: _pageController,
        itemCount: widget.notes.length,
        onPageChanged: (i) async {
          await _saveCurrent();
          // A nota que está sendo deixada volta a travar sozinha, mesmo
          // que ainda esteja "viva" em memória (você pode voltar a ela
          // depois deslizando de novo).
          final leavingNote = widget.notes[_currentIndex];
          final leavingState = _states[leavingNote.id];
          if (leavingState != null && !leavingState.viewOnly) {
            leavingState.viewOnly = true;
            leavingState.quillController.readOnly = true;
          }
          if (!mounted) return;
          setState(() => _currentIndex = i);
          _ensureState(widget.notes[i]);
        },
        itemBuilder: (context, i) {
          final n = widget.notes[i];
          final state = _ensureState(n);
          final backgroundImage = _resolveBackgroundImage(n);
          final storedBgColor = Color(n.backgroundColor);
          final ambientIsDark = Theme.of(context).brightness == Brightness.dark;
          // Se o tema do app estiver escuro, uma página clara/branca escurece
          // sozinha também (só visualmente — a cor escolhida pra nota continua
          // guardada como estava e volta a aparecer normal se o app voltar
          // pro tema claro). Fotos de fundo não entram nessa troca, porque
          // não daria pra escurecer uma foto de forma limpa assim.
          final autoDarken =
              ambientIsDark && backgroundImage == null && storedBgColor.computeLuminance() > 0.4;
          final effectiveBgColor = autoDarken ? const Color(0xFF263238) : storedBgColor;
          final pageIsDark = autoDarken || n.backgroundColor == 0xFF263238;
          final pageTextColor = pageIsDark ? Colors.white : Colors.black87;

          // Um sub-tema local, baseado na cor de fundo DESSA página (não no
          // tema claro/escuro do app). Isso garante que ícones e texto do
          // editor tenham contraste correto mesmo se o app estiver no modo
          // escuro e a página for clara (ou vice-versa) — mas preserva o
          // colorScheme Material 3 (a cor de tema escolhida pelo usuário),
          // só ajustando as cores padrão de ícone/texto por cima dele.
          final ambientTheme = Theme.of(context);
          final pageTheme = ambientTheme.copyWith(
            iconTheme: IconThemeData(color: pageTextColor),
            textTheme: ambientTheme.textTheme.apply(bodyColor: pageTextColor, displayColor: pageTextColor),
          );

          return Theme(
            data: pageTheme,
            child: DefaultTextStyle(
              style: TextStyle(color: pageTextColor, fontSize: 16, letterSpacing: 0.5),
              child: Stack(
                children: [
                  PaperBackground(
                    style: n.pageStyle,
                    lineColor: Color(n.lineColor),
                    backgroundColor: effectiveBgColor,
                    backgroundImage: backgroundImage,
                    topOffset: 96,
                    child: Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(14, 20, 14, 0),
                          child: TextField(
                            controller: state.titleController,
                            readOnly: state.viewOnly,
                            autocorrect: false,
                            enableSuggestions: false,
                            enableIMEPersonalizedLearning: false,
                            style: TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.w500,
                              color: pageTextColor,
                              letterSpacing: 0.1,
                            ),
                            decoration: InputDecoration(
                              border: InputBorder.none,
                              filled: false,
                              contentPadding: EdgeInsets.zero,
                              hintText: 'Título',
                              hintStyle: TextStyle(
                                fontWeight: FontWeight.w400,
                                color: pageTextColor.withOpacity(0.5),
                              ),
                            ),
                            onChanged: (_) => _save(n, state),
                          ),
                        ),
                        if (n.attachments.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(14, 8, 14, 0),
                            child: Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: n.attachments.map((a) {
                                return InputChip(
                                  avatar: const Icon(Icons.insert_drive_file_outlined, size: 18),
                                  label: Text(
                                    '${a.fileName} · ${_formatFileSize(a.sizeBytes)}',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  onPressed: () => _openAttachment(a),
                                  onDeleted: state.viewOnly ? null : () => _removeAttachment(a),
                                  deleteIcon: const Icon(Icons.close, size: 16),
                                );
                              }).toList(),
                            ),
                          ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          child: Divider(height: 18, thickness: 1, color: pageTextColor.withOpacity(0.25)),
                        ),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                            child: quill.QuillEditor(
                              controller: state.quillController,
                              focusNode: state.focusNode,
                              scrollController: state.scrollController,
                              config: quill.QuillEditorConfig(
                                placeholder: 'Comece a escrever...',
                                padding: const EdgeInsets.symmetric(vertical: 8),
                                expands: true,
                                scrollable: true,
                                embedBuilders: FlutterQuillEmbeds.editorBuilders(),
                                // Explícito de propósito: toque num link
                                // abre no navegador do aparelho, em vez de
                                // depender do comportamento padrão da lib.
                                onLaunchUrl: (url) async {
                                  final uri = Uri.tryParse(url);
                                  if (uri == null) return;
                                  await launchUrl(uri, mode: LaunchMode.externalApplication);
                                },
                                // Antes o texto digitado usava o tamanho padrão do
                                // Quill (menor) enquanto o hint usava outro estilo,
                                // deixando o corpo visivelmente menor que o hint.
                                // Aqui igualamos os dois ao mesmo TextStyle.
                                customStyles: quill.DefaultStyles(
                                  paragraph: quill.DefaultTextBlockStyle(
                                    TextStyle(
                                      fontSize: kNoteBodyFontSize,
                                      color: pageTextColor,
                                      letterSpacing: 0.5,
                                      height: 1.15,
                                    ),
                                    const quill.HorizontalSpacing(0, 0),
                                    const quill.VerticalSpacing(6, 0),
                                    const quill.VerticalSpacing(0, 0),
                                    null,
                                  ),
                                  placeHolder: quill.DefaultTextBlockStyle(
                                    TextStyle(
                                      fontSize: kNoteBodyFontSize,
                                      color: pageTextColor.withOpacity(0.5),
                                      letterSpacing: 0.5,
                                      height: 1.15,
                                    ),
                                    const quill.HorizontalSpacing(0, 0),
                                    const quill.VerticalSpacing(6, 0),
                                    const quill.VerticalSpacing(0, 0),
                                    null,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        if (n.dateStamp != null)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(14, 0, 14, 4),
                            child: Align(
                              alignment: Alignment.centerRight,
                              child: Text(
                                n.dateStamp!,
                                style: TextStyle(
                                  fontSize: 12,
                                  fontStyle: FontStyle.italic,
                                  color: pageTextColor.withOpacity(0.65),
                                ),
                              ),
                            ),
                          ),
                        if (!state.viewOnly)
                          NoteToolbar(
                            controller: state.quillController,
                            isDark: pageIsDark,
                            pageColor: effectiveBgColor,
                            onPickBackgroundColor: _pickBackgroundColor,
                            onPickPageStyle: _pickPageStyle,
                            onInsertDivider: _insertDivider,
                            onInsertImage: _insertImage,
                            onInsertDate: _insertDate,
                            onAttachFile: _attachFile,
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    ),
    );
  }

  @override
  void dispose() {
    _saveCurrent();
    for (final s in _states.values) {
      s.dispose();
    }
    _pageController.dispose();
    super.dispose();
  }
}
