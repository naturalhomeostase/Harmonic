package com.harmonic.player.ui.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.playback.EqualizerController
import com.harmonic.player.playback.equalizerPresets
import com.harmonic.player.playback.reverbPresetNames
import com.harmonic.player.playback.toBandLevels
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
                title = { Text("Equalizador", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    com.harmonic.player.ui.common.ThemedSwitch(
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
            Text("Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(equalizerPresets) { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val newLevels = preset.toBandLevels(eqState.bands)
                            newLevels.forEachIndexed { index, level ->
                                equalizerController.setBandLevel(index, level)
                            }
                            scope.launch { settings.setEqBandLevels(newLevels, preset.name) }
                            if (!eqState.enabled) {
                                equalizerController.setEnabled(true)
                                scope.launch { settings.setEqEnabled(true) }
                            }
                        },
                        label = { Text(preset.name) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Bandas de frequência", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                eqState.bands.forEach { band ->
                    val level = eqState.bandLevels.getOrElse(band.index) { 0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.width(48.dp).height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
                                    .width(160.dp)
                                    .rotate(-90f)
                            )
                        }
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

            Text("Bass Boost", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = eqState.bassBoostStrength.toFloat(),
                onValueChange = { equalizerController.setBassBoostStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setBassBoostStrength(eqState.bassBoostStrength) } },
                valueRange = 0f..1000f
            )

            Spacer(Modifier.height(8.dp))

            Text("Virtualizador (efeito surround)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = eqState.virtualizerStrength.toFloat(),
                onValueChange = { equalizerController.setVirtualizerStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setVirtualizerStrength(eqState.virtualizerStrength) } },
                valueRange = 0f..1000f
            )

            Spacer(Modifier.height(8.dp))

            Text("Reverb", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            var reverbMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { reverbMenuExpanded = true }) {
                    Text(reverbPresetNames.getOrElse(eqState.reverbPreset) { "Nenhum" })
                }
                com.harmonic.player.ui.common.ThemedDropdownMenu(expanded = reverbMenuExpanded, onDismissRequest = { reverbMenuExpanded = false }) {
                    val onAccent = MaterialTheme.colorScheme.onSurface
                    reverbPresetNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                equalizerController.setReverbPreset(index)
                                scope.launch { settings.setReverbPreset(index) }
                                reverbMenuExpanded = false
                            }
                        )
                        if (index != reverbPresetNames.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
                        }
                    }
                }
            }
        }
    }
}
