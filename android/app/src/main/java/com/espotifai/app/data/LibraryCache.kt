package com.espotifai.app.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Copia local de la ultima biblioteca que devolvio el servidor.
 *
 * Sirve para poder abrir la app sin conexion: de aqui se sacan los titulos,
 * artistas, albumes y caratulas de lo que hay descargado en el telefono,
 * filtrando por lo que esta en [DownloadStore].
 */
object LibraryCache {

    private const val FICHERO = "biblioteca.json"

    private fun fichero(context: Context) = File(context.filesDir, FICHERO)

    fun save(context: Context, library: Library) {
        runCatching { fichero(context).writeText(Gson().toJson(library)) }
    }

    fun load(context: Context): Library? {
        val f = fichero(context)
        if (!f.exists()) return null
        return runCatching { Gson().fromJson(f.readText(), Library::class.java) }.getOrNull()
    }

    /** Se queda solo con las pistas cuyo id esta en [ids] (las descargadas). */
    fun soloDescargadas(library: Library, ids: Set<String>): Library {
        val artistas = library.library.mapNotNull { artista ->
            val albumes = artista.albums.mapNotNull { album ->
                val pistas = album.tracks.filter { it.id in ids }
                if (pistas.isEmpty()) null else album.copy(tracks = pistas)
            }
            if (albumes.isEmpty()) null else artista.copy(albums = albumes)
        }
        val total = artistas.sumOf { a -> a.albums.sumOf { it.tracks.size } }
        return Library(library = artistas, totalTracks = total)
    }
}
