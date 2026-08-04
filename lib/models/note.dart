import 'dart:convert';

/// Estilo de "papel" da página, imitando papel de verdade.
enum PageStyle { blank, lined, grid, dotted, cornell }

/// Origem do fundo da página.
enum PageBackgroundType { color, image, asset }

/// Metadados de um arquivo anexado a uma nota (PDF, documento, etc).
///
/// O conteúdo real do arquivo NÃO fica aqui — fica cifrado à parte, num
/// arquivo próprio gerenciado pelo `AttachmentService`. Aqui só ficam os
/// metadados (nome, tamanho, quando foi anexado), que são pequenos o
/// bastante pra viajar junto com o resto do JSON cifrado da nota.
class NoteAttachment {
  final String id;
  final String fileName;
  final int sizeBytes;
  final DateTime addedAt;

  NoteAttachment({
    required this.id,
    required this.fileName,
    required this.sizeBytes,
    DateTime? addedAt,
  }) : addedAt = addedAt ?? DateTime.now();

  Map<String, dynamic> toJson() => {
        'id': id,
        'fileName': fileName,
        'sizeBytes': sizeBytes,
        'addedAt': addedAt.toIso8601String(),
      };

  factory NoteAttachment.fromJson(Map<String, dynamic> json) => NoteAttachment(
        id: json['id'] as String,
        fileName: json['fileName'] as String,
        sizeBytes: json['sizeBytes'] as int,
        addedAt: DateTime.parse(json['addedAt'] as String),
      );
}

class Note {
  String id;
  String notebookId;
  String title;
  String content;

  /// Arquivos anexados (PDFs, documentos, etc — qualquer tipo). O
  /// conteúdo de cada um fica cifrado separadamente; aqui só os metadados.
  List<NoteAttachment> attachments;

  PageStyle pageStyle;
  PageBackgroundType backgroundType;

  /// Usado quando backgroundType == color. Valor ARGB.
  int backgroundColor;

  /// Usado quando backgroundType == image. Caminho absoluto no dispositivo
  /// (imagem escolhida pelo usuário na galeria, copiada para a pasta de
  /// dados do app).
  String? backgroundImagePath;

  /// Usado quando backgroundType == asset. Caminho dentro de assets/papers/
  /// (papéis de carta prontos, incluídos no próprio app).
  String? backgroundAssetPath;

  /// Cor das linhas/pauta desenhadas sobre o fundo.
  int lineColor;

  /// Carimbo de data (texto pronto, ex: "24/07/2026 20:13"), exibido fixo
  /// no canto da página — separado do conteúdo editável.
  String? dateStamp;

  /// Nota fixada no topo da lista dentro do caderno.
  bool pinned;

  /// Usado na ordenação manual (arrastar pra reordenar). Números menores
  /// aparecem primeiro.
  int sortOrder;

  /// Quando não-nulo, a nota está na lixeira desde essa data/hora.
  DateTime? deletedAt;

  /// Quando não-nulo, a nota está arquivada desde essa data/hora — fora
  /// de vista na lista normal do caderno, mas não excluída (diferente da
  /// lixeira: arquivar é reversível a qualquer momento e não expira).
  DateTime? archivedAt;

  DateTime createdAt;
  DateTime updatedAt;

  Note({
    required this.id,
    required this.notebookId,
    required this.title,
    required this.content,
    List<NoteAttachment>? attachments,
    this.pageStyle = PageStyle.blank,
    this.backgroundType = PageBackgroundType.color,
    this.backgroundColor = 0xFFFFFFFF, // branco (padrão)
    this.backgroundImagePath,
    this.backgroundAssetPath,
    this.lineColor = 0x33000000,
    this.dateStamp,
    this.pinned = false,
    int? sortOrder,
    this.deletedAt,
    this.archivedAt,
    DateTime? createdAt,
    DateTime? updatedAt,
  })  : createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now(),
        attachments = attachments ?? [],
        sortOrder = sortOrder ?? DateTime.now().microsecondsSinceEpoch;

  /// Extrai o texto puro do conteúdo (que é salvo como JSON do Quill Delta),
  /// ignorando formatação e imagens — usado pra busca e contagem.
  String get plainText {
    if (content.trim().isEmpty) return '';
    try {
      final ops = jsonDecode(content) as List;
      final buffer = StringBuffer();
      for (final op in ops) {
        if (op is Map && op['insert'] is String) {
          buffer.write(op['insert'] as String);
        }
      }
      return buffer.toString();
    } catch (_) {
      // Conteúdo antigo (texto puro, salvo antes da edição rica).
      return content;
    }
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'notebookId': notebookId,
        'title': title,
        'content': content,
        'attachments': attachments.map((a) => a.toJson()).toList(),
        'pageStyle': pageStyle.index,
        'backgroundType': backgroundType.index,
        'backgroundColor': backgroundColor,
        'backgroundImagePath': backgroundImagePath,
        'backgroundAssetPath': backgroundAssetPath,
        'lineColor': lineColor,
        'dateStamp': dateStamp,
        'pinned': pinned,
        'sortOrder': sortOrder,
        'deletedAt': deletedAt?.toIso8601String(),
        'archivedAt': archivedAt?.toIso8601String(),
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  factory Note.fromJson(Map<String, dynamic> json) => Note(
        id: json['id'] as String,
        notebookId: json['notebookId'] as String,
        title: json['title'] as String,
        content: json['content'] as String,
        // Campo novo — notas salvas antes dessa versão não têm essa chave
        // no JSON, então tratamos ausência como "nenhum anexo".
        attachments: (json['attachments'] as List?)
                ?.map((a) => NoteAttachment.fromJson(a as Map<String, dynamic>))
                .toList() ??
            [],
        pageStyle: PageStyle.values[json['pageStyle'] as int],
        backgroundType: PageBackgroundType.values[json['backgroundType'] as int],
        backgroundColor: json['backgroundColor'] as int,
        backgroundImagePath: json['backgroundImagePath'] as String?,
        backgroundAssetPath: json['backgroundAssetPath'] as String?,
        lineColor: json['lineColor'] as int,
        dateStamp: json['dateStamp'] as String?,
        pinned: (json['pinned'] as bool?) ?? false,
        sortOrder: json['sortOrder'] as int?,
        deletedAt:
            json['deletedAt'] != null ? DateTime.parse(json['deletedAt'] as String) : null,
        // Campo novo — notas salvas antes dessa versão simplesmente não têm
        // essa chave no JSON, então tratamos ausência como "não arquivada".
        archivedAt:
            json['archivedAt'] != null ? DateTime.parse(json['archivedAt'] as String) : null,
        createdAt: DateTime.parse(json['createdAt'] as String),
        updatedAt: DateTime.parse(json['updatedAt'] as String),
      );
}
