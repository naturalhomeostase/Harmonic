package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Um item do menu de opções (music/playlist/pasta/artista/álbum...).
 * [tint] é opcional — usado por ações "perigosas" tipo Excluir, que ficam
 * em vermelho em vez da cor padrão.
 */
data class ActionSheetItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/**
 * Bottom sheet genérico com uma lista de ações — usado por todos os menus
 * "..." do app (música, playlist, pasta, artista, álbum). Em vez de montar
 * um ModalBottomSheet do zero em cada tela, cada uma só monta a lista de
 * [ActionSheetItem] com as opções que fazem sentido pra ela.
 *
 * Visual "vidro fosco flutuante": fundo transparente de verdade no
 * ModalBottomSheet padrão do Material3 (que normalmente é opaco), com um
 * cartão próprio por cima — cantos bem arredondados, gradiente translúcido
 * branco e uma borda fina de brilho, soltando o menu da base da tela em
 * vez de grudar como uma barra sólida.
 *
 * IMPORTANTE: este composable NÃO fecha a folha sozinho quando um item é
 * tocado — quem chama decide isso dentro do próprio `onClick` do item (ex:
 * `{ visible = false; showRenameDialog = true }`). Isso existe porque
 * várias ações (renomear, cortar, escolher playlist...) precisam abrir um
 * diálogo de continuação depois, e fechar tudo automaticamente destruiria
 * esse diálogo antes dele aparecer. [onDismiss] só é chamado quando o
 * usuário arrasta/toca fora da folha pra fechá-la sem escolher nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    items: List<ActionSheetItem>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        dragHandle = null,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                // Bem mais opaco que a versão anterior — antes o fundo
                // "vidro" deixava o conteúdo por trás se misturar com o
                // texto do menu; agora lê como um cartão sólido de verdade,
                // só um pouco translúcido pra ainda parecer "flutuando".
                .background(Color(0xFF161616).copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(vertical = 6.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
            if (title != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
                    }
                }
            }
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { item.onClick() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = item.tint ?: Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(item.label, color = item.tint ?: Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
