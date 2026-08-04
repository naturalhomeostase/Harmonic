import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart' as quill;

/// Barra de ferramentas do editor: uma fileira fixa de ferramentas de
/// inserir (cor da página, pauta, linha, imagem, data, anexo) sempre à
/// vista durante a edição, e logo abaixo a barra de formatação de texto
/// do flutter_quill — essa sim escondida por padrão, mostrando só uma
/// alcinha. Toque ou arraste a alcinha pra cima pra revelar.
class NoteToolbar extends StatefulWidget {
  const NoteToolbar({
    super.key,
    required this.controller,
    required this.isDark,
    required this.pageColor,
    required this.onPickBackgroundColor,
    required this.onPickPageStyle,
    required this.onInsertDivider,
    required this.onInsertImage,
    required this.onInsertDate,
    required this.onAttachFile,
  });

  final quill.QuillController controller;

  /// Se a página atual é escura — define a cor da barra e dos ícones,
  /// independente do tema do app (pra sempre ter contraste garantido).
  final bool isDark;

  /// A cor de fundo escolhida para ESSA nota (azul, verde, etc.) — a barra
  /// usa essa cor em vez da cor de tema geral do app, pra combinar com a
  /// nota que está aberta.
  final Color pageColor;

  final VoidCallback onPickBackgroundColor;
  final VoidCallback onPickPageStyle;
  final VoidCallback onInsertDivider;
  final VoidCallback onInsertImage;
  final VoidCallback onInsertDate;
  final VoidCallback onAttachFile;

  @override
  State<NoteToolbar> createState() => _NoteToolbarState();
}

class _NoteToolbarState extends State<NoteToolbar> {
  bool _expanded = false;

  void _toggle() => setState(() => _expanded = !_expanded);

  void _handleVerticalDrag(DragEndDetails details) {
    final velocity = details.primaryVelocity ?? 0;
    if (velocity < -150 && !_expanded) {
      setState(() => _expanded = true);
    } else if (velocity > 150 && _expanded) {
      setState(() => _expanded = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    // Usa a cor da própria nota (a que a pessoa escolheu pra essa página)
    // como base, em vez da cor de tema geral do app — assim a barra
    // combina com a nota aberta.
    final seedColor = widget.pageColor;
    // Antes usava só 10% de opacidade no tema claro — em notas com cor de
    // página forte (azul, verde etc.) a barra ficava quase branca, sem
    // parecer "temática" de fato. Subindo pra 30% (mesma faixa do tema
    // escuro) a cor da nota fica visível, mas ainda suave o bastante pra
    // não competir com os ícones de formatação por cima.
    final bgColor = widget.isDark
        ? Color.alphaBlend(seedColor.withOpacity(0.35), const Color(0xFF1C1C1C))
        : Color.alphaBlend(seedColor.withOpacity(0.30), Colors.white);
    final fgColor = widget.isDark ? Colors.white : Colors.black87;

    return DecoratedBox(
      decoration: BoxDecoration(
        // Opaco de propósito, pra não se confundir com o papel de fundo.
        color: bgColor,
        border: Border(top: BorderSide(color: fgColor.withOpacity(0.12))),
      ),
      child: SafeArea(
        top: false,
        child: Theme(
          data: Theme.of(context).copyWith(
            iconTheme: IconThemeData(color: fgColor),
            textTheme: Theme.of(context).textTheme.apply(bodyColor: fgColor, displayColor: fgColor),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Ferramentas de inserir — fixas, sempre visíveis durante a
              // edição. Ficam num scroll horizontal por segurança (nunca
              // mais um botão sobrepondo o outro em aparelhos estreitos,
              // mesmo se um dia entrar mais um ícone aqui).
              SizedBox(
                height: 44,
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Row(
                    children: [
                      IconButton(
                        tooltip: 'Fundo da página',
                        icon: const Icon(Icons.palette_outlined, size: 21),
                        onPressed: widget.onPickBackgroundColor,
                      ),
                      IconButton(
                        tooltip: 'Tipo de pauta',
                        icon: const Icon(Icons.grid_on_outlined, size: 21),
                        onPressed: widget.onPickPageStyle,
                      ),
                      IconButton(
                        tooltip: 'Inserir linha divisória',
                        icon: const Icon(Icons.horizontal_rule, size: 21),
                        onPressed: widget.onInsertDivider,
                      ),
                      IconButton(
                        tooltip: 'Inserir imagem',
                        icon: const Icon(Icons.image_outlined, size: 21),
                        onPressed: widget.onInsertImage,
                      ),
                      IconButton(
                        tooltip: 'Inserir data',
                        icon: const Icon(Icons.calendar_today_outlined, size: 21),
                        onPressed: widget.onInsertDate,
                      ),
                      IconButton(
                        tooltip: 'Anexar arquivo',
                        icon: const Icon(Icons.attach_file_outlined, size: 21),
                        onPressed: widget.onAttachFile,
                      ),
                    ],
                  ),
                ),
              ),
              Divider(height: 1, thickness: 1, color: fgColor.withOpacity(0.12)),
              GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: _toggle,
                onVerticalDragEnd: _handleVerticalDrag,
                child: SizedBox(
                  height: 26,
                  width: double.infinity,
                  child: Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: fgColor.withOpacity(0.35),
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  ),
                ),
              ),
              AnimatedSize(
                duration: const Duration(milliseconds: 180),
                alignment: Alignment.bottomCenter,
                child: _expanded
                    ? quill.QuillSimpleToolbar(
                        controller: widget.controller,
                        config: const quill.QuillSimpleToolbarConfig(
                          showListBullets: true,
                          showListNumbers: true,
                          showListCheck: true,
                          showColorButton: true,
                          showBackgroundColorButton: true,
                          showAlignmentButtons: true,
                          showFontFamily: true,
                          showFontSize: true,
                          // Explícito de propósito (não é só o padrão da
                          // lib): cria/edita links no texto selecionado.
                          showLink: true,
                        ),
                      )
                    : const SizedBox(width: double.infinity, height: 0),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
