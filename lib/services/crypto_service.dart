import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

/// Centraliza toda a criptografia do app.
///
/// Estratégia:
/// 1. O usuário digita uma senha. Nunca a guardamos.
/// 2. Derivamos uma chave de 32 bytes com Argon2id (senha + salt aleatório).
/// 3. Essa chave de 32 bytes é usada:
///    - como chave AES do Hive (HiveAesCipher), para criptografar o banco local inteiro.
///    - para criptografar o arquivo de backup antes de subir pro Drive.
/// 4. Guardamos apenas o salt e um "verificador" (um texto fixo criptografado)
///    em flutter_secure_storage — nunca a senha nem a chave.
class CryptoService {
  static const _argon2Memory = 65536; // 64 MB
  static const _argon2Iterations = 3;
  static const _argon2Parallelism = 4;
  static const _keyLength = 32; // AES-256

  final _argon2 = Argon2id(
    memory: _argon2Memory,
    iterations: _argon2Iterations,
    parallelism: _argon2Parallelism,
    hashLength: _keyLength,
  );

  final _aesGcm = AesGcm.with256bits();

  /// Gera um salt aleatório de 16 bytes (usar um por instalação/senha).
  Uint8List generateSalt() {
    final rnd = Random.secure();
    return Uint8List.fromList(List<int>.generate(16, (_) => rnd.nextInt(256)));
  }

  /// Deriva a chave de 32 bytes a partir da senha + salt.
  Future<Uint8List> deriveKey(String password, Uint8List salt) async {
    final secretKey = await _argon2.deriveKeyFromPassword(
      password: password,
      nonce: salt,
    );
    final bytes = await secretKey.extractBytes();
    return Uint8List.fromList(bytes);
  }

  /// Criptografa bytes arbitrários com AES-GCM. Retorna nonce + ciphertext + mac
  /// concatenados, prontos para salvar/transmitir.
  Future<Uint8List> encryptBytes(Uint8List key, Uint8List plaintext) async {
    final secretKey = SecretKey(key);
    final nonce = _aesGcm.newNonce();
    final box = await _aesGcm.encrypt(
      plaintext,
      secretKey: secretKey,
      nonce: nonce,
    );
    return Uint8List.fromList(box.nonce + box.cipherText + box.mac.bytes);
  }

  /// Decifra o que foi gerado por [encryptBytes]. Lança exceção se a
  /// senha/chave estiver errada ou os dados estiverem corrompidos.
  Future<Uint8List> decryptBytes(Uint8List key, Uint8List data) async {
    const nonceLength = 12; // padrão AES-GCM
    const macLength = 16;
    final nonce = data.sublist(0, nonceLength);
    final mac = data.sublist(data.length - macLength);
    final cipherText = data.sublist(nonceLength, data.length - macLength);

    final secretKey = SecretKey(key);
    final box = SecretBox(cipherText, nonce: nonce, mac: Mac(mac));
    final clear = await _aesGcm.decrypt(box, secretKey: secretKey);
    return Uint8List.fromList(clear);
  }

  /// Atalhos para strings (usados no verificador de senha e em pequenos JSONs).
  Future<Uint8List> encryptString(Uint8List key, String text) =>
      encryptBytes(key, Uint8List.fromList(utf8.encode(text)));

  Future<String> decryptString(Uint8List key, Uint8List data) async {
    final bytes = await decryptBytes(key, data);
    return utf8.decode(bytes);
  }
}
