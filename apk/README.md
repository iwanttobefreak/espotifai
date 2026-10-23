# Versiones de la app

`versions.json` es el manifiesto de versiones: nombre, `versionCode`, fichero, `sha256`, tamaño y fecha.

Los **binarios APK no se guardan en este repo público**: se publican en la carpeta `/apk/` de la
instancia (por ejemplo, `https://tu-instancia.espotifai.es/apk/`). Así el repo no expone datos del
dispositivo, del paquete privado ni de la instancia real.

## Publicar una versión

```bash
APK_PUBLIC_DIR=/ruta/a/apk \
  scripts/publicar-apk.sh app/build/outputs/apk/release/app-release.apk 1.14 16
```

El script:

1. Copia el APK a `<APK_PUBLIC_DIR>/espotifai-v<code>-<version>.apk` y a `espotifai.apk` (última).
2. Calcula `sha256` y tamaño, y actualiza `apk/versions.json` (sin URLs personales).
3. Hace commit del manifiesto y crea el tag `v<version>`.
4. Intenta `git push origin main --tags` si hay remoto.

La carpeta pública debe ser la que sirve nginx en `/apk/` de la instancia. El repo público solo
contiene el manifiesto y el historial de versiones en git (tags), no los APK.
