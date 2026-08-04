import 'dart:convert';
import 'dart:typed_data';

import 'package:hive_flutter/hive_flutter.dart';
import 'package:uuid/uuid.dart';

import '../models/note.dart';
import '../models/notebook.dart';
import 'attachment_service.dart';
import 'crypto_service.dart';

const _notebooksBoxName = 'notebooks_v2';
const _notesBoxName = 'notes_v2';

/// Quantos dias um item fica na lixeira antes de ser apagado de vez.
const trashRetentionDays = 7;

/// Como ordenar cadernos/notas nas listas.
enum SortMode { dateCreated, dateUpdated, name, manual }

/// Um resultado de busca: a nota encontrada, o caderno a que pertence, e um
/// trecho do texto mostrando onde o termo aparece.
class NoteSearchResult {
  NoteSearchResult({required this.note, required this.notebook, required this.snippet});

  final Note note;
  final Notebook notebook;
  final String snippet;
}

/// Guarda os dados localmente. Cada caderno/nota é serializado em JSON e
/// cifrado individualmente com AES-256-GCM (autenticado) antes de ir pro
/// disco — não dependemos da criptografia embutida do Hive. O Hive aqui só
/// funciona como um key-value store simples (chave -> blob cifrado em
/// base64); ele nunca vê os dados em texto puro.
///
/// Uma cópia decifrada fica em memória (cache) enquanto o app está
/// desbloqueado, pra telas poderem ler a lista de cadernos/notas sem
/// precisar esperar uma operação assíncrona a cada rebuild.
class StorageService {
  final _crypto = CryptoService();
  final _attachments = AttachmentService();
  final _uuid = const Uuid();

  Uint8List? _key;
  Box<String>? _notebooksBox;
  Box<String>? _notesBox;

  final Map<String, Notebook> _notebooksCache = {};
  final Map<String, Note> _notesCache = {};

  /// Chaves (IDs) de registros que existiam no disco mas não puderam ser
  /// decifrados na última abertura — corrompidos, ou cifrados com uma
  /// chave diferente da atual. Usado só pra avisar o usuário; não some
  /// silenciosamente.
  final List<String> _corruptedNotebookKeys = [];
  final List<String> _corruptedNoteKeys = [];
  bool get hasCorruptedRecords =>
      _corruptedNotebookKeys.isNotEmpty || _corruptedNoteKeys.isNotEmpty;
  int get corruptedRecordCount => _corruptedNotebookKeys.length + _corruptedNoteKeys.length;

  /// Deve ser chamado uma vez no início do app (antes de qualquer unlock).
  static Future<void> init() async {
    await Hive.initFlutter();
  }

  /// Abre os boxes, decifra tudo pra memória, e apaga de vez o que já
  /// passou do prazo da lixeira.
  Future<void> openWithKey(Uint8List key) async {
    _key = key;
    _notebooksBox = await Hive.openBox<String>(_notebooksBoxName);
    _notesBox = await Hive.openBox<String>(_notesBoxName);
    await _loadCache();
    await _purgeExpiredTrash();
  }

  Future<void> _loadCache() async {
    _notebooksCache.clear();
    _notesCache.clear();
    _corruptedNotebookKeys.clear();
    _corruptedNoteKeys.clear();

    for (final key in _notebooksBox!.keys) {
      final blob = _notebooksBox!.get(key);
      if (blob == null) continue;
      try {
        final json = await _decryptJson(blob);
        final nb = Notebook.fromJson(json);
        _notebooksCache[nb.id] = nb;
      } catch (_) {
        // Registro corrompido ou cifrado com outra chave — não trava o
        // app inteiro por causa de um item só, mas fica registrado pra
        // avisar o usuário (ver hasCorruptedRecords).
        _corruptedNotebookKeys.add(key.toString());
      }
    }

    for (final key in _notesBox!.keys) {
      final blob = _notesBox!.get(key);
      if (blob == null) continue;
      try {
        final json = await _decryptJson(blob);
        final note = Note.fromJson(json);
        _notesCache[note.id] = note;
      } catch (_) {
        _corruptedNoteKeys.add(key.toString());
      }
    }
  }

  Future<void> _purgeExpiredTrash() async {
    final cutoff = DateTime.now().subtract(const Duration(days: trashRetentionDays));

    final expiredNotebooks = _notebooksCache.values
        .where((n) => n.deletedAt != null && n.deletedAt!.isBefore(cutoff))
        .toList();
    for (final nb in expiredNotebooks) {
      await permanentlyDeleteNotebook(nb);
    }

    final expiredNotes =
        _notesCache.values.where((n) => n.deletedAt != null && n.deletedAt!.isBefore(cutoff)).toList();
    for (final note in expiredNotes) {
      await permanentlyDeleteNote(note);
    }
  }

  Future<void> closeAll() async {
    await _notebooksBox?.close();
    await _notesBox?.close();
    _notebooksBox = null;
    _notesBox = null;
    _notebooksCache.clear();
    _notesCache.clear();
    _key = null;
  }

  // ---------- Criptografia por registro ----------

  Future<String> _encryptJson(Map<String, dynamic> json) async {
    final bytes = Uint8List.fromList(utf8.encode(jsonEncode(json)));
    final encrypted = await _crypto.encryptBytes(_key!, bytes);
    return base64Encode(encrypted);
  }

  Future<Map<String, dynamic>> _decryptJson(String blob) async {
    final bytes = await _crypto.decryptBytes(_key!, base64Decode(blob));
    return jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
  }

  Future<void> _persistNotebook(Notebook nb) async {
    final blob = await _encryptJson(nb.toJson());
    await _notebooksBox!.put(nb.id, blob);
  }

  Future<void> _persistNote(Note note) async {
    final blob = await _encryptJson(note.toJson());
    await _notesBox!.put(note.id, blob);
  }

  /// Re-cifra tudo com uma nova chave (usar ao trocar a senha mestre).
  /// Como o cache já está decifrado em memória, isso é só reescrever cada
  /// registro com a chave nova.
  Future<void> reencryptAll(Uint8List newKey) async {
    _key = newKey;
    for (final nb in _notebooksCache.values) {
      await _persistNotebook(nb);
    }
    for (final note in _notesCache.values) {
      await _persistNote(note);
    }
  }

  void _sortList<T>(
    List<T> list,
    SortMode sortMode, {
    required bool Function(T) isPinned,
    required DateTime Function(T) createdAt,
    required DateTime Function(T) updatedAt,
    required String Function(T) title,
    required int Function(T) sortOrder,
  }) {
    list.sort((a, b) {
      // Fixados sempre primeiro, independente do modo de ordenação.
      if (isPinned(a) != isPinned(b)) return isPinned(a) ? -1 : 1;
      switch (sortMode) {
        case SortMode.dateCreated:
          return createdAt(b).compareTo(createdAt(a));
        case SortMode.dateUpdated:
          return updatedAt(b).compareTo(updatedAt(a));
        case SortMode.name:
          return title(a).toLowerCase().compareTo(title(b).toLowerCase());
        case SortMode.manual:
          return sortOrder(a).compareTo(sortOrder(b));
      }
    });
  }

  // ---------- Cadernos ----------

  List<Notebook> getNotebooks({SortMode sortMode = SortMode.dateUpdated}) {
    final list = _notebooksCache.values.where((n) => n.deletedAt == null).toList();
    _sortList<Notebook>(
      list,
      sortMode,
      isPinned: (n) => n.pinned,
      createdAt: (n) => n.createdAt,
      updatedAt: (n) => n.updatedAt,
      title: (n) => n.title,
      sortOrder: (n) => n.sortOrder,
    );
    return list;
  }

  Future<Notebook> createNotebook({
    required String title,
    required CoverType coverType,
    int? coverColor,
    List<int>? gradientColors,
    String? assetImagePath,
    String? deviceImagePath,
    CoverFont font = CoverFont.montserrat,
    int titleColor = 0xFFFFFFFF,
  }) async {
    final notebook = Notebook(
      id: _uuid.v4(),
      title: title,
      coverType: coverType,
      coverColor: coverColor,
      gradientColors: gradientColors,
      assetImagePath: assetImagePath,
      deviceImagePath: deviceImagePath,
      font: font,
      titleColor: titleColor,
    );
    _notebooksCache[notebook.id] = notebook;
    await _persistNotebook(notebook);
    return notebook;
  }

  Future<void> updateNotebook(Notebook notebook) async {
    notebook.updatedAt = DateTime.now();
    _notebooksCache[notebook.id] = notebook;
    await _persistNotebook(notebook);
  }

  Future<void> togglePin(Notebook notebook) async {
    notebook.pinned = !notebook.pinned;
    await updateNotebook(notebook);
  }

  /// Grava a nova ordem manual (arrastar-pra-reordenar) de uma lista de
  /// cadernos já reordenada pela UI.
  Future<void> reorderNotebooks(List<Notebook> newOrder) async {
    for (var i = 0; i < newOrder.length; i++) {
      newOrder[i].sortOrder = i;
      await _persistNotebook(newOrder[i]);
    }
  }

  /// Move o caderno (e todas as notas ativas dele) pra lixeira.
  Future<void> moveNotebookToTrash(Notebook notebook) async {
    final now = DateTime.now();
    notebook.deletedAt = now;
    await _persistNotebook(notebook);
    for (final note in _notesCache.values.where((n) => n.notebookId == notebook.id && n.deletedAt == null)) {
      note.deletedAt = now;
      await _persistNote(note);
    }
  }

  /// Restaura o caderno e as notas que foram pra lixeira junto com ele.
  Future<void> restoreNotebook(Notebook notebook) async {
    notebook.deletedAt = null;
    await _persistNotebook(notebook);
    for (final note in _notesCache.values.where((n) => n.notebookId == notebook.id && n.deletedAt != null)) {
      note.deletedAt = null;
      await _persistNote(note);
    }
  }

  /// Apaga o caderno e todas as notas dele de vez — sem volta.
  Future<void> permanentlyDeleteNotebook(Notebook notebook) async {
    for (final note in _notesCache.values.where((n) => n.notebookId == notebook.id).toList()) {
      await permanentlyDeleteNote(note);
    }
    _notebooksCache.remove(notebook.id);
    await _notebooksBox!.delete(notebook.id);
  }

  List<Notebook> getTrashedNotebooks() {
    final list = _notebooksCache.values.where((n) => n.deletedAt != null).toList();
    list.sort((a, b) => b.deletedAt!.compareTo(a.deletedAt!));
    return list;
  }

  // ---------- Notas ----------

  List<Note> getNotesForNotebook(String notebookId, {SortMode sortMode = SortMode.dateUpdated}) {
    final list = _notesCache.values
        .where((n) => n.notebookId == notebookId && n.deletedAt == null && n.archivedAt == null)
        .toList();
    _sortList<Note>(
      list,
      sortMode,
      isPinned: (n) => n.pinned,
      createdAt: (n) => n.createdAt,
      updatedAt: (n) => n.updatedAt,
      title: (n) => n.title,
      sortOrder: (n) => n.sortOrder,
    );
    return list;
  }

  Future<void> toggleNotePin(Note note) async {
    note.pinned = !note.pinned;
    await updateNote(note);
  }

  /// Grava a nova ordem manual de uma lista de notas já reordenada pela UI.
  Future<void> reorderNotes(List<Note> newOrder) async {
    for (var i = 0; i < newOrder.length; i++) {
      newOrder[i].sortOrder = i;
      await _persistNote(newOrder[i]);
    }
  }

  Future<Note> createNote({
    required Notebook notebook,
    required String title,
    String content = '',
    PageStyle pageStyle = PageStyle.blank,
  }) async {
    final note = Note(
      id: _uuid.v4(),
      notebookId: notebook.id,
      title: title,
      content: content,
      pageStyle: pageStyle,
    );
    _notesCache[note.id] = note;
    await _persistNote(note);

    notebook.noteIds.add(note.id);
    await updateNotebook(notebook);

    return note;
  }

  Future<void> updateNote(Note note) async {
    note.updatedAt = DateTime.now();
    _notesCache[note.id] = note;
    await _persistNote(note);
  }

  /// Move a nota pra lixeira (recuperável por alguns dias).
  Future<void> moveNoteToTrash(Note note) async {
    note.deletedAt = DateTime.now();
    await _persistNote(note);
  }

  Future<void> restoreNote(Note note) async {
    note.deletedAt = null;
    await _persistNote(note);
  }

  /// Arquiva a nota — some da lista normal do caderno, mas sem prazo de
  /// validade e sem risco de ser apagada (diferente da lixeira).
  Future<void> archiveNote(Note note) async {
    note.archivedAt = DateTime.now();
    await _persistNote(note);
  }

  Future<void> unarchiveNote(Note note) async {
    note.archivedAt = null;
    await _persistNote(note);
  }

  /// Todas as notas arquivadas, de qualquer caderno, mais recentes primeiro.
  List<Note> getArchivedNotes() {
    final list = _notesCache.values.where((n) => n.archivedAt != null && n.deletedAt == null).toList();
    list.sort((a, b) => b.archivedAt!.compareTo(a.archivedAt!));
    return list;
  }

  /// Apaga a nota de vez — sem volta. Isso inclui os arquivos anexados a
  /// ela (que ficam fora do blob JSON principal, então precisam ser
  /// apagados à parte, ou ficariam órfãos no armazenamento do app), e a
  /// referência dela na lista `noteIds` do caderno.
  Future<void> permanentlyDeleteNote(Note note) async {
    _notesCache.remove(note.id);
    await _notesBox!.delete(note.id);
    await _attachments.deleteAllForNote(note.id);

    final notebook = _notebooksCache[note.notebookId];
    if (notebook != null && notebook.noteIds.remove(note.id)) {
      await _persistNotebook(notebook);
    }
  }

  List<Note> getTrashedNotes() {
    final list = _notesCache.values.where((n) => n.deletedAt != null).toList();
    list.sort((a, b) => b.deletedAt!.compareTo(a.deletedAt!));
    return list;
  }

  /// Nome do caderno de uma nota (usado na tela da lixeira, que mistura
  /// notas de vários cadernos).
  String? notebookTitleFor(Note note) => _notebooksCache[note.notebookId]?.title;

  // Exposto para o serviço de backup (exportar tudo em memória).
  List<Notebook> get allNotebooksRaw => _notebooksCache.values.toList();
  List<Note> get allNotesRaw => _notesCache.values.toList();

  // ---------- Busca ----------

  /// Busca notas por título ou conteúdo (texto puro, sem formatação).
  /// Notas na lixeira não aparecem nos resultados — mas notas arquivadas
  /// sim, de propósito: arquivar só tira da lista do caderno, não deveria
  /// esconder a nota de quem está procurando por ela.
  List<NoteSearchResult> search(String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return [];

    final results = <NoteSearchResult>[];
    for (final note in _notesCache.values) {
      if (note.deletedAt != null) continue;
      final titleMatch = note.title.toLowerCase().contains(q);
      final plainText = note.plainText;
      final contentMatch = plainText.toLowerCase().contains(q);
      if (!titleMatch && !contentMatch) continue;

      final notebook = _notebooksCache[note.notebookId];
      if (notebook == null || notebook.deletedAt != null) continue;

      results.add(NoteSearchResult(
        note: note,
        notebook: notebook,
        snippet: _buildSnippet(plainText, q),
      ));
    }

    results.sort((a, b) => b.note.updatedAt.compareTo(a.note.updatedAt));
    return results;
  }

  String _buildSnippet(String plainText, String query) {
    final lower = plainText.toLowerCase();
    final idx = lower.indexOf(query);
    if (idx < 0) return plainText.trim().replaceAll('\n', ' ');

    const window = 40;
    final start = (idx - window).clamp(0, plainText.length);
    final end = (idx + query.length + window).clamp(0, plainText.length);
    final prefix = start > 0 ? '…' : '';
    final suffix = end < plainText.length ? '…' : '';
    return '$prefix${plainText.substring(start, end).replaceAll('\n', ' ')}$suffix';
  }

  // ---------- Restauração de backup ----------

  Future<void> importFromBackup(Map<String, dynamic> data, {bool replaceAll = true}) async {
    final notebooksJson = (data['notebooks'] as List).cast<Map<String, dynamic>>();
    final notesJson = (data['notes'] as List).cast<Map<String, dynamic>>();

    // Monta todos os objetos ANTES de apagar o que já está salvo. Se algum
    // item do backup estiver com o formato errado, o erro estoura aqui —
    // antes de mexer em qualquer dado existente — em vez de no meio da
    // importação, o que deixaria o app com parte dos dados antigos já
    // apagados e só um pedaço do backup restaurado.
    final notebooks = notebooksJson.map(Notebook.fromJson).toList();
    final notes = notesJson.map(Note.fromJson).toList();

    if (replaceAll) {
      _notebooksCache.clear();
      _notesCache.clear();
      await _notebooksBox!.clear();
      await _notesBox!.clear();
    }

    for (final notebook in notebooks) {
      _notebooksCache[notebook.id] = notebook;
      await _persistNotebook(notebook);
    }

    for (final note in notes) {
      _notesCache[note.id] = note;
      await _persistNote(note);
    }
  }
}
