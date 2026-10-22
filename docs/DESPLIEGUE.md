# Despliegue

## Frontal e instancias

- El **frontal** (`web/`) se sirve en `espotifai.es`: es estático (portada, docs y repo) y no lleva API.
- Cada **instancia** se despliega en `micasa.espotifai.es`, con un CNAME a tu DDNS o a la IP de tu casa:

```text
micasa.espotifai.es.  CNAME  tu-ddns.example.com.
```

- La instancia sirve la **app web** en `/`, la **API** en `/api/` y el **APK** en `/apk/`.

## Requisitos

- Docker y Docker Compose.
- Una carpeta con música organizada por artista/álbum.
- Opcional: un dominio y un certificado TLS.

## Opción A: sin nginx

```bash
cp .env.example .env
nano .env                 # API_TOKEN, MUSIC_DIR, PORT
docker compose up -d --build
```

El servidor queda en `http://IP:8088`. Es la opción más simple para la LAN.

## Opción B: con nginx y HTTPS (instancia)

Crea el CNAME antes de pedir el certificado. Después:

```bash
cd examples/con-nginx
cp .env.example .env
nano .env
nano nginx.conf           # cambia micasa.espotifai.es
docker compose up -d --build
```

1. Comprueba que el DNS del dominio apunta a la IP pública.
2. Emite el certificado:

   ```bash
   docker compose run --rm --entrypoint certbot certbot certonly \
     --webroot -w /var/www/certbot \
     -d micasa.espotifai.es \
     --email tu@email.com --agree-tos --no-eff-email
   ```

3. Descomenta el bloque `listen 443` de `nginx.conf` y reinicia:

   ```bash
   docker compose exec nginx nginx -t
   docker compose restart nginx
   ```

4. La renovación la hace el contenedor `certbot` cada 12 h.

> Si usas otro proxy delante (Traefik, Caddy, un nginx central…), no publiques los puertos 80/443 de este compose; conecta el proxy a la red y apunta a `espotifai:8088`.
>
> La app web se sirve desde `../../webapp`; el frontal del proyecto (`../../web`) es otra web estática y no va en este compose.

## Variables

| Variable | Obligatoria | Descripción |
|---|---|---|
| `API_TOKEN` | Sí en producción | Token admin. Sin él y sin usuarios, la API queda abierta. |
| `MUSIC_DIR` | Sí | Carpeta del host con la música. Se monta en `/music:ro`. |
| `PORT` | No (8088) | Puerto publicado en el host. |
| `EXCLUDE_DIRS` | No | Carpetas a ignorar, separadas por coma. |
| `INDEX_FILE` | No | Ruta del índice dentro del contenedor. |
| `USERS_FILE` | No | Ruta de `users.json` dentro del contenedor. |

## Actualizar

```bash
git pull
docker compose up -d --build
```

El índice y los usuarios viven en `data/` (volumen), así que sobreviven a la reconstrucción. Si cambias la estructura de la música, pulsa **Recargar** en la app o llama a `POST /api/rescan`.

## Copias de seguridad

Guarda:

- `data/index.json` (se puede regenerar).
- `data/users.json` (**importante**: contiene los tokens).
- `data/clientlog.txt` (opcional).
- La carpeta de música (fuera del repo).

No copies `.env` ni `data/` a un repositorio.

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `401 Unauthorized` | Token mal copiado, caducado o con caracteres especiales sin codificar en la URL | Usa la cabecera `Authorization: Bearer`; en el stream, codifica el token (`Uri.encode` en la app). |
| `404` en `/api/stream/...` | El fichero cambió de sitio o el id no es de esta biblioteca | Reescanea (`POST /api/rescan`) y recarga la biblioteca. |
| El reescaneo tarda mucho | Biblioteca grande y discos lentos | Es normal; usa timeout amplio en el cliente. El servidor solo lee metadatos. |
| La app no conecta fuera de casa | DNS, puerto cerrado o falta HTTPS | Comprueba `/api/health` desde fuera; revisa el proxy y el certificado. |
| El audio corta o no hace seek | Un proxy sin soporte de `Range` | No interceptes `Range`; nginx lo pasa tal cual. |
| `EACCES` al leer música | Permisos del contenedor | El proceso corre como `root` en la imagen; revisa los permisos del host. |
| La app se cierra al cargar la biblioteca | Un MP3 con metadatos corruptos | El servidor acota `duration`/`bitrate`; si persiste, aparta el fichero y reescanea. |

## Seguridad

- Cambia `API_TOKEN` antes de exponer el servicio.
- Usa HTTPS fuera de la LAN.
- Crea un token por dispositivo/persona y revócalo con `DELETE /api/users/:name`.
- No publiques `data/` ni `.env`.
- Si el servidor está expuesto, valora una VPN o una allowlist en el proxy.
- El endpoint `/api/clientlog` puede contener información del dispositivo; no lo hagas público.
