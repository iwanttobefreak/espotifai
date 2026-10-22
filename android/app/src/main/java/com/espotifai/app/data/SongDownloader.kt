package com.espotifai.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * Descarga pistas del servidor al telefono.
 *
 * Destino:
 * - Si el usuario ha elegido una carpeta con el explorador (SAF), se escribe ahi:
 *   `<carpeta>/ARTISTA/ALBUM/archivo.mp3`. Sirve para la tarjeta SD.
 * - Si no, carpeta predeterminada `Musica/Espotifai/...` del almacenamiento interno:
 *   MediaStore en Android 10+ (sin permisos) y fichero directo en 8/9.
 *
 * La ruta se copia de la del servidor: el id de la pista es la ruta relativa
 * en base64url, asi que `ARTISTA/ALBUM/01-titulo.mp3` se conserva tal cual.
 */
object SongDownloader {

    /** Carpeta propia dentro de "Musica" del telefono (destino predeterminado). */
    const val ROOT = "Espotifai"

    private val BAD_CHARS = Regex("""[\\/:*?"<>|]""")

    /** En Android 8/9 hay que pedir permiso de almacenamiento antes. */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Ruta relativa dentro de la carpeta de descargas: `ARTISTA/ALBUM/archivo.mp3`. */
    fun pathWithin(track: Track): String {
        val rel = decode(track.id)
        return if (rel.isNullOrBlank()) fallbackWithin(track) else rel.trim('/')
    }

    /** Ruta dentro de `Musica/` (predeterminada): `Espotifai/ARTISTA/ALBUM/archivo.mp3`. */
    fun relativePath(track: Track): String = "$ROOT/${pathWithin(track)}"

    /** Ruta visible para el usuario, sin el `Musica/` inicial. */
    fun displayPath(track: Track): String = relativePath(track).trim('/')

    /**
     * Descarga la pista y devuelve la uri donde ha quedado guardada.
     * @param treeUri carpeta elegida con SAF; vacia = destino predeterminado.
     * Bloqueante: llamar desde Dispatchers.IO.
     */
    suspend fun download(
        context: Context,
        client: OkHttpClient,
        baseUrl: String,
        track: Track,
        treeUri: String = ""
    ): Uri = withContext(Dispatchers.IO) {
        val rel = pathWithin(track)
        val fileName = rel.substringAfterLast('/')
        val dir = rel.substringBeforeLast('/', "")

        val url = baseUrl.trimEnd('/') + "/api/stream/" + track.id
        val request = Request.Builder().url(url).build() // el token lo pone el interceptor

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("el servidor ha devuelto HTTP ${resp.code}")
            val body: ResponseBody = resp.body ?: throw IOException("respuesta vacia")
            val ct = resp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            val mime = if (ct.startsWith("audio/")) ct else mimeOf(fileName)

            val tree = treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            when {
                // carpeta elegida y accesible (si han quitado la SD, se cae al predeterminado)
                tree != null && treeUsable(context, tree) ->
                    saveSaf(context, tree, dir, fileName, mime, body)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    saveMediaStore(context, dir, fileName, mime, body)
                else ->
                    saveLegacy(context, rel, mime, body)
            }
        }
    }

    /** Borra la descarga. Devuelve true si ya no esta. */
    fun delete(context: Context, uri: String): Boolean = try {
        val u = Uri.parse(uri)
        if (u.scheme == "content") {
            context.contentResolver.delete(u, null, null)
            true
        } else {
            val f = File(u.path ?: uri)
            !f.exists() || f.delete()
        }
    } catch (_: Exception) {
        false
    }

    // ---------- guardado ----------

    private fun treeUsable(context: Context, treeUri: Uri): Boolean = try {
        DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true
    } catch (_: Exception) {
        false
    }

    /** Escribe via SAF en la carpeta elegida: `<arbol>/dir/archivo`. */
    private fun saveSaf(
        context: Context,
        treeUri: Uri,
        dir: String,
        fileName: String,
        mime: String,
        body: ResponseBody
    ): Uri {
        val resolver = context.contentResolver
        var folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("carpeta de descargas no accesible")
        if (!folder.canWrite()) throw IOException("sin permiso de escritura en la carpeta")

        for (seg in dir.split('/').filter { it.isNotBlank() }) {
            folder = folder.findFile(seg)?.takeIf { it.isDirectory }
                ?: folder.createDirectory(seg)
                ?: throw IOException("no se pudo crear la carpeta '$seg'")
        }

        // si ya existe, se reemplaza
        folder.findFile(fileName)?.delete()
        val doc = folder.createFile(mime, fileName)
            ?: throw IOException("no se pudo crear el fichero")
        try {
            val out = resolver.openOutputStream(doc.uri)
                ?: throw IOException("no se pudo abrir el fichero")
            out.use { s -> body.byteStream().copyTo(s) }
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
        return doc.uri
    }

    private fun saveMediaStore(
        context: Context,
        dir: String,
        fileName: String,
        mime: String,
        body: ResponseBody
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$ROOT/$dir/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("no se pudo crear el fichero")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("no se pudo abrir el fichero")
            out.use { s -> body.byteStream().copyTo(s) }
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun saveLegacy(context: Context, rel: String, mime: String, body: ResponseBody): Uri {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val file = File(root, "$ROOT/$rel")
        file.parentFile?.mkdirs()
        try {
            file.outputStream().use { s -> body.byteStream().copyTo(s) }
        } catch (e: Exception) {
            runCatching { file.delete() }
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(file)
    }

    // ---------- utilidades ----------

    /** El id del servidor es la ruta relativa en base64url (sin relleno). */
    private fun decode(id: String): String? = try {
        val padded = id + "=".repeat((4 - id.length % 4) % 4)
        String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun fallbackWithin(track: Track): String {
        val num = track.track?.let { "$it-" } ?: ""
        val name = "${num}${track.title}".replace(BAD_CHARS, "-").trim()
        return "${track.artist.replace(BAD_CHARS, "-")}/${track.album.replace(BAD_CHARS, "-")}/$name.mp3"
    }

    private fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "audio/mpeg"
    }
}
