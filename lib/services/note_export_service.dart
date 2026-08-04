import 'dart:async';
import 'dart:io';

import 'package:cross_file/cross_file.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:share_plus/share_plus.dart';

import '../models/note.dart';

/// Copia o título + conteúdo (texto puro) da nota pra área de
/// transferência, e agenda a limpeza automática dela um tempo depois —
/// texto de uma nota criptografada pode ser algo sensível, e diferente de
/// uma senha (que passa por um app de teclado seguro), o texto colado
/// fica exposto em texto puro em qualquer lugar em que for colado. Só
/// limpamos se ninguém copiou outra coisa por cima nesse meio-tempo (a
/// gente confere o conteúdo atual antes de apagar, pra não atrapalhar
/// quem já colou e seguiu usando a área de transferência pra outra coisa).
Future<void> copyNoteToClipboard(Note note, {Duration autoClearAfter = const Duration(seconds: 45)}) async {
  final title = note.title.trim();
  final body = note.plainText.trim();
  final text = title.isEmpty ? body : '$title\n\n$body';
  await Clipboard.setData(ClipboardData(text: text));

  unawaited(Future.delayed(autoClearAfter, () async {
    final current = await Clipboard.getData(Clipboard.kTextPlain);
    if (current?.text == text) {
      await Clipboard.setData(const ClipboardData(text: ''));
    }
  }));
}

/// Compartilha o título + conteúdo (texto puro) da nota através do menu de
/// compartilhamento do sistema.
Future<void> shareNoteAsText(Note note) async {
  final title = note.title.trim();
  final body = note.plainText.trim();
  final text = title.isEmpty ? body : '$title\n\n$body';
  await SharePlus.instance.share(ShareParams(text: text, subject: title.isEmpty ? null : title));
}

/// Gera um PDF simples (título + texto puro) e compartilha o arquivo
/// através do menu de compartilhamento do sistema — de lá, o usuário pode
/// escolher "Salvar no dispositivo"/Drive/Arquivos ou mandar direto pra
/// outro app.
///
/// O PDF é texto puro (sem criptografia) por natureza — é assim que a
/// pessoa vai poder abri-lo em outro app. Por isso ele fica só numa pasta
/// temporária dedicada e é apagado logo depois que o compartilhamento
/// termina (e também limpamos qualquer sobra de uma exportação anterior
/// que não tenha sido apagada, por exemplo se o app foi fechado no meio
/// do compartilhamento).
Future<void> shareNoteAsPdf(Note note) async {
  final title = note.title.trim();
  final body = note.plainText.trim();

  final doc = pw.Document();
  doc.addPage(
    pw.MultiPage(
      pageFormat: PdfPageFormat.a4,
      build: (context) => [
        if (title.isNotEmpty)
          pw.Padding(
            padding: const pw.EdgeInsets.only(bottom: 16),
            child: pw.Text(title, style: pw.TextStyle(fontSize: 22, fontWeight: pw.FontWeight.bold)),
          ),
        pw.Text(body, style: const pw.TextStyle(fontSize: 12)),
      ],
    ),
  );

  final bytes = await doc.save();
  final tempDir = await getTemporaryDirectory();
  final exportDir = Directory('${tempDir.path}/note_exports');
  await exportDir.create(recursive: true);
  await _clearExportDir(exportDir);

  final fileName = '${title.isEmpty ? 'nota' : _sanitizeFileName(title)}.pdf';
  final file = File('${exportDir.path}/$fileName');
  await file.writeAsBytes(bytes);

  await SharePlus.instance.share(ShareParams(files: [XFile(file.path)], subject: title));

  // share() no Android/macOS já retorna assim que a pessoa escolhe um app
  // de destino, não necessariamente depois dele terminar de ler o
  // arquivo — apagar na hora arrisca corromper o compartilhamento. Damos
  // uma folga antes de apagar o PDF em texto puro do cache. Se o app for
  // fechado antes disso, [purgeStaleExports] limpa na próxima abertura.
  unawaited(Future.delayed(const Duration(seconds: 45), () async {
    if (await file.exists()) await file.delete();
  }));
}

/// Apaga qualquer PDF de exportação esquecido de uma sessão anterior (por
/// exemplo, se o app foi fechado antes da limpeza com atraso rodar).
/// Chamado uma vez na inicialização do app.
Future<void> purgeStaleExports() async {
  try {
    final tempDir = await getTemporaryDirectory();
    final exportDir = Directory('${tempDir.path}/note_exports');
    if (await exportDir.exists()) await _clearExportDir(exportDir);
  } catch (_) {
    // Best-effort.
  }
}

Future<void> _clearExportDir(Directory dir) async {
  try {
    final entries = await dir.list().toList();
    for (final entry in entries) {
      if (entry is File) await entry.delete();
    }
  } catch (_) {
    // Best-effort — se não der pra limpar agora, não impede a exportação atual.
  }
}

String _sanitizeFileName(String input) {
  return input.replaceAll(RegExp(r'[^\w\s-]'), '').trim().replaceAll(RegExp(r'\s+'), '_');
}
