import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../models/note.dart';
import '../models/notebook.dart';
import '../services/auth_service.dart';
import '../services/note_export_service.dart';
import '../services/storage_service.dart';
import '../widgets/notebook_gradient.dart';
import '../widgets/cover_widget.dart';
import 'note_editor_screen.dart';

class NotebookNotesScreen extends StatefulWidget {
  const NotebookNotesScreen({super.key, required this.notebook, required this.storage, required this.auth});

  final Notebook notebook;
  final StorageService storage;
  final AuthService auth;

  @override
  State<NotebookNotesScreen> createState() => _NotebookNotesScreenState();
}

class _NotebookNotesScreenState extends State<NotebookNotesScreen> {
  // Padrão é data de criação, não de edição — abrir/ler uma nota não deve
  // fazer ela pular de posição na lista. "Última atualização" continua
  // disponível como opção no menu de ordenar, pra quem preferir.
  SortMode _sortMode = SortMode.dateCreated;

  /// IDs das notas com a prévia expandida na lista.
  final Set<String> _expandedIds = {};

  @override
  void initState() {
    super.initState();
    // Se a capa for uma foto, calcula a cor de destaque dela em segundo
    // plano (só na primeira vez; fica em cache depois).
    ensureNotebookAccentColorExtracted(widget.notebook, () {
      if (mounted) setState(() {});
    });
  }

  Future<void> _pickTabColor() async {
    final result = await showModalBottomSheet<int?>(
      context: context,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Cor das abas', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                'Vale só pra esse caderno. Escolha uma cor fixa, ou volte pra '
                'automática (baseada na capa).',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 14,
                runSpacing: 14,
                children: kTabColorPresets.map((c) {
                  final selected = widget.notebook.tabColor == c;
                  return GestureDetector(
                    onTap: () => Navigator.pop(context, c),
                    child: Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: Color(c),
                        shape: BoxShape.circle,
                        border: selected ? Border.all(width: 3, color: Colors.black87) : null,
                      ),
                      child: selected ? const Icon(Icons.check, color: Colors.white, size: 18) : null,
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: () => Navigator.pop(context, -1),
                icon: const Icon(Icons.auto_awesome_outlined),
                label: const Text('Usar cor automática (baseada na capa)'),
              ),
            ],
          ),
        ),
      ),
    );

    if (result == null) return;
    setState(() {
      widget.notebook.tabColor = result == -1 ? null : result;
    });
    await widget.storage.updateNotebook(widget.notebook);
    // Se voltou pro automático e a capa for foto, garante que a cor
    // extraída (ou o cálculo dela) esteja pronta.
    ensureNotebookAccentColorExtracted(widget.notebook, () {
      if (mounted) setState(() {});
    });
  }

  Future<void> _newNote() async {
    final note = await widget.storage.createNote(
      notebook: widget.notebook,
      title: '',
    );
    if (!mounted) return;
    final notes = widget.storage.getNotesForNotebook(widget.notebook.id);
    final index = notes.indexWhere((n) => n.id == note.id);
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NoteEditorScreen(
          notebook: widget.notebook,
          notes: notes,
          initialIndex: index < 0 ? 0 : index,
          storage: widget.storage,
          auth: widget.auth,
          // Nota recém-criada: começa liberada pra digitar, sem precisar
          // destravar o cadeado primeiro.
          startUnlocked: true,
        ),
      ),
    );
    setState(() {});
  }

  void _toggleExpanded(Note note) {
    setState(() {
      if (_expandedIds.contains(note.id)) {
        _expandedIds.remove(note.id);
      } else {
        _expandedIds.add(note.id);
      }
    });
  }

  /// Expande todas as notas visíveis de uma vez se nem todas já estiverem
  /// expandidas; senão, recolhe todas — alterna, como um só toque.
  void _toggleExpandAll(List<Note> notes) {
    setState(() {
      final allExpanded = notes.every((n) => _expandedIds.contains(n.id));
      if (allExpanded) {
        _expandedIds.clear();
      } else {
        _expandedIds.addAll(notes.map((n) => n.id));
      }
    });
  }

  Future<void> _showNoteMenu(Note note) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (_) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: Icon(note.pinned ? Icons.push_pin : Icons.push_pin_outlined),
              title: Text(note.pinned ? 'Desafixar do topo' : 'Fixar no topo'),
              onTap: () => Navigator.pop(context, 'pin'),
            ),
            ListTile(
              leading: const Icon(Icons.content_copy_outlined),
              title: const Text('Copiar texto'),
              subtitle: const Text('Some da área de transferência sozinho em 45s'),
              onTap: () => Navigator.pop(context, 'copy'),
            ),
            ListTile(
              leading: const Icon(Icons.share_outlined),
              title: const Text('Compartilhar como texto'),
              onTap: () => Navigator.pop(context, 'share_text'),
            ),
            ListTile(
              leading: const Icon(Icons.picture_as_pdf_outlined),
              title: const Text('Salvar/enviar como PDF'),
              onTap: () => Navigator.pop(context, 'share_pdf'),
            ),
            ListTile(
              leading: const Icon(Icons.archive_outlined),
              title: const Text('Arquivar'),
              subtitle: const Text('Sai da lista, sem prazo de validade — dá pra desarquivar quando quiser'),
              onTap: () => Navigator.pop(context, 'archive'),
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline),
              title: const Text('Mover para a lixeira'),
              onTap: () => Navigator.pop(context, 'delete'),
            ),
          ],
        ),
      ),
    );

    if (!mounted || action == null) return;

    switch (action) {
      case 'pin':
        await widget.storage.toggleNotePin(note);
        setState(() {});
        break;
      case 'copy':
        await copyNoteToClipboard(note);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Copiado — some da área de transferência em 45s.')),
          );
        }
        break;
      case 'share_text':
        await shareNoteAsText(note);
        break;
      case 'share_pdf':
        await shareNoteAsPdf(note);
        break;
      case 'archive':
        await widget.storage.archiveNote(note);
        setState(() {});
        break;
      case 'delete':
        await _confirmDelete(note);
        break;
    }
  }

  Future<void> _confirmDeleteNotebook() async {
    final nb = widget.notebook;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Mover caderno para a lixeira?'),
        content: Text(
          '"${nb.title}" e as notas dele vão para a lixeira por '
          '$trashRetentionDays dias, e depois são apagados de vez. '
          'Você pode restaurar antes disso, na tela da lixeira.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Mover')),
        ],
      ),
    );
    if (confirmed != true) return;

    await widget.storage.moveNotebookToTrash(nb);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _confirmDelete(Note note) async {
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
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Mover'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await widget.storage.moveNoteToTrash(note);
      setState(() {});
    }
  }

  /// Fundo "cheio" da tela, com a cor/imagem da capa do caderno cobrindo
  /// tudo, inclusive por trás da barra de status.
  Widget _buildBackdrop(BuildContext context) {
    final nb = widget.notebook;
    if (nb.coverType == CoverType.assetImage || nb.coverType == CoverType.deviceImage) {
      final path = nb.coverType == CoverType.assetImage ? nb.assetImagePath : nb.deviceImagePath;
      if (path == null) return _gradientBackdrop();
      final image = nb.coverType == CoverType.assetImage
          ? Image.asset(path, fit: BoxFit.cover, width: double.infinity, height: double.infinity)
          : Image.file(File(path), fit: BoxFit.cover, width: double.infinity, height: double.infinity);
      return Stack(
        fit: StackFit.expand,
        children: [
          image,
          Container(color: Colors.black.withOpacity(0.35)),
        ],
      );
    }
    return _gradientBackdrop();
  }

  Widget _gradientBackdrop() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: notebookGradientColors(widget.notebook),
        ),
      ),
    );
  }

  String _sortLabel(SortMode mode) {
    switch (mode) {
      case SortMode.dateCreated:
        return 'Data de criação';
      case SortMode.dateUpdated:
        return 'Última atualização';
      case SortMode.name:
        return 'Nome (A-Z)';
      case SortMode.manual:
        return 'Manual (arrastar)';
    }
  }

  @override
  Widget build(BuildContext context) {
    final notes = widget.storage.getNotesForNotebook(widget.notebook.id, sortMode: _sortMode);
    final dateFmt = DateFormat('dd/MM/yyyy HH:mm');
    final accentColor = notebookAccentColor(widget.notebook);

    // A capa/degradê desse caderno pode ser clara (ex: alguns dos degradês
    // prontos são em tons pastel/amarelo), então os ícones da barra de
    // status e o texto por cima precisam se adaptar à cor real de fundo, e
    // não presumir que é sempre escura — senão ficam camuflados quando a
    // capa é clara.
    //
    // IMPORTANTE: vai direto no `AppBar.systemOverlayStyle`, não num
    // AnnotatedRegion por fora do Scaffold — o AppBar cria sua própria
    // AnnotatedRegion internamente, e ela sempre vence a de fora (a
    // documentação do Flutter até avisa pra não fazer isso). Foi por isso
    // que a primeira tentativa de correção não teve efeito nenhum.
    final backdropIsDark = notebookBackdropIsDark(widget.notebook);
    final onBackdropColor = backdropIsDark ? Colors.white : Colors.black87;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: backdropIsDark ? Brightness.light : Brightness.dark,
          statusBarBrightness: backdropIsDark ? Brightness.dark : Brightness.light,
        ),
        backgroundColor: Colors.transparent,
        foregroundColor: onBackdropColor,
        elevation: 0,
        leadingWidth: 56,
        leading: Center(
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: onBackdropColor.withOpacity(0.12),
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
        title: Text(
          widget.notebook.title,
          overflow: TextOverflow.ellipsis,
          style: CoverWidget.previewStyle(widget.notebook.font).copyWith(color: onBackdropColor),
        ),
        actions: [
          PopupMenuButton<SortMode>(
            tooltip: 'Ordenar',
            icon: const Icon(Icons.sort),
            initialValue: _sortMode,
            onSelected: (mode) => setState(() => _sortMode = mode),
            itemBuilder: (_) => SortMode.values
                .map((m) => PopupMenuItem(value: m, child: Text(_sortLabel(m))))
                .toList(),
          ),
          PopupMenuButton<String>(
            tooltip: 'Mais opções',
            icon: const Icon(Icons.more_vert),
            onSelected: (value) {
              switch (value) {
                case 'tab_color':
                  _pickTabColor();
                  break;
                case 'delete':
                  _confirmDeleteNotebook();
                  break;
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'tab_color',
                child: ListTile(
                  leading: Icon(Icons.palette_outlined),
                  title: Text('Cor das abas'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuDivider(),
              PopupMenuItem(
                value: 'delete',
                child: ListTile(
                  leading: Icon(Icons.delete_outline),
                  title: Text('Mover caderno para a lixeira'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(child: _buildBackdrop(context)),
          SafeArea(
            child: notes.isEmpty
                ? Center(
                    child: Text(
                      'Nenhuma nota ainda. Toque em + pra criar.',
                      style: TextStyle(color: onBackdropColor.withOpacity(0.9)),
                    ),
                  )
                : Column(
                    children: [
                      // Cabeçalho da lista: contagem de notas e o toque de
                      // expandir/recolher tudo, bem colados na primeira
                      // nota (não lá em cima perto do título do caderno).
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 4, 8, 2),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              notes.length == 1 ? '1 nota' : '${notes.length} notas',
                              style: TextStyle(fontSize: 13, color: onBackdropColor.withOpacity(0.75)),
                            ),
                            IconButton(
                              visualDensity: VisualDensity.compact,
                              tooltip: notes.every((n) => _expandedIds.contains(n.id))
                                  ? 'Recolher todas'
                                  : 'Expandir todas',
                              icon: Icon(
                                notes.every((n) => _expandedIds.contains(n.id))
                                    ? Icons.unfold_less
                                    : Icons.unfold_more,
                                size: 20,
                                color: onBackdropColor.withOpacity(0.75),
                              ),
                              onPressed: () => _toggleExpandAll(notes),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        child: _sortMode == SortMode.manual
                            ? _buildManualList(notes, dateFmt, accentColor)
                            : _buildList(notes, dateFmt, accentColor),
                      ),
                    ],
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: accentColor,
        foregroundColor: Colors.white,
        onPressed: _newNote,
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildList(List<Note> notes, DateFormat dateFmt, Color accentColor) {
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(0, 90, 20, 20),
      itemCount: notes.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, i) {
        final note = notes[i];
        return _NoteTab(
          note: note,
          dateLabel: dateFmt.format(note.updatedAt),
          accentColor: accentColor,
          expanded: _expandedIds.contains(note.id),
          onToggleExpand: () => _toggleExpanded(note),
          onOpen: () => _openNote(notes, i),
          onLongPress: () => _showNoteMenu(note),
          onDelete: () => _confirmDelete(note),
        );
      },
    );
  }

  /// Modo manual: lista arrastável (built-in do Flutter, sem depender de
  /// pacote extra pra reordenar).
  Widget _buildManualList(List<Note> notes, DateFormat dateFmt, Color accentColor) {
    return ReorderableListView.builder(
      padding: const EdgeInsets.fromLTRB(0, 90, 20, 20),
      itemCount: notes.length,
      buildDefaultDragHandles: false,
      onReorder: (oldIndex, newIndex) async {
        if (newIndex > oldIndex) newIndex -= 1;
        final reordered = List<Note>.from(notes);
        final moved = reordered.removeAt(oldIndex);
        reordered.insert(newIndex, moved);
        await widget.storage.reorderNotes(reordered);
        setState(() {});
      },
      itemBuilder: (context, i) {
        final note = notes[i];
        return Padding(
          key: ValueKey(note.id),
          padding: const EdgeInsets.only(bottom: 12),
          child: _NoteTab(
            note: note,
            dateLabel: dateFmt.format(note.updatedAt),
            accentColor: accentColor,
            expanded: _expandedIds.contains(note.id),
            onToggleExpand: () => _toggleExpanded(note),
            onOpen: () => _openNote(notes, i),
            onLongPress: () => _showNoteMenu(note),
            onDelete: () => _confirmDelete(note),
            dragIndex: i,
          ),
        );
      },
    );
  }

  Future<void> _openNote(List<Note> notes, int index) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NoteEditorScreen(
          notebook: widget.notebook,
          notes: notes,
          initialIndex: index,
          storage: widget.storage,
          auth: widget.auth,
        ),
      ),
    );
    setState(() {});
  }
}

/// Item da lista de notas, desenhado como uma aba/índice de agenda. O corpo
/// usa a própria cor de identidade do caderno, semi-transparente (deixa o
/// fundo colorido da tela transparecer por trás, criando um efeito de
/// "vidro fosco"), com uma borda fina mais clara. Toque em qualquer lugar
/// expande/recolhe uma prévia do texto; com a prévia expandida, aparece um
/// link pra abrir a nota por completo.
class _NoteTab extends StatelessWidget {
  const _NoteTab({
    required this.note,
    required this.dateLabel,
    required this.accentColor,
    required this.expanded,
    required this.onToggleExpand,
    required this.onOpen,
    required this.onLongPress,
    required this.onDelete,
    this.dragIndex,
  });

  final Note note;
  final String dateLabel;
  final Color accentColor;
  final bool expanded;
  final VoidCallback onToggleExpand;
  final VoidCallback onOpen;
  final VoidCallback onLongPress;
  final VoidCallback onDelete;

  /// Se não-nulo, a barra colorida à esquerda vira uma alça de arrastar
  /// dedicada (modo de ordenação manual). Usar uma alça separada em vez de
  /// "toque longo no card inteiro" evita o conflito com o toque longo que
  /// já abre o menu de opções da nota (os dois gestos disputavam o mesmo
  /// toque longo, e o menu quase sempre vencia, impedindo o arraste).
  final int? dragIndex;

  @override
  Widget build(BuildContext context) {
    final tabColor = accentColor.withOpacity(0.68);
    final borderColor = Color.alphaBlend(Colors.white.withOpacity(0.55), accentColor);
    final noteColor = Color(note.backgroundColor);
    const radius = BorderRadius.only(
      topRight: Radius.circular(16),
      bottomRight: Radius.circular(16),
    );

    final preview = note.plainText.trim();

    final colorBar = Container(
      width: dragIndex != null ? 26 : 10,
      color: noteColor,
      alignment: Alignment.center,
      child: dragIndex != null
          ? const Icon(Icons.drag_indicator, size: 16, color: Colors.white70)
          : null,
    );

    return Container(
      decoration: BoxDecoration(
        color: tabColor,
        borderRadius: radius,
        border: Border.all(color: borderColor, width: 1.4),
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onToggleExpand,
            onDoubleTap: onOpen,
            onLongPress: onLongPress,
            child: IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  dragIndex != null
                      ? ReorderableDragStartListener(index: dragIndex!, child: colorBar)
                      : colorBar,
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Row(
                            children: [
                              if (note.pinned) ...[
                                const Icon(Icons.push_pin, size: 14, color: Colors.white),
                                const SizedBox(width: 4),
                              ],
                              Expanded(
                                child: Text(
                                  note.title.isEmpty ? 'Sem título' : note.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w600,
                                    fontSize: 16,
                                    letterSpacing: 0.15,
                                    color: Colors.white,
                                    shadows: [Shadow(color: Colors.black38, blurRadius: 3)],
                                  ),
                                ),
                              ),
                              Icon(
                                expanded ? Icons.expand_less : Icons.expand_more,
                                size: 20,
                                color: Colors.white70,
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(
                            dateLabel,
                            style: const TextStyle(
                              fontSize: 12,
                              letterSpacing: 0.4,
                              color: Colors.white70,
                            ),
                          ),
                          if (expanded) ...[
                            const SizedBox(height: 10),
                            Text(
                              preview.isEmpty ? '(sem conteúdo)' : preview,
                              maxLines: 12,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 14,
                                height: 1.4,
                                color: Colors.white,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.white70),
                        onPressed: onDelete,
                      ),
                      if (expanded) ...[
                        const SizedBox(height: 2),
                        Material(
                          color: Colors.white.withOpacity(0.18),
                          shape: const CircleBorder(),
                          child: InkWell(
                            customBorder: const CircleBorder(),
                            onTap: onOpen,
                            child: const Padding(
                              padding: EdgeInsets.all(8),
                              child: Tooltip(
                                message: 'Abrir nota completa',
                                child: Icon(Icons.arrow_forward_rounded, size: 16, color: Colors.white),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 6),
                      ],
                    ],
                  ),
                  const SizedBox(width: 4),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
