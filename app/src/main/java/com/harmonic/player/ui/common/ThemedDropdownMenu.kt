package com.harmonic.player.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Versão "casca" do [DropdownMenu] padrão do Material3, só que pintada com
 * a cor de destaque do tema em vez do cinza neutro padrão — inspirado na
 * referência visual enviada (cartão sólido, cantos arredondados, itens em
 * lista com ícone + texto claro por cima).
 *
 * Reaproveita o [DropdownMenu]/[androidx.compose.material3.DropdownMenuItem]
 * de sempre por dentro (então todo o comportamento de posicionamento,
 * dismiss ao tocar fora, etc. continua igual) — a mudança é só que a gente
 * sobrescreve localmente as cores de superfície do MaterialTheme antes de
 * montar o menu, já que é dali que o Material3 tira a cor de fundo e do
 * texto/ícone dos itens. Como a cor de destaque muda com o tema escolhido
 * em Aparência, este menu acompanha automaticamente sem precisar de nenhum
 * ajuste manual em cada tela que o usa.
 */
@Composable
fun ThemedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val themedScheme = MaterialTheme.colorScheme.copy(
        surface = accent,
        surfaceContainer = accent,
        surfaceContainerHigh = accent,
        surfaceContainerHighest = accent,
        surfaceContainerLow = accent,
        surfaceContainerLowest = accent,
        surfaceVariant = accent,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.85f),
        outlineVariant = Color.White.copy(alpha = 0.22f)
    )

    MaterialTheme(colorScheme = themedScheme) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
            content = content
        )
    }
}
