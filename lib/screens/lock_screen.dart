import 'dart:async';

import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../services/storage_service.dart';
import 'notebook_list_screen.dart';

class LockScreen extends StatefulWidget {
  const LockScreen({super.key, required this.auth, required this.storage});

  final AuthService auth;
  final StorageService storage;

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> {
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _isFirstRun = false;
  bool _loading = true;
  bool _obscure = true;
  String? _error;
  Duration? _lockoutRemaining;
  Timer? _lockoutTimer;

  // Biometria agora acontece ANTES da senha (a pessoa pediu essa ordem).
  // Enquanto isso não for confirmado (ou pulado, veja abaixo), o campo de
  // senha nem aparece.
  bool _biometricChecking = false;
  bool _biometricPassed = false;
  int _biometricFailCount = 0;

  /// Saída de emergência: depois de algumas falhas seguidas de biometria
  /// (sensor sujo, com defeito, etc.), deixa seguir só com a senha em vez
  /// de travar a pessoa pra sempre fora do próprio app.
  bool _biometricSkipped = false;
  static const _kBiometricFailsBeforeSkip = 2;

  @override
  void initState() {
    super.initState();
    _check();
  }

  @override
  void dispose() {
    _lockoutTimer?.cancel();
    super.dispose();
  }

  Future<void> _check() async {
    final configured = await widget.auth.hasPasswordConfigured();
    final remaining = await widget.auth.lockoutRemaining();
    await widget.auth.refreshBiometricsAvailability();
    setState(() {
      _isFirstRun = !configured;
      _loading = false;
    });
    if (remaining != null) _startLockoutCountdown(remaining);
    // Primeira configuração não tem biometria pra pedir ainda (a senha
    // mestre está sendo criada agora); nas próximas aberturas, se o
    // aparelho tiver biometria, ela é a primeira coisa pedida.
    if (!_isFirstRun && widget.auth.biometricsAvailable) {
      _runBiometricGate();
    }
  }

  Future<void> _runBiometricGate() async {
    setState(() {
      _biometricChecking = true;
      _error = null;
    });
    final ok = await widget.auth.authenticateBiometric();
    if (!mounted) return;
    setState(() {
      _biometricChecking = false;
      _biometricPassed = ok;
      if (!ok) {
        _biometricFailCount++;
        _error = widget.auth.lastBiometricError ?? 'Não foi possível confirmar a biometria.';
      }
    });
  }

  void _startLockoutCountdown(Duration remaining) {
    _lockoutTimer?.cancel();
    setState(() => _lockoutRemaining = remaining);
    _lockoutTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final left = _lockoutRemaining! - const Duration(seconds: 1);
      if (left.inSeconds <= 0) {
        timer.cancel();
        setState(() {
          _lockoutRemaining = null;
          _error = null;
        });
      } else {
        setState(() => _lockoutRemaining = left);
      }
    });
  }

  Future<void> _submit() async {
    if (_lockoutRemaining != null) return;
    if (!_isFirstRun && widget.auth.biometricsAvailable && !_biometricPassed && !_biometricSkipped) return;

    setState(() => _error = null);
    final password = _passwordCtrl.text;

    if (password.isEmpty) {
      setState(() => _error = 'Digite uma senha.');
      return;
    }

    if (_isFirstRun) {
      if (password.length < 8) {
        setState(() => _error = 'Use pelo menos 8 caracteres.');
        return;
      }
      if (password != _confirmCtrl.text) {
        setState(() => _error = 'As senhas não coincidem.');
        return;
      }
      await widget.auth.setupPassword(password);
    } else {
      try {
        await widget.auth.unlock(password);
      } on LockedOutException catch (e) {
        setState(() => _error = 'Muitas tentativas erradas. Aguarde um pouco.');
        _startLockoutCountdown(e.remaining);
        return;
      } on WrongPasswordException {
        final remaining = await widget.auth.lockoutRemaining();
        if (remaining != null) {
          setState(() => _error = 'Muitas tentativas erradas. Aguarde um pouco.');
          _startLockoutCountdown(remaining);
        } else {
          setState(() => _error = 'Senha incorreta.');
        }
        return;
      }
    }

    await widget.storage.openWithKey(widget.auth.sessionKey!);

    if (!mounted) return;

    if (widget.storage.hasCorruptedRecords) {
      final count = widget.storage.corruptedRecordCount;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Atenção'),
          content: Text(
            count == 1
                ? 'Um item não pôde ser aberto (dado corrompido ou salvo com '
                    'outra senha) e foi ignorado. O restante dos seus '
                    'cadernos e notas abriu normalmente.'
                : '$count itens não puderam ser abertos (dados corrompidos '
                    'ou salvos com outra senha) e foram ignorados. O '
                    'restante dos seus cadernos e notas abriu normalmente.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Entendi')),
          ],
        ),
      );
      if (!mounted) return;
    }

    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) => NotebookListScreen(auth: widget.auth, storage: widget.storage),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final needsBiometricGate =
        !_isFirstRun && widget.auth.biometricsAvailable && !_biometricPassed && !_biometricSkipped;

    // A tela de senha é montada por baixo o tempo todo (só com os campos
    // desabilitados enquanto a biometria não é resolvida), e o pedido de
    // biometria aparece por cima como um cartão flutuante com um véu
    // semitransparente. Antes, enquanto a biometria rodava, a tela inteira
    // trocava pra uma outra só com um spinner solto no meio — dava a
    // impressão de algo "girando à toa" sem contexto. Agora dá pra ver que
    // esse carregamento é só uma etapa antes do campo de senha, que já está
    // logo ali atrás.
    return Scaffold(
      body: Stack(
        children: [
          _buildPasswordScreenBody(disabled: needsBiometricGate),
          if (needsBiometricGate) _buildBiometricOverlay(context),
        ],
      ),
    );
  }

  Widget _buildBiometricOverlay(BuildContext context) {
    return Positioned.fill(
      child: Container(
        color: Colors.black.withOpacity(0.55),
        child: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 380),
              child: Card(
                margin: const EdgeInsets.all(24),
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.fingerprint, size: 64),
                      const SizedBox(height: 16),
                      Text(
                        'Confirme sua biometria',
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Use a impressão digital ou o rosto pra continuar. '
                        'Depois disso, a senha ainda é pedida.',
                        style: Theme.of(context).textTheme.bodyMedium,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 24),
                      if (_biometricChecking) ...[
                        const CircularProgressIndicator(),
                        const SizedBox(height: 12),
                        Text(
                          'Aguardando a confirmação biométrica…',
                          style: Theme.of(context).textTheme.bodySmall,
                          textAlign: TextAlign.center,
                        ),
                      ],
                      if (!_biometricChecking) ...[
                        if (_error != null) ...[
                          Text(
                            _error!,
                            style: const TextStyle(color: Colors.red),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 16),
                        ],
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            onPressed: _runBiometricGate,
                            icon: const Icon(Icons.fingerprint),
                            label: const Text('Tentar novamente'),
                          ),
                        ),
                        if (_biometricFailCount >= _kBiometricFailsBeforeSkip) ...[
                          const SizedBox(height: 12),
                          TextButton(
                            onPressed: () => setState(() {
                              _biometricSkipped = true;
                              _error = null;
                            }),
                            child: const Text('Continuar só com a senha'),
                          ),
                        ],
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPasswordScreenBody({required bool disabled}) {
    return SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 380),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.lock_outline_rounded, size: 56),
                  const SizedBox(height: 16),
                  Text(
                    _isFirstRun ? 'Crie sua senha mestre' : 'Digite sua senha',
                    style: Theme.of(context).textTheme.headlineSmall,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  if (_isFirstRun)
                    Card(
                      color: Theme.of(context).colorScheme.errorContainer,
                      margin: const EdgeInsets.only(top: 4),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(Icons.warning_amber_rounded,
                                size: 20, color: Theme.of(context).colorScheme.onErrorContainer),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                'Essa senha criptografa tudo localmente, e não fica guardada '
                                'em nenhum servidor. Se você esquecê-la, NÃO tem como '
                                'recuperar suas notas — guarde-a em um lugar seguro.',
                                style: TextStyle(
                                  fontSize: 13,
                                  color: Theme.of(context).colorScheme.onErrorContainer,
                                ),
                                textAlign: TextAlign.left,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (_biometricSkipped)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        'Biometria pulada — entrando só com a senha.',
                        style: Theme.of(context).textTheme.bodySmall,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _passwordCtrl,
                    obscureText: _obscure,
                    autocorrect: false,
                    enableSuggestions: false,
                    enableIMEPersonalizedLearning: false,
                    enabled: _lockoutRemaining == null && !disabled,
                    decoration: InputDecoration(
                      labelText: 'Senha',
                      suffixIcon: IconButton(
                        icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                        onPressed: () => setState(() => _obscure = !_obscure),
                      ),
                    ),
                    onSubmitted: (_) => _submit(),
                  ),
                  if (_isFirstRun) ...[
                    const SizedBox(height: 12),
                    TextField(
                      controller: _confirmCtrl,
                      obscureText: _obscure,
                      autocorrect: false,
                      enableSuggestions: false,
                      enableIMEPersonalizedLearning: false,
                      enabled: !disabled,
                      decoration: const InputDecoration(labelText: 'Confirme a senha'),
                      onSubmitted: (_) => _submit(),
                    ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(_error!, style: const TextStyle(color: Colors.red), textAlign: TextAlign.center),
                  ],
                  if (_lockoutRemaining != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      'Tente novamente em ${_formatRemaining(_lockoutRemaining!)}',
                      style: Theme.of(context).textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                  ],
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: (_lockoutRemaining == null && !disabled) ? _submit : null,
                      child: Text(_isFirstRun ? 'Criar e entrar' : 'Entrar'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
  }

  String _formatRemaining(Duration d) {
    final total = d.inSeconds.clamp(0, 999);
    final m = total ~/ 60;
    final s = total % 60;
    if (m > 0) return '${m}min ${s.toString().padLeft(2, '0')}s';
    return '${s}s';
  }
}
