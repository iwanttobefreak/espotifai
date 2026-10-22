# App Android

Cliente de Espotifai: Kotlin + Jetpack Compose + Media3/ExoPlayer.

## Requisitos

- JDK 17.
- Android SDK 34 (compileSdk 34, minSdk 26).
- Opcional: Docker para compilar sin instalar el SDK.

## Compilar

Con Android Studio: abre esta carpeta y `Build > Build APK(s)`.

Por terminal:

```bash
./gradlew assembleRelease
# APK: app-release.apk (firmado) o app-release-unsigned.apk (sin keystore)
```

Con Docker (sin instalar Java/SDK en el host):

```bash
docker run --rm --name espotifai-apk --memory 2g --cpus 1.5 \
  -v "$PWD":/app -v espotifai-gradle-home:/root/.gradle \
  -w /app android-builder bash -c 'nice -n 15 ./gradlew --no-daemon assembleRelease'
```

La imagen `android-builder` debe tener JDK 17 + Android SDK 34. Puedes construir una propia a partir de `gradle:8-jdk17` instalando `sdkmanager`.

## Configurar las direcciones del servidor

La app usa dos direcciones y salta de una a otra si la configurada no responde:

| Propiedad | Por defecto | Uso |
|---|---|---|
| `espotifai.urlCasa` | `http://192.0.2.10:8088` | LAN (sin DNS). |
| `espotifai.urlFuera` | `https://music.example.com` | Fuera de casa (HTTPS). |

Se pasan al compilar:

```bash
./gradlew assembleRelease \
  -Pespotifai.urlCasa=http://192.0.2.10:8088 \
  -Pespotifai.urlFuera=https://music.midominio.com
```

También se pueden cambiar en **Ajustes** dentro de la app.

## Firma

1. Copia `keystore.properties.example` a `keystore.properties`.
2. Genera tu keystore:

   ```bash
   keytool -genkeypair -v -keystore keystore/espotifai-release.jks \
     -alias espotifai -keyalg RSA -keysize 2048 -validity 10000
   ```

3. Rellena `keystore.properties`.

Sin `keystore.properties`, `assembleRelease` genera un APK sin firmar (útil para probar).

## Funciones

- Biblioteca por artista/álbum y búsqueda sin acentos.
- Reproductor con carátula, seek, play/pause y salto.
- Cola: añadir, reproducir a continuación, saltar, quitar y vaciar.
- Listas: crear varias, renombrar, eliminar, añadir canciones y reproducir.
- Descargas al teléfono (MediaStore o carpeta SAF), reproducción offline.
- Caché de la última biblioteca para abrir sin conexión.
- Semáforo de conexión y fallback entre LAN y dominio público.

## Estructura

```
app/src/main/java/com/espotifai/app/
├── MusicViewModel.kt        # estado y lógica
├── MainActivity.kt          # navegación Compose
├── playback/                # MediaSessionService + ExoPlayer
├── data/                    # API, modelos, ajustes, descargas, listas
└── ui/                      # pantallas y componentes
```

## Notas

- En la LAN se permite HTTP (`usesCleartextTraffic`); fuera de casa usa HTTPS.
- No hay telemetría. El único envío opcional es el log de fallos (`/api/clientlog`), que se puede desactivar no usando la app o bloqueando esa ruta en el proxy.
- Las listas son locales al teléfono; no se sincronizan entre dispositivos.
