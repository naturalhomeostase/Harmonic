package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.launch

/** Cores de destaque prontas, cada uma combinando com um dos wallpapers padrão. */
private val accentPresets = listOf(
    Color(0xFFFF7043) to "Chamas (leão)",
    Color(0xFF29B6F6) to "Elétrico (guitarra)",
    Color(0xFFF8BBD0) to "Vinil pastel",
    Color(0xFF9C6ADE) to "Floresta mágica",
    Color(0xFFB388FF) to "Neon lo-fi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val currentWallpaper by settings.defaultWallpaper.collectAsState(initial = null)
    val currentAccent by settings.accentColor.collectAsState(initial = null)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Aparência") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            Text("Cor de destaque", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                accentPresets.forEach { (color, _) ->
                    val isSelected = currentAccent == color.toArgb()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                scope.launch { settings.setAccentColor(color.toArgb()) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("Imagem de fundo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Escolha um dos fundos inclusos ou use uma imagem sua nas configurações avançadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(DefaultWallpaper.values().toList()) { wallpaper ->
                    val isSelected = currentWallpaper == wallpaper.name
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.6f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { scope.launch { settings.setDefaultWallpaper(wallpaper) } }
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/${wallpaper.assetPath}",
                            contentDescription = wallpaper.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            wallpaper.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
