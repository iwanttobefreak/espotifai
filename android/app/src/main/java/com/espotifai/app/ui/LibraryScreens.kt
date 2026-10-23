package com.espotifai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.espotifai.app.MusicViewModel
import com.espotifai.app.data.Album
import com.espotifai.app.data.Artist
import com.espotifai.app.data.EstadoConexion
import com.espotifai.app.data.Track
import com.espotifai.app.ui.theme.EspotifaiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    vm: MusicViewModel,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onSettings: () -> Unit = {},
    onPlaylists: () -> Unit = {},
    onQueue: () -> Unit = {},
    onHome: () -> Unit = {}
) {
    val library by vm.library.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val offline by vm.offline.collectAsState()
    val estado by vm.estadoConexion.collectAsState()
    val cola by vm.queue.collectAsState()

    var consulta by rememberSaveable { mutableStateOf("") }
    var artistOptions by remember { mutableStateOf<Artist?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Artistas") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                        } else {
                            IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Inicio") }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Recargar") }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configuracion") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HomeQuickActions(vm, onPlaylists, onQueue, cola.size)
            }
        }
    ) { padding ->
        when {
            loading && library == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            error != null && library == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se pudo conectar", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Servidor: ${vm.baseUrl.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Reintentar")
                    }
                }
            }

            library != null -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Buscador(consulta) { consulta = it }
                    if (offline) BannerEstado(estado)
                    if (consulta.isBlank()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(library!!.library) { artist ->
                                ArtistRow(artist, onClick = { onArtistClick(artist.id) }, onOptions = { artistOptions = artist })
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    } else {
                        val resultados = remember(library, consulta) { buscar(library!!, consulta) }
                        ListaResultados(vm, resultados, onArtistClick, onAlbumClick)
                    }
                }
            }
        }
    }

    artistOptions?.let { a ->
        MediaOptionsSheet(
            vm = vm,
            title = a.name,
            subtitle = "${a.albums.size} álbumes · ${a.albums.sumOf { it.tracks.size }} pistas",
            tracks = a.albums.flatMap { it.tracks },
            onDismiss = { artistOptions = null }
        )
    }
}

/** Acciones siempre visibles en la pantalla de inicio. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeQuickActions(
    vm: MusicViewModel,
    onPlaylists: () -> Unit,
    onQueue: () -> Unit,
    colaCount: Int
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ConnectionStatusIndicator(vm)
        AssistChip(
            onClick = onPlaylists,
            label = { Text("Listas de reproducción") },
            leadingIcon = {
                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
        AssistChip(
            onClick = onQueue,
            label = {
                Text(if (colaCount > 0) "Cola de reproducción ($colaCount)" else "Cola de reproducción")
            },
            leadingIcon = {
                Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
    }
}

/** Campo de busqueda (artista, album o cancion) con boton de borrar. */
@Composable
private fun Buscador(consulta: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = consulta,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar artista, álbum o canción") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
        trailingIcon = {
            if (consulta.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar busqueda")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

/** Aviso de modo local: ademas del semaforo, explica que solo hay descargas. */
@Composable
private fun BannerEstado(estado: EstadoConexion) {
    val (color, _) = colorYEtiqueta(estado)
    val detalle = when (estado) {
        EstadoConexion.SIN_INTERNET -> "Sin internet · solo tus canciones descargadas"
        EstadoConexion.SIN_SERVIDOR -> "Sin conexión al servidor · solo tus canciones descargadas"
        EstadoConexion.ONLINE -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            detalle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Resultados de la busqueda agrupados por artistas, albumes y canciones. */
@Composable
private fun ListaResultados(
    vm: MusicViewModel,
    resultados: ResultadosBusqueda,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    var opciones by remember { mutableStateOf<Track?>(null) }
    var artistOptions by remember { mutableStateOf<Artist?>(null) }
    var albumOptions by remember { mutableStateOf<AlbumHit?>(null) }
    if (resultados.vacio) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (resultados.artistas.isNotEmpty()) {
            item { SeccionTitulo("Artistas", resultados.artistas.size) }
            items(resultados.artistas) { artist ->
                ArtistRow(artist, onClick = { onArtistClick(artist.id) }, onOptions = { artistOptions = artist })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (resultados.albumes.isNotEmpty()) {
            item { SeccionTitulo("Álbumes", resultados.albumes.size) }
            items(resultados.albumes) { hit ->
                AlbumRow(vm, hit.album, onClick = { onAlbumClick(hit.album.id) }, onOptions = { albumOptions = hit })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (resultados.canciones.isNotEmpty()) {
            item { SeccionTitulo("Canciones", resultados.canciones.size) }
            items(resultados.canciones) { hit ->
                TrackRow(
                    vm = vm,
                    track = hit.track,
                    onClick = { vm.play(hit.track, hit.album.tracks) },
                    onOptions = { opciones = hit.track }
                )
            }
        }
    }

    opciones?.let { t ->
        TrackOptionsSheet(vm = vm, track = t, onDismiss = { opciones = null })
    }
    artistOptions?.let { a ->
        MediaOptionsSheet(
            vm = vm,
            title = a.name,
            subtitle = "${a.albums.size} álbumes · ${a.albums.sumOf { it.tracks.size }} pistas",
            tracks = a.albums.flatMap { it.tracks },
            onDismiss = { artistOptions = null }
        )
    }
    albumOptions?.let { hit ->
        MediaOptionsSheet(
            vm = vm,
            title = hit.album.name,
            subtitle = "${hit.artistName} · ${hit.album.tracks.size} pistas",
            tracks = hit.album.tracks,
            onDismiss = { albumOptions = null }
        )
    }
}

@Composable
private fun SeccionTitulo(texto: String, count: Int) {
    Text(
        "$texto · $count",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = EspotifaiGreen,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    onOptions: (() -> Unit)? = null
) {
    val trackCount = artist.albums.sumOf { it.tracks.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${artist.albums.size} albumes · $trackCount pistas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onOptions != null) {
            IconButton(onClick = onOptions) {
                Icon(Icons.Default.MoreVert, "Opciones del artista")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    vm: MusicViewModel,
    artistId: String,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    val artist = vm.artistById(artistId)
    val snackbarHostState = remember { SnackbarHostState() }
    var albumOptions by remember { mutableStateOf<Album?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (artist == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No se encontro el artista")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(artist.albums) { album ->
                    AlbumRow(
                        vm = vm,
                        album = album,
                        onClick = { onAlbumClick(album.id) },
                        onOptions = { albumOptions = album }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
    DownloadMessageHost(vm, snackbarHostState)
    albumOptions?.let { al ->
        MediaOptionsSheet(
            vm = vm,
            title = al.name,
            subtitle = "${artist?.name ?: ""} · ${al.tracks.size} pistas",
            tracks = al.tracks,
            onDismiss = { albumOptions = null }
        )
    }
}

@Composable
private fun AlbumRow(vm: MusicViewModel, album: Album, onClick: () -> Unit, onOptions: (() -> Unit)? = null) {
    val downloaded by vm.downloaded.collectAsState()
    val progreso by vm.albumProgress.collectAsState()
    val pedirPermiso = rememberStoragePermission { vm.setAlbumDownload(album, true) }
    val bajando = progreso[album.id]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(vm.coverUrl(album.cover), size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (bajando != null) "Descargando $bajando"
                else "${album.tracks.size} pistas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onOptions != null) {
            IconButton(onClick = onOptions) {
                Icon(Icons.Default.MoreVert, "Opciones del álbum")
            }
        }
        if (bajando != null) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = EspotifaiGreen
            )
        } else {
            val completo = album.tracks.isNotEmpty() && album.tracks.all { downloaded.contains(it.id) }
            Checkbox(
                checked = completo,
                onCheckedChange = { checked ->
                    if (checked) pedirPermiso() else vm.setAlbumDownload(album, false)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(vm: MusicViewModel, albumId: String, onBack: () -> Unit, onHome: () -> Unit = {}) {
    val album = vm.albumById(albumId)
    val snackbarHostState = remember { SnackbarHostState() }
    var opciones by remember { mutableStateOf<Track?>(null) }
    var albumMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atras") }
                },
                actions = {
                    if (album != null && album.tracks.isNotEmpty()) {
                        IconButton(onClick = { albumMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones del álbum")
                        }
                    }
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (album != null && album.tracks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { vm.playAlbum(album) },
                    containerColor = EspotifaiGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.PlayArrow, "Reproducir album")
                }
            }
        }
    ) { padding ->
        if (album == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No se encontro el album")
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverImage(vm.coverUrl(album.cover), size = 112.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${album.tracks.size} pistas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(album.tracks) { track ->
                        TrackRow(
                            vm = vm,
                            track = track,
                            onClick = { vm.play(track, album.tracks) },
                            onOptions = { opciones = track }
                        )
                    }
                }
            }
        }
    }
    DownloadMessageHost(vm, snackbarHostState)
    opciones?.let { t ->
        TrackOptionsSheet(vm = vm, track = t, onDismiss = { opciones = null })
    }
    if (albumMenu && album != null) {
        MediaOptionsSheet(
            vm = vm,
            title = album.name,
            subtitle = "${album.tracks.firstOrNull()?.artist ?: ""} · ${album.tracks.size} pistas",
            tracks = album.tracks,
            onDismiss = { albumMenu = false }
        )
    }
}

@Composable
private fun TrackRow(
    vm: MusicViewModel,
    track: Track,
    onClick: () -> Unit,
    onOptions: (() -> Unit)? = null
) {
    val current by vm.currentTrack.collectAsState()
    val isCurrent = current?.id == track.id
    val downloaded by vm.downloaded.collectAsState()
    val bajando by vm.downloadingTracks.collectAsState()
    val pedirPermiso = rememberStoragePermission { vm.setTrackDownload(track, true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            track.track?.toString() ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) EspotifaiGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (bajando.contains(track.id)) "Descargando…" else track.artist,
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
        Spacer(Modifier.width(4.dp))
        if (onOptions != null) {
            IconButton(onClick = onOptions) {
                Icon(Icons.Default.MoreVert, "Más opciones")
            }
        }
        if (bajando.contains(track.id)) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = EspotifaiGreen
            )
        } else {
            Checkbox(
                checked = downloaded.contains(track.id),
                onCheckedChange = { checked ->
                    if (checked) pedirPermiso() else vm.setTrackDownload(track, false)
                }
            )
        }
    }
}
