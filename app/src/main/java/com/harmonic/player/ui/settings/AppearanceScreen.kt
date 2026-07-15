package com.harmonic.player.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Paleta ampla de cores de destaque — cobre bem mais gostos do que os 5 originais. */
private val accentPresets = listOf(
    Color(0xFFFF7043), Color(0xFFFF5252), Color(0xFFEC407A), Color(0xFFAB47BC),
    Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF29B6F6), Color(0xFF26C6DA),
    Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
    Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFF8BBD0), Color(0xFFB388FF)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentWallpaper by settings.defaultWallpaper.collectAsState(initial = null)
    val currentCustomBg by settings.backgroundUri.collectAsState(initial = null)
    val currentGradient by settings.gradientTheme.collectAsState(initial = null)
    val currentAccent by settings.accentColor.collectAsState(initial = null)
    val blurRadius by settings.backgroundBlurRadius.collectAsState(initial = 0)
    val scrimAlpha by settings.backgroundScrimAlpha.collectAsState(initial = 45)

    var showCustomColorDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = copyImageToInternalStorage(context, uri)
                if (savedPath != null) settings.setCustomBackground(savedPath)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Aparência", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Cor de destaque", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp)
            ) {
                item {
                    // Botão "+": abre o seletor de cor personalizada, com
                    // liberdade total (RGB), em vez de ficar preso a presets.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showCustomColorDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Cor personalizada")
                    }
                }
                items(accentPresets) { color ->
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

            Text("Desfocar o fundo (blur)", style = MaterialTheme.typography.titleMedium)
            Text(
                "0 = nítido. Só tem efeito real no Android 12 ou mais recente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = blurRadius.toFloat(),
                onValueChange = { scope.launch { settings.setBackgroundBlurRadius(it.toInt()) } },
                valueRange = 0f..40f
            )

            Spacer(Modifier.height(16.dp))

            Text("Sombra sobre o fundo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Escurece a imagem/gradiente pra o texto ficar mais legível.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = scrimAlpha.toFloat(),
                onValueChange = { scope.launch { settings.setBackgroundScrimAlpha(it.toInt()) } },
                valueRange = 0f..90f
            )

            Spacer(Modifier.height(24.dp))

            Text("Gradientes (sem imagem, mais leve)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(com.harmonic.player.data.GradientTheme.values().toList()) { theme ->
                    val isSelected = currentCustomBg == null && currentWallpaper == null && currentGradient == theme.name
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(theme.colorsArgb.map { Color(it) }))
                            .clickable { scope.launch { settings.setGradientTheme(theme) } },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            theme.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Imagem de fundo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Escolha um dos fundos inclusos ou uma foto da sua galeria.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(560.dp)
            ) {
                item {
                    // Card "Escolher da galeria" — sempre primeiro, pra ficar
                    // fácil de achar.
                    val isSelected = currentCustomBg != null
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.6f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { pickImageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected && currentCustomBg != null) {
                            AsyncImage(
                                model = Uri.parse(currentCustomBg),
                                contentDescription = "Sua foto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
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
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Photo, contentDescription = null)
                                Spacer(Modifier.height(4.dp))
                                Text("Da galeria", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                items(DefaultWallpaper.values().toList()) { wallpaper ->
                    val isSelected = currentCustomBg == null && currentWallpaper == wallpaper.name
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

    if (showCustomColorDialog) {
        CustomColorPickerDialog(
            initialColor = currentAccent?.let { Color(it) } ?: Color(0xFFFF7043),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { color ->
                scope.launch { settings.setAccentColor(color.toArgb()) }
                showCustomColorDialog = false
            }
        )
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var red by remember { mutableStateOf(initialColor.red) }
    var green by remember { mutableStateOf(initialColor.green) }
    var blue by remember { mutableStateOf(initialColor.blue) }
    val previewColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cor personalizada") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                )
                Spacer(Modifier.height(16.dp))

                Text("Vermelho", style = MaterialTheme.typography.labelMedium)
                Slider(value = red, onValueChange = { red = it }, valueRange = 0f..1f)

                Text("Verde", style = MaterialTheme.typography.labelMedium)
                Slider(value = green, onValueChange = { green = it }, valueRange = 0f..1f)

                Text("Azul", style = MaterialTheme.typography.labelMedium)
                Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(previewColor) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Copia a imagem escolhida na galeria pra dentro do armazenamento do
 * próprio app. Isso evita depender da permissão da URI original, que pode
 * expirar ou não sobreviver a um reinício do aparelho — copiando, o fundo
 * escolhido continua funcionando pra sempre, exatamente como qualquer outra
 * configuração salva.
 */
private suspend fun copyImageToInternalStorage(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val destFile = File(context.filesDir, "custom_background.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.toURI().toString()
        } catch (e: Exception) {
            null
        }
    }
