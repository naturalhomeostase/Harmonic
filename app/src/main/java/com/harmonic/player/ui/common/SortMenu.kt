package com.harmonic.player.ui.common

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Uma opção de ordenação — [key] é um identificador interno estável, [label] é o texto mostrado. */
data class SortOption(val key: String, val label: String)

/**
 * Botão de "ordenar por" com um menu suspenso: lista de critérios (ex.
 * Título/Artista/Duração/Data adicionada) + um toggle de
 * crescente/decrescente no final. Reutilizado em Músicas, Playlists,
 * Pastas, Artistas e Álbuns — só a lista de [options] muda.
 */
@Composable
fun SortMenuButton(
    options: List<SortOption>,
    selectedKey: String,
    ascending: Boolean,
    onSelect: (String) -> Unit,
    onToggleDirection: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = "Ordenar por", tint = Color.White.copy(alpha = 0.85f))
    }

    ThemedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        androidx.compose.material3.Text(
            "Ordenar por",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.6f),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider()
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = {
                    if (option.key == selectedKey) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onSelect(option.key)
                    expanded = false
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (ascending) "Crescente" else "Decrescente") },
            leadingIcon = {
                Icon(
                    if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null
                )
            },
            onClick = {
                onToggleDirection()
                expanded = false
            }
        )
    }
}
