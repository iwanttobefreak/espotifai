package com.espotifai.app.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("espotifai_local", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", "") ?: ""
        set(value) = prefs.edit().putString("baseUrl", value).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    /**
     * Carpeta elegida para las descargas (uri de arbol SAF, permiso persistente).
     * Vacia = carpeta predeterminada `Musica/Espotifai` del almacenamiento interno.
     */
    var downloadTreeUri: String
        get() = prefs.getString("downloadTreeUri", "") ?: ""
        set(value) = prefs.edit().putString("downloadTreeUri", value).apply()

    fun isConfigured(): Boolean = baseUrl.isNotBlank() && token.isNotBlank()
}
