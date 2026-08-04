import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../services/auth_service.dart';
import '../services/storage_service.dart';
import 'note_editor_screen.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key, required this.storage, required this.auth});

  final StorageService storage;
  final AuthService auth;

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final _queryCtrl = TextEditingController();
  List<NoteSearchResult> _results = [];

  void _onQueryChanged(String query) {
    setState(() => _results = widget.storage.search(query));
  }

  Future<void> _openResult(NoteSearchResult result) async {
    final notes = widget.storage.getNotesForNotebook(result.notebook.id);
    final index = notes.indexWhere((n) => n.id == result.note.id);
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NoteEditorScreen(
          notebook: result.notebook,
          notes: notes,
          initialIndex: index < 0 ? 0 : index,
          storage: widget.storage,
          auth: widget.auth,
        ),
      ),
    );
    if (mounted) _onQueryChanged(_queryCtrl.text);
  }

  @override
  Widget build(BuildContext context) {
    final dateFmt = DateFormat('dd/MM/yyyy');

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _queryCtrl,
          autofocus: true,
          onChanged: _onQueryChanged,
          decoration: const InputDecoration(
            border: InputBorder.none,
            hintText: 'Buscar em título e conteúdo…',
          ),
          style: const TextStyle(fontSize: 18),
        ),
        actions: [
          if (_queryCtrl.text.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () {
                _queryCtrl.clear();
                _onQueryChanged('');
              },
            ),
        ],
      ),
      body: _buildBody(dateFmt),
    );
  }

  Widget _buildBody(DateFormat dateFmt) {
    if (_queryCtrl.text.trim().isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Digite pra buscar em todas as suas notas.', textAlign: TextAlign.center),
        ),
      );
    }
    if (_results.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Nenhuma nota encontrada.', textAlign: TextAlign.center),
        ),
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _results.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (context, i) {
        final result = _results[i];
        return Card(
          child: ListTile(
            leading: CircleAvatar(backgroundColor: Color(result.note.backgroundColor)),
            title: Text(
              result.note.title.isEmpty ? 'Sem título' : result.note.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  result.snippet,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  '${result.notebook.title} · ${dateFmt.format(result.note.updatedAt)}',
                  style: TextStyle(
                    fontSize: 12,
                    color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                  ),
                ),
              ],
            ),
            isThreeLine: true,
            onTap: () => _openResult(result),
          ),
        );
      },
    );
  }

  @override
  void dispose() {
    _queryCtrl.dispose();
    super.dispose();
  }
}
