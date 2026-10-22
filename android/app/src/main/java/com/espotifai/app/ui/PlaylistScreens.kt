package com.espotifai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.espotifai.app.MusicViewModel
import com.espotifai.app.data.Playlist
import com.espotifai.app.data.Track
import com.espotifai.app.ui.theme.EspotifaiGreen

// ---------------- listas de reproduccion ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    vm: MusicViewModel,
    onPlaylistClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val playlists by vm.playlists.collectAsState()
    var crear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listas") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    IconButton(onClick = { crear = true }) { Icon(Icons.Default.Add, "Nueva lista") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { crear = true },
                containerColor = EspotifaiGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Nueva lista")
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No hay listas todavía", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Crea una lista y añade canciones desde el menú (⋮) de cualquier canción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(playlists, key = { it.id }) { lista ->
                    PlaylistRow(vm, lista, onClick = { onPlaylistClick(lista.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (crear) {
        NuevaListaDialog(
            onDismiss = { crear = false },
            onCrear = { nombre ->
                vm.createPlaylist(nombre)
                crear = false
            }
        )
    }
}

@Composable
private fun PlaylistRow(vm: MusicViewModel, playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                tint = EspotifaiGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (playlist.tracks.size == 1) "1 canción" else "${playlist.tracks.size} canciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { vm.playPlaylist(playlist) },
            enabled = playlist.tracks.isNotEmpty()
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Reproducir lista",
                tint = if (playlist.tracks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else EspotifaiGreen
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    vm: MusicViewModel,
    playlistId: String,
    onBack: () -> Unit
) {
    val playlists by vm.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()

    var menu by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        playlist?.name ?: "Lista",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    if (playlist != null) {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones de la lista")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Renombrar lista") },
                                onClick = { menu = false; renombrar = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar lista") },
                                onClick = { menu = false; borrar = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (playlist != null && playlist.tracks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { vm.playPlaylist(playlist) },
                    containerColor = EspotifaiGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.PlayArrow, "Reproducir lista")
                }
            }
        }
    ) { padding ->
        when {
            playlist == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No se encontró la lista")
                }
            }

            playlist.tracks.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("La lista está vacía", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Añade canciones desde el menú (⋮) de cualquier canción.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = EspotifaiGreen,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (playlist.tracks.size == 1) "1 canción" else "${playlist.tracks.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(playlist.tracks, key = { _, t -> t.id }) { index, track ->
                            PlaylistTrackRow(
                                track = track,
                                posicion = index + 1,
                                esActual = current?.id == track.id,
                                sonando = isPlaying && current?.id == track.id,
                                onPlay = { vm.play(track, playlist.tracks) },
                                onRemove = { vm.removeFromPlaylist(playlist.id, track.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (renombrar && playlist != null) {
        RenombrarListaDialog(
            nombreActual = playlist.name,
            onDismiss = { renombrar = false },
            onRenombrar = { nombre ->
                vm.renamePlaylist(playlist.id, nombre)
                renombrar = false
            }
        )
    }

    if (borrar && playlist != null) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            title = { Text("Eliminar lista") },
            text = { Text("¿Seguro que quieres eliminar «${playlist.name}»? Las canciones no se borran del teléfono.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(playlist.id)
                    borrar = false
                    onBack()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { borrar = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    posicion: Int,
    esActual: Boolean,
    sonando: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (esActual) "▶" else posicion.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (esActual) EspotifaiGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                color = if (esActual) EspotifaiGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar de la lista",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RenombrarListaDialog(
    nombreActual: String,
    onDismiss: () -> Unit,
    onRenombrar: (String) -> Unit
) {
    var nombre by remember { mutableStateOf(nombreActual) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar lista") },
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
                onClick = { onRenombrar(nombre) },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ---------------- cola de reproduccion ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val queue by vm.queue.collectAsState()
    val queueIndex by vm.queueIndex.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    var confirmarVaciar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cola de reproducción")
                        if (queue.isNotEmpty()) {
                            Text(
                                if (queue.size == 1) "1 canción" else "${queue.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { confirmarVaciar = true }) {
                            Icon(Icons.Default.Delete, "Vaciar cola")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("La cola está vacía", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Añade canciones con el menú (⋮) de cualquier canción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(queue, key = { index, t -> "$index-${t.id}" }) { index, track ->
                    val esActual = index == queueIndex && current?.id == track.id
                    QueueRow(
                        track = track,
                        posicion = index + 1,
                        esActual = esActual,
                        sonando = esActual && isPlaying,
                        onPlay = { vm.playQueueAt(index) },
                        onRemove = { vm.removeFromQueue(index) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (confirmarVaciar) {
        AlertDialog(
            onDismissRequest = { confirmarVaciar = false },
            title = { Text("Vaciar la cola") },
            text = { Text("Se quitarán todas las canciones de la cola y se detendrá la reproducción.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearQueue()
                    confirmarVaciar = false
                }) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarVaciar = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun QueueRow(
    track: Track,
    posicion: Int,
    esActual: Boolean,
    sonando: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (esActual) "▶" else posicion.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (esActual) EspotifaiGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                color = if (esActual) EspotifaiGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (sonando) "Sonando · ${track.artist}" else track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = if (sonando) EspotifaiGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar de la cola",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
