package com.harmonic.player.ui.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.playback.EqualizerController
import com.harmonic.player.playback.reverbPresetNames
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    equalizerController: EqualizerController,
    settings: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val eqState by equalizerController.uiState.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Equalizador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Switch(
                        checked = eqState.enabled,
                        onCheckedChange = { enabled ->
                            equalizerController.setEnabled(enabled)
                            scope.launch { settings.setEqEnabled(enabled) }
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (!eqState.ready) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Toque em uma música pra ativar o equalizador.\n" +
                    "Ele precisa de uma reprodução em andamento pra se conectar ao áudio.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Bandas de frequência", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                eqState.bands.forEach { band ->
                    val level = eqState.bandLevels.getOrElse(band.index) { 0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newValue ->
                                equalizerController.setBandLevel(band.index, newValue.toInt())
                            },
                            onValueChangeFinished = {
                                scope.launch { settings.setEqBandLevels(eqState.bandLevels) }
                            },
                            valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                            modifier = Modifier
                                .verticalSlider()
                                .width(160.dp)
                        )
                        Text(
                            if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}kHz" else "${band.centerFreqHz}Hz",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Bass Boost", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = eqState.bassBoostStrength.toFloat(),
                onValueChange = { equalizerController.setBassBoostStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setBassBoostStrength(eqState.bassBoostStrength) } },
                valueRange = 0f..1000f
            )

            Spacer(Modifier.height(8.dp))

            Text("Virtualizador (efeito surround)", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = eqState.virtualizerStrength.toFloat(),
                onValueChange = { equalizerController.setVirtualizerStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setVirtualizerStrength(eqState.virtualizerStrength) } },
                valueRange = 0f..1000f
            )

            Spacer(Modifier.height(8.dp))

            Text("Reverb", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            var reverbMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { reverbMenuExpanded = true }) {
                    Text(reverbPresetNames.getOrElse(eqState.reverbPreset) { "Nenhum" })
                }
                DropdownMenu(expanded = reverbMenuExpanded, onDismissRequest = { reverbMenuExpanded = false }) {
                    reverbPresetNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                equalizerController.setReverbPreset(index)
                                scope.launch { settings.setReverbPreset(index) }
                                reverbMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slider vertical: o Material3 não tem um Slider vertical nativo, então
 * giramos um Slider horizontal comum em 270° e reajustamos as restrições de
 * layout — truque padrão em Compose para esse caso.
 */
private fun Modifier.verticalSlider(): Modifier = this
    .graphicsLayer {
        rotationZ = -90f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }
