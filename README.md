# Nodus

App de notas offline, criptografado, com cadernos personalizáveis.

## O que já está implementado

- **Senha mestre** com derivação de chave via Argon2id (`cryptography` package) — a senha nunca é armazenada, só um salt e um verificador criptografado.
- **Banco local 100% criptografado** com Hive + `HiveAesCipher`, usando a chave derivada da senha.
- **Backup opcional no Google Drive**: os dados são serializados, criptografados localmente com AES-GCM e só então enviados — o Drive nunca vê texto legível. Usa o escopo `drive.file` (só acessa arquivos criados pelo próprio app).
- **Cadernos** com capa em cor sólida, gradiente, imagem pronta (asset) ou imagem escolhida da galeria do celular, com título em 5 opções de fonte (2 cursivas: Dancing Script e Great Vibes).
- **Notas** com fundo colorido ou com imagem, e 5 estilos de pauta desenhados via `CustomPainter`: liso, pautado, quadriculado, pontilhado e Cornell.
- **Botão de inserir data automaticamente** no editor de nota.

## Setup

```bash
flutter pub get
```

Não é necessário rodar `build_runner` — os adapters do Hive (`NotebookAdapter`, `NoteAdapter`) foram escritos manualmente em `lib/models/`.

### Google Drive (opcional, só necessário se for usar o backup)

1. Criar um projeto no [Google Cloud Console](https://console.cloud.google.com/).
2. Ativar a **Google Drive API**.
3. Configurar a tela de consentimento OAuth e criar credenciais OAuth (Android/iOS conforme a plataforma).
4. Seguir a documentação do pacote [`google_sign_in`](https://pub.dev/packages/google_sign_in) pra colocar o `client_id`/SHA-1 (Android) e o `GoogleService-Info.plist`/URL scheme (iOS).

Sem essa configuração, o app funciona normalmente offline — só o botão de backup não vai funcionar.

## Onde entram as imagens que você vai enviar

- **Capas prontas do app**: coloque os arquivos em `assets/covers/` com os nomes referenciados em `lib/screens/notebook_editor_screen.dart` (`kBuiltInCovers`) — ou me envie as imagens depois que eu ajusto a lista e os nomes.
- **Imagens de fundo de página**: não precisam ir em `assets/`, pois já são escolhidas pelo usuário via galeria (`image_picker`) e copiadas para a pasta de dados do app.

## Próximos passos sugeridos

1. `flutter create .` na raiz do projeto pra gerar as pastas `android/`, `ios/`, etc. (esse repositório só tem o código Dart/Flutter em si).
2. Testar o fluxo completo num emulador: criar senha → criar caderno → criar nota → trocar pauta/cor → inserir data → backup no Drive.
3. Adicionar biometria (`local_auth`) como atalho de desbloqueio (a senha continua sendo a fonte da chave de criptografia).
4. Adicionar autenticação de reautorização de backup automático (ex: a cada X dias, perguntar se quer fazer backup).
5. Enviar as imagens de capa e me pedir pra integrar.

## Estrutura

```
lib/
  models/       Notebook, Note (+ adapters manuais do Hive)
  services/     CryptoService, AuthService, StorageService, DriveBackupService
  screens/      LockScreen, NotebookListScreen, NotebookEditorScreen,
                NotebookNotesScreen, NoteEditorScreen
  widgets/      CoverWidget (capas), PaperPainter/PaperBackground (pautas)
  theme/        AppTheme
```
