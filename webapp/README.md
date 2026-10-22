# App web

Reproductor web de una instancia de Espotifai. Es un **único `index.html`** con HTML, CSS y JavaScript: no hay build, ni dependencias, ni CDNs.

## Qué hace

- Biblioteca por artista/álbum, búsqueda sin acentos y reproductor con seek.
- Cola de reproducción y listas (guardadas en `localStorage`).
- **Descargar álbum**: pide `GET /api/album/:id/zip` y el navegador guarda el ZIP. **No hay modo offline**: no se guarda audio en el navegador.
- Ajustes para cambiar servidor y token; el token se guarda solo en este navegador.

## Cómo se sirve

En una instancia, nginx sirve este fichero en `/` y proxya `/api/` al servidor Node. El ejemplo completo está en [`../examples/con-nginx/`](../examples/con-nginx/):

```
location = / { root /usr/share/nginx/html/espotifai-app; try_files /index.html =404; }
location /api/ { proxy_pass http://espotifai:8088; ... }
location / { root /usr/share/nginx/html/espotifai-app; try_files $uri /index.html; }
```

No la sirvas desde `file://`: el navegador necesita el mismo origen para `/api/` y para las URLs de audio/carátulas.

## Token

La primera vez pide:

- **Servidor**: por defecto el mismo origen (`https://micasa.espotifai.es`).
- **Token**: el `API_TOKEN` (o un token de usuario) de esa instancia.

Se guarda en `localStorage` (`espotifai.token`, `espotifai.server`). Las peticiones JSON usan `Authorization: Bearer`; el audio, las carátulas y el ZIP usan `?token=` porque los elementos multimedia no permiten cabeceras.

## Seguridad

- No pongas el token en el HTML ni en el repo.
- Usa HTTPS fuera de la LAN.
- El token queda en el navegador: si compartes el dispositivo, usa **Cerrar sesión**.
- La descarga es un ZIP generado por el servidor; no se cachea audio en el navegador.
