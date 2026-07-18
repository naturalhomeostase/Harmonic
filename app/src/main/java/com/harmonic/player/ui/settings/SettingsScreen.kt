package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Configurações "de verdade" do app — hub central. A customização de tema
 * (gradientes/imagem/cor de destaque) que antes era a própria tela de
 * Configurações agora é só uma das opções daqui ("Mudar tema").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenHiddenFolders: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val albumGridView by settings.albumGridView.collectAsState(initial = false)
    val artistGridView by settings.artistGridView.collectAsState(initial = false)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SettingsRow(
                icon = Icons.Filled.Palette,
                title = "Mudar tema",
                subtitle = "Gradientes, imagem de fundo, cor de destaque e gradiente dos títulos",
                onClick = onOpenTheme
            )

            SettingsRow(
                icon = Icons.Filled.GridView,
                title = "Visualização em grade dos álbuns",
                subtitle = "Mostra os álbuns como capas em grade em vez de lista",
                onClick = { scope.launch { settings.setAlbumGridView(!albumGridView) } },
                trailing = {
                    Switch(
                        checked = albumGridView,
                        onCheckedChange = { scope.launch { settings.setAlbumGridView(it) } }
                    )
                }
            )

            SettingsRow(
                icon = Icons.Filled.GridView,
                title = "Visualização em grade dos artistas",
                subtitle = "Mostra os artistas com foto em grade em vez de lista",
                onClick = { scope.launch { settings.setArtistGridView(!artistGridView) } },
                trailing = {
                    Switch(
                        checked = artistGridView,
                        onCheckedChange = { scope.launch { settings.setArtistGridView(it) } }
                    )
                }
            )

            SettingsRow(
                icon = Icons.Filled.FolderOff,
                title = "Pastas ocultas",
                subtitle = "Escolha quais pastas ficam de fora da biblioteca",
                onClick = onOpenHiddenFolders
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(title, color = Color.White) },
        supportingContent = { Text(subtitle, color = Color.White.copy(alpha = 0.65f)) },
        trailingContent = trailing ?: {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    )
}
