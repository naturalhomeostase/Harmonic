import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:file_picker/file_picker.dart';

import 'crypto_service.dart';
import 'storage_service.dart';

const _backupFileName = 'nodus_backup.enc';

/// Versão mais nova de formato de backup que ESTE app sabe ler. Se um
/// backup vier com "version" maior que isso, foi feito por uma versão do
/// Nodus mais nova que essa instalação — melhor recusar de forma clara do
/// que tentar interpretar um formato que ainda não existia quando esse
/// código foi escrito.
const kSupportedBackupVersion = 1;

/// Senha/chave errada, ou arquivo corrompido/adulterado — o AES-GCM já
/// detecta os dois casos da mesma forma (falha de autenticação), então
/// não dá pra distinguir qual dos dois foi com certeza.
class BackupWrongKeyOrCorruptedException implements Exception {
  const BackupWrongKeyOrCorruptedException();
}

/// Decifrou certinho, mas o conteúdo não é um backup do Nodus (JSON
/// inválido ou faltando os campos esperados) — ex.: escolheu o arquivo
/// errado no seletor.
class BackupInvalidFormatException implements Exception {
  const BackupInvalidFormatException();
}

/// Backup válido, mas de uma versão mais nova do app do que essa
/// instalação sabe ler.
class BackupUnsupportedVersionException implements Exception {
  const BackupUnsupportedVersionException(this.version);
  final int version;
}

/// Faz backup/restauração escolhendo o arquivo na hora, pelo seletor
/// nativo do Android (o mesmo "Salvar como..."/"Abrir de..." usado por
/// qualquer app) — sem precisar logar com conta do Google dentro do app.
///
/// A pessoa escolhe onde salvar (inclusive dentro do Google Drive, se o
/// app do Drive estiver instalado — ele aparece como um dos locais no
/// seletor, do mesmo jeito que aparece ao salvar uma foto ou um PDF).
///
/// A segurança não depende de onde o arquivo é salvo: o conteúdo já sai
/// do aparelho criptografado (AES-GCM com chave derivada da senha
/// mestre). Ninguém com acesso só ao arquivo — nem o próprio Google
/// Drive — consegue ler as notas sem a senha.
class BackupFileService {
  BackupFileService({CryptoService? crypto}) : _crypto = crypto ?? CryptoService();

  final CryptoService _crypto;

  /// Serializa cadernos + notas em JSON, criptografa com a chave de sessão
  /// e abre o seletor "Salvar como..." pra pessoa escolher onde guardar.
  ///
  /// Retorna `true` se um arquivo foi salvo, `false` se a pessoa cancelou
  /// o seletor.
  Future<bool> backupNow({
    required StorageService storage,
    required Uint8List sessionKey,
  }) async {
    final payload = {
      'version': 1,
      'exportedAt': DateTime.now().toIso8601String(),
      'notebooks': storage.allNotebooksRaw.map((n) => n.toJson()).toList(),
      'notes': storage.allNotesRaw.map((n) => n.toJson()).toList(),
    };

    final plainBytes = Uint8List.fromList(utf8.encode(jsonEncode(payload)));
    final encrypted = await _crypto.encryptBytes(sessionKey, plainBytes);

    final savedPath = await FilePicker.saveFile(
      dialogTitle: 'Salvar backup do Nodus',
      fileName: _backupFileName,
      bytes: encrypted,
      type: FileType.any,
    );

    return savedPath != null;
  }

  /// Abre o seletor "Abrir de..." pra pessoa escolher o arquivo de backup,
  /// decifra com a chave de sessão e retorna o JSON decodificado.
  ///
  /// Retorna `null` se a pessoa cancelou o seletor. Quem chama é
  /// responsável por importar os dados no StorageService.
  Future<Map<String, dynamic>?> restoreLatest({
    required Uint8List sessionKey,
  }) async {
    final result = await FilePicker.pickFiles(
      dialogTitle: 'Escolher backup do Nodus',
      type: FileType.any,
      withData: true,
    );
    if (result == null || result.files.isEmpty) return null;

    final bytes = result.files.first.bytes;
    if (bytes == null) {
      throw StateError('Não foi possível ler o arquivo escolhido.');
    }

    final decrypted = await _decrypt(sessionKey, bytes);

    late final Map<String, dynamic> data;
    try {
      final decoded = jsonDecode(utf8.decode(decrypted));
      if (decoded is! Map<String, dynamic> || decoded['notebooks'] is! List || decoded['notes'] is! List) {
        throw const BackupInvalidFormatException();
      }
      data = decoded;
    } on BackupInvalidFormatException {
      rethrow;
    } catch (_) {
      throw const BackupInvalidFormatException();
    }

    final version = data['version'];
    if (version is! int || version > kSupportedBackupVersion) {
      throw BackupUnsupportedVersionException(version is int ? version : -1);
    }

    return data;
  }

  Future<Uint8List> _decrypt(Uint8List sessionKey, Uint8List bytes) async {
    try {
      return await _crypto.decryptBytes(sessionKey, bytes);
    } on SecretBoxAuthenticationError {
      throw const BackupWrongKeyOrCorruptedException();
    } catch (_) {
      // Arquivo curto/truncado demais nem pra tentar decifrar (RangeError
      // ao cortar nonce/mac) cai aqui — na prática, pra quem está usando o
      // app, é o mesmo problema: esse arquivo não é um backup válido.
      throw const BackupWrongKeyOrCorruptedException();
    }
  }
}
