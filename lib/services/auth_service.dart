import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

import 'crypto_service.dart';
import 'storage_service.dart';

const _kVerifierText = 'notas_app_ok';

/// Quantas tentativas erradas seguidas até travar temporariamente.
const kMaxFailedAttempts = 3;

/// Por quanto tempo fica bloqueado depois de estourar [kMaxFailedAttempts].
const kLockoutDuration = Duration(minutes: 1);

class WrongPasswordException implements Exception {}

/// Lançada quando ainda está no período de espera do bloqueio temporário.
class LockedOutException implements Exception {
  LockedOutException(this.remaining);
  final Duration remaining;
}

class AuthService {
  AuthService({CryptoService? crypto, FlutterSecureStorage? secureStorage, LocalAuthentication? localAuth})
      : _crypto = crypto ?? CryptoService(),
        _secure = secureStorage ?? const FlutterSecureStorage(),
        _localAuth = localAuth ?? LocalAuthentication();

  /// true enquanto um seletor nativo (arquivos, "salvar como...") está
  /// aberto. Diferente do seletor de fotos do Android (que geralmente não
  /// tira o app de primeiro plano de verdade), o seletor de arquivos
  /// sempre tira — o que, com o auto-bloqueio, fazia o app voltar direto
  /// pra tela de senha e perder o arquivo escolhido. Ver [runWithoutAutoLock].
  bool suppressAutoLock = false;

  /// Roda [action] (algo que abre um seletor nativo de arquivos) sem
  /// deixar o auto-bloqueio trancar o app quando ele sai de primeiro
  /// plano pra mostrar esse seletor.
  Future<T> runWithoutAutoLock<T>(Future<T> Function() action) async {
    suppressAutoLock = true;
    try {
      return await action();
    } finally {
      suppressAutoLock = false;
    }
  }

  final CryptoService _crypto;
  final FlutterSecureStorage _secure;
  final LocalAuthentication _localAuth;

  static const _saltKey = 'master_salt';
  static const _verifierKey = 'master_verifier';
  static const _failedAttemptsKey = 'failed_attempts';
  static const _lockoutUntilKey = 'lockout_until';

  /// Chave de 32 bytes derivada da senha, mantida em memória apenas
  /// enquanto o app está desbloqueado (nunca é persistida).
  Uint8List? _sessionKey;
  Uint8List? get sessionKey => _sessionKey;

  Future<bool> hasPasswordConfigured() async {
    final salt = await _secure.read(key: _saltKey);
    return salt != null;
  }

  /// Se ainda houver um bloqueio temporário ativo (persiste entre
  /// reaberturas do app — fechar e abrir de novo não burla a espera),
  /// retorna quanto tempo falta. Senão, retorna null.
  Future<Duration?> lockoutRemaining() async {
    final untilStr = await _secure.read(key: _lockoutUntilKey);
    if (untilStr == null) return null;
    final until = DateTime.tryParse(untilStr);
    if (until == null) return null;
    final remaining = until.difference(DateTime.now());
    if (remaining.isNegative) {
      // Bloqueio já expirou — limpa e libera.
      await _secure.delete(key: _lockoutUntilKey);
      return null;
    }
    return remaining;
  }

  Future<int> _readFailedAttempts() async {
    final raw = await _secure.read(key: _failedAttemptsKey);
    return int.tryParse(raw ?? '') ?? 0;
  }

  Future<void> _registerWrongAttempt() async {
    final attempts = await _readFailedAttempts() + 1;
    if (attempts >= kMaxFailedAttempts) {
      final until = DateTime.now().add(kLockoutDuration);
      await _secure.write(key: _lockoutUntilKey, value: until.toIso8601String());
      await _secure.write(key: _failedAttemptsKey, value: '0');
    } else {
      await _secure.write(key: _failedAttemptsKey, value: attempts.toString());
    }
  }

  Future<void> _clearFailedAttempts() async {
    await _secure.delete(key: _failedAttemptsKey);
    await _secure.delete(key: _lockoutUntilKey);
  }

  /// Primeira configuração: cria salt, deriva a chave e grava o verificador.
  Future<void> setupPassword(String password) async {
    final salt = _crypto.generateSalt();
    final key = await _crypto.deriveKey(password, salt);
    final verifier = await _crypto.encryptString(key, _kVerifierText);

    await _secure.write(key: _saltKey, value: base64Encode(salt));
    await _secure.write(key: _verifierKey, value: base64Encode(verifier));
    await _clearFailedAttempts();

    _sessionKey = key;
  }

  /// Tenta destravar o app com a senha informada.
  /// Lança [WrongPasswordException] se a senha estiver incorreta, ou
  /// [LockedOutException] se ainda estiver no período de espera após
  /// muitas tentativas erradas.
  Future<void> unlock(String password) async {
    final remaining = await lockoutRemaining();
    if (remaining != null) throw LockedOutException(remaining);

    final saltB64 = await _secure.read(key: _saltKey);
    final verifierB64 = await _secure.read(key: _verifierKey);
    if (saltB64 == null || verifierB64 == null) {
      throw StateError('Nenhuma senha configurada ainda.');
    }

    final salt = base64Decode(saltB64);
    final verifierBytes = base64Decode(verifierB64);
    final key = await _crypto.deriveKey(password, salt);

    try {
      final decrypted = await _crypto.decryptString(key, verifierBytes);
      if (decrypted != _kVerifierText) throw WrongPasswordException();
    } catch (_) {
      await _registerWrongAttempt();
      throw WrongPasswordException();
    }

    await _clearFailedAttempts();
    _sessionKey = key;
  }

  /// Troca a senha mestre (requer a senha atual para reautenticar) e
  /// re-cifra todos os dados já salvos com a chave nova.
  ///
  /// Ordem importante por segurança: só gravamos o salt/verificador da
  /// senha NOVA depois que TODAS as notas já foram re-cifradas com
  /// sucesso. Se isso fosse feito na ordem contrária (ativar a senha nova
  /// primeiro, re-cifrar depois) e o app fosse interrompido no meio do
  /// caminho — queda de bateria, sistema encerrando o app à força — a
  /// senha nova ficaria "ativa" mas nem todas as notas teriam sido
  /// re-cifradas, tornando parte delas ilegível pra sempre. Com a senha
  /// atual permanecendo válida até o fim, uma interrupção no meio do
  /// caminho na pior das hipóteses deixa algumas notas já na chave nova
  /// (que dá pra tentar de novo), mas nunca destrói acesso aos dados.
  Future<void> changePassword(
    String currentPassword,
    String newPassword,
    StorageService storage,
  ) async {
    await unlock(currentPassword); // valida a atual, garante _sessionKey antigo

    final newSalt = _crypto.generateSalt();
    final newKey = await _crypto.deriveKey(newPassword, newSalt);
    final newVerifier = await _crypto.encryptString(newKey, _kVerifierText);

    // Re-cifra tudo primeiro. Se der erro aqui, a senha atual continua
    // sendo a válida — nada foi "trocado" oficialmente ainda.
    await storage.reencryptAll(newKey);

    // Só agora, com tudo já re-cifrado com sucesso, a senha nova passa a
    // valer de fato.
    await _secure.write(key: _saltKey, value: base64Encode(newSalt));
    await _secure.write(key: _verifierKey, value: base64Encode(newVerifier));
    await _clearFailedAttempts();

    _sessionKey = newKey;
  }

  void lock() {
    _sessionKey = null;
  }

  // --- Biometria (segundo fator) -------------------------------------
  //
  // A biometria NUNCA substitui a senha mestre — ela não participa da
  // derivação da chave de criptografia, só é um segundo fator de acesso
  // ao app. Isso é uma limitação consciente: tecnicamente seria possível
  // abrir o app sem digitar a senha se alguém conseguisse burlar só a
  // checagem de biometria, mas os dados em si continuam protegidos pela
  // criptografia derivada da senha, que a biometria não consegue destravar
  // sozinha.

  bool _biometricsAvailable = false;
  bool get biometricsAvailable => _biometricsAvailable;

  String? _lastBiometricError;

  /// Mensagem real do último erro de biometria (ex.: aparelho sem
  /// FragmentActivity configurada, permissão faltando, nada cadastrado
  /// etc.) — útil pra diagnosticar quando a biometria falha sem abrir o
  /// diálogo do sistema.
  String? get lastBiometricError => _lastBiometricError;

  /// Verifica se o aparelho tem biometria configurada. Deve ser chamado
  /// (e aguardado) antes de exibir a tela de login, pra [biometricsAvailable]
  /// já vir correto no primeiro build.
  Future<void> refreshBiometricsAvailability() async {
    try {
      final supported = await _localAuth.isDeviceSupported();
      final canCheck = await _localAuth.canCheckBiometrics;
      final enrolled = await _localAuth.getAvailableBiometrics();
      _biometricsAvailable = supported && canCheck && enrolled.isNotEmpty;
    } catch (_) {
      // Aparelho sem suporte, permissão negada, plugin indisponível etc.
      // Nesses casos simplesmente não exigimos biometria — não tem como
      // forçar um segundo fator que o aparelho não oferece.
      _biometricsAvailable = false;
    }
  }

  /// Pede a biometria do aparelho (impressão digital/rosto).
  Future<bool> authenticateBiometric() async {
    _lastBiometricError = null;
    try {
      final ok = await _localAuth.authenticate(
        localizedReason: 'Confirme sua identidade para abrir o Nodus',
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
        ),
      );
      if (!ok) _lastBiometricError = 'Biometria cancelada ou não confirmada.';
      return ok;
    } catch (e) {
      _lastBiometricError = e.toString();
      return false;
    }
  }
}
