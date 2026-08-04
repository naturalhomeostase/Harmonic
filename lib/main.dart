import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_quill/flutter_quill.dart' show FlutterQuillLocalizations;
import 'package:provider/provider.dart';

import 'screens/lock_screen.dart';
import 'services/auth_service.dart';
import 'services/note_export_service.dart';
import 'services/settings_service.dart';
import 'services/storage_service.dart';
import 'theme/app_theme.dart';

final navigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await StorageService.init();
  await purgeStaleExports();

  final settings = SettingsService();
  await settings.load();

  // Tela cheia: o conteúdo do app desenha por baixo da barra de status e da
  // barra de navegação, que ficam transparentes por cima. A cor dos ícones
  // (clara/escura) é ajustada dinamicamente conforme o tema, no builder do
  // MaterialApp abaixo.
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

  runApp(
    ChangeNotifierProvider.value(
      value: settings,
      child: NotasApp(auth: AuthService(), storage: StorageService()),
    ),
  );
}

class NotasApp extends StatefulWidget {
  const NotasApp({super.key, required this.auth, required this.storage});

  final AuthService auth;
  final StorageService storage;

  @override
  State<NotasApp> createState() => _NotasAppState();
}

class _NotasAppState extends State<NotasApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Guarda o momento em que o app saiu de primeiro plano, pra comparar
  /// com a hora atual quando ele volta. Um `Timer` normal não serve pra
  /// isso: o sistema operacional pausa os timers do Dart quando o app tá
  /// totalmente em segundo plano, então ele nunca dispararia sozinho.
  /// Comparando os horários no momento em que o app volta, o timeout
  /// funciona mesmo depois de horas em segundo plano.
  DateTime? _pausedAt;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (widget.auth.sessionKey == null) return; // já trancado, nada a fazer
    if (widget.auth.suppressAutoLock) return; // saiu de primeiro plano por causa de um seletor nativo do próprio app

    if (state == AppLifecycleState.paused) {
      final settings = context.read<SettingsService>();
      if (settings.autoLockSeconds == 0) {
        // Sem tolerância configurada: tranca na hora, como sempre foi.
        _lockNow();
      } else {
        _pausedAt = DateTime.now();
      }
    } else if (state == AppLifecycleState.resumed && _pausedAt != null) {
      final settings = context.read<SettingsService>();
      final elapsed = DateTime.now().difference(_pausedAt!);
      _pausedAt = null;
      if (elapsed >= Duration(seconds: settings.autoLockSeconds)) {
        _lockNow();
      }
    }
  }

  Future<void> _lockNow() async {
    await widget.storage.closeAll();
    widget.auth.lock();
    navigatorKey.currentState?.pushAndRemoveUntil(
      MaterialPageRoute(
        builder: (_) => LockScreen(auth: widget.auth, storage: widget.storage),
      ),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final auth = widget.auth;
    final storage = widget.storage;

    return MaterialApp(
      navigatorKey: navigatorKey,
      title: 'Nodus',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(settings.seedColor, coloredBackground: settings.coloredBackground),
      darkTheme: AppTheme.dark(settings.seedColor, coloredBackground: settings.coloredBackground),
      themeMode: settings.themeMode,
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        FlutterQuillLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('pt', 'BR'),
        Locale('en'),
      ],
      builder: (context, child) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        // Construído por extenso (sem usar os atalhos .light/.dark prontos),
        // porque esses atalhos têm nomes que não batem exatamente com o que
        // configuram, e isso causou ícones invisíveis no tema claro.
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: SystemUiOverlayStyle(
            statusBarColor: Colors.transparent,
            statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
            statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
            systemNavigationBarColor: Colors.transparent,
            systemNavigationBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
            systemNavigationBarDividerColor: Colors.transparent,
          ),
          child: child!,
        );
      },
      home: LockScreen(auth: auth, storage: storage),
    );
  }
}
