package com.espotifai.app

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.espotifai.app.data.Album
import com.espotifai.app.data.ApiClient
import com.espotifai.app.data.Artist
import com.espotifai.app.data.DownloadStore
import com.espotifai.app.data.EstadoConexion
import com.espotifai.app.data.Library
import com.espotifai.app.data.LibraryCache
import com.espotifai.app.data.Playlist
import com.espotifai.app.data.PlaylistStore
import com.espotifai.app.data.Settings
import com.espotifai.app.data.SongDownloader
import com.espotifai.app.data.EspotifaiApi
import com.espotifai.app.data.Track
import com.espotifai.app.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)

    // Listas de reproduccion (locales al telefono, ver PlaylistStore).
    private val playlistStore = PlaylistStore(app)
    private val _playlists = MutableStateFlow(playlistStore.all())
    val playlists: StateFlow<List<Playlist>> = _playlists

    val baseUrl = MutableStateFlow(settings.baseUrl)
    val token = MutableStateFlow(settings.token)
    val configured = MutableStateFlow(settings.isConfigured())

    /** Carpeta elegida para las descargas (uri SAF); vacia = predeterminada. */
    val downloadTreeUri = MutableStateFlow(settings.downloadTreeUri)

    fun setDownloadTreeUri(uri: String) {
        settings.downloadTreeUri = uri
        downloadTreeUri.value = uri
    }

    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** true cuando se esta mostrando solo lo descargado (sin servidor). */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    /**
     * Semaforo de conexion: verde online, amarillo sin servidor, rojo sin
     * internet. Ver [EstadoConexion].
     */
    private val _estadoConexion = MutableStateFlow(EstadoConexion.ONLINE)
    val estadoConexion: StateFlow<EstadoConexion> = _estadoConexion

    private val connectivity: ConnectivityManager? =
        app.getSystemService(ConnectivityManager::class.java)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private var controller: MediaController? = null

    /** Cola de reproduccion actual (mismo orden que los MediaItem del player). */
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    /** Posicion dentro de [queue] de la pista que suena. */
    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            if (mediaItem == null) {
                _currentTrack.value = null
                _queueIndex.value = 0
                return
            }
            val idx = c.currentMediaItemIndex
            _queueIndex.value = idx
            _currentTrack.value = _queue.value.getOrNull(idx)
                ?: _queue.value.firstOrNull { it.id == mediaItem.mediaId }
            _positionMs.value = 0
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val c = controller ?: return
            if (c.mediaItemCount == 0) {
                _currentTrack.value = null
                _queueIndex.value = 0
            } else {
                _queueIndex.value = c.currentMediaItemIndex
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                val d = controller?.duration ?: 0L
                if (d > 0) _durationMs.value = d
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = reaccionarAlCambioDeRed()
        override fun onLost(network: Network) = reaccionarAlCambioDeRed()
    }

    init {
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _positionMs.value = c.currentPosition
                }
                delay(500)
            }
        }
        if (configured.value) {
            connect()
            loadLibrary()
        }
        // Al cambiar la red (wifi movil, perder cobertura...) se reevalua el
        // estado y, si vuelve el servidor, se recarga la biblioteca completa.
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun reaccionarAlCambioDeRed() {
        if (!configured.value) return
        // Si ya hay una carga en curso, se deja terminar: ella misma fija el estado.
        if (_loading.value) return
        loadLibrary()
    }

    /** Hay alguna interfaz de red activa (wifi o datos), con o sin internet. */
    private fun tieneRed(): Boolean = connectivity?.activeNetwork != null

    /** La red activa tiene internet de verdad (validado por el sistema). */
    private fun hayInternet(): Boolean {
        val cm = connectivity ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun api(): EspotifaiApi = ApiClient.build(baseUrl.value, token.value)

    private fun connect() {
        if (!configured.value || controller != null) return
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.addListener(playerListener)
                controller = c
                // Si el servicio ya tenia una cola (p. ej. la actividad se
                // recreo mientras sonaba), se reconstruye para poder verla.
                if (_queue.value.isEmpty() && c.mediaItemCount > 0) {
                    val tracks = (0 until c.mediaItemCount).mapNotNull { i ->
                        trackFromMediaItem(c.getMediaItemAt(i))
                    }
                    if (tracks.size == c.mediaItemCount) {
                        _queue.value = tracks
                        val idx = c.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
                        _queueIndex.value = idx
                        _currentTrack.value = tracks.getOrNull(idx)
                    }
                }
            } catch (_: Exception) {
                // no se pudo conectar al servicio de reproduccion
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun saveSettings(url: String, tok: String) {
        settings.baseUrl = url
        settings.token = tok
        baseUrl.value = url
        token.value = tok
        configured.value = settings.isConfigured()
        disconnect()
        loadLibrary()
        connect()
    }

    fun loadLibrary() {
        if (!configured.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _library.value = cargarBiblioteca()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error de conexion"
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() {
        if (!configured.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // el reindexado tarda minutos: timeout amplio solo para esto.
                // Si no hay servidor, no pasa nada: se recarga lo local.
                try {
                    ApiClient.build(baseUrl.value, token.value, readTimeoutSegundos = 600).rescan()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                _library.value = cargarBiblioteca()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error de conexion"
            } finally {
                _loading.value = false
            }
        }
    }

    // ------- direcciones del servidor -------
    // La app conoce dos direcciones: la de la LAN (rapida, sin DNS de por
    // medio) y la publica (fuera de casa). Si la configurada no responde, se
    // prueba la otra y se fija la que funcione. Ambas se configuran al
    // compilar con -Pespotifai.urlCasa / -Pespotifai.urlFuera (ver README).

    /** La otra direccion conocida (LAN <-> publica). */
    private fun alternativa(): String =
        if (baseUrl.value.trimEnd('/') == BuildConfig.URL_CASA.trimEnd('/')) {
            BuildConfig.URL_FUERA
        } else {
            BuildConfig.URL_CASA
        }

    private fun fijarBase(url: String) {
        if (baseUrl.value.trimEnd('/') == url) return
        settings.baseUrl = url
        baseUrl.value = url
    }

    /** Prueba la otra direccion y, si responde, la fija como base. */
    private suspend fun cambiarBaseSiResponde(): Boolean {
        val otra = alternativa()
        return try {
            ApiClient.build(otra, token.value, readTimeoutSegundos = 10).health()
            fijarBase(otra)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    private suspend fun cargarBiblioteca(): Library {
        // Sin ninguna red no tiene sentido esperar los timeouts del servidor:
        // se va directo a lo descargado (rojo).
        if (!tieneRed()) {
            _estadoConexion.value = EstadoConexion.SIN_INTERNET
            _offline.value = true
            return bibliotecaLocal() ?: throw IOException("Sin conexion y sin canciones descargadas")
        }
        try {
            return guardarYEvolver(api().library())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        if (cambiarBaseSiResponde()) {
            return guardarYEvolver(api().library())
        }
        // Sin servidor: amarillo si hay internet, rojo si no lo hay. En ambos
        // casos solo lo que hay descargado en el telefono.
        _estadoConexion.value =
            if (hayInternet()) EstadoConexion.SIN_SERVIDOR else EstadoConexion.SIN_INTERNET
        _offline.value = true
        return bibliotecaLocal() ?: throw IOException("Sin conexion y sin canciones descargadas")
    }

    /** Biblioteca local: la ultima cache filtrada por lo descargado, o null. */
    private fun bibliotecaLocal(): Library? =
        LibraryCache.load(getApplication<Application>())
            ?.let { LibraryCache.soloDescargadas(it, _downloaded.value) }
            ?.takeIf { it.library.isNotEmpty() }

    private fun guardarYEvolver(lib: Library): Library {
        _offline.value = false
        _estadoConexion.value = EstadoConexion.ONLINE
        LibraryCache.save(getApplication<Application>(), lib)
        return lib
    }

    /** Descarga la pista; si falla por red, reintenta contra la otra direccion conocida. */
    private suspend fun descargarPista(context: Application, client: OkHttpClient, track: Track): Uri =
        try {
            SongDownloader.download(context, client, baseUrl.value, track, downloadTreeUri.value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!esDeRed(e)) throw e
            val otra = alternativa()
            val uri = SongDownloader.download(context, client, otra, track, downloadTreeUri.value)
            fijarBase(otra)
            uri
        }

    fun openSettings() {
        configured.value = false
    }

    fun artistById(id: String): Artist? = _library.value?.library?.find { it.id == id }
    fun albumById(id: String): Album? = _library.value?.library?.flatMap { it.albums }?.find { it.id == id }

    fun streamUrl(track: Track): String =
        baseUrl.value.trimEnd('/') + "/api/stream/" + track.id + "?token=" + Uri.encode(token.value)
    fun coverUrl(coverId: String?): String? =
        coverId?.let { baseUrl.value.trimEnd('/') + "/api/cover/" + it }

    // ------- descargas al telefono -------
    // Guardan la cancion en Musica/Espotifai/... del telefono (ver SongDownloader).

    private val downloadStore = DownloadStore(app)
    private val storeLock = Any()

    /** trackId -> uri local del fichero descargado (para reproducir sin red). */
    @Volatile
    private var localUris: Map<String, String> = downloadStore.all()

    private val _downloaded = MutableStateFlow(downloadStore.all().keys)
    val downloaded: StateFlow<Set<String>> = _downloaded

    /** Relee el registro de descargas (llamar con [storeLock] tomado). */
    private fun refrescarDescargas() {
        val todas = downloadStore.all()
        localUris = todas
        _downloaded.value = todas.keys
    }

    /** Ids de pistas que se estan bajando ahora mismo. */
    private val _downloadingTracks = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTracks: StateFlow<Set<String>> = _downloadingTracks

    /** albumId -> progreso visible, p. ej. "3/10". Solo mientras se descarga. */
    private val _albumProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val albumProgress: StateFlow<Map<String, String>> = _albumProgress

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage

    fun consumeDownloadMessage() {
        _downloadMessage.value = null
    }
    fun setTrackDownload(track: Track, download: Boolean) {
        try {
            if (download) downloadTracks(listOf(track), albumId = null, base = 0, total = 0)
            else removeDownloads(listOf(track))
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            CrashReporter.reportar(getApplication<Application>(), e)
            _downloadMessage.value = "Fallo al preparar la descarga: ${describir(e)}"
        }
    }

    fun setAlbumDownload(album: Album, download: Boolean) {
        if (album.tracks.isEmpty()) return
        try {
            if (download) {
                val pending = album.tracks.filter { !_downloaded.value.contains(it.id) }
                if (pending.isEmpty()) return
                downloadTracks(
                    pending,
                    albumId = album.id,
                    base = album.tracks.size - pending.size,
                    total = album.tracks.size
                )
            } else {
                removeDownloads(album.tracks.filter { _downloaded.value.contains(it.id) })
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            CrashReporter.reportar(getApplication<Application>(), e)
            _downloadMessage.value = "Fallo al preparar la descarga: ${describir(e)}"
        }
    }

    /** Descarga las pistas una detras de otra (para no saturar el servidor). */
    private fun downloadTracks(tracks: List<Track>, albumId: String?, base: Int, total: Int) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            var done = 0
            if (albumId != null) _albumProgress.value = _albumProgress.value + (albumId to "$base/$total")
            try {
                val client = ApiClient.buildOkHttp(token.value)
                for (t in tracks) {
                    _downloadingTracks.value = _downloadingTracks.value + t.id
                    try {
                        val uri = descargarPista(context, client, t)
                        synchronized(storeLock) {
                            downloadStore.put(t.id, uri.toString())
                            refrescarDescargas()
                        }
                    } catch (e: Throwable) {
                        // Throwable y no Exception: un Error tambien debe avisarse,
                        // no cerrar la app (se sube la traza al servidor).
                        if (e is CancellationException) throw e
                        if (!esDeRed(e)) CrashReporter.reportar(context, e)
                        _downloadMessage.value = "No se pudo descargar «${t.title}»: ${describir(e)}"
                    } finally {
                        _downloadingTracks.value = _downloadingTracks.value - t.id
                        done++
                        if (albumId != null) {
                            _albumProgress.value = _albumProgress.value + (albumId to "${base + done}/$total")
                        }
                    }
                }
            } catch (t2: Throwable) {
                if (t2 is CancellationException) throw t2
                CrashReporter.reportar(context, t2)
                _downloadMessage.value = "Fallo al descargar: ${describir(t2)}"
            } finally {
                if (albumId != null) _albumProgress.value = _albumProgress.value - albumId
            }
        }
    }

    private fun removeDownloads(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // primero se recogen las uris (con el bloqueo corto), luego se borra
                // los ficheros sin retener el bloqueo: asi no se puede bloquear el
                // hilo principal esperando a la E/S de disco.
                val guardadas = synchronized(storeLock) {
                    val todas = downloadStore.all()
                    tracks.mapNotNull { t -> todas[t.id]?.let { t.id to it } }
                }
                var fallos = 0
                guardadas.forEach { (id, uri) ->
                    if (!SongDownloader.delete(context, uri)) fallos++
                    synchronized(storeLock) {
                        downloadStore.remove(id)
                        refrescarDescargas()
                    }
                }
                if (fallos > 0) _downloadMessage.value = "No se pudo eliminar $fallos descarga(s)"
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                CrashReporter.reportar(context, e)
                _downloadMessage.value = "Fallo al borrar: ${describir(e)}"
            }
        }
    }

    // ------- reproduccion -------

    /** Reconstruye una pista a partir de un MediaItem (para recuperar la cola). */
    private fun trackFromMediaItem(item: MediaItem): Track? {
        if (item.mediaId.isBlank()) return null
        val md = item.mediaMetadata
        return Track(
            id = item.mediaId,
            title = md.title?.toString().orEmpty().ifBlank { "Desconocido" },
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            track = null,
            duration = 0L,
            bitrate = null
        )
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val base = baseUrl.value.trimEnd('/')
        // si esta descargada, se reproduce del fichero del telefono (funciona sin red)
        // El token va codificado: si lleva '#', '&', '+'... sin codificar, el '#' se
        // interpreta como fragmento y el servidor recibe un token truncado (401).
        val uri = localUris[track.id]
            ?: (base + "/api/stream/" + track.id + "?token=" + Uri.encode(token.value))
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
    }

    fun play(track: Track, queue: List<Track>) {
        val c = controller ?: return
        if (queue.isEmpty()) return
        _queue.value = queue
        val items = queue.map { buildMediaItem(it) }
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        _queueIndex.value = index
        _currentTrack.value = queue.getOrNull(index) ?: track
        _positionMs.value = 0
        _durationMs.value = 0
    }

    fun playAlbum(album: Album) {
        if (album.tracks.isEmpty()) return
        play(album.tracks.first(), album.tracks)
    }

    // ------- cola de reproduccion -------

    /** Añade pistas al final de la cola. Si no habia nada, empieza a sonar. */
    fun enqueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val c = controller ?: return
        val actual = _queue.value
        if (actual.isEmpty() || c.mediaItemCount == 0) {
            play(tracks.first(), tracks)
            return
        }
        c.addMediaItems(tracks.map { buildMediaItem(it) })
        _queue.value = actual + tracks
        toast(if (tracks.size == 1) "Añadida a la cola" else "${tracks.size} canciones añadidas a la cola")
    }

    /** Inserta pistas justo despues de la que esta sonando. */
    fun enqueueNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val c = controller ?: return
        val actual = _queue.value
        if (actual.isEmpty() || c.mediaItemCount == 0) {
            play(tracks.first(), tracks)
            return
        }
        val pos = (c.currentMediaItemIndex + 1).coerceIn(0, actual.size)
        c.addMediaItems(pos, tracks.map { buildMediaItem(it) })
        _queue.value = actual.toMutableList().apply { addAll(pos, tracks) }
        toast("Se reproducirá a continuación")
    }

    /** Salta a una pista de la cola. */
    fun playQueueAt(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        c.seekTo(index, 0L)
        c.play()
        _queueIndex.value = index
        _currentTrack.value = _queue.value[index]
        _positionMs.value = 0
        _durationMs.value = 0
    }

    /** Quita una pista de la cola. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        val actual = _queue.value
        if (index !in actual.indices) return
        val nuevo = actual.toMutableList().apply { removeAt(index) }
        c.removeMediaItem(index)
        _queue.value = nuevo
        if (nuevo.isEmpty()) {
            c.stop()
            c.clearMediaItems()
            _currentTrack.value = null
            _queueIndex.value = 0
            _isPlaying.value = false
            _positionMs.value = 0
            _durationMs.value = 0
        }
    }

    /** Vacia la cola y para la reproduccion. */
    fun clearQueue() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        _queue.value = emptyList()
        _queueIndex.value = 0
        _currentTrack.value = null
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        toast("Cola vaciada")
    }

    // ------- listas de reproduccion -------

    fun playlistById(id: String?): Playlist? =
        _playlists.value.firstOrNull { it.id == id }

    /**
     * Crea una lista. Si se pasa [track], la cancion entra ya en ella.
     * Devuelve el id o null si el nombre esta vacio.
     */
    fun createPlaylist(name: String, track: Track? = null): String? {
        val limpio = name.trim()
        if (limpio.isEmpty()) return null
        val lista = Playlist(
            id = UUID.randomUUID().toString(),
            name = limpio,
            tracks = if (track != null) listOf(track) else emptyList(),
            created = System.currentTimeMillis()
        )
        guardarPlaylists(_playlists.value + lista)
        toast(
            if (track != null) "«${track.title}» añadida a «$limpio»"
            else "Lista «$limpio» creada"
        )
        return lista.id
    }

    fun renamePlaylist(id: String, name: String) {
        val limpio = name.trim()
        if (limpio.isEmpty()) return
        guardarPlaylists(_playlists.value.map { if (it.id == id) it.copy(name = limpio) else it })
        toast("Lista renombrada")
    }

    fun deletePlaylist(id: String) {
        val lista = playlistById(id) ?: return
        guardarPlaylists(_playlists.value.filterNot { it.id == id })
        toast("Lista «${lista.name}» eliminada")
    }

    /** Añade una cancion a la lista. No duplica. */
    fun addToPlaylist(playlistId: String, track: Track): Boolean {
        val lista = playlistById(playlistId) ?: return false
        if (lista.tracks.any { it.id == track.id }) {
            toast("«${track.title}» ya está en «${lista.name}»")
            return false
        }
        guardarPlaylists(
            _playlists.value.map {
                if (it.id == playlistId) it.copy(tracks = it.tracks + track) else it
            }
        )
        toast("«${track.title}» añadida a «${lista.name}»")
        return true
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        guardarPlaylists(
            _playlists.value.map {
                if (it.id == playlistId) it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId })
                else it
            }
        )
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = playlist.tracks
        if (tracks.isEmpty()) {
            toast("La lista «${playlist.name}» está vacía")
            return
        }
        play(tracks.first(), tracks)
    }

    private fun guardarPlaylists(listas: List<Playlist>) {
        _playlists.value = listas
        playlistStore.save(listas)
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _positionMs.value = ms
    }

    private fun disconnect() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        _queue.value = emptyList()
        _queueIndex.value = 0
        _currentTrack.value = null
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        disconnect()
        super.onCleared()
    }

    private companion object {
        /** Mensaje corto y entendible para los errores de red. */
        fun describir(e: Throwable): String = when (e) {
            is UnknownHostException -> "no se encuentra el servidor (DNS)"
            is SocketTimeoutException -> "el servidor no responde"
            is ConnectException -> "no se puede conectar con el servidor"
            else -> "${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}"
        }

        /** Los fallos de red no son bugs de la app: no se suben como traza. */
        fun esDeRed(e: Throwable): Boolean =
            e is UnknownHostException || e is ConnectException || e is SocketTimeoutException
    }
}
