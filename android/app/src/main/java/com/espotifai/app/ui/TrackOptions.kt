package com.espotifai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.espotifai.app.MusicViewModel
import com.espotifai.app.data.Track
import com.espotifai.app.ui.theme.EspotifaiGreen

/**
 * Menu que aparece al pulsar "mas opciones" en una cancion:
 * añadir a la cola, reproducir a continuacion y añadir a una lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOptionsSheet(
    vm: MusicViewModel,
    title: String,
    subtitle: String,
    tracks: List<Track>,
    onDismiss: () -> Unit
) {
    if (tracks.isEmpty()) return
    val playlists by vm.playlists.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nuevaLista by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Opcion("Reproducir", Icons.Default.PlayArrow) {
                vm.play(tracks.first(), tracks)
                onDismiss()
            }
            Opcion("Añadir a la cola", Icons.Default.QueueMusic) {
                vm.enqueue(tracks)
                onDismiss()
            }
            Opcion("Reproducir a continuación", Icons.Default.PlaylistPlay) {
                vm.enqueueNext(tracks)
                onDismiss()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(
                "Añadir a lista",
                style = MaterialTheme.typography.labelLarge,
                color = EspotifaiGreen,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            if (playlists.isEmpty()) {
                Text(
                    "Todavía no tienes listas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            } else {
                playlists.forEach { lista ->
                    val contiene = tracks.all { t -> lista.tracks.any { it.id == t.id } }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.addAllToPlaylist(lista.id, tracks) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (contiene) Icons.Default.Check else Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = if (contiene) EspotifaiGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            lista.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${lista.tracks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Opcion("Nueva lista…", Icons.Default.Add) { nuevaLista = true }
        }
    }

    if (nuevaLista) {
        NuevaListaDialog(
            onDismiss = { nuevaLista = false },
            onCrear = { nombre ->
                vm.createPlaylist(nombre, tracks)
                nuevaLista = false
                onDismiss()
            }
        )
    }
}

/** Menu de opciones para una sola cancion. */
@Composable
fun TrackOptionsSheet(vm: MusicViewModel, track: Track, onDismiss: () -> Unit) {
    MediaOptionsSheet(
        vm = vm,
        title = track.title,
        subtitle = "${track.artist} · ${track.album}",
        tracks = listOf(track),
        onDismiss = onDismiss
    )
}

@Composable
private fun Opcion(texto: String, icono: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Dialogo para pedir el nombre de una lista nueva. */
@Composable
fun NuevaListaDialog(
    onDismiss: () -> Unit,
    onCrear: (String) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva lista") },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCrear(nombre) },
                enabled = nombre.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
