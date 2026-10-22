package com.espotifai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.espotifai.app.MusicViewModel
import com.espotifai.app.data.SongDownloader

/**
 * Devuelve una accion que pide el permiso de almacenamiento si hace falta
 * (solo Android 8/9) y una vez concedido ejecuta [onGranted].
 * En Android 10+ no se pide nada: la app escribe en su propia carpeta
 * dentro de "Musica" via MediaStore.
 */
@Composable
fun rememberStoragePermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            onGranted()
        } else {
            Toast.makeText(
                context,
                "Sin permiso de almacenamiento no se puede descargar",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    return {
        when {
            !SongDownloader.needsPermission() -> onGranted()
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> onGranted()
            else -> launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

/** Muestra en un snackbar el ultimo aviso de descarga (errores, permisos...). */
@Composable
fun DownloadMessageHost(vm: MusicViewModel, hostState: SnackbarHostState) {
    val message by vm.downloadMessage.collectAsState()
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        hostState.showSnackbar(m)
        // solo se consume si no ha llegado otro aviso mientras se mostraba
        if (vm.downloadMessage.value == m) vm.consumeDownloadMessage()
    }
}
