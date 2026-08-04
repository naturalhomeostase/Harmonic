import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/notebook.dart';
import '../services/auth_service.dart';
import '../services/backup_file_service.dart';
import '../services/settings_service.dart';
import '../services/storage_service.dart';
import '../widgets/cover_widget.dart';
import 'lock_screen.dart';
import 'notebook_editor_screen.dart';
import 'notebook_notes_screen.dart';
import 'search_screen.dart';
import 'settings_screen.dart';

class NotebookListScreen extends StatefulWidget {
  const NotebookListScreen({super.key, required this.auth, required this.storage});

  final AuthService auth;
  final StorageService storage;

  @override
  State<NotebookListScreen> createState() => _NotebookListScreenState();
}

class _NotebookListScreenState extends State<NotebookListScreen> {
  final _backupService = BackupFileService();
  bool _backingUp = false;
  bool _restoring = false;
  SortMode _sortMode = SortMode.dateUpdated;

  Future<void> _openNewNotebook() async {
    final created = await Navigator.of(context).push<Notebook>(
      MaterialPageRoute(
        builder: (_) => NotebookEditorScreen(storage: widget.storage),
      ),
    );
    if (created != null) setState(() {});
  }

  Future<void> _backup() async {
    setState(() => _backingUp = true);
    try {
      final saved = await widget.auth.runWithoutAutoLock(
        () => _backupService.backupNow(
          storage: widget.storage,
          sessionKey: widget.auth.sessionKey!,
        ),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              saved ? 'Backup criptografado salvo com sucesso.' : 'Backup cancelado.',
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Falha no backup: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _backingUp = false);
    }
  }

  Future<void> _restore() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Restaurar backup?'),
        content: const Text(
          'Isso vai substituir todos os cadernos e notas que estão no '
          'aparelho agora pelos dados do arquivo de backup que você '
          'escolher a seguir. Essa ação não pode ser desfeita.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Restaurar'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _restoring = true);
    try {
      final data = await widget.auth.runWithoutAutoLock(
        () => _backupService.restoreLatest(sessionKey: widget.auth.sessionKey!),
      );
      if (data == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Nenhum arquivo escolhido.')),
          );
        }
        return;
      }
      await widget.storage.importFromBackup(data);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Backup restaurado com sucesso.')),
        );
      }
    } on BackupWrongKeyOrCorruptedException {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Não consegui abrir esse backup. Ou é de outra senha, ou o '
              'arquivo está corrompido/incompleto.',
            ),
            duration: Duration(seconds: 5),
          ),
        );
      }
    } on BackupInvalidFormatException {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Esse arquivo não parece ser um backup do Nodus.'),
            duration: Duration(seconds: 5),
          ),
        );
      }
    } on BackupUnsupportedVersionException {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Esse backup foi feito por uma versão mais nova do Nodus. '
              'Atualize o app antes de tentar restaurá-lo.',
            ),
            duration: Duration(seconds: 5),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Falha ao restaurar: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _restoring = false);
    }
  }

  Future<void> _showNotebookMenu(Notebook nb) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (_) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: Icon(nb.pinned ? Icons.push_pin : Icons.push_pin_outlined),
              title: Text(nb.pinned ? 'Desafixar do topo' : 'Fixar no topo'),
              onTap: () => Navigator.pop(context, 'pin'),
            ),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('Editar caderno'),
              onTap: () => Navigator.pop(context, 'edit'),
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
        await widget.storage.togglePin(nb);
        setState(() {});
        break;
      case 'edit':
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => NotebookEditorScreen(storage: widget.storage, existing: nb),
          ),
        );
        setState(() {});
        break;
      case 'delete':
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('Mover para a lixeira?'),
            content: Text(
              '"${nb.title}" e as notas dele vão para a lixeira por '
              '$trashRetentionDays dias, e depois são apagados de vez. '
              'Você pode restaurar antes disso, na tela da lixeira.',
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
          await widget.storage.moveNotebookToTrash(nb);
          setState(() {});
        }
        break;
    }
  }

  Future<void> _renameLibrary(SettingsService settings) async {
    final ctrl = TextEditingController(text: settings.libraryName);
    final newName = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Renomear'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Nome da tela inicial'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(
            onPressed: () => Navigator.pop(context, ctrl.text),
            child: const Text('Salvar'),
          ),
        ],
      ),
    );
    if (newName != null) await settings.setLibraryName(newName);
  }

  String _sortLabel(SortMode mode) {
    switch (mode) {
      case SortMode.dateCreated:
        return 'Data de criação';
      case SortMode.dateUpdated:
        return 'Data de atualização';
      case SortMode.name:
        return 'Nome (A-Z)';
      case SortMode.manual:
        return 'Manual (arrastar)';
    }
  }

  @override
  Widget build(BuildContext context) {
    final notebooks = widget.storage.getNotebooks(sortMode: _sortMode);
    final settings = context.watch<SettingsService>();

    return Scaffold(
      appBar: AppBar(
        title: InkWell(
          onTap: () => _renameLibrary(settings),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Text(
                  settings.libraryName,
                  overflow: TextOverflow.ellipsis,
                  // Mesma fonte escolhida em Configurações (uma das 5
                  // fontes usadas também nos títulos de capa dos cadernos).
                  style: CoverWidget.previewStyle(settings.libraryNameFont),
                ),
              ),
              const SizedBox(width: 6),
              const Icon(Icons.edit_outlined, size: 16),
            ],
          ),
        ),
        actions: [
          IconButton(
            tooltip: 'Buscar',
            icon: const Icon(Icons.search),
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => SearchScreen(storage: widget.storage, auth: widget.auth)),
              );
            },
          ),
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
            icon: (_backingUp || _restoring)
                ? const SizedBox(
                    width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.more_vert),
            onSelected: (value) {
              switch (value) {
                case 'settings':
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => SettingsScreen(storage: widget.storage, auth: widget.auth),
                    ),
                  );
                  break;
                case 'backup':
                  _backup();
                  break;
                case 'restore':
                  _restore();
                  break;
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'settings',
                child: ListTile(
                  leading: Icon(Icons.settings_outlined),
                  title: Text('Configurações'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: 'backup',
                child: ListTile(
                  leading: Icon(Icons.save_outlined),
                  title: Text('Fazer backup'),
                  subtitle: Text('Escolher onde salvar (inclui Google Drive)'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: 'restore',
                child: ListTile(
                  leading: Icon(Icons.folder_open_outlined),
                  title: Text('Restaurar backup'),
                  subtitle: Text('Escolher o arquivo de backup'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
          IconButton(
            tooltip: 'Bloquear o app (voltar à tela de senha)',
            icon: const Icon(Icons.lock_outline_rounded),
            onPressed: () async {
              // Fecha os boxes e limpa o cache decifrado da memória antes de
              // travar — não basta zerar a chave de sessão.
              await widget.storage.closeAll();
              widget.auth.lock();
              if (!context.mounted) return;
              // A LockScreen não está mais na pilha de navegação (o login
              // usa pushReplacement), então popUntil(isFirst) não tinha
              // efeito nenhum. Substituímos toda a pilha por uma LockScreen
              // nova.
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(
                  builder: (_) => LockScreen(auth: widget.auth, storage: widget.storage),
                ),
                (route) => false,
              );
            },
          ),
        ],
      ),
      body: notebooks.isEmpty
          ? Center(
              child: Text('Nenhum caderno ainda. Toque em + pra criar o primeiro.',
                  style: Theme.of(context).textTheme.bodyMedium),
            )
          : (_sortMode == SortMode.manual
              ? _buildManualList(notebooks)
              : _buildGrid(notebooks, settings)),
      floatingActionButton: FloatingActionButton(
        onPressed: _openNewNotebook,
        child: const Icon(Icons.add),
      ),
    );
  }

  double _pinchBase = 1.0;

  Widget _buildGrid(List<Notebook> notebooks, SettingsService settings) {
    return GestureDetector(
      onScaleStart: (_) => _pinchBase = 1.0,
      onScaleUpdate: (details) {
        final delta = details.scale - _pinchBase;
        if (delta > 0.2) {
          // Beliscar "abrindo" (afastando os dedos) = capas maiores = menos colunas.
          settings.setGridColumns(settings.gridColumns - 1);
          _pinchBase = details.scale;
        } else if (delta < -0.2) {
          // Beliscar "fechando" (juntando os dedos) = capas menores = mais colunas.
          settings.setGridColumns(settings.gridColumns + 1);
          _pinchBase = details.scale;
        }
      },
      child: GridView.builder(
        padding: const EdgeInsets.all(20),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: settings.gridColumns,
          mainAxisSpacing: 20,
          crossAxisSpacing: 20,
          childAspectRatio: 0.72,
        ),
        itemCount: notebooks.length,
        itemBuilder: (context, i) {
          final nb = notebooks[i];
          return GestureDetector(
            onTap: () async {
              await Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => NotebookNotesScreen(notebook: nb, storage: widget.storage, auth: widget.auth),
                ),
              );
              setState(() {});
            },
            onLongPress: () => _showNotebookMenu(nb),
            child: Stack(
              children: [
                Positioned.fill(
                  child: CoverWidget(
                    notebook: nb,
                    width: double.infinity,
                    height: double.infinity,
                    noteCount: widget.storage.getNotesForNotebook(nb.id).length,
                  ),
                ),
                if (nb.pinned)
                  Positioned(
                    top: 8,
                    right: 8,
                    child: Container(
                      padding: const EdgeInsets.all(6),
                      decoration: const BoxDecoration(
                        color: Colors.black54,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.push_pin, size: 16, color: Colors.white),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  /// Modo de ordenação manual: uma lista vertical arrastável (a grade não
  /// dá pra reordenar por arraste sem um pacote extra, então nesse modo a
  /// exibição muda pra lista).
  Widget _buildManualList(List<Notebook> notebooks) {
    return ReorderableListView.builder(
      padding: const EdgeInsets.all(20),
      itemCount: notebooks.length,
      onReorder: (oldIndex, newIndex) async {
        if (newIndex > oldIndex) newIndex -= 1;
        final reordered = List<Notebook>.from(notebooks);
        final moved = reordered.removeAt(oldIndex);
        reordered.insert(newIndex, moved);
        await widget.storage.reorderNotebooks(reordered);
        setState(() {});
      },
      itemBuilder: (context, i) {
        final nb = notebooks[i];
        return Card(
          key: ValueKey(nb.id),
          margin: const EdgeInsets.only(bottom: 12),
          child: ListTile(
            leading: SizedBox(
              width: 44,
              height: 56,
              child: CoverWidget(notebook: nb, width: 44, height: 56),
            ),
            title: Text(nb.title),
            trailing: const Icon(Icons.drag_handle),
            onTap: () async {
              await Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => NotebookNotesScreen(notebook: nb, storage: widget.storage, auth: widget.auth),
                ),
              );
              setState(() {});
            },
            onLongPress: () => _showNotebookMenu(nb),
          ),
        );
      },
    );
  }
}
