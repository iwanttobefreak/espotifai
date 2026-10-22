package com.espotifai.app

import android.content.Context
import android.os.Build
import com.espotifai.app.data.ApiClient
import com.espotifai.app.data.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recoge las trazas de fallo de la app y las sube al servidor
 * (`POST /api/clientlog`) para poder diagnosticarlas sin acceso al movil.
 *
 * - install(): cuando la app se cae, guarda la traza en `filesDir/crash/`.
 * - uploadPending(): en el siguiente arranque, sube y borra lo pendiente.
 * - reportar(): sube una traza concreta sin esperar a reiniciar.
 */
object CrashReporter {

    private fun dir(context: Context) = File(context.filesDir, "crash").apply { mkdirs() }

    fun install(context: Context) {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching { guardar(context, hilo, error) }
            previo?.uncaughtException(hilo, error)
        }
    }

    fun guardar(context: Context, hilo: Thread, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val sello = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val texto = buildString {
            append("APP CRASH $sello\n")
            append("app=${version(context)} android=${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE}) ")
            append("movil=${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("hilo=${hilo.name}\n")
            append(sw.toString())
        }
        File(dir(context), "crash-${System.currentTimeMillis()}.txt").writeText(texto)
    }

    /** Sube y borra las trazas pendientes. Llamar desde un hilo aparte. */
    fun uploadPending(context: Context) {
        val pendientes = dir(context).listFiles()?.filter { it.isFile } ?: return
        if (pendientes.isEmpty()) return
        val settings = Settings(context)
        if (!settings.isConfigured()) return
        val client = ApiClient.buildOkHttp(settings.token, readTimeoutSegundos = 20)
        for (f in pendientes) {
            try {
                val cuerpo = f.readText().toRequestBody("text/plain; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(settings.baseUrl.trimEnd('/') + "/api/clientlog")
                    .post(cuerpo)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) f.delete()
                }
            } catch (_: Throwable) {
                // se reintenta en el siguiente arranque
            }
        }
    }

    /** Guarda y sube una traza ahora mismo (por ejemplo, un fallo al descargar). */
    fun reportar(context: Context, error: Throwable) {
        runCatching { guardar(context, Thread.currentThread(), error) }
        Thread { uploadPending(context) }.start()
    }

    private fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
