package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicRepository
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.ui.library.LibraryTab
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
    musicRepository: MusicRepository,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenHiddenFolders: () -> Unit,
    onOpenHiddenSongs: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val albumGridView by settings.albumGridView.collectAsState(initial = false)
    val artistGridView by settings.artistGridView.collectAsState(initial = false)
    val hiddenTabs by settings.hiddenTabs.collectAsState(initial = emptySet())
    var showTabsDialog by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
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
                    com.harmonic.player.ui.common.ThemedSwitch(
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
                    com.harmonic.player.ui.common.ThemedSwitch(
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

            SettingsRow(
                icon = Icons.Filled.VisibilityOff,
                title = "Músicas ocultas",
                subtitle = "Veja e reexiba músicas ocultadas individualmente (ou álbuns inteiros)",
                onClick = onOpenHiddenSongs
            )

            SettingsRow(
                icon = Icons.Filled.Checklist,
                title = "Abas visíveis",
                subtitle = "Escolha quais abas aparecem na tela principal",
                onClick = { showTabsDialog = true }
            )

            SettingsRow(
                icon = Icons.Filled.Sync,
                title = if (scanning) "Escaneando..." else "Escanear novas músicas",
                subtitle = "Procura por músicas baixadas recentemente que ainda não apareceram no app",
                onClick = {
                    if (!scanning) {
                        scanning = true
                        musicRepository.rescanNow(scope)
                        // Feedback simples: a varredura roda em segundo
                        // plano, então só mostramos "escaneando" por um
                        // tempinho — não temos um sinal exato de "terminou"
                        // exposto aqui sem mudar mais a fundo o repositório.
                        scope.launch {
                            kotlinx.coroutines.delay(2500)
                            scanning = false
                        }
                    }
                }
            )

            val notifContext = androidx.compose.ui.platform.LocalContext.current
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Notificação do player",
                subtitle = "Se os botões de play/pause não aparecerem na barra de notificação, confira aqui se a permissão está ativada",
                onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, notifContext.packageName)
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${notifContext.packageName}"))
                    }
                    notifContext.startActivity(intent)
                }
            )
        }
    }

    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Abas visíveis") },
            text = {
                Column {
                    Text(
                        "Músicas não pode ser escondida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LibraryTab.values().filter { it != LibraryTab.SONGS }.forEach { tab ->
                        val isHidden = hiddenTabs.contains(tab.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { settings.setTabHidden(tab.name, !isHidden) } }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(tab.label)
                            com.harmonic.player.ui.common.ThemedSwitch(
                                checked = !isHidden,
                                onCheckedChange = { visible -> scope.launch { settings.setTabHidden(tab.name, !visible) } }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTabsDialog = false }) { Text("Fechar") }
            }
        )
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
