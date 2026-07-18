package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
        // Deixa uma folga em volta pro cartão de vidro "flutuar" solto em
        // vez de encostar nas bordas da tela como um bottom sheet comum.
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))
                    )
                )
                .background(Color.Black.copy(alpha = 0.35f)) // reforça o contraste sobre fundos muito claros
                .padding(bottom = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
            ) {
                // Alcinha de arrasto própria, já que dragHandle = null acima
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
                if (title != null) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                items.forEach { item ->
                    ListItem(
                        modifier = Modifier.clickable { item.onClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(item.icon, contentDescription = null, tint = item.tint ?: Color.White.copy(alpha = 0.85f))
                        },
                        headlineContent = {
                            Text(item.label, color = item.tint ?: Color.White)
                        }
                    )
                }
            }
        }
    }
}
