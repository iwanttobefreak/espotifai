package com.espotifai.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.espotifai.app.MusicViewModel
import com.espotifai.app.ui.theme.EspotifaiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: MusicViewModel, onBack: () -> Unit, onQueue: () -> Unit = {}) {
    val track = vm.currentTrack.collectAsState().value
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val cola by vm.queue.collectAsState()

    var dragging by remember { mutableStateOf(false) }
    var slider by remember { mutableStateOf(0f) }
    if (!dragging) {
        slider = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    IconButton(onClick = onQueue) {
                        BadgedBox(
                            badge = {
                                if (cola.isNotEmpty()) Badge { Text("${cola.size}") }
                            }
                        ) {
                            Icon(Icons.Default.PlaylistPlay, "Cola de reproducción")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (track == null) {
                Text("Selecciona una cancion", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Scaffold
            }

            CoverImage(vm.coverUrl(track.album), size = 280.dp)
            Spacer(Modifier.height(32.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(32.dp))

            Slider(
                value = slider.coerceIn(0f, 1f),
                onValueChange = { dragging = true; slider = it },
                onValueChangeFinished = {
                    vm.seekTo((slider * duration).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDurationMs(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDurationMs(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.previous() }, Modifier.size(64.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(40.dp))
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(72.dp)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = EspotifaiGreen,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { vm.next() }, Modifier.size(64.dp)) {
                    Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(vm: MusicViewModel, onClick: () -> Unit) {
    val track = vm.currentTrack.collectAsState().value
    val isPlaying by vm.isPlaying.collectAsState()
    if (track == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(vm.coverUrl(track.album), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.togglePlay() }) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = EspotifaiGreen
            )
        }
        IconButton(onClick = { vm.next() }) {
            Icon(Icons.Default.SkipNext, "Siguiente")
        }
    }
}
