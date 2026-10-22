# Con nginx y HTTPS

Stack de tres contenedores: `espotifai` (Node), `nginx` (TLS, app web, `/apk/` y proxy de la API) y `certbot` (renovación).

## 1. Configurar

```bash
cp .env.example .env
nano .env                  # API_TOKEN y MUSIC_DIR
nano nginx.conf            # cambia micasa.espotifai.es por tu dominio
```

Crea antes el CNAME del dominio a tu DDNS (o un A a la IP de casa). Ejemplo: `micasa.espotifai.es CNAME tu-ddns.example.com.`

## 2. Arrancar

```bash
docker compose up -d --build
```

En este punto responde por HTTP:

```bash
curl http://micasa.espotifai.es/api/health
```

## 3. Emitir el certificado (una sola vez)

```bash
docker compose run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot \
  -d micasa.espotifai.es \
  --email tu@email.com --agree-tos --no-eff-email
```

Descomenta el bloque `server { listen 443 ... }` de `nginx.conf` y recarga:

```bash
docker compose exec nginx nginx -t
docker compose restart nginx
```

Comprueba:

```bash
curl -I https://micasa.espotifai.es/api/health
```

La renovación la hace el contenedor `certbot` cada 12 h. Los certificados quedan en `./certbot/conf/` (ignorado por git).

## Notas

- La app web se monta desde `../../webapp`. El frontal del proyecto (`../../web`) es otra web estática y no va en este compose.
- Los APK se sirven desde `webapp/apk/` (no se versionan; `*.apk` está en `.gitignore`).
- Si pones el servidor detrás de otro proxy, no publiques los puertos 80/443 de este compose.
