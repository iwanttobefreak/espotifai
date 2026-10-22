package com.espotifai.app.data

import android.content.Context
import com.google.gson.Gson

/**
 * Lista de reproduccion creada por el usuario.
 *
 * Se guardan las pistas completas (no solo el id) para que la lista siga
 * mostrando titulo/artista aunque la biblioteca no se haya podido cargar
 * (sin conexion) y para poder reproducir las descargadas.
 */
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val created: Long = 0L
)

/**
 * Persistencia local de las listas en SharedPreferences (JSON).
 *
 * Es local al telefono: sobrevive a reinicios y actualizaciones de la app,
 * pero no se sincroniza entre dispositivos. Al no tocar el servidor, las
 * listas funcionan tambien sin conexion.
 */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("espotifai_local", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<Playlist> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<Playlist>::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(playlists: List<Playlist>) {
        runCatching {
            prefs.edit().putString(KEY, gson.toJson(playlists.toTypedArray())).apply()
        }
    }

    companion object {
        private const val KEY = "listas_reproduccion"
    }
}
