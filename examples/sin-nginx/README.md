# Sin nginx

Servidor Espotifai expuesto directamente en el puerto 8088.

```bash
cp .env.example .env
nano .env                 # API_TOKEN y MUSIC_DIR
docker compose up -d --build
```

Comprobación:

```bash
curl http://localhost:8088/api/health
curl -H "Authorization: Bearer $API_TOKEN" http://localhost:8088/api/library
```

La app se configura con `http://IP_DEL_SERVIDOR:8088` y el token.

> Si lo expones a Internet sin nginx, no hay TLS. Úsalo solo en la LAN o ponlo detrás de un proxy con HTTPS.

> Este ejemplo no sirve la app web. Para la app web usa `../con-nginx/` o sirve `webapp/index.html` con un servidor estático.
