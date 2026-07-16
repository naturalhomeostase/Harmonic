package com.harmonic.player.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val titleGradientEnabled by settings.titleGradientEnabled.collectAsState(initial = false)
    val titleGradientMode by settings.titleGradientMode.collectAsState(initial = "theme")

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
            Text("Cor de destaque", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

            Text("Desfocar o fundo (blur)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

            Text("Sombra sobre o fundo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

            Text("Gradientes (sem imagem, mais leve)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Toque em um tema para aplicar — o preview acima mostra exatamente como o app vai ficar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Preview "printscreen" ao vivo: reflete o estado real das
            // configurações (gradiente/imagem, blur, sombra e gradiente de
            // texto), então ele já reage assim que o usuário toca em outro
            // tema logo abaixo — sem precisar de nenhum estado paralelo.
            AppearancePreviewMockup(
                gradientTheme = com.harmonic.player.data.GradientTheme.values()
                    .find { it.name == currentGradient } ?: com.harmonic.player.data.GradientTheme.MIDNIGHT,
                useImageBackground = currentWallpaper != null || currentCustomBg != null,
                imageModel = currentCustomBg ?: currentWallpaper?.let {
                    "file:///android_asset/${com.harmonic.player.data.DefaultWallpaper.valueOf(it).assetPath}"
                },
                accentColor = currentAccent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                scrimAlphaPercent = scrimAlpha,
                titleGradientEnabled = titleGradientEnabled,
                titleGradientMode = titleGradientMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.62f)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            // Linha horizontal de temas — rolagem lateral em vez da grade
            // vertical de antes, deixando o preview acima como protagonista.
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(com.harmonic.player.data.GradientTheme.values().toList()) { theme ->
                    val isSelected = currentCustomBg == null && currentWallpaper == null && currentGradient == theme.name
                    Box(
                        modifier = Modifier
                            .size(width = 84.dp, height = 96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(theme.colorsArgb.map { Color(it) }))
                            .then(
                                if (isSelected)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                else Modifier
                            )
                            .clickable { scope.launch { settings.setGradientTheme(theme) } },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            theme.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Opção de aplicar o mesmo gradiente também no texto dos
            // títulos das listas (em vez de só no fundo) — fica a critério
            // do usuário, já que nem todo mundo gosta do efeito em texto.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Gradiente também nos títulos", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        "Usa as cores do tema acima no título das músicas nas listas, em vez de branco sólido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = titleGradientEnabled,
                    onCheckedChange = { scope.launch { settings.setTitleGradientEnabled(it) } }
                )
            }

            if (titleGradientEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = titleGradientMode == "theme",
                        onClick = { scope.launch { settings.setTitleGradientMode("theme") } },
                        label = { Text("Cores do tema") }
                    )
                    FilterChip(
                        selected = titleGradientMode == "monochrome",
                        onClick = { scope.launch { settings.setTitleGradientMode("monochrome") } },
                        label = { Text("Tom único (clara → escura)") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Imagem de fundo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

/**
 * Mini "printscreen" ao vivo de como a Biblioteca fica com as configurações
 * atuais: mesmo fundo (imagem ou gradiente + blur/sombra), mesma cor de
 * destaque, e até o gradiente no título das músicas, se ativado — tudo
 * numa moldura de tela pra dar a sensação de preview real do app.
 */
@Composable
private fun AppearancePreviewMockup(
    gradientTheme: com.harmonic.player.data.GradientTheme,
    useImageBackground: Boolean,
    imageModel: Any?,
    accentColor: Color,
    scrimAlphaPercent: Int,
    titleGradientEnabled: Boolean,
    titleGradientMode: String,
    modifier: Modifier = Modifier
) {
    val fakeSongs = listOf("Noite sem fim" to "Coletivo Aurora", "Deriva" to "Baía Sul", "Eco de vidro" to "Marte 91")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
    ) {
        // Fundo: mesma lógica do AppBackground de verdade (imagem crop, ou gradiente)
        if (useImageBackground && imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientTheme.colorsArgb.map { Color(it) }))
            )
        }

        if (scrimAlphaPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlphaPercent / 100f))
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text("Harmonic", color = accentColor, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))

            // Abinha falsa, só pra dar o contexto visual do menu horizontal
            Row {
                Text(
                    "Músicas",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Artistas",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(44.dp)
                    .height(2.dp)
                    .background(accentColor)
            )

            Spacer(Modifier.height(16.dp))

            val titleBrush = if (titleGradientEnabled) {
                if (titleGradientMode == "monochrome") {
                    Brush.linearGradient(listOf(accentColor.copy(alpha = 0.75f), lightenColor(accentColor, 0.55f)))
                } else {
                    Brush.linearGradient(gradientTheme.colorsArgb.map { Color(it) })
                }
            } else null

            fakeSongs.forEach { (title, artist) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Photo,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (titleBrush != null) {
                            Text(
                                title,
                                style = MaterialTheme.typography.bodySmall.copy(brush = titleBrush)
                            )
                        } else {
                            Text(title, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            artist,
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
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

/** Clareia uma cor em direção ao branco, por um fator de 0 (sem mudança) a 1 (vira branco). */
private fun lightenColor(color: Color, factor: Float): Color = Color(
    red = color.red + (1f - color.red) * factor,
    green = color.green + (1f - color.green) * factor,
    blue = color.blue + (1f - color.blue) * factor,
    alpha = color.alpha
)
