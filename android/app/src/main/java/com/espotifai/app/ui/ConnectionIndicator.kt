package com.espotifai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.espotifai.app.MusicViewModel
import com.espotifai.app.data.EstadoConexion
import com.espotifai.app.ui.theme.EspotifaiGreen

val AmbarConexion = Color(0xFFFFB300)
val RojoConexion = Color(0xFFE53935)

/** Color y etiqueta del semaforo para cada estado. */
fun colorYEtiqueta(estado: EstadoConexion): Pair<Color, String> = when (estado) {
    EstadoConexion.ONLINE -> EspotifaiGreen to "Online"
    EstadoConexion.SIN_SERVIDOR -> AmbarConexion to "Sin servidor"
    EstadoConexion.SIN_INTERNET -> RojoConexion to "Sin internet"
}

/**
 * Semaforo de conexion: punto de color + etiqueta.
 * verde = online, amarillo = sin servidor, rojo = sin internet.
 */
@Composable
fun ConnectionStatusIndicator(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val estado by vm.estadoConexion.collectAsState()
    val (color, etiqueta) = colorYEtiqueta(estado)
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = "Estado: $etiqueta" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
