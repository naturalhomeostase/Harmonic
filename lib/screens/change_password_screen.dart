import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../services/storage_service.dart';

/// Troca a senha mestre do app.
///
/// Por baixo dos panos isso não é só "trocar um texto": a senha nunca fica
/// guardada em lugar nenhum, ela só existe pra derivar a chave de
/// criptografia (ver [AuthService.changePassword]). Então trocar a senha
/// significa: confirmar a senha atual, derivar uma chave nova a partir da
/// senha nova, e re-cifrar TODOS os cadernos e notas já salvos com essa
/// chave nova — [AuthService.changePassword] já faz isso de ponta a ponta,
/// essa tela só cuida da parte de UI/validação.
class ChangePasswordScreen extends StatefulWidget {
  const ChangePasswordScreen({super.key, required this.auth, required this.storage});

  final AuthService auth;
  final StorageService storage;

  @override
  State<ChangePasswordScreen> createState() => _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends State<ChangePasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _currentCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();

  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _working = false;
  String? _error;

  @override
  void dispose() {
    // Limpa os controllers explicitamente (em vez de só confiar no
    // garbage collector) pra não deixar a senha digitada em memória mais
    // tempo do que o necessário.
    _currentCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _working = true;
      _error = null;
    });

    try {
      await widget.auth.changePassword(_currentCtrl.text, _newCtrl.text, widget.storage);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Senha alterada. Suas notas foram re-cifradas com a nova senha.')),
      );
      Navigator.of(context).pop(true);
    } on WrongPasswordException {
      setState(() {
        _error = 'Senha atual incorreta.';
        _working = false;
      });
    } on LockedOutException catch (e) {
      final minutes = (e.remaining.inSeconds / 60).ceil();
      setState(() {
        _error = 'Muitas tentativas erradas. Tente de novo em cerca de $minutes min.';
        _working = false;
      });
    } catch (e) {
      // Qualquer outro erro (ex.: falha ao gravar no armazenamento seguro
      // no meio da re-cifragem) — melhor mostrar e deixar tentar de novo
      // do que fingir que deu certo.
      setState(() {
        _error = 'Não foi possível trocar a senha: $e';
        _working = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Trocar senha')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Card(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                margin: EdgeInsets.zero,
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.info_outline, size: 20),
                      SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Depois de trocar, todas as suas notas e cadernos '
                          'são re-cifrados com a senha nova automaticamente. '
                          'Isso pode levar alguns segundos se você tiver '
                          'muitas notas — não feche o app enquanto estiver '
                          'rodando.',
                          style: TextStyle(fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                margin: EdgeInsets.zero,
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
                          'A senha nova também não fica guardada em nenhum lugar — '
                          'ela só serve para gerar a chave de criptografia. Se você '
                          'esquecê-la depois de trocar, NÃO tem como recuperar suas '
                          'notas, nem com a senha antiga.',
                          style: TextStyle(fontSize: 13, color: Theme.of(context).colorScheme.onErrorContainer),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _currentCtrl,
                obscureText: _obscureCurrent,
                autocorrect: false,
                enableSuggestions: false,
                enableIMEPersonalizedLearning: false,
                decoration: InputDecoration(
                  labelText: 'Senha atual',
                  suffixIcon: IconButton(
                    icon: Icon(_obscureCurrent ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    onPressed: () => setState(() => _obscureCurrent = !_obscureCurrent),
                  ),
                ),
                validator: (v) => (v == null || v.isEmpty) ? 'Digite sua senha atual' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _newCtrl,
                obscureText: _obscureNew,
                autocorrect: false,
                enableSuggestions: false,
                enableIMEPersonalizedLearning: false,
                decoration: InputDecoration(
                  labelText: 'Nova senha',
                  suffixIcon: IconButton(
                    icon: Icon(_obscureNew ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    onPressed: () => setState(() => _obscureNew = !_obscureNew),
                  ),
                ),
                validator: (v) {
                  if (v == null || v.length < 8) return 'Pelo menos 8 caracteres';
                  if (v == _currentCtrl.text) return 'Escolha uma senha diferente da atual';
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _confirmCtrl,
                obscureText: _obscureNew,
                autocorrect: false,
                enableSuggestions: false,
                enableIMEPersonalizedLearning: false,
                decoration: const InputDecoration(labelText: 'Confirme a nova senha'),
                validator: (v) => v != _newCtrl.text ? 'As senhas não coincidem' : null,
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!, style: const TextStyle(color: Colors.red)),
              ],
              const SizedBox(height: 24),
              FilledButton(
                onPressed: _working ? null : _submit,
                child: _working
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Trocar senha'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
