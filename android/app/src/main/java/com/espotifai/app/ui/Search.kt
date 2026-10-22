package com.espotifai.app.ui

import com.espotifai.app.data.Album
import com.espotifai.app.data.Artist
import com.espotifai.app.data.Library
import com.espotifai.app.data.Track
import java.text.Normalizer

/** Album junto al artista al que pertenece (el modelo [Album] no guarda el artista). */
data class AlbumHit(val album: Album, val artistName: String)

/** Cancion junto a su album, para reproducirla con la cola correcta. */
data class SongHit(val track: Track, val album: Album, val artistName: String)

/** Resultado de una busqueda, ya agrupado para pintarlo por secciones. */
data class ResultadosBusqueda(
    val artistas: List<Artist>,
    val albumes: List<AlbumHit>,
    val canciones: List<SongHit>
) {
    val total: Int get() = artistas.size + albumes.size + canciones.size
    val vacio: Boolean get() = total == 0
}

private val ACENTOS = Regex("\\p{Mn}+")

/** Pasa a minusculas y quita acentos/diacriticos: "cancion" encuentra "canción". */
private fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(ACENTOS, "")
        .lowercase()

private fun coincide(texto: String?, consulta: String): Boolean =
    !texto.isNullOrBlank() && normalizar(texto).contains(consulta)

/**
 * Busca [consulta] en toda la biblioteca por coincidencia (no solo prefijo)
 * en nombre de artista, de album y en el titulo/artista de cada cancion.
 * Dentro de cada grupo, primero las coincidencias por prefijo.
 */
fun buscar(library: Library, consulta: String): ResultadosBusqueda {
    val q = normalizar(consulta.trim())
    if (q.isEmpty()) return ResultadosBusqueda(emptyList(), emptyList(), emptyList())

    val artistas = library.library
        .filter { coincide(it.name, q) }
        .sortedBy { !normalizar(it.name).startsWith(q) }

    val albumes = library.library
        .flatMap { artista -> artista.albums.map { AlbumHit(it, artista.name) } }
        .filter { coincide(it.album.name, q) }
        .sortedBy { !normalizar(it.album.name).startsWith(q) }

    val canciones = library.library
        .flatMap { artista -> artista.albums.map { album -> album to artista.name } }
        .flatMap { (album, artista) ->
            album.tracks
                .filter { coincide(it.title, q) || coincide(it.artist, q) }
                .map { SongHit(it, album, artista) }
        }
        .sortedBy { !normalizar(it.track.title).startsWith(q) }

    return ResultadosBusqueda(artistas, albumes, canciones)
}
