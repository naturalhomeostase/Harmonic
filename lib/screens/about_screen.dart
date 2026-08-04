import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

// ---------------------------------------------------------------------
// PREENCHA AQUI quando tiver esses links prontos. Enquanto estiverem
// vazios (''), os botões correspondentes mostram um aviso em vez de abrir
// um link quebrado — então é seguro deixar em branco por enquanto e ir
// preenchendo aos poucos.
// ---------------------------------------------------------------------
const _kDeveloperName = 'Ren';
const _kPrivacyPolicyUrl = ''; // ex.: 'https://github.com/seu-usuario/nodus/blob/main/PRIVACY.md'
const _kSupportEmail = ''; // ex.: 'suporte@seuemail.com'
const _kGithubUrl = ''; // ex.: 'https://github.com/seu-usuario/nodus'
// Pacote do app definido no workflow de build (--org com.notasapp
// --project-name notas_app) — usado pra montar o link da Play Store.
const _kPlayStorePackage = 'com.notasapp.notas_app';

class AboutScreen extends StatefulWidget {
  const AboutScreen({super.key});

  @override
  State<AboutScreen> createState() => _AboutScreenState();
}

class _AboutScreenState extends State<AboutScreen> {
  PackageInfo? _packageInfo;

  @override
  void initState() {
    super.initState();
    PackageInfo.fromPlatform().then((info) {
      if (mounted) setState(() => _packageInfo = info);
    });
  }

  Future<void> _openUrl(String url, {required String missingMessage}) async {
    if (url.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(missingMessage)));
      return;
    }
    final uri = Uri.parse(url);
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Não consegui abrir esse link.')),
      );
    }
  }

  Future<void> _openSupportEmail() async {
    if (_kSupportEmail.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('E-mail de suporte ainda não configurado.')),
      );
      return;
    }
    final uri = Uri(
      scheme: 'mailto',
      path: _kSupportEmail,
      queryParameters: {'subject': 'Suporte — Nodus'},
    );
    final ok = await launchUrl(uri);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Não consegui abrir um app de e-mail.')),
      );
    }
  }

  Future<void> _shareApp() async {
    final storeUrl = 'https://play.google.com/store/apps/details?id=$_kPlayStorePackage';
    await SharePlus.instance.share(
      ShareParams(
        text: 'Nodus — notas criptografadas, offline, sem contas nem nuvem.\n$storeUrl',
        subject: 'Conheça o Nodus',
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final version = _packageInfo == null
        ? '…'
        : '${_packageInfo!.version} (build ${_packageInfo!.buildNumber})';

    return Scaffold(
      appBar: AppBar(title: const Text('Sobre')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Center(
            child: Column(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(20),
                  child: Image.asset(
                    'assets/icon/app_icon.png',
                    width: 84,
                    height: 84,
                    errorBuilder: (_, __, ___) => const Icon(Icons.lock_outline_rounded, size: 84),
                  ),
                ),
                const SizedBox(height: 12),
                Text('Nodus', style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 4),
                Text('Versão $version', style: Theme.of(context).textTheme.bodyMedium),
                const SizedBox(height: 2),
                Text(
                  'Desenvolvido por $_kDeveloperName',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          Text('Criptografia', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            margin: EdgeInsets.zero,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _AlgoRow(label: 'Derivação de chave', value: 'Argon2id (64 MB, 3 iterações, paralelismo 4)'),
                  SizedBox(height: 10),
                  _AlgoRow(label: 'Cifra de dados', value: 'AES-256-GCM (autenticada)'),
                  SizedBox(height: 10),
                  _AlgoRow(label: 'Tamanho da chave', value: '256 bits, derivada da sua senha + salt aleatório'),
                  SizedBox(height: 12),
                  Text(
                    'Sua senha nunca é salva em nenhum lugar — só o salt e um '
                    '"verificador" cifrado ficam no armazenamento seguro do '
                    'aparelho. Por isso não existe recuperação de senha: sem '
                    'ela, a chave não pode ser recalculada.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),
          Text('Links', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            margin: EdgeInsets.zero,
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.privacy_tip_outlined),
                  title: const Text('Política de Privacidade'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: () => _openUrl(
                    _kPrivacyPolicyUrl,
                    missingMessage: 'Política de privacidade ainda não publicada.',
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.support_agent_outlined),
                  title: const Text('Suporte'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: _openSupportEmail,
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.code_outlined),
                  title: const Text('Código no GitHub'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: () => _openUrl(
                    _kGithubUrl,
                    missingMessage: 'Repositório ainda não é público.',
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.star_outline),
                  title: const Text('Avaliar na Play Store'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: () => _openUrl(
                    'https://play.google.com/store/apps/details?id=$_kPlayStorePackage',
                    missingMessage: 'Ainda não publicado na Play Store.',
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.share_outlined),
                  title: const Text('Compartilhar aplicativo'),
                  onTap: _shareApp,
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: const Text('Licenças de bibliotecas de terceiros'),
                  onTap: () => showLicensePage(
                    context: context,
                    applicationName: 'Nodus',
                    applicationVersion: version,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AlgoRow extends StatelessWidget {
  const _AlgoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 130,
          child: Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600)),
        ),
        Expanded(child: Text(value, style: Theme.of(context).textTheme.bodySmall)),
      ],
    );
  }
}
