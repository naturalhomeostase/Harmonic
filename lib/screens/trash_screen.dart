import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/note.dart';
import '../models/notebook.dart';
import '../services/storage_service.dart';

class TrashScreen extends StatefulWidget {
  const TrashScreen({super.key, required this.storage});

  final StorageService storage;

  @override
  State<TrashScreen> createState() => _TrashScreenState();
}

class _TrashScreenState extends State<TrashScreen> {
  int _daysLeft(DateTime deletedAt) {
    final expiresAt = deletedAt.add(const Duration(days: trashRetentionDays));
    final left = expiresAt.difference(DateTime.now()).inDays;
    return left < 0 ? 0 : left;
  }

  Future<void> _restoreNotebook(Notebook nb) async {
    await widget.storage.restoreNotebook(nb);
    setState(() {});
  }

  Future<void> _permanentlyDeleteNotebook(Notebook nb) async {
    final confirmed = await _confirmForever('"${nb.title}" e todas as notas dele');
    if (confirmed == true) {
      await widget.storage.permanentlyDeleteNotebook(nb);
      setState(() {});
    }
  }

  Future<void> _restoreNote(Note note) async {
    await widget.storage.restoreNote(note);
    setState(() {});
  }

  Future<void> _permanentlyDeleteNote(Note note) async {
    final confirmed =
        await _confirmForever('"${note.title.isEmpty ? 'Sem título' : note.title}"');
    if (confirmed == true) {
      await widget.storage.permanentlyDeleteNote(note);
      setState(() {});
    }
  }

  Future<bool?> _confirmForever(String what) {
    return showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Excluir definitivamente?'),
        content: Text('$what será apagado pra sempre. Essa ação não pode ser desfeita.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Excluir'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final notebooks = widget.storage.getTrashedNotebooks();
    final notes = widget.storage.getTrashedNotes();
    final dateFmt = DateFormat('dd/MM/yyyy');

    return Scaffold(
      appBar: AppBar(title: const Text('Lixeira')),
      body: (notebooks.isEmpty && notes.isEmpty)
          ? const Center(child: Text('A lixeira está vazia.'))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Itens ficam aqui por $trashRetentionDays dias antes de serem '
                  'apagados definitivamente.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                if (notebooks.isNotEmpty) ...[
                  Text('Cadernos', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ...notebooks.map((nb) => Card(
                        child: ListTile(
                          leading: const Icon(Icons.menu_book_outlined),
                          title: Text(nb.title),
                          subtitle: Text(
                            'Excluído em ${dateFmt.format(nb.deletedAt!)} · '
                            'expira em ${_daysLeft(nb.deletedAt!)} dia(s)',
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: 'Restaurar',
                                icon: const Icon(Icons.restore_outlined),
                                onPressed: () => _restoreNotebook(nb),
                              ),
                              IconButton(
                                tooltip: 'Excluir definitivamente',
                                icon: const Icon(Icons.delete_forever_outlined),
                                onPressed: () => _permanentlyDeleteNotebook(nb),
                              ),
                            ],
                          ),
                        ),
                      )),
                  const SizedBox(height: 20),
                ],
                if (notes.isNotEmpty) ...[
                  Text('Notas', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ...notes.map((note) {
                    final notebookTitle = widget.storage.notebookTitleFor(note);
                    return Card(
                      child: ListTile(
                        leading: CircleAvatar(backgroundColor: Color(note.backgroundColor)),
                        title: Text(note.title.isEmpty ? 'Sem título' : note.title),
                        subtitle: Text(
                          '${notebookTitle != null ? '$notebookTitle · ' : ''}'
                          'excluída em ${dateFmt.format(note.deletedAt!)} · '
                          'expira em ${_daysLeft(note.deletedAt!)} dia(s)',
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              tooltip: 'Restaurar',
                              icon: const Icon(Icons.restore_outlined),
                              onPressed: () => _restoreNote(note),
                            ),
                            IconButton(
                              tooltip: 'Excluir definitivamente',
                              icon: const Icon(Icons.delete_forever_outlined),
                              onPressed: () => _permanentlyDeleteNote(note),
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                ],
              ],
            ),
    );
  }
}
