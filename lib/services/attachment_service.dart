import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import '../models/note.dart';
import 'crypto_service.dart';

/// Guarda e recupera os arquivos anexados às notas.
///
/// Cada anexo é cifrado com a mesma chave de sessão usada pro resto do
/// app (a mesma derivada da senha mestre) e salvo como um arquivo `.enc`
/// próprio, separado do JSON da nota — diferente de título/conteúdo (que
/// cabem tranquilamente dentro de um blob JSON), um PDF pode ter vários
/// MB, então não faz sentido inflar o blob da nota com isso.
class AttachmentService {
  AttachmentService({CryptoService? crypto}) : _crypto = crypto ?? CryptoService();

  final CryptoService _crypto;
  static const _uuid = Uuid();

  Future<Directory> _dirFor(String noteId) async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/attachments/$noteId');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  /// Cifra [bytes] e salva como um novo anexo da nota [noteId]. Retorna
  /// os metadados pra guardar na lista `note.attachments`.
  Future<NoteAttachment> addAttachment({
    required String noteId,
    required Uint8List key,
    required String fileName,
    required Uint8List bytes,
  }) async {
    final id = _uuid.v4();
    final encrypted = await _crypto.encryptBytes(key, bytes);
    final dir = await _dirFor(noteId);
    await File('${dir.path}/$id.enc').writeAsBytes(encrypted, flush: true);
    return NoteAttachment(id: id, fileName: fileName, sizeBytes: bytes.length);
  }

  /// Decifra o anexo pra um arquivo temporário (pra abrir com um app
  /// externo) e retorna o caminho. Quem chama é responsável por apagar
  /// esse arquivo temporário depois de um tempo — ver [scheduleTempCleanup].
  Future<File> decryptToTemp({
    required String noteId,
    required Uint8List key,
    required NoteAttachment attachment,
  }) async {
    final dir = await _dirFor(noteId);
    final encryptedFile = File('${dir.path}/${attachment.id}.enc');
    final encryptedBytes = await encryptedFile.readAsBytes();
    final decrypted = await _crypto.decryptBytes(key, encryptedBytes);

    final tempDir = await getTemporaryDirectory();
    final safeName = attachment.fileName.replaceAll(RegExp(r'[\\/]'), '_');
    final tempFile = File('${tempDir.path}/nodus_${attachment.id}_$safeName');
    await tempFile.writeAsBytes(decrypted, flush: true);
    return tempFile;
  }

  /// Apaga o arquivo decifrado temporário depois de um tempo — ele existe
  /// só pra dar tempo do app de visualização externo abrir e ler, não
  /// deveria ficar em texto puro no aparelho por mais tempo que isso.
  void scheduleTempCleanup(File file, {Duration after = const Duration(minutes: 2)}) {
    Future.delayed(after, () async {
      if (await file.exists()) await file.delete();
    });
  }

  Future<void> deleteAttachment({required String noteId, required NoteAttachment attachment}) async {
    final dir = await _dirFor(noteId);
    final file = File('${dir.path}/${attachment.id}.enc');
    if (await file.exists()) await file.delete();
  }

  /// Apaga todos os anexos cifrados de uma nota — usar ao excluir a nota
  /// em definitivo (esvaziar a lixeira), pra não deixar arquivo órfão.
  Future<void> deleteAllForNote(String noteId) async {
    final dir = await _dirFor(noteId);
    if (await dir.exists()) await dir.delete(recursive: true);
  }
}
