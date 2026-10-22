package com.espotifai.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.espotifai.app.BuildConfig
import com.espotifai.app.MusicViewModel
import com.espotifai.app.ui.theme.EspotifaiGreen

@Composable
fun SettingsScreen(vm: MusicViewModel) {
    val baseUrl by vm.baseUrl.collectAsState()
    val token by vm.token.collectAsState()
    val error by vm.error.collectAsState()

    var url by remember { mutableStateOf(baseUrl) }
    var tok by remember { mutableStateOf(token) }

    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    val treeUri by vm.downloadTreeUri.collectAsState()
    val nombreCarpeta = remember(treeUri, context) {
        if (treeUri.isBlank()) "" else runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name
        }.getOrNull() ?: "carpeta elegida"
    }
    // Explorador de carpetas (SAF). El permiso se persiste para poder escribir
    // en esa carpeta (p. ej. la tarjeta SD) en futuras descargas.
    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setDownloadTreeUri(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = EspotifaiGreen,
            modifier = Modifier.height(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Espotifai", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Conecta con tu servidor de musica",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del servidor") },
            placeholder = { Text(BuildConfig.URL_CASA) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tok,
            onValueChange = { tok = it },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Text(
            "Carpeta de descargas",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (treeUri.isBlank()) "Música/Espotifai (predeterminada)"
            else "Carpeta elegida: $nombreCarpeta",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Los mp3 se guardan en ARTISTA/ÁLBUM/… dentro de esa carpeta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                val inicial = treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                elegirCarpeta.launch(inicial)
            }) {
                Text("Elegir carpeta…")
            }
            if (treeUri.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(treeUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    vm.setDownloadTreeUri("")
                }) {
                    Text("Predeterminada")
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { vm.saveSettings(url.trim(), tok.trim()) },
            enabled = url.isNotBlank() && tok.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y conectar")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Version $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
