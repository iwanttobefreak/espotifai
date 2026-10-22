package com.espotifai.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Registro de las canciones que ya estan descargadas en el telefono.
 *
 * Clave: id de la pista (el mismo que usa el servidor).
 * Valor: uri donde se guardo el fichero (content:// de MediaStore en
 * Android 10+, file:// en Android 8/9). Hace falta para poder borrarlo
 * despues desde el checkbox.
 */
class DownloadStore(context: Context) {
    private val prefs = context.getSharedPreferences("espotifai_local", Context.MODE_PRIVATE)

    fun all(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (v.isNotBlank()) out[k] = v
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun put(id: String, uri: String) {
        val obj = JSONObject()
        all().forEach { (k, v) -> obj.put(k, v) }
        obj.put(id, uri)
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    fun remove(id: String) {
        val obj = JSONObject()
        all().forEach { (k, v) -> if (k != id) obj.put(k, v) }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    companion object {
        private const val KEY = "descargadas"
    }
}
