/// Tipos de capa que um caderno pode ter.
enum CoverType { color, gradient, assetImage, deviceImage }

/// Fontes disponíveis para o título na capa do caderno.
/// O nome aqui deve bater com o nome usado no GoogleFonts (ver widgets/cover_widget.dart).
enum CoverFont {
  playfairDisplay, // serifada elegante
  montserrat, // moderna/sans
  poppins, // moderna/arredondada
  dancingScript, // cursiva
  greatVibes, // cursiva mais decorativa
}

class Notebook {
  String id;
  String title;
  CoverType coverType;

  /// Usado quando coverType == color. Valor ARGB (Color.value).
  int? coverColor;

  /// Usado quando coverType == gradient. Lista de valores ARGB (2 ou mais cores).
  List<int>? gradientColors;

  /// Usado quando coverType == assetImage. Caminho dentro de assets/covers/.
  String? assetImagePath;

  /// Usado quando coverType == deviceImage. Caminho absoluto no dispositivo
  /// (a imagem é copiada para a pasta de dados do app na hora da escolha).
  String? deviceImagePath;

  CoverFont font;

  /// Cor do texto do título sobre a capa (ARGB).
  int titleColor;

  DateTime createdAt;
  DateTime updatedAt;

  /// Caderno fixado no topo da lista.
  bool pinned;

  /// Usado na ordenação manual (arrastar pra reordenar). Números menores
  /// aparecem primeiro.
  int sortOrder;

  /// Quando não-nulo, o caderno está na lixeira desde essa data/hora.
  DateTime? deletedAt;

  /// Cor das abas de notas desse caderno, escolhida manualmente pelo
  /// usuário (ARGB). Quando nula, a cor é derivada automaticamente da capa
  /// (ver notebookAccentColor).
  int? tabColor;

  /// IDs das notas que pertencem a este caderno (a ordem é a ordem de exibição).
  List<String> noteIds;

  Notebook({
    required this.id,
    required this.title,
    required this.coverType,
    this.coverColor,
    this.gradientColors,
    this.assetImagePath,
    this.deviceImagePath,
    this.font = CoverFont.montserrat,
    this.titleColor = 0xFFFFFFFF,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.pinned = false,
    int? sortOrder,
    this.deletedAt,
    this.tabColor,
    List<String>? noteIds,
  })  : createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now(),
        sortOrder = sortOrder ?? DateTime.now().microsecondsSinceEpoch,
        noteIds = noteIds ?? [];

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'coverType': coverType.index,
        'coverColor': coverColor,
        'gradientColors': gradientColors,
        'assetImagePath': assetImagePath,
        'deviceImagePath': deviceImagePath,
        'font': font.index,
        'titleColor': titleColor,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
        'pinned': pinned,
        'sortOrder': sortOrder,
        'deletedAt': deletedAt?.toIso8601String(),
        'tabColor': tabColor,
        'noteIds': noteIds,
      };

  factory Notebook.fromJson(Map<String, dynamic> json) => Notebook(
        id: json['id'] as String,
        title: json['title'] as String,
        coverType: CoverType.values[json['coverType'] as int],
        coverColor: json['coverColor'] as int?,
        gradientColors: (json['gradientColors'] as List?)?.cast<int>(),
        assetImagePath: json['assetImagePath'] as String?,
        deviceImagePath: json['deviceImagePath'] as String?,
        font: CoverFont.values[json['font'] as int],
        titleColor: json['titleColor'] as int,
        createdAt: DateTime.parse(json['createdAt'] as String),
        updatedAt: DateTime.parse(json['updatedAt'] as String),
        pinned: (json['pinned'] as bool?) ?? false,
        sortOrder: json['sortOrder'] as int?,
        deletedAt:
            json['deletedAt'] != null ? DateTime.parse(json['deletedAt'] as String) : null,
        tabColor: json['tabColor'] as int?,
        noteIds: (json['noteIds'] as List?)?.cast<String>() ?? [],
      );
}
