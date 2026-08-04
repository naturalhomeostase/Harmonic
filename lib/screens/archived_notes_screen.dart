import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../models/note.dart';
import '../services/auth_service.dart';
import '../services/storage_service.dart';
import 'note_editor_screen.dart';

class ArchivedNotesScreen extends StatefulWidget {
  const ArchivedNotesScreen({super.key, required this.storage, required this.auth});

  final StorageService storage;
  final AuthService auth;

  @override
  State<ArchivedNotesScreen> createState() => _ArchivedNotesScreenState();
}

class _ArchivedNotesScreenState extends State<ArchivedNotesScreen> {
  Future<void> _unarchive(Note note) async {
    await widget.storage.unarchiveNote(note);
    setState(() {});
  }

  Future<void> _open(Note note) async {
    final matches = widget.storage.allNotebooksRaw.where((nb) => nb.id == note.notebookId);
    final notebook = matches.isEmpty ? null : matches.first;
    if (notebook == null) {
      // Caso raro: o caderno da nota foi excluído, mas a nota em si
      // continua arquivada em algum lugar. Não deveria acontecer no fluxo
      // normal (excluir o caderno também move as notas dele pra lixeira),
      // mas não custa não travar a tela se acontecer.
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('O caderno dessa nota não foi encontrado.')),
      );
      return;
    }
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NoteEditorScreen(
          notebook: notebook,
          notes: [note],
          initialIndex: 0,
          storage: widget.storage,
          auth: widget.auth,
        ),
      ),
    );
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final notes = widget.storage.getArchivedNotes();
    final dateFmt = DateFormat('dd/MM/yyyy');

    return Scaffold(
      appBar: AppBar(title: const Text('Notas arquivadas')),
      body: notes.isEmpty
          ? const Center(child: Text('Nenhuma nota arquivada.'))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Notas arquivadas ficam fora da lista do caderno, mas não '
                  'têm prazo de validade — desarquive quando quiser.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                ...notes.map((note) {
                  final notebookTitle = widget.storage.notebookTitleFor(note);
                  return Card(
                    child: ListTile(
                      leading: CircleAvatar(backgroundColor: Color(note.backgroundColor)),
                      title: Text(note.title.isEmpty ? 'Sem título' : note.title),
                      subtitle: Text(
                        '${notebookTitle != null ? '$notebookTitle · ' : ''}'
                        'arquivada em ${dateFmt.format(note.archivedAt!)}',
                      ),
                      onTap: () => _open(note),
                      trailing: IconButton(
                        tooltip: 'Desarquivar',
                        icon: const Icon(Icons.unarchive_outlined),
                        onPressed: () => _unarchive(note),
                      ),
                    ),
                  );
                }),
              ],
            ),
    );
  }
}
