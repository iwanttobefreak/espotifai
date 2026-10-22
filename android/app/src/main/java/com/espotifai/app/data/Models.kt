package com.espotifai.app.data

data class Library(
    val library: List<Artist>,
    val totalTracks: Int
)

data class Artist(
    val id: String,
    val name: String,
    val albums: List<Album>
)

data class Album(
    val id: String,
    val name: String,
    val cover: String?,
    val tracks: List<Track>
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val track: Int?,
    // duration/bitrate en Long a proposito: un MP3 corrupto puede devolver
    // valores que no caben en Int (p. ej. bitrate 46044718001) y reventaban
    // el parseo de TODA la biblioteca con NumberFormatException.
    val duration: Long,
    val bitrate: Long?
)

/**
 * Estado de conexion con el servidor, para el semaforo de la app:
 * - [ONLINE] verde: el servidor responde.
 * - [SIN_SERVIDOR] amarillo: hay internet pero el servidor no contesta.
 * - [SIN_INTERNET] rojo: el telefono no tiene red.
 *
 * En [SIN_SERVIDOR] y [SIN_INTERNET] la app muestra solo las canciones
 * descargadas en el telefono.
 */
enum class EstadoConexion { ONLINE, SIN_SERVIDOR, SIN_INTERNET }
